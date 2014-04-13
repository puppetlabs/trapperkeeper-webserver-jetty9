(ns examples.war-app.war-app
  (:require [puppetlabs.trapperkeeper.core :refer [defservice]]
            [clojure.tools.logging :as log]))

(defservice hello-webservice
  [[:WebserverService add-war-handler]]
  (init [this context]
        (log/info "Initializing hello web service")
        (add-war-handler "dev-resources/helloWorld.war" "/test")
        context)
  (stop [this context]
        (log/info "Shutting down hello web service")
        context))