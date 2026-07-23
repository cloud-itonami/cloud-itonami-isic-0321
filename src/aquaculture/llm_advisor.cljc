(ns aquaculture.llm-advisor
  "Aquaculture Operations Advisor (LLM interface). The advisor generates
  proposals for facility coordination operations, all routed through the
  governor before commitment. No external service calls in the base module --
  callers inject a real LLM or a mock.")

(defprotocol Advisor
  "An LLM advisor that generates proposals for aquaculture operations."
  (-advise [advisor store request]
    "Generates a proposal from the request. Returns
     {:effect :propose :value {...} :cites [...] :summary ... :confidence ...}"))

(def mock-advisor-impl
  "A mock advisor for testing. Always generates :propose effects with
  high confidence."
  (reify Advisor
    (-advise [_advisor _store request]
      (let [{:keys [op site-id value]} request]
        {:effect :propose
         :op op
         :site-id site-id
         :value (or value {:op op})
         :cites ["mock-basis"]
         :summary (str "Mock proposal for " op " at " site-id)
         :confidence 0.9}))))

(defn mock-advisor []
  mock-advisor-impl)

(defn advise [advisor store request]
  (-advise advisor store request))

(defn trace
  "Audit trace for an advisory call."
  [request proposal]
  {:t :advisor-trace
   :op (:op request)
   :site-id (:site-id request)
   :confidence (:confidence proposal)
   :summary (:summary proposal)})
