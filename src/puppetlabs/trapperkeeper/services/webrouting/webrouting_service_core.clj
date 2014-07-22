(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core
  (:require [schema.core :as schema]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def WebroutingMultipleConfig
  {schema/Keyword schema/Str})

(def WebroutingServiceConfig
  {schema/Keyword (schema/either schema/Str WebroutingMultipleConfig)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private Utility Functions

(defn get-endpoint-from-config
  [context svc route-id]
  (let [config (:web-router-service context)
        endpoint (get-in config [svc route-id])]
    endpoint))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Lifecycle implementations

(schema/defn ^:always-validate init!
  [context config :- WebroutingServiceConfig]
  (let [configuration (into {} (for [[svc svc-config] config]
                        (let [single (schema/check schema/Str svc-config)
                              multi  (schema/check WebroutingMultipleConfig svc-config)]
                          (cond
                            (nil? single) [svc {:default svc-config}]
                            (nil? multi)  [svc svc-config]))))]
    (assoc context :web-router-service configuration)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service function implementations

(schema/defn ^:always-validate add-context-handler-to!
  [context webserver-service
   svc :- schema/Keyword
   route-id :- schema/Keyword
   server-id :- schema/Keyword
   base-path context-listeners]
  (let [context-path           (get-endpoint-from-config context svc route-id)
        add-context-handler-to (:add-context-handler-to webserver-service)]
    (add-context-handler-to server-id base-path context-path context-listeners)))
