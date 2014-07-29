(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core
  (:import (javax.servlet ServletContextListener))
  (:require [schema.core :as schema]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def WebroutingMultipleConfig
  {schema/Keyword schema/Str})

(def WebroutingServiceConfig
  {schema/Keyword (schema/either schema/Str WebroutingMultipleConfig)})

(def ServerAndRouteOptions
  {(schema/optional-key :server-id) schema/Keyword
   (schema/optional-key :route-id) schema/Keyword})

(def ContextHandlerOptions
  (assoc ServerAndRouteOptions
    (schema/optional-key :context-listeners) [ServletContextListener]))

(def ServletHandlerOptions
  (assoc ServerAndRouteOptions
    (schema/optional-key :servlet-init-params) {schema/Str schema/Str}))

(def ProxyRouteOptions
  (assoc ServerAndRouteOptions
    (schema/optional-key :scheme) (schema/enum :orig :http :https)
    (schema/optional-key :ssl-config) (schema/either
                                        (schema/eq :use-server-config)
                                        config/WebserverSslPemConfig)
    (schema/optional-key :callback-fn) (schema/pred ifn?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private Utility Functions

(defn get-endpoint-from-config
  [context svc route-id]
  (let [config (:web-router-service context)
        route-id (if (nil? route-id)
                   :default
                   route-id)
        endpoint (get-in config [svc route-id])]
    (if (nil? endpoint)
      (throw
        (IllegalArgumentException.
          "specified service does not appear in configuration file"))
      endpoint)))

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

(schema/defn ^:always-validate add-context-handler!
  [context webserver-service
   svc :- schema/Keyword
   base-path
   options :- ContextHandlerOptions]
  (let [route-id               (:route-id options)
        context-path           (get-endpoint-from-config context svc route-id)
        add-context-handler    (:add-context-handler webserver-service)
        opts                   (dissoc options :route-id)]
    (add-context-handler base-path context-path opts)))

(schema/defn ^:always-validate add-ring-handler!
  [context webserver-service
   svc :- schema/Keyword
   handler options :- ServerAndRouteOptions]
  (let [route-id            (:route-id options)
        path                (get-endpoint-from-config context svc route-id)
        add-ring-handler    (:add-ring-handler webserver-service)
        opts                (dissoc options :route-id)]
    (add-ring-handler handler path opts)))

(schema/defn ^:always-validate add-servlet-handler!
  [context webserver-service
   svc :- schema/Keyword
   servlet options :- ServletHandlerOptions]
  (let [route-id               (:route-id options)
        path                   (get-endpoint-from-config context svc route-id)
        add-servlet-handler    (:add-servlet-handler webserver-service)
        opts                   (dissoc options :route-id)]
    (add-servlet-handler servlet path opts)))

(schema/defn ^:always-validate add-war-handler!
  [context webserver-service
   svc :- schema/Keyword
   war options :- ServerAndRouteOptions]
  (let [route-id           (:route-id options)
        path               (get-endpoint-from-config context svc route-id)
        add-war-handler    (:add-war-handler webserver-service)
        opts               (dissoc options :route-id)]
    (add-war-handler war path opts)))

(schema/defn ^:always-validate add-proxy-route!
  [context webserver-service
   svc :- schema/Keyword
   target options :- ProxyRouteOptions]
  (let [route-id           (:route-id options)
        path               (get-endpoint-from-config context svc route-id)
        add-proxy-route    (:add-proxy-route webserver-service)
        opts               (dissoc options :route-id)]
    (add-proxy-route target path opts)))
