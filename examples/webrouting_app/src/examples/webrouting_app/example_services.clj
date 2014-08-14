(ns examples.webrouting-app.example-services
  (:require [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.core :refer [defservice]]
            [puppetlabs.trapperkeeper.services :refer [get-services]]))

(defn hello-app
  [req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello, World!"})

(defn goodbye-app
  [req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello, World!"})

(defprotocol FooService)
(defprotocol BarService)

(defservice foo-service
  FooService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing foo service")
    (add-ring-handler (get-service this :FooService) hello-app)
    context))

(defservice bar-service
  BarService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing bar service")
    (add-ring-handler (get-service this :BarService) hello-app)
    (add-ring-handler (get-service this :BarService) goodbye-app {:route-id :baz})
    context))
