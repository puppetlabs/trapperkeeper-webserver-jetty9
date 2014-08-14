(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core
  (:import (javax.servlet ServletContextListener))
  (:require [schema.core :as schema]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty9-core]
            [puppetlabs.trapperkeeper.services :as tk-services]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def RouteWithServerConfig
  {:route  schema/Str
   :server schema/Str})

(def WebroutingMultipleConfig
  {:default       schema/Str
   schema/Keyword (schema/either schema/Str RouteWithServerConfig)})

(def WebroutingServiceConfig
  {schema/Keyword (schema/either schema/Str RouteWithServerConfig WebroutingMultipleConfig)})

(def RouteOption
  {(schema/optional-key :route-id) schema/Keyword})

(def ContextHandlerOptions
  (dissoc (merge jetty9-core/ContextHandlerOptions RouteOption) :server-id))

(def ServletHandlerOptions
  (dissoc (merge jetty9-core/ServletHandlerOptions RouteOption) :server-id))

(def ProxyRouteOptions
  (dissoc (merge jetty9-core/ProxyOptions RouteOption) :server-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Private Utility Functions

(defn get-endpoint-and-server-from-config
  [context svc route-id]
  (let [config        (:web-router-service context)
        route-id      (if (nil? route-id)
                        :default
                        route-id)
        endpoint      (get-in config [svc route-id])
        no-endpoint?  (nil? endpoint)
        no-server?    (nil? (schema/check schema/Str endpoint))
        server?       (nil? (schema/check RouteWithServerConfig endpoint))]
    (cond
      no-endpoint? (throw
                     (IllegalArgumentException.
                       "specified service or endpoint does not appear in configuration file"))
      no-server?   {:route endpoint :server "default"}
      server?      endpoint)))

(defn compute-common-elements
  [context svc options]
  (let [svc-id           (keyword (tk-services/service-symbol svc))
        route-id         (:route-id options)
        route-and-server (get-endpoint-and-server-from-config context svc-id route-id)
        path             (:route route-and-server)
        server           (keyword (:server route-and-server))
        opts             (assoc (dissoc options :route-id) :server-id server)]
    {:path path :opts opts}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Lifecycle implementations

(schema/defn ^:always-validate init
  [context config :- WebroutingServiceConfig]
  (let [configuration (into {} (for [[svc svc-config] config]
                                 (cond
                                   (nil? (schema/check (schema/either schema/Str RouteWithServerConfig) svc-config))
                                     [svc {:default svc-config}]
                                   (nil? (schema/check WebroutingMultipleConfig svc-config))
                                     [svc svc-config])))]
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
   handler options :- RouteOption]
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
   war options :- RouteOption]
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
