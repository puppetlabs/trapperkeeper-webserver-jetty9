(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]
    [schema.core :as schema]))

(defprotocol WebroutingService
  (add-context-handler [this svc context-path] [this svc context-path context-listeners]))

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
                             config              (:web-router-service context)
                             context-path        (get config svc)
                             add-context-handler (:add-context-handler WebserverService)]
                         (add-context-handler base-path context-path)))

  (add-context-handler [this svc base-path context-listeners]
                       (let [context             (service-context this)
                             config              (:web-router-service context)
                             context-path        (get config svc)
                             add-context-handler (:add-context-handler WebserverService)]
                         (add-context-handler base-path context-path context-listeners))))
