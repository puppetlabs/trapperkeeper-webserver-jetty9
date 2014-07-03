(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
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
  (get-registered-endpoints [this])
  (log-registered-endpoints [this])
  (join [this]))

(defservice jetty9-service
  "Provides a Jetty 9 web server as a service"
  WebserverService
  [[:ConfigService get-in-config]]
  (init [this context]
        (log/info "Initializing web server.")
        (assoc context :jetty9-servers {:default (core/initialize-context)}))

  (start [this context]
         (log/info "Starting web server.")
         (let [config (or (get-in-config [:webserver])
                          ;; Here for backward compatibility with existing projects
                          (get-in-config [:jetty])
                          {})
               webserver (core/start-webserver! (:default (:jetty9-servers context)) config)]
           (swap! (:state (:default (:jetty9-servers context))) assoc :endpoints #{})
           (assoc context :jetty9-servers (assoc (:jetty9-servers context) :default webserver))))

  (stop [this context]
        (log/info "Shutting down web server.")
        (if-let [server (:default (:jetty9-servers context))]
          (core/shutdown server))
        context)

  (add-context-handler [this base-path context-path]
                       (let [s             (:default ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type      :context
                                            :base-path base-path
                                            :endpoint  context-path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-context-handler s base-path context-path)))

  (add-context-handler [this base-path context-path context-listeners]
                       (let [s             (:default ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type              :context
                                            :base-path         base-path
                                            :context-listeners context-listeners
                                            :endpoint          context-path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-context-handler s base-path context-path context-listeners)))

  (add-ring-handler [this handler path]
                    (let [s             (:default ((service-context this) :jetty9-servers))
                          state         (:state s)
                          endpoint      {:type     :ring
                                         :endpoint path}]
                      (core/register-endpoint! state endpoint)
                      (core/add-ring-handler s handler path)))

  (add-servlet-handler [this servlet path]
                       (let [s             (:default ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type    :servlet
                                            :servlet (type servlet)
                                            :endpoint path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-servlet-handler s servlet path)))

  (add-servlet-handler [this servlet path servlet-init-params]
                       (let [s             (:default ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type    :servlet
                                            :servlet (type servlet)
                                            :endpoint path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-servlet-handler s servlet path servlet-init-params)))

  (add-war-handler [this war path]
                   (let [s             (:default ((service-context this) :jetty9-servers))
                         state         (:state s)
                         endpoint      {:type     :war
                                        :war-path war
                                        :endpoint path}]
                     (core/register-endpoint! state endpoint)
                     (core/add-war-handler s war path)))

  (add-proxy-route [this target path]
                   (let [s             (:default ((service-context this) :jetty9-servers))
                         state         (:state s)
                         endpoint      {:type        :proxy
                                        :target-host (:host target)
                                        :target-port (:port target)
                                        :target-path (:path target)
                                        :endpoint     path}]
                     (core/register-endpoint! state endpoint)
                     (core/add-proxy-route s target path {})))

  (add-proxy-route [this target path options]
                   (let [s             (:default ((service-context this) :jetty9-servers))
                         state         (:state s)
                         endpoint      {:type        :proxy
                                        :target-host (:host target)
                                        :target-port (:port target)
                                        :target-path (:path target)
                                        :endpoint     path}]
                     (core/register-endpoint! state endpoint)
                     (core/add-proxy-route s target path options)))

  (override-webserver-settings! [this overrides]
                                (let [s (:default ((service-context this) :jetty9-servers))]
                                  (core/override-webserver-settings! s
                                                                     overrides)))

  (get-registered-endpoints [this]
                            (let [s (:default ((service-context this) :jetty9-servers))]
                              (core/get-registered-endpoints s)))

  (log-registered-endpoints [this]
                            (log/info (str (get-registered-endpoints this))))

  (join [this]
        (let [s (:default ((service-context this) :jetty9-servers))]
          (core/join s))))
