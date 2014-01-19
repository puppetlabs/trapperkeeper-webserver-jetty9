(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:require
    [clojure.tools.logging :as log]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]))


;; TODO: this should probably be moved to a separate jar that can be used as
;; a dependency for all webserver service implementations
(defprotocol WebserverService
  (add-ring-handler [this handler path])
  (add-servlet-handler [this servlet path])
  (join [this]))

(defservice jetty9-service
  "Provides a Jetty 9 web server as a service"
  WebserverService
  [[:ConfigService get-in-config]]
  (init [this context]
        (log/info "Initializing web server.")
        (let [config (or (get-in-config [:webserver])
                         ;; Here for backward compatibility with existing projects
                         (get-in-config [:jetty])
                         {})
              webserver (core/create-webserver config)]
          (assoc context :jetty9-server webserver)))

  (start [this context]
         (log/info "Starting web server.")
         (core/start-webserver (context :jetty9-server))
         context)

  (stop [this context]
        (log/info "Shutting down web server.")
        (core/shutdown (context :jetty9-server))
        context)

  (add-ring-handler [this handler path]
                    (let [s ((service-context this) :jetty9-server)]
                      (core/add-ring-handler s handler path)))

  (add-servlet-handler [this servlet path]
                       (let [s ((service-context this) :jetty9-server)]
                         (core/add-servlet-handler s servlet path)))

  (join [this]
        (let [s ((service-context this) :jetty9-server)]
          (core/join s))))
