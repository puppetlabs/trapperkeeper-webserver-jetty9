(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services :refer [service-context]]
    [puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]
    [schema.core :as schema]))

(defprotocol WebroutingService
  (get-route [this svc] [this svc route-id])
  (add-context-handler [this svc context-path] [this svc context-path options])
  (add-ring-handler [this svc handler] [this svc handler options])
  (add-servlet-handler [this svc servlet] [this svc servlet options])
  (add-websocket-handler [this svc handlers] [this svc handlers options])
  (add-war-handler [this svc war] [this svc war options])
  (add-proxy-route [this svc target] [this svc target options])
  (override-webserver-settings! [this overrides] [this server-id overrides])
  (get-registered-endpoints [this] [this server-id])
  (log-registered-endpoints [this] [this server-id])
  (join [this] [this server-id]))

(defservice webrouting-service
  "Provides the ability to route handlers to different jetty9 webserver services"
  WebroutingService
  [WebserverService
   [:ConfigService get-in-config]]
  (init [this context]
        (let [config (get-in-config [:web-router-service])]
          (when (nil? config)
            (throw (IllegalArgumentException.
                     ":web-router-service section of configuration not present")))
          (core/init context config)))

  (get-route [this svc]
             (core/get-route (service-context this) svc nil))

  (get-route [this svc route-id]
             (core/get-route (service-context this) svc route-id))

  (add-context-handler [this svc base-path]
                    (core/add-context-handler! (service-context this)
                                               WebserverService svc
                                               base-path {}))

  (add-context-handler [this svc base-path options]
                    (core/add-context-handler! (service-context this)
                                               WebserverService svc
                                               base-path options))

  (add-ring-handler [this svc handler]
                    (core/add-ring-handler! (service-context this)
                                            WebserverService svc
                                            handler {}))

  (add-ring-handler [this svc handler options]
                    (core/add-ring-handler! (service-context this)
                                            WebserverService svc
                                            handler options))

  (add-servlet-handler [this svc servlet]
                    (core/add-servlet-handler! (service-context this)
                                               WebserverService svc
                                               servlet {}))

  (add-servlet-handler [this svc servlet options]
                    (core/add-servlet-handler! (service-context this)
                                               WebserverService svc
                                               servlet options))

  (add-websocket-handler [this svc handlers]
                         (core/add-websocket-handler! (service-context this)
                                                      WebserverService svc
                                                      handlers {}))

  (add-websocket-handler [this svc handlers options]
                         (core/add-websocket-handler! (service-context this)
                                                    WebserverService svc
                                                    handlers options))

  (add-war-handler [this svc war]
                   (core/add-war-handler! (service-context this)
                                          WebserverService svc
                                          war {}))

  (add-war-handler [this svc war options]
                   (core/add-war-handler! (service-context this)
                                          WebserverService svc
                                          war options))

  (add-proxy-route [this svc target]
                   (core/add-proxy-route! (service-context this)
                                          WebserverService svc
                                          target {}))

  (add-proxy-route [this svc target options]
                   (core/add-proxy-route! (service-context this)
                                          WebserverService svc
                                          target options))

  (override-webserver-settings! [this overrides]
                   (let [override-webserver-settings
                           (:override-webserver-settings! WebserverService)]
                     (override-webserver-settings overrides)))

  (override-webserver-settings! [this server-id overrides]
                   (let [override-webserver-settings
                           (:override-webserver-settings! WebserverService)]
                     (override-webserver-settings server-id overrides)))

  (get-registered-endpoints [this]
                   (let [get-registered-endpoints
                           (:get-registered-endpoints WebserverService)]
                     (get-registered-endpoints)))

  (get-registered-endpoints [this server-id]
                   (let [get-registered-endpoints
                           (:get-registered-endpoints WebserverService)]
                     (get-registered-endpoints server-id)))

  (log-registered-endpoints [this]
                   (let [log-registered-endpoints
                           (:log-registered-endpoints WebserverService)]
                     (log-registered-endpoints)))

  (log-registered-endpoints [this server-id]
                   (let [log-registered-endpoints
                           (:log-registered-endpoints WebserverService)]
                     (log-registered-endpoints server-id)))

  (join [this]
        (let [join (:join WebserverService)]
          (join)))

  (join [this server-id]
               (let [join (:join WebserverService)]
                 (join server-id))))
