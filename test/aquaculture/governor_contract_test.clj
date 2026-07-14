(ns aquaculture.governor-contract-test
  "Contract tests for the AquacultureOperationsGovernor."
  (:require [clojure.test :refer [deftest is]]
            [aquaculture.governor :as governor]
            [aquaculture.store :as store]))

(deftest effect-mismatch-detection
  (let [st (store/mem-store)
        req {:op :log-stock-record :site-id "farm-001"}
        prop {:effect :commit}]
    (is (some #(= :effect-mismatch (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest facility-verification-required
  (let [st (store/mem-store)
        req {:op :log-stock-record :site-id "nonexistent"}
        prop {:effect :propose :value {}}]
    (is (some #(= :facility-not-found (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest facility-must-be-active
  (let [inactive-facility {:site-id "farm-001" :status :inactive}
        st (store/mem-store {:fixtures {"farm-001" inactive-facility}})
        req {:op :log-stock-record :site-id "farm-001"}
        prop {:effect :propose :value {}}]
    (is (some #(= :facility-inactive (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest only-propose-effects-allowed
  (let [active-facility {:site-id "farm-001" :status :active}
        st (store/mem-store {:fixtures {"farm-001" active-facility}})
        req {:op :log-stock-record :site-id "farm-001"}
        prop {:effect :commit :value {}}]
    (is (some #(= :non-propose-effect (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest blocked-operations-rejected
  (let [active-facility {:site-id "farm-001" :status :active}
        st (store/mem-store {:fixtures {"farm-001" active-facility}})
        req {:op :administer-treatment :site-id "farm-001"}
        prop {:effect :propose :value {:treatment "antibiotic"}}]
    (is (some #(= :blocked-operation (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest forbidden-fields-blocked
  (let [active-facility {:site-id "farm-001" :status :active}
        st (store/mem-store {:fixtures {"farm-001" active-facility}})
        req {:op :log-stock-record :site-id "farm-001"}
        prop {:effect :propose :value {:site-id "farm-002"}}]
    (is (some #(= :forbidden-field (:rule %))
              (:violations (governor/check req nil prop st))))))

(deftest health-concern-triggers-escalation
  (let [active-facility {:site-id "farm-001" :status :active}
        st (store/mem-store {:fixtures {"farm-001" active-facility}})
        req {:op :flag-stock-health-concern :site-id "farm-001"}
        prop {:effect :propose :value {:concern "algal-bloom"}}
        verdict (governor/check req nil prop st)]
    (is (:escalate? verdict))
    (is (some #(= :health-concern-escalation (:rule %)) (:soft-flags verdict)))))

(deftest high-cost-supply-escalates
  (let [active-facility {:site-id "farm-001" :status :active}
        st (store/mem-store {:fixtures {"farm-001" active-facility}})
        req {:op :order-supplies :site-id "farm-001"}
        prop {:effect :propose :value {:item "feed" :estimated-cost 15000}}
        verdict (governor/check req nil prop st)]
    (is (:escalate? verdict))
    (is (some #(= :high-cost-escalation (:rule %)) (:soft-flags verdict)))))

(deftest low-confidence-escalates
  (let [active-facility {:site-id "farm-001" :status :active}
        st (store/mem-store {:fixtures {"farm-001" active-facility}})
        req {:op :log-stock-record :site-id "farm-001"}
        prop {:effect :propose :value {} :confidence 0.4}
        verdict (governor/check req nil prop st)]
    (is (:escalate? verdict))))

(deftest clean-proposal-passes
  (let [active-facility {:site-id "farm-001" :status :active}
        st (store/mem-store {:fixtures {"farm-001" active-facility}})
        req {:op :log-stock-record :site-id "farm-001"}
        prop {:effect :propose :value {:count 1000} :confidence 0.95}
        verdict (governor/check req nil prop st)]
    (is (:ok? verdict))
    (is (not (:hard? verdict)))
    (is (not (:escalate? verdict)))))
