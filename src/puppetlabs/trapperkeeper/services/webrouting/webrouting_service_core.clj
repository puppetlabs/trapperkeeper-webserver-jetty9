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

(schema/defn ^:always-validate add-context-handler!
  [context webserver-service
   svc :- schema/Keyword
   base-path
   options :- ContextHandlerOptions]
  (let [defaults               (assoc server-and-route-defaults
                                 :context-listeners [])
        opts                   (merge defaults options)
        server-id              (:server-id opts)
        route-id               (:route-id opts)
        context-listeners      (:context-listeners opts)
        context-path           (get-endpoint-from-config context svc route-id)
        add-context-handler-to (:add-context-handler-to webserver-service)]
    (add-context-handler-to server-id base-path context-path context-listeners)))

(schema/defn ^:always-validate add-ring-handler!
  [context webserver-service
   svc :- schema/Keyword
   handler options :- ServerAndRouteOptions]
  (let [defaults            server-and-route-defaults
        opts                (merge defaults options)
        server-id           (:server-id opts)
        route-id            (:route-id opts)
        path                (get-endpoint-from-config context svc route-id)
        add-ring-handler-to (:add-ring-handler-to webserver-service)]
    (add-ring-handler-to server-id handler path)))

(schema/defn ^:always-validate add-servlet-handler!
  [context webserver-service
   svc :- schema/Keyword
   servlet options :- ServletHandlerOptions]
  (let [defaults               (assoc server-and-route-defaults
                                 :servlet-init-params {})
        opts                   (merge defaults options)
        server-id              (:server-id opts)
        route-id               (:route-id opts)
        path                   (get-endpoint-from-config context svc route-id)
        servlet-init-params    (:servlet-init-params opts)
        add-servlet-handler-to (:add-servlet-handler-to webserver-service)]
    (add-servlet-handler-to server-id servlet path servlet-init-params)))

(schema/defn ^:always-validate add-war-handler!
  [context webserver-service
   svc :- schema/Keyword
   war options :- ServerAndRouteOptions]
  (let [defaults           server-and-route-defaults
        opts               (merge defaults options)
        server-id          (:server-id opts)
        route-id           (:route-id opts)
        path               (get-endpoint-from-config context svc route-id)
        add-war-handler-to (:add-war-handler-to webserver-service)]
    (add-war-handler-to server-id war path)))

(schema/defn ^:always-validate add-proxy-route!
  [context webserver-service
   svc :- schema/Keyword
   target options :- ProxyRouteOptions]
  (let [defaults server-and-route-defaults
        opts     (merge defaults options)
        server-id (:server-id opts)
        route-id (:route-id opts)
        path     (get-endpoint-from-config context svc route-id)
        add-proxy-route-to (:add-proxy-route-to webserver-service)]
    (add-proxy-route-to server-id target path)))
