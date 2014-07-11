(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]
    [schema.core :as schema]))

;; TODO: this should probably be moved to a separate jar that can be used as
;; a dependency for all webserver service implementations
(defprotocol WebserverService
  (add-context-handler [this base-path context-path] [this base-path context-path context-listeners])
  (add-context-handler-to [this base-path context-path server-id] [this base-path context-path context-listeners server-id])
  (add-ring-handler [this handler path])
  (add-ring-handler-to [this handler path server-id])
  (add-servlet-handler [this servlet path] [this servlet path servlet-init-params])
  (add-servlet-handler-to [this servlet path server-id] [this servlet path servlet-init-params server-id])
  (add-war-handler [this war path])
  (add-war-handler-to [this war path server-id])
  (add-proxy-route [this target path] [this target path options])
  (add-proxy-route-to [this target path server-id] [this target path options server-id])
  (override-webserver-settings! [this overrides])
  (override-webserver-settings-for! [this overrides server-id])
  (get-registered-endpoints [this])
  (get-registered-endpoints-from [this server-id])
  (log-registered-endpoints [this])
  (log-registered-endpoints-from [this server-id])
  (join [this])
  (join-server [this server-id])
  )

(defservice jetty9-service
  "Provides a Jetty 9 web server as a service"
  WebserverService
  [[:ConfigService get-in-config]]
  (init [this context]
        (log/info "Initializing web server(s).")
        (let [config (or (get-in-config [:webserver])
                         ;; Here for backward compatibility with existing projects
                         (get-in-config [:jetty])
                         {})]
          (core/init! context config)))

  (start [this context]
         (log/info "Starting web server(s).")
         (let [config (or (get-in-config [:webserver])
                          ;; Here for backward compatibility with existing projects
                          (get-in-config [:jetty])
                          {})]
           (core/start! context config)))

  (stop [this context]
        (log/info "Shutting down web server(s).")
        (doseq [key (keys (:jetty9-servers context))]
          (if-let [server (key (:jetty9-servers context))]
            (core/shutdown server)))
        context)

  (add-context-handler [this base-path context-path]
                       (add-context-handler-to this base-path context-path :default))

  (add-context-handler [this base-path context-path context-listeners]
                       (add-context-handler-to this base-path context-path context-listeners :default))

  (add-context-handler-to [this base-path context-path server-id]
                       (core/add-context-handler-to! (service-context this) server-id base-path context-path))

  (add-context-handler-to [this base-path context-path context-listeners server-id]
                       (core/add-context-handler-to! (service-context this) server-id base-path
                                                     context-path context-listeners))

  (add-ring-handler [this handler path]
                    (add-ring-handler-to this handler path :default))

  (add-ring-handler-to [this handler path server-id]
                    (core/add-ring-handler-to! (service-context this) server-id
                                               handler path))

  (add-servlet-handler [this servlet path]
                       (add-servlet-handler-to this servlet path :default))

  (add-servlet-handler [this servlet path servlet-init-params]
                       (add-servlet-handler-to this servlet path servlet-init-params :default))

  (add-servlet-handler-to [this servlet path server-id]
                       (core/add-servlet-handler-to! (service-context this) server-id servlet path))

  (add-servlet-handler-to [this servlet path servlet-init-params server-id]
                       (core/add-servlet-handler-to! (service-context this) server-id servlet
                                                     path servlet-init-params))

  (add-war-handler [this war path]
                   (add-war-handler-to this war path :default))

  (add-war-handler-to [this war path server-id]
                   (core/add-war-handler-to! (service-context this) server-id war path))

  (add-proxy-route [this target path]
                   (add-proxy-route-to this target path :default))

  (add-proxy-route [this target path options]
                   (add-proxy-route-to this target path options :default))

  (add-proxy-route-to [this target path server-id]
                   (core/add-proxy-route-to! (service-context this) server-id target path))

  (add-proxy-route-to [this target path options server-id]
                   (core/add-proxy-route-to! (service-context this) server-id
                                             target path options))

  (override-webserver-settings! [this overrides]
                                (override-webserver-settings-for! this overrides :default))

  (override-webserver-settings-for! [this overrides server-id]
                                (let [s (server-id ((service-context this) :jetty9-servers))]
                                  (core/override-webserver-settings! s
                                                                     overrides)))

  (get-registered-endpoints [this]
                            (get-registered-endpoints-from this :default))

  (get-registered-endpoints-from [this server-id]
                            (let [s (server-id ((service-context this) :jetty9-servers))]
                              (core/get-registered-endpoints s)))

  (log-registered-endpoints [this]
                            (log-registered-endpoints-from this :default))

  (log-registered-endpoints-from [this server-id]
                            (log/info (str (get-registered-endpoints-from this server-id))))

  (join [this]
        (join-server this :default))

  (join-server [this server-id]
        (let [s (server-id ((service-context this) :jetty9-servers))]
          (core/join s))))
