(ns examples.webrouting-app.example-services
  (:require [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.core :refer [defservice]]
            [puppetlabs.trapperkeeper.services :refer [get-services]]
            [compojure.core :as compojure]
            [compojure.route :as route]))

(defn hello-world-app
  [req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    "Hello, World!"})

(defn hello-app
  []
  (compojure/routes
    (compojure/GET "/:caller" [caller]
      (fn [req]
        (log/info "Handling request for caller:" caller)
        {:status  200
         :headers {"Content-Type" "text/plain"}
         :body    (format "Hello, %s!" caller)}))
    (route/not-found "Not Found")))

(defservice foo-service
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing foo service")
    (add-ring-handler this hello-world-app)
    context))

(defservice bar-service
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing bar service")
    (add-ring-handler this hello-world-app {:route-id :bar})
    (add-ring-handler this hello-world-app {:route-id :baz})
    context))

(defservice quux-service
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing quux service")
    (add-ring-handler this hello-world-app)
    context))

(defservice bert-service
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (log/info "Initializing bert service")
    (add-ring-handler this hello-world-app {:route-id :baz})
    (add-ring-handler this hello-world-app {:route-id :bert})
    context))

(defservice hello-service
  [[:WebroutingService add-ring-handler get-route]]
  (init [this context]
    (log/info "Initializing hello service")
    (let [url-prefix (get-route this)]
      (add-ring-handler
        this
        (compojure/context url-prefix []
          (hello-app))))
    context))