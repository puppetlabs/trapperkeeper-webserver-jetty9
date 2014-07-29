(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core
  (:import (javax.servlet ServletContextListener))
  (:require [schema.core :as schema]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]))

(def server-and-route-defaults
  {:server-id :default
   :route-id  :default})

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
  (let [defaults               (assoc server-and-route-defaults
                                 :context-listeners [])
        opts                   (merge defaults options)
        route-id               (:route-id opts)
        context-path           (get-endpoint-from-config context svc route-id)
        add-context-handler    (:add-context-handler webserver-service)
        opts                   (dissoc opts :route-id)]
    (add-context-handler base-path context-path opts)))

(schema/defn ^:always-validate add-ring-handler!
  [context webserver-service
   svc :- schema/Keyword
   handler options :- ServerAndRouteOptions]
  (let [defaults            server-and-route-defaults
        opts                (merge defaults options)
        route-id            (:route-id opts)
        path                (get-endpoint-from-config context svc route-id)
        add-ring-handler    (:add-ring-handler webserver-service)
        opts                (dissoc opts :route-id)]
    (add-ring-handler handler path opts)))

(schema/defn ^:always-validate add-servlet-handler!
  [context webserver-service
   svc :- schema/Keyword
   servlet options :- ServletHandlerOptions]
  (let [defaults               (assoc server-and-route-defaults
                                 :servlet-init-params {})
        opts                   (merge defaults options)
        route-id               (:route-id opts)
        path                   (get-endpoint-from-config context svc route-id)
        add-servlet-handler    (:add-servlet-handler webserver-service)
        opts                   (dissoc opts :route-id)]
    (add-servlet-handler servlet path opts)))

(schema/defn ^:always-validate add-war-handler!
  [context webserver-service
   svc :- schema/Keyword
   war options :- ServerAndRouteOptions]
  (let [defaults           server-and-route-defaults
        opts               (merge defaults options)
        route-id           (:route-id opts)
        path               (get-endpoint-from-config context svc route-id)
        add-war-handler    (:add-war-handler webserver-service)
        opts               (dissoc opts :route-id)]
    (add-war-handler war path opts)))

(schema/defn ^:always-validate add-proxy-route!
  [context webserver-service
   svc :- schema/Keyword
   target options :- ProxyRouteOptions]
  (let [defaults           server-and-route-defaults
        opts               (merge defaults options)
        route-id           (:route-id opts)
        path               (get-endpoint-from-config context svc route-id)
        add-proxy-route    (:add-proxy-route webserver-service)
        opts               (dissoc opts :route-id)]
    (add-proxy-route target path opts)))
