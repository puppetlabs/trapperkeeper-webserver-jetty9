(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webrouting.webrouting-service-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]
    [schema.core :as schema]))

(defprotocol WebroutingService
  (add-context-handler [this svc context-path] [this svc context-path context-listeners])
  (add-context-handler-to [this svc server-id context-path] [this svc server-id context-path context-listeners])
  (add-ring-handler [this svc handler])
  (add-ring-handler-to [this svc server-id handler])
  (add-servlet-handler [this svc servlet] [this svc servlet servlet-init-params])
  (add-servlet-handler-to [this svc server-id servlet] [this svc server-id servlet servlet-init-params])
  (add-war-handler [this svc war])
  (add-war-handler-to [this svc server-id war])
  (add-proxy-route [this svc target] [this svc target options])
  (add-proxy-route-to [this svc server-id target] [this svc server-id target options])
  (override-webserver-settings! [this overrides])
  (override-webserver-settings-for! [this server-id overrides])
  (get-registered-endpoints [this])
  (get-registered-endpoints-from [this server-id])
  (log-registered-endpoints [this])
  (log-registered-endpoints-from [this server-id])
  (join [this])
  (join-server [this server-id]))

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

  (add-context-handler-to [this svc server-id base-path]
                           (let [context                 (service-context this)
                                 context-path            (core/get-endpoint-from-config context svc)
                                 add-context-handler-to  (:add-context-handler-to WebserverService)]
                             (add-context-handler-to server-id base-path context-path)))

  (add-context-handler-to [this svc server-id base-path context-listeners]
                           (let [context                 (service-context this)
                                 context-path            (core/get-endpoint-from-config context svc)
                                 add-context-handler-to  (:add-context-handler-to WebserverService)]
                             (add-context-handler-to server-id base-path context-path context-listeners)))

  (add-ring-handler [this svc handler]
                    (let [context          (service-context this)
                          endpoint         (core/get-endpoint-from-config context svc)
                          add-ring-handler (:add-ring-handler WebserverService)]
                      (add-ring-handler handler endpoint)))

  (add-ring-handler-to [this svc server-id handler]
                       (let [context             (service-context this)
                             endpoint            (core/get-endpoint-from-config context svc)
                             add-ring-handler-to (:add-ring-handler-to WebserverService)]
                         (add-ring-handler-to server-id handler endpoint)))

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

  (add-servlet-handler-to [this svc server-id servlet]
                          (let [context                (service-context this)
                                endpoint               (core/get-endpoint-from-config context svc)
                                add-servlet-handler-to (:add-servlet-handler-to WebserverService)]
                            (add-servlet-handler-to server-id servlet endpoint)))

  (add-servlet-handler-to [this svc server-id servlet servlet-init-params]
                          (let [context                (service-context this)
                                endpoint               (core/get-endpoint-from-config context svc)
                                add-servlet-handler-to (:add-servlet-handler-to WebserverService)]
                            (add-servlet-handler-to server-id servlet endpoint servlet-init-params)))

  (add-war-handler [this svc war]
                   (let [context         (service-context this)
                         endpoint        (core/get-endpoint-from-config context svc)
                         add-war-handler (:add-war-handler WebserverService)]
                     (add-war-handler war endpoint)))

  (add-war-handler-to [this svc server-id war]
                      (let [context            (service-context this)
                            endpoint           (core/get-endpoint-from-config context svc)
                            add-war-handler-to (:add-war-handler-to WebserverService)]
                        (add-war-handler-to server-id war endpoint)))

  (add-proxy-route [this svc target]
                   (let [context         (service-context this)
                         endpoint        (core/get-endpoint-from-config context svc)
                         add-proxy-route (:add-proxy-route WebserverService)]
                     (add-proxy-route target endpoint)))

  (add-proxy-route [this svc target options]
                   (let [context         (service-context this)
                         endpoint        (core/get-endpoint-from-config context svc)
                         add-proxy-route (:add-proxy-route WebserverService)]
                     (add-proxy-route target endpoint options)))

  (add-proxy-route-to [this svc server-id target]
                      (let [context            (service-context this)
                            endpoint           (core/get-endpoint-from-config context svc)
                            add-proxy-route-to (:add-proxy-route-to WebserverService)]
                        (add-proxy-route-to server-id target endpoint)))

  (add-proxy-route-to [this svc server-id target options]
                      (let [context            (service-context this)
                            endpoint           (core/get-endpoint-from-config context svc)
                            add-proxy-route-to (:add-proxy-route-to WebserverService)]
                        (add-proxy-route-to server-id target endpoint options)))

  (override-webserver-settings! [this overrides]
                                (let [override-webserver-settings (:override-webserver-settings! WebserverService)]
                                  (override-webserver-settings overrides)))

  (override-webserver-settings-for! [this server-id overrides]
                                    (let [override-webserver-settings-for (:override-webserver-settings-for! WebserverService)]
                                      (override-webserver-settings-for server-id overrides)))

  (get-registered-endpoints [this]
                            (let [get-registered-endpoints (:get-registered-endpoints WebserverService)]
                              (get-registered-endpoints)))

  (get-registered-endpoints-from [this server-id]
                            (let [get-registered-endpoints-from (:get-registered-endpoints-from WebserverService)]
                              (get-registered-endpoints-from server-id)))

  (log-registered-endpoints [this]
                            (let [log-registered-endpoints (:log-registered-endpoints WebserverService)]
                              (log-registered-endpoints)))

  (log-registered-endpoints-from [this server-id]
                                (let [log-registered-endpoints-from (:log-registered-endpoints-from WebserverService)]
                                  (log-registered-endpoints-from server-id)))

  (join [this]
        (let [join (:join WebserverService)]
          (join)))

  (join-server [this server-id]
               (let [join-server (:join-server WebserverService)]
                 (join-server server-id))))
