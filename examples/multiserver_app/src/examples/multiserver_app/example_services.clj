(ns examples.multiserver-app.example-services
  (:require [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.core :refer [defservice]]))

(defservice hello-web-service
  [[:ConfigService get-in-config]
   [:WebserverService add-ring-handler]]
  (init [this context]
    (log/info "Initializing hello webservice")
    (let [url-prefix (get-in-config [:hello-web :url-prefix])]
      ; Since we're using add-ring-handler, the ring handler will be added to the :default
      ; server specified in the config file automatically
      (add-ring-handler
        (fn [req]
          {:status  200
           :headers {"Content-Type" "text/plain"}
           :body    "Hello, World!"})
        url-prefix)
      (assoc context :url-prefix url-prefix))))

(defservice hello-proxy-service
  [[:ConfigService get-in-config]
   [:WebserverService add-proxy-route add-ring-handler]]
  (init [this context]
    (log/info "Initializing hello webservice")
    (let [url-prefix (get-in-config [:hello-web :url-prefix])]
      ; Since we're using the -to versions of the below functions and are specifying
      ; server-id :foo, these will be added to the :foo server specified in the
      ; config file.
      (add-proxy-route
        {:host "localhost"
         :port 8080
         :path "/hello"}
        "/hello"
        {:server-id :foo})
      (add-ring-handler
        (fn [req]
          {:status 200
           :headers {"Content-Type" "text/plain"}
           :body "Goodbye world"})
        "/goodbye"
        {:server-id :foo})
      (assoc context :url-prefix url-prefix))))
