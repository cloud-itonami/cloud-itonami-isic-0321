(ns aquaculture.sim
  "Simulation/demo: drive the aquaculture operations actor through a few
  scenarios, actually running the compiled graph (previously this just
  printed a static description and never invoked it -- see git history)."
  (:require [langgraph.graph :as g]
            [aquaculture.store :as store]
            [aquaculture.operation :as operation]))

(defn- run-op! [actor request]
  (g/run* actor {:request request :context {:actor-id "op-1"}} {:thread-id (str (random-uuid))}))

(defn -main [& _args]
  (println "Marine Aquaculture Operations Coordination Actor - Demo")
  (println "========================================================")

  (let [test-facility {:site-id "marine-farm-001"
                       :name "Coastal Marine Farm"
                       :status :active
                       :location "North Coast"
                       :license-number "LIC-001-2024"
                       :capacity-tonnes 500}
        st (store/mem-store {:fixtures {"marine-farm-001" test-facility}})
        actor (operation/build st)]

    (println "\nFacility registered:")
    (println "  Site ID:" (:site-id test-facility))
    (println "  Name:" (:name test-facility))
    (println "  Capacity:" (:capacity-tonnes test-facility) "tonnes")
    (println "  License:" (:license-number test-facility))

    (println "\n== Scenario 1: log-stock-record commits ==")
    (let [result (run-op! actor {:op :log-stock-record :site-id "marine-farm-001"
                              :value {:count 12000 :growth-kg 340 :mortality 12}})]
      (println "  disposition:" (get-in result [:state :disposition])))

    (println "\n== Scenario 2: unregistered site holds ==")
    (let [result (run-op! actor {:op :schedule-facility-maintenance :site-id "marine-farm-999"
                              :value {:task "net-inspection"}})]
      (println "  disposition:" (get-in result [:state :disposition])))

    (println "\n== Scenario 3: health concern always escalates ==")
    (let [result (run-op! actor {:op :flag-stock-health-concern :site-id "marine-farm-001"
                              :value {:concern "elevated mortality in pen 3"}})]
      (println "  disposition:" (get-in result [:state :disposition])))

    (println "\n== Scenario 4: high-cost supply order escalates ==")
    (let [result (run-op! actor {:op :order-supplies :site-id "marine-farm-001"
                              :value {:item "feed" :estimated-cost 15000}})]
      (println "  disposition:" (get-in result [:state :disposition])))

    (println "\n== audit log (marine-farm-001) ==")
    (doseq [entry (store/audit-log st "marine-farm-001")]
      (println " " entry))

    (println "\ndone.")))
