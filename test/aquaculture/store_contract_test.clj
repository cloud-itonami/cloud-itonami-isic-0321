(ns aquaculture.store-contract-test
  "Contract tests for the aquaculture Store."
  (:require [clojure.test :refer [deftest is]]
            [aquaculture.store :as store]))

(deftest facility-lookup
  (let [f {:site-id "farm-001" :name "Test Farm" :status :active}
        st (store/mem-store {:fixtures {"farm-001" f}})]
    (is (= f (store/facility st "farm-001")))
    (is (nil? (store/facility st "nonexistent")))))

(deftest log-stock-record
  (let [st (store/mem-store {:fixtures {"farm-001" {:site-id "farm-001" :status :active}}})
        record {:count 1000 :weight-kg 8500}
        st2 (store/log-stock! st "farm-001" record)]
    (is (contains? (store/facility st2 "farm-001") :last-stock-record))
    (is (= (:count (get-in (store/facility st2 "farm-001") [:last-stock-record]))
           1000))))

(deftest audit-trail
  (let [st (store/mem-store {:fixtures {"farm-001" {:site-id "farm-001" :status :active}}})
        st1 (store/log-stock! st "farm-001" {:count 1000})
        st2 (store/flag-health-concern! st1 "farm-001" {:concern "algal-bloom"})
        log (store/audit-log st2 "farm-001")]
    (is (= 2 (count log)))
    (is (some #(= :stock-logged (:type %)) log))
    (is (some #(= :health-concern-flagged (:type %)) log))))

(deftest schedule-maintenance
  (let [st (store/mem-store {:fixtures {"farm-001" {:site-id "farm-001" :status :active}}})
        maint {:type :filter-replacement :scheduled-date "2024-07-20"}
        st2 (store/schedule-maintenance! st "farm-001" maint)]
    (is (contains? (get-in (store/facility st2 "farm-001") [:pending-maintenance 0])
                   :type))
    (is (= :filter-replacement (get-in (store/facility st2 "farm-001") [:pending-maintenance 0 :type])))))

(deftest order-supply
  (let [st (store/mem-store {:fixtures {"farm-001" {:site-id "farm-001" :status :active}}})
        order {:item "feed" :quantity-kg 5000 :estimated-cost 8000}
        st2 (store/order-supply! st "farm-001" order)]
    (is (contains? (get-in (store/facility st2 "farm-001") [:supply-orders 0])
                   :item))
    (is (= 8000 (get-in (store/facility st2 "farm-001") [:supply-orders 0 :estimated-cost])))))
