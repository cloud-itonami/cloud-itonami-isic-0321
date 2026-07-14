(ns aquaculture.sim
  "Simulation/demo: drive the aquaculture operations actor through a few scenarios."
  (:require [aquaculture.store :as store]
            [aquaculture.operation :as operation]))

(defn -main []
  (println "Marine Aquaculture Operations Coordination Actor - Demo")
  (println "========================================================")

  ;; Setup: create store with a test facility.
  (let [test-facility {:site-id "marine-farm-001"
                       :name "Coastal Marine Farm"
                       :status :active
                       :location "North Coast"
                       :license-number "LIC-001-2024"
                       :capacity-tonnes 500}
        st (store/mem-store {:fixtures {"marine-farm-001" test-facility}})

        ;; Build the operation actor.
        actor (operation/build st)]

    (println "\nFacility registered:")
    (println "  Site ID:" (:site-id test-facility))
    (println "  Name:" (:name test-facility))
    (println "  Capacity:" (:capacity-tonnes test-facility) "tonnes")
    (println "  License:" (:license-number test-facility))

    (println "\nOperation Actor built and ready.")
    (println "Governance rules active:")
    (println "  - Facility verification required")
    (println "  - All effects :propose only")
    (println "  - Health concerns always escalate")
    (println "  - High-cost supplies escalate")
    (println "  - Treatment/harvest decisions blocked (human authority)")))
