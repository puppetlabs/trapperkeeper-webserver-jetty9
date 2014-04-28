(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:import (java.io IOException))
  (:require
    [clojure.tools.logging :as log]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]))


;; TODO: this should probably be moved to a separate jar that can be used as
;; a dependency for all webserver service implementations
(defprotocol WebserverService
  (add-context-handler [this base-path context-path] [this base-path context-path context-listeners])
  (add-ring-handler [this handler path])
  (add-servlet-handler [this servlet path] [this servlet path servlet-init-params])
  (add-war-handler [this war path])
  (add-proxy-route [this target path] [this target path options])
  (override-webserver-settings! [this overrides])
  (join [this]))

(defservice jetty9-service
  "Provides a Jetty 9 web server as a service"
  WebserverService
  [[:ConfigService get-in-config]]
  (init [this context]
        (log/info "Initializing web server.")
        (assoc context :jetty9-server (core/create-handlers)))

  (start [this context]
         (let [config (or (get-in-config [:webserver])
                          ;; Here for backward compatibility with existing projects
                          (get-in-config [:jetty])
                          {})
               webserver (core/create-webserver config (:jetty9-server context))]
           (log/info "Starting web server.")
           (try
             (core/start-webserver webserver)
             (catch IOException e
               (log/error
                 e
                 "Encountered error starting web server, so shutting down")
               (core/shutdown webserver)
               (throw e)))
           (assoc context :jetty9-server webserver)))

  (stop [this context]
        (log/info "Shutting down web server.")
        (core/shutdown (context :jetty9-server))
        context)

  (add-context-handler [this base-path context-path]
                       (let [s ((service-context this) :jetty9-server)]
                         (core/add-context-handler s base-path context-path)))

  (add-context-handler [this base-path context-path context-listeners]
                       (let [s ((service-context this) :jetty9-server)]
                         (core/add-context-handler s base-path context-path context-listeners)))

  (add-ring-handler [this handler path]
                    (let [s ((service-context this) :jetty9-server)]
                      (core/add-ring-handler s handler path)))

  (add-servlet-handler [this servlet path]
                       (let [s ((service-context this) :jetty9-server)]
                         (core/add-servlet-handler s servlet path)))

  (add-servlet-handler [this servlet path servlet-init-params]
                       (let [s ((service-context this) :jetty9-server)]
                         (core/add-servlet-handler s servlet path servlet-init-params)))

  (add-war-handler [this war path]
                   (let [s ((service-context this) :jetty9-server)]
                     (core/add-war-handler s war path)))

  (add-proxy-route [this target path]
                   (let [s ((service-context this) :jetty9-server)]
                     (core/add-proxy-route s target path {})))

  (add-proxy-route [this target path options]
                   (let [s ((service-context this) :jetty9-server)]
                     (core/add-proxy-route s target path options)))

  (override-webserver-settings! [this overrides]
                                (let [s ((service-context this) :jetty9-server)]
                                  (core/override-webserver-settings! s
                                                                     overrides)))

  (join [this]
        (let [s ((service-context this) :jetty9-server)]
          (core/join s))))
