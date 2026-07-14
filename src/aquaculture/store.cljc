(ns aquaculture.store
  "Store protocol and in-memory implementation for marine aquaculture operations
  coordination. Separates data persistence from business logic, enabling swaps
  (in-mem -> Datomic/kotoba-server)."
  #?(:clj (:refer-clojure :exclude [type])))

(defprotocol Store
  "Persistent store for aquaculture facility records, stock history, and
  maintenance logs."

  (facility [st site-id]
    "Returns facility record by :site-id, or nil if not found.
     Facility record: {:site-id .. :name .. :status :active|:inactive
     :location .. :license-number .. :capacity-tonnes ..}")

  (log-stock! [st site-id record]
    "Commits a stock record (count, growth, mortality data).
     Returns updated store.")

  (schedule-maintenance! [st site-id maintenance-proposal]
    "Schedules facility maintenance.
     Returns updated store.")

  (flag-health-concern! [st site-id concern]
    "Logs a stock health concern (disease, water quality, etc).
     Returns updated store.")

  (order-supply! [st site-id order]
    "Logs a supply order proposal (feed, veterinary supplies).
     Returns updated store.")

  (audit-log [st site-id]
    "Returns all audit entries for this facility."))

(defn- now []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(deftype MemStore [state-atom]
  Store
  (facility [st site-id]
    (get-in @state-atom [:facilities site-id]))

  (log-stock! [st site-id record]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:facilities site-id :last-stock-record]
                           (constantly (assoc record :t (now))))
                 (update-in [:audit site-id] (fnil conj [])
                           {:t (now)
                            :type :stock-logged
                            :record record}))))
    st)

  (schedule-maintenance! [st site-id maintenance-proposal]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:facilities site-id :pending-maintenance] (fnil conj [])
                           (assoc maintenance-proposal :t (now)))
                 (update-in [:audit site-id] (fnil conj [])
                           {:t (now)
                            :type :maintenance-scheduled
                            :proposal maintenance-proposal}))))
    st)

  (flag-health-concern! [st site-id concern]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:facilities site-id :health-concerns] (fnil conj [])
                           (assoc concern :t (now)))
                 (update-in [:audit site-id] (fnil conj [])
                           {:t (now)
                            :type :health-concern-flagged
                            :concern concern
                            :escalated true}))))
    st)

  (order-supply! [st site-id order]
    (swap! state-atom
           (fn [s]
             (-> s
                 (update-in [:facilities site-id :supply-orders] (fnil conj [])
                           (assoc order :t (now)))
                 (update-in [:audit site-id] (fnil conj [])
                           {:t (now)
                            :type :supply-ordered
                            :order order}))))
    st)

  (audit-log [st site-id]
    (get-in @state-atom [:audit site-id] [])))

(defn mem-store
  "Creates an in-memory aquaculture store, optionally initialized with fixtures."
  [& [{:keys [fixtures]}]]
  (let [state (cond-> {:facilities {} :audit {}}
                fixtures (update :facilities merge fixtures))]
    (MemStore. (atom state))))
