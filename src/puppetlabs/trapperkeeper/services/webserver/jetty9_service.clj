(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:require
    [clojure.tools.logging :as log]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]))


;; TODO: this should probably be moved to a separate jar that can be used as
;; a dependency for all webserver service implementations
(defprotocol WebserverService
  (add-ring-handler [this handler path])
  (add-servlet-handler [this servlet path] [this servlet path servlet-init-params])
  (add-war-handler [this war path])
  (join [this]))

(defservice jetty9-service
  "Provides a Jetty 9 web server as a service"
  WebserverService
  [[:ConfigService get-in-config]]
  (init [this context]
        context)

  (start [this context]
         (log/info "Initializing web server.")
         (let [config (or (get-in-config [:webserver])
                          ;; Here for backward compatibility with existing projects
                          (get-in-config [:jetty])
                          {})
               webserver (core/create-webserver config)
               webserver-context (assoc context :jetty9-server webserver)]
           (log/info "Starting web server.")
           (core/start-webserver (webserver-context :jetty9-server))
           webserver-context))


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

  (add-servlet-handler [this servlet path servlet-init-params]
                       (let [s ((service-context this) :jetty9-server)]
                         (core/add-servlet-handler s servlet path servlet-init-params)))

  (add-war-handler [this war path]
                   (let [s ((service-context this) :jetty9-server)]
                     (core/add-war-handler s war path)))

  (join [this]
        (let [s ((service-context this) :jetty9-server)]
          (core/join s))))
