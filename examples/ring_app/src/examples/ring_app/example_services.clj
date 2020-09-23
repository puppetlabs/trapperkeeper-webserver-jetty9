(ns examples.ring-app.example-services
  (:import (clojure.lang Atom))
  (:require [clojure.tools.logging :as log]
            [puppetlabs.kitchensink.core :refer [pprint-to-string]]
            [puppetlabs.trapperkeeper.core :refer [defservice]]
            [puppetlabs.trapperkeeper.services :refer [service-context]]))

(defn- inc-and-get*
  "Increments the hit count for the provided endpoint and returns the new hit count."
  [hit-counts endpoint]
  {:pre [(instance? Atom hit-counts)
         (string? endpoint)]
   :post [(integer? %) (> % 0)]}

  (let [new-hit-counts (swap! hit-counts update-in [endpoint] (fnil inc 0))]
    (log/debug "Incrementing hit count for" endpoint "from"
               (dec (new-hit-counts endpoint)) "to" (new-hit-counts endpoint))

    (new-hit-counts endpoint)))

(defprotocol CountService
  :extend-via-metadata true
  (inc-and-get [this endpoint]))

(defservice count-service
  "This is a simple service which simply keeps a counter. It contains one function, inc-and-get, which
   increments the count and returns it."
  ;; Here we specify the service's protocol
  CountService
  ;; This vector declares the service's dependencies on other services and their functions,
  []
  ;; Implement the `init` function from the `Lifecycle` protocol to
  ;; initialize state:
  (init [this context]
    (assoc context :hit-counts (atom {})))
  ;; Implement the inc-and-get function.
  (inc-and-get [this endpoint]
    (inc-and-get* ((service-context this) :hit-counts) endpoint)))

(defn- success-response
  "Return a ring response map containing a HTTP response code of 200 (OK) and HTML which displays the hitcount on this
   endpoint as well as all the data provided by Ring."
  [hit-count req]
  {:status 200
   :body (str "<h1>Hello from http://" (:server-name req) ":" (:server-port req) (:uri req) "</h1>"
              (if (:debug? req) "<h3>DEBUGGING ENABLED!</h3>" "")
              "<p>You are visitor number " hit-count ".</p>"
              "<pre>" (pprint-to-string req) "</pre>")})

(defn- ring-handler
  "Executes the inc-and-get command and passes it into success-reponse which generates a ring response."
  [inc-and-get endpoint req]
  (success-response (inc-and-get endpoint) req))

(defservice bert-service
  "This is the bert web service. The Clojure web application library, Ring, is used to create simple
   responses to an endpoint. It depends on the count-service above to use as a primitive hit counter.
   See https://github.com/ring-clojure/ring for documentation on Ring."

  ;; This service needs functionality from the webserver service, and the count service.
  [[:WebserverService add-ring-handler]
   [:CountService inc-and-get]]

  ;; Implement the `init` lifecycle function to register the ring handler
  (init [this context]
    (let [endpoint "/bert"]
      (add-ring-handler (partial ring-handler inc-and-get endpoint) endpoint))
    context)

  (stop [this context]
    (log/info "Bert service shutting down")
    context))

(defn debug-middleware
  "Ring middleware to add the :debug configuration value to the request map."
  [app debug?]
  (fn [req]
    (app (assoc req :debug? debug?))))

(defservice ernie-service
  "This is the ernie service which operates on the /ernie endpoint. It is essentially identical to the bert service."
  [[:WebserverService add-ring-handler]
   [:CountService inc-and-get]
   [:ConfigService get-in-config]]

  (init [this context]
    (let [endpoint (get-in-config [:example :ernie-url-prefix])
          ring-handler (-> (partial ring-handler inc-and-get endpoint)
                           (debug-middleware (get-in-config [:debug])))]
      (add-ring-handler ring-handler endpoint))
    context)

  (stop [this context]
    (log/info "Ernie service shutting down")
    context))
