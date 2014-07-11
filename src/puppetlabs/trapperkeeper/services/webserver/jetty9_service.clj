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
        (let [config (or (get-in-config [:webserver])
                         ;; Here for backward compatibility with existing projects
                         (get-in-config [:jetty])
                         {})]
          (log/info "Initializing web server(s).")
          (if (nil? (schema/check config/WebserverRawConfig config))
            (assoc context :jetty9-servers {:default (core/initialize-context)})
            (assoc context :jetty9-servers (into {} (for [[server-id] config]
                                                      [server-id (core/initialize-context)]))))))

  (start [this context]
         (log/info "Starting web server(s).")
         (let [config (or (get-in-config [:webserver])
                          ;; Here for backward compatibility with existing projects
                          (get-in-config [:jetty])
                          {})]
           (if (nil? (schema/check config/WebserverRawConfig config))
             (let [default-context     (:default (:jetty9-servers context))
                   webserver           (core/start-webserver! default-context config)
                   server-context-list (assoc (:jetty9-servers context) :default webserver)]
               (assoc context :jetty9-servers server-context-list))
             (let [context-seq (for [[server-id server-context] (:jetty9-servers context)]
                                 [server-id (core/start-webserver! server-context (server-id config))])]
               (assoc context :jetty9-servers (into {} context-seq))))))

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
                       (let [s             (server-id ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type      :context
                                            :base-path base-path
                                            :endpoint  context-path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-context-handler s base-path context-path)))

  (add-context-handler-to [this base-path context-path context-listeners server-id]
                       (let [s             (server-id ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type              :context
                                            :base-path         base-path
                                            :context-listeners context-listeners
                                            :endpoint          context-path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-context-handler s base-path context-path context-listeners)))

  (add-ring-handler [this handler path]
                    (add-ring-handler-to this handler path :default))

  (add-ring-handler-to [this handler path server-id]
                    (let [s             (server-id ((service-context this) :jetty9-servers))
                          state         (:state s)
                          endpoint      {:type     :ring
                                         :endpoint path}]
                      (core/register-endpoint! state endpoint)
                      (core/add-ring-handler s handler path)))

  (add-servlet-handler [this servlet path]
                       (add-servlet-handler-to this servlet path :default))

  (add-servlet-handler [this servlet path servlet-init-params]
                       (add-servlet-handler-to this servlet path servlet-init-params :default))

  (add-servlet-handler-to [this servlet path server-id]
                       (let [s             (server-id ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type    :servlet
                                            :servlet (type servlet)
                                            :endpoint path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-servlet-handler s servlet path)))

  (add-servlet-handler-to [this servlet path servlet-init-params server-id]
                       (let [s             (server-id ((service-context this) :jetty9-servers))
                             state         (:state s)
                             endpoint      {:type    :servlet
                                            :servlet (type servlet)
                                            :endpoint path}]
                         (core/register-endpoint! state endpoint)
                         (core/add-servlet-handler s servlet path servlet-init-params)))

  (add-war-handler [this war path]
                   (add-war-handler-to this war path :default))

  (add-war-handler-to [this war path server-id]
                   (let [s             (server-id ((service-context this) :jetty9-servers))
                         state         (:state s)
                         endpoint      {:type     :war
                                        :war-path war
                                        :endpoint path}]
                     (core/register-endpoint! state endpoint)
                     (core/add-war-handler s war path)))

  (add-proxy-route [this target path]
                   (add-proxy-route-to this target path :default))

  (add-proxy-route [this target path options]
                   (add-proxy-route-to this target path options :default))

  (add-proxy-route-to [this target path server-id]
                   (let [s             (server-id ((service-context this) :jetty9-servers))
                         state         (:state s)
                         endpoint      {:type        :proxy
                                        :target-host (:host target)
                                        :target-port (:port target)
                                        :target-path (:path target)
                                        :endpoint     path}]
                     (core/register-endpoint! state endpoint)
                     (core/add-proxy-route s target path {})))

  (add-proxy-route-to [this target path options server-id]
                   (let [s             (server-id ((service-context this) :jetty9-servers))
                         state         (:state s)
                         endpoint      {:type        :proxy
                                        :target-host (:host target)
                                        :target-port (:port target)
                                        :target-path (:path target)
                                        :endpoint     path}]
                     (core/register-endpoint! state endpoint)
                     (core/add-proxy-route s target path options)))

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
