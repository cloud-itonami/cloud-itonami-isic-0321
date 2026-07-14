(ns aquaculture.governor
  "AquacultureOperationsGovernor -- independent compliance layer for marine
  aquaculture facility operations coordination. The Advisory-LLM has no notion
  of facility registration, safety thresholds, or escalation conditions, so
  this MUST be a separate system able to reject a proposal and fall back to HOLD.

  Governance Rules (all HARD, no overrides):

  1. Facility verification: facility/site record must be verified and registered
     before ANY action. :site-id must exist in store and have :status :active.

  2. Effect matches op: proposal :effect must match the ONE legitimate effect
     for the request :op (see `op->effect`).

  3. Effect only :propose: all proposals have `:effect :propose` only -- this
     actor never commits direct facility operations or treatment decisions,
     which remain exclusive human authority.

  4. Escalation rules:
     - :flag-stock-health-concern ALWAYS escalates (human sign-off mandatory).
     - supply orders above cost threshold (default 10000 units) escalate.
     - low advisor confidence (< 0.6) escalates.

  Blocked permanently (structural holds):
  - Any proposal for direct animal treatment administration.
  - Any proposal for harvest/mortality decisions without explicit facility mgr
  - Any proposal modifying :site-id or :facility-license fields."
  (:require [aquaculture.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 10000)

(def high-stakes
  "Stakes grave enough to always require human escalation."
  #{:health-concern :treatment-decision :harvest-decision})

;; ----------------------------- checks -----------------------------

(def op->effect
  "The ONE legitimate :effect a proposal may declare for each op."
  {:log-stock-record          :propose
   :schedule-facility-maintenance :propose
   :flag-stock-health-concern :propose
   :order-supplies            :propose})

(defn- facility-verification-violations
  "HARD: facility must be verified and active before any action."
  [{:keys [op site-id]} proposal st]
  (when (and op site-id)
    (let [facility (store/facility st site-id)]
      (cond
        (nil? facility)
        [{:rule :facility-not-found
          :detail "登録されていない施設 ID での提案"}]
        (not= :active (:status facility))
        [{:rule :facility-inactive
          :detail "非アクティブな施設での操作提案"}]
        :else nil))))

(defn- effect-mismatch-violations
  "HARD, checked first: effect must match op."
  [{:keys [op]} proposal]
  (when-let [expected (op->effect op)]
    (when (not= expected (:effect proposal))
      [{:rule :effect-mismatch
        :detail (str "op " op " の提案は :effect " expected
                     " のはずが実際には " (:effect proposal) " になっている")}])))

(defn- only-propose-violations
  "HARD: all effects must be :propose (coordination only, no direct actuation)."
  [{:keys [op]} proposal]
  (when (and op (not= :propose (:effect proposal)))
    [{:rule :non-propose-effect
      :detail "この actor では :effect は :propose のみ。直接操作系は人間の authority に留める"}]))

(defn- forbidden-fields-violations
  "HARD: certain fields are never modifiable via proposals."
  [{:keys [op site-id]} proposal]
  (let [forbidden #{:site-id :facility-license :facility-status}
        patch-fields (when-let [v (:value proposal)] (set (keys v)))
        found (seq (filter forbidden patch-fields))]
    (when found
      [{:rule :forbidden-field
        :detail (str "変更禁止フィールドが含まれている: " (vec found))}])))

(defn- blocked-operation-violations
  "HARD: certain operations are permanently blocked."
  [{:keys [op]} _proposal]
  (when (contains? #{:administer-treatment :decide-harvest :decide-cull} op)
    [{:rule :blocked-operation
      :detail "この操作は人間の veterinarian / facility manager の exclusive authority です"}]))

(defn- stock-health-escalation-violations
  "SOFT: :flag-stock-health-concern ALWAYS escalates."
  [{:keys [op]} _proposal]
  (when (= op :flag-stock-health-concern)
    [{:rule :health-concern-escalation
      :detail "健康懸念フラグは必ず人間の確認で escalate されます"}]))

(defn- supply-cost-escalation-violations
  "SOFT: supply orders above threshold escalate."
  [{:keys [op]} proposal]
  (when (= op :order-supplies)
    (let [cost (get-in proposal [:value :estimated-cost] 0)]
      (when (>= cost supply-cost-threshold)
        [{:rule :high-cost-escalation
          :detail (str "コスト " cost " 超過。人間の確認で escalate 必須")}]))))

(defn check
  "Censors an Advisor proposal against the aquaculture governor rules.
   Returns {:ok? bool :violations [..] :confidence c :escalate? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (effect-mismatch-violations request proposal)
                           (facility-verification-violations request proposal st)
                           (only-propose-violations request proposal)
                           (forbidden-fields-violations request proposal)
                           (blocked-operation-violations request proposal)))
        soft (into []
                   (concat (stock-health-escalation-violations request proposal)
                           (supply-cost-escalation-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        hard? (boolean (seq hard))
        soft? (boolean (seq soft))]
    {:ok?          (and (not hard?) (not low?) (not soft?))
     :violations   hard
     :soft-flags   soft
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? soft?))}))

(defn hold-fact
  "Audit fact for a rejected proposal (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :soft-flags (:soft-flags verdict)
   :confidence (:confidence verdict)})
