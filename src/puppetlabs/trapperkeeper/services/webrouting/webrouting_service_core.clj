(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private Utility Functions

(defn get-endpoint-from-config
  [context svc]
  (let [config (:web-router-service context)
        endpoint (get config svc)]
    endpoint))
