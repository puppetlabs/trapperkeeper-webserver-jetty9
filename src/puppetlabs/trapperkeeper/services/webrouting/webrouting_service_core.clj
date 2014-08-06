(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core
  (:import (javax.servlet ServletContextListener)
           (java.net NoRouteToHostException))
  (:require [schema.core :as schema]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty9-core]
            [puppetlabs.trapperkeeper.services :as tk-services]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def WebroutingMultipleConfig
  {schema/Keyword schema/Str})

(def WebroutingServiceConfig
  {schema/Keyword (schema/either schema/Str WebroutingMultipleConfig)})

(def RouteOption
  {(schema/optional-key :route-id) schema/Keyword})

(def ServerAndRouteOptions
  (merge jetty9-core/ServerIDOption RouteOption))

(def ContextHandlerOptions
  (merge jetty9-core/ContextHandlerOptions RouteOption))

(def ServletHandlerOptions
  (merge jetty9-core/ServletHandlerOptions RouteOption))

(def ProxyRouteOptions
  (merge jetty9-core/ProxyOptions RouteOption))

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

(defn compute-common-elements
  [context svc options]
  (let [svc-id   (keyword (tk-services/service-symbol svc))
        route-id (:route-id options)
        path     (get-endpoint-from-config context svc-id route-id)
        opts     (dissoc options :route-id)]
    {:path path :opts opts}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Lifecycle implementations

(schema/defn ^:always-validate init
  [context config :- WebroutingServiceConfig]
  (let [configuration (into {} (for [[svc svc-config] config]
                                 (cond
                                   (nil? (schema/check schema/Str svc-config)) [svc {:default svc-config}]
                                   (nil? (schema/check WebroutingMultipleConfig svc-config)) [svc svc-config])))]
    (assoc context :web-router-service configuration)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service function implementations

(schema/defn ^:always-validate add-context-handler!
  [context webserver-service
   svc :- tk-services/Service
   base-path
   options :- ContextHandlerOptions]
  (let [{:keys [path opts]} (compute-common-elements context svc options)
        add-context-handler (:add-context-handler webserver-service)]
    (add-context-handler base-path path opts)))

(schema/defn ^:always-validate add-ring-handler!
  [context webserver-service
   svc :- tk-services/Service
   handler options :- ServerAndRouteOptions]
  (let [{:keys [path opts]} (compute-common-elements context svc options)
        add-ring-handler    (:add-ring-handler webserver-service)]
    (add-ring-handler handler path opts)))

(schema/defn ^:always-validate add-servlet-handler!
  [context webserver-service
   svc :- tk-services/Service
   servlet options :- ServletHandlerOptions]
  (let [{:keys [path opts]} (compute-common-elements context svc options)
        add-servlet-handler (:add-servlet-handler webserver-service)]
    (add-servlet-handler servlet path opts)))

(schema/defn ^:always-validate add-war-handler!
  [context webserver-service
   svc :- tk-services/Service
   war options :- ServerAndRouteOptions]
  (let [{:keys [path opts]} (compute-common-elements context svc options)
        add-war-handler     (:add-war-handler webserver-service)]
    (add-war-handler war path opts)))

(schema/defn ^:always-validate add-proxy-route!
  [context webserver-service
   svc :- tk-services/Service
   target options :- ProxyRouteOptions]
  (let [{:keys [path opts]} (compute-common-elements context svc options)
        add-proxy-route     (:add-proxy-route webserver-service)]
    (add-proxy-route target path opts)))
