(ns aquaculture.operation-test
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [aquaculture.store :as store]
            [aquaculture.operation :as operation]))

(defn- run-op! [store request]
  (g/run* (operation/build store) {:request request :context {:actor-id "op-1"}}
          {:thread-id (str (random-uuid))}))

(def ^:private active-facility
  {:site-id "farm-001" :name "Test Farm" :status :active})

(deftest log-stock-record-commits-and-persists
  (testing "a clean proposal against a verified facility commits and actually writes to the store
  -- previously operation.cljc called the nonexistent langgraph.graph/compile (real name:
  compile-graph), so this graph had never been runnable at all, and :finalize was a no-op even
  when it did compile"
    (let [st (store/mem-store {:fixtures {"farm-001" active-facility}})
          result (run-op! st {:op :log-stock-record :site-id "farm-001" :value {:count 5000}})]
      (is (= :done (:status result)))
      (is (= :commit (get-in result [:state :disposition])))
      (is (contains? (store/facility st "farm-001") :last-stock-record))
      (is (= 5000 (get-in (store/facility st "farm-001") [:last-stock-record :count])))
      (is (= 1 (count (store/audit-log st "farm-001")))))))

(deftest schedule-maintenance-commits-and-persists
  (let [st (store/mem-store {:fixtures {"farm-001" active-facility}})
        result (run-op! st {:op :schedule-facility-maintenance :site-id "farm-001"
                          :value {:task "net-inspection"}})]
    (is (= :commit (get-in result [:state :disposition])))
    (is (= 1 (count (get-in (store/facility st "farm-001") [:pending-maintenance]))))))

(deftest unverified-facility-holds-without-persisting
  (let [st (store/mem-store {})
        result (run-op! st {:op :log-stock-record :site-id "nonexistent" :value {:count 5000}})]
    (is (= :done (:status result)))
    (is (= :hold (get-in result [:state :disposition])))
    (is (empty? (store/audit-log st "nonexistent")))))

(deftest health-concern-always-escalates-without-persisting
  (testing "the mock advisor forwards the request's :value into the proposal (previously it
  hardcoded {:op op}, which would have silently defeated the governor's cost-threshold
  escalation check regardless of what was actually proposed)"
    (let [st (store/mem-store {:fixtures {"farm-001" active-facility}})
          result (run-op! st {:op :flag-stock-health-concern :site-id "farm-001"
                            :value {:concern "elevated mortality"}})]
      (is (= :done (:status result)))
      (is (= :escalate (get-in result [:state :disposition])))
      (is (empty? (get-in (store/facility st "farm-001") [:health-concerns]))
          "escalated, not yet committed -- this actor has no resume/approval path, so an
          escalated proposal never reaches the store"))))

(deftest high-cost-supply-order-escalates-without-persisting
  (let [st (store/mem-store {:fixtures {"farm-001" active-facility}})
        result (run-op! st {:op :order-supplies :site-id "farm-001"
                          :value {:item "feed" :estimated-cost 15000}})]
    (is (= :escalate (get-in result [:state :disposition])))
    (is (empty? (get-in (store/facility st "farm-001") [:supply-orders])))))

(deftest low-cost-supply-order-commits-and-persists
  (let [st (store/mem-store {:fixtures {"farm-001" active-facility}})
        result (run-op! st {:op :order-supplies :site-id "farm-001"
                          :value {:item "feed" :estimated-cost 500}})]
    (is (= :commit (get-in result [:state :disposition])))
    (is (= 1 (count (get-in (store/facility st "farm-001") [:supply-orders]))))))
