(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]
    [schema.core :as schema]))

(defprotocol WebroutingService
  (add-context-handler [this svc context-path] [this svc context-path context-listeners])
  (add-ring-handler [this svc handler])
  (add-servlet-handler [this svc servlet] [this svc servlet servlet-init-params])
  (add-war-handler [this svc war])
  (add-proxy-route [this svc target] [this svc target options])
  (override-webserver-settings! [this overrides])
  (get-registered-endpoints [this])
  (log-registered-endpoints [this])
  (join [this]))

(defservice webrouting-service
  "Provides the ability to route handlers to different jetty9 webserver services"
  WebroutingService
  [WebserverService
   [:ConfigService get-in-config]]
  (init [this context]
        (let [config (get-in-config [:web-router-service])]
          (assoc context :web-router-service config)))

  (add-context-handler [this svc base-path]
                       (let [context             (service-context this)
                             context-path        (core/get-endpoint-from-config context svc)
                             add-context-handler (:add-context-handler WebserverService)]
                         (add-context-handler base-path context-path)))

  (add-context-handler [this svc base-path context-listeners]
                       (let [context             (service-context this)
                             context-path        (core/get-endpoint-from-config context svc)
                             add-context-handler (:add-context-handler WebserverService)]
                         (add-context-handler base-path context-path context-listeners)))

  (add-ring-handler [this svc handler]
                    (let [context          (service-context this)
                          endpoint         (core/get-endpoint-from-config context svc)
                          add-ring-handler (:add-ring-handler WebserverService)]
                      (add-ring-handler handler endpoint)))

  (add-servlet-handler [this svc servlet]
                       (let [context             (service-context this)
                             endpoint            (core/get-endpoint-from-config context svc)
                             add-servlet-handler (:add-servlet-handler WebserverService)]
                         (add-servlet-handler servlet endpoint)))

  (add-servlet-handler [this svc servlet servlet-init-params]
                       (let [context             (service-context this)
                             endpoint            (core/get-endpoint-from-config context svc)
                             add-servlet-handler (:add-servlet-handler WebserverService)]
                         (add-servlet-handler servlet endpoint servlet-init-params)))

  (add-war-handler [this svc war]
                   (let [context         (service-context this)
                         endpoint        (core/get-endpoint-from-config context svc)
                         add-war-handler (:add-war-handler WebserverService)]
                     (add-war-handler war endpoint)))

  (add-proxy-route [this svc target]
                   (let [context (service-context this)
                         endpoint (core/get-endpoint-from-config context svc)
                         add-proxy-route (:add-proxy-route WebserverService)]
                     (add-proxy-route target endpoint)))

  (add-proxy-route [this svc target options]
                   (let [context (service-context this)
                         endpoint (core/get-endpoint-from-config context svc)
                         add-proxy-route (:add-proxy-route WebserverService)]
                     (add-proxy-route target endpoint options)))

  (override-webserver-settings! [this overrides]
                                (let [override-webserver-settings (:override-webserver-settings! WebserverService)]
                                  (override-webserver-settings overrides)))

  (get-registered-endpoints [this]
                            (let [get-registered-endpoints (:get-registered-endpoints WebserverService)]
                              (get-registered-endpoints)))

  (log-registered-endpoints [this]
                            (let [log-registered-endpoints (:log-registered-endpoints WebserverService)]
                              (log-registered-endpoints)))

  (join [this]
        (let [join (:join WebserverService)]
          (join)))
  )
