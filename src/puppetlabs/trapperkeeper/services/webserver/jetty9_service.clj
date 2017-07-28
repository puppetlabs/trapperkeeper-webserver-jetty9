(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:import (org.eclipse.jetty.jmx MBeanContainer)
           (java.io File))
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.services.protocols.filesystem-watch-service
     :as watch-protocol]
    [puppetlabs.trapperkeeper.services :refer [get-service
                                               maybe-get-service
                                               service-context]]
    [puppetlabs.trapperkeeper.config :as tk-config]
    [puppetlabs.trapperkeeper.core :refer [defservice]]
    [puppetlabs.i18n.core :as i18n]
    [me.raynes.fs :as fs]))

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
  {:required [ConfigService]
   :optional [FilesystemWatchService]}
  (init [this context]
        (log/info (i18n/trs "Initializing web server(s)."))
        (let [config-service (get-service this :ConfigService)
              config (or (tk-config/get-in-config config-service [:webserver])
                         ;; Here for backward compatibility with existing projects
                         (tk-config/get-in-config config-service [:jetty])
                         {})]
          (config/validate-config config)
          (core/init! context config)))

  (start [this context]
         (log/info (i18n/trs "Starting web server(s)."))
         (let [config-service (get-service this :ConfigService)
               config (or (tk-config/get-in-config config-service [:webserver])
                          ;; Here for backward compatibility with existing projects
                          (tk-config/get-in-config config-service [:jetty])
                          {})
               started-context (core/start! context config)]
           (if-let [filesystem-watcher-service
                    (maybe-get-service this :FilesystemWatchService)]
             (let [watcher (watch-protocol/create-watcher filesystem-watcher-service)]
               (doseq [server (:jetty9-servers started-context)]
                 (when-let [ssl-context-factory (-> server
                                                    second
                                                    :state
                                                    deref
                                                    :ssl-context-factory)]
                   (core/reload-crl-on-change! ssl-context-factory watcher)))
               (assoc started-context :watcher watcher))
             started-context)))

  (stop [this context]
        (log/info (i18n/trs "Shutting down web server(s)."))
        (doseq [key (keys (:jetty9-servers context))]
          (if-let [server (key (:jetty9-servers context))]
            (core/shutdown server)))
        ;; this class leaks MBean names if this method is not called
        (MBeanContainer/resetUnique)
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
