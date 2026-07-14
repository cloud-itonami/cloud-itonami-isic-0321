(ns aquaculture.operation
  "OperationActor -- one aquaculture operation = one supervised actor run,
  expressed as a langgraph-clj StateGraph. The advisor (Aquaculture-LLM) is
  sealed into a single node (:advise); its proposal is ALWAYS routed through
  the AquacultureGovernor (:govern) before anything commits to the SSoT.

  One graph run = one operation (request -> advise -> govern -> decide -> commit | hold).
  No unbounded inner loop -- each operation is auditable and checkpointed.

  All proposals have :effect :propose (coordination only, no direct facility
  operation or treatment authority). Escalation points: health concerns
  (always escalate), high-cost supply orders, low confidence."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [aquaculture.llm-advisor :as advisor]
            [aquaculture.governor :as governor]
            [aquaculture.store :as store]))

(defn- commit-fact
  "Ledger fact for a committed operation."
  [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:effect  (:effect proposal)
   :site-id (:site-id request)
   :op      (:op request)
   :value   (or (:value proposal) {})})

(defn build
  "Compiles an OperationActor graph bound to `store`.
  opts:
    :advisor      -- an Advisor (default: mock-advisor)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; Aquaculture LLM inference -- proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advisor/advise advisor store request)]
            {:proposal p :audit [(advisor/trace request p)]})))

      ;; AquacultureGovernor -- independent censor.
      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      ;; Decide: governor disposition.
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [{:keys [hard? escalate?]} verdict]
            (cond
              hard?
              {:disposition :hold
               :audit [(governor/hold-fact request context verdict)]}

              escalate?
              {:disposition :escalate
               :audit [{:t :advisor-escalation
                        :op (:op request)
                        :site-id (:site-id request)
                        :soft-flags (:soft-flags verdict)
                        :confidence (:confidence verdict)}]}

              :else
              {:disposition :commit
               :record (commit-record request context proposal)
               :audit [(commit-fact request context proposal)]}))))

      ;; Commit: apply to store or hold.
      (g/add-node :finalize
        (fn [{:keys [disposition record]}]
          (if (= disposition :commit)
            {:disposition disposition}
            {:disposition disposition})))

      ;; Edges: linear flow with early exit on hold.
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)
      (g/add-edge :decide :finalize)

      ;; Terminal states.
      (g/set-finish-point :finalize)

      (g/compile {:checkpointer checkpointer})))
