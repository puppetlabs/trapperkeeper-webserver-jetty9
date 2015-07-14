(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.services :refer [service-context]]
    [puppetlabs.trapperkeeper.core :refer [defservice]]
    [schema.core :as schema]))

;; TODO: this should probably be moved to a separate jar that can be used as
;; a dependency for all webserver service implementations
(defprotocol WebserverService
  (add-context-handler [this base-path context-path] [this base-path context-path options])
  (add-ring-handler [this handler path] [this handler path options])
  (add-websocket-handler [this handlers path] [this handler path options])
  (add-servlet-handler [this servlet path] [this servlet path options])
  (add-war-handler [this war path] [this war path options])
  (add-proxy-route [this target path] [this target path options])
  (override-webserver-settings! [this overrides] [this server-id overrides])
  (get-registered-endpoints [this] [this server-id])
  (log-registered-endpoints [this] [this server-id])
  (join [this] [this server-id]))

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
          (config/validate-config config)
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
                       (core/add-context-handler! (service-context this) base-path context-path {}))

  (add-context-handler [this base-path context-path options]
                       (core/add-context-handler! (service-context this) base-path context-path options))

  (add-ring-handler [this handler path]
                    (core/add-ring-handler! (service-context this) handler path {}))

  (add-ring-handler [this handler path options]
                    (core/add-ring-handler! (service-context this) handler path options))

  (add-websocket-handler [this handlers path]
    (core/add-websocket-handler! (service-context this) handlers path {}))

  (add-websocket-handler [this handlers path options]
    (core/add-websocket-handler! (service-context this) handlers path options))

  (add-servlet-handler [this servlet path]
                       (core/add-servlet-handler! (service-context this) servlet path {}))

  (add-servlet-handler [this servlet path options]
                       (core/add-servlet-handler! (service-context this) servlet path options))

  (add-war-handler [this war path]
                   (core/add-war-handler! (service-context this) war path {}))

  (add-war-handler [this war path options]
                   (core/add-war-handler! (service-context this) war path options))

  (add-proxy-route [this target path]
                   (core/add-proxy-route! (service-context this) target path {}))

  (add-proxy-route [this target path options]
                   (core/add-proxy-route! (service-context this) target path options))

  (override-webserver-settings! [this overrides]
                                (let [s (core/get-server-context (service-context this) nil)]
                                  (core/override-webserver-settings! s overrides)))

  (override-webserver-settings! [this server-id overrides]
                                (let [s (core/get-server-context (service-context this) server-id)]
                                  (core/override-webserver-settings! s overrides)))

  (get-registered-endpoints [this]
                            (let [s (core/get-server-context (service-context this) nil)]
                              (core/get-registered-endpoints s)))

  (get-registered-endpoints [this server-id]
                            (let [s (core/get-server-context (service-context this) server-id)]
                              (core/get-registered-endpoints s)))
  (log-registered-endpoints [this]
                            (log/info (str (get-registered-endpoints this))))

  (log-registered-endpoints[this server-id]
                            (log/info (str (get-registered-endpoints this server-id))))

  (join [this]
        (let [s (core/get-server-context (service-context this) nil)]
          (core/join s)))

  (join [this server-id]
        (let [s (core/get-server-context (service-context this) server-id)]
          (core/join s))))
