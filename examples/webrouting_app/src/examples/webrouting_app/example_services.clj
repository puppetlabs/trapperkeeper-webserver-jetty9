(ns examples.webrouting-app.example-services
  (:require [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.core :refer [defservice]]
            [puppetlabs.trapperkeeper.services :refer [get-services]]))

(defn hello-app
  [req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello, World!"})

(defprotocol FooService)
(defprotocol BarService)
(defprotocol QuuxService)
(defprotocol BertService)

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
    (add-ring-handler (get-service this :BarService) hello-app {:route-id :baz})
    context))

(defservice quux-service
  QuuxService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing quux service")
    (add-ring-handler (get-service this :QuuxService) hello-app)
    context))

(defservice bert-service
  BertService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing bert service")
    (add-ring-handler (get-service this :BertService) hello-app)
    (add-ring-handler (get-service this :BertService) hello-app {:route-id :bert})
    context))
