(ns examples.servlet-app.servlet-app
  (:import  [examples.servlet_app MyServlet])
  (:require [puppetlabs.trapperkeeper.core :refer [defservice]]
            [clojure.tools.logging :as log]))

(defservice hello-servlet-service
  [[:WebserverService add-servlet-handler]]
  (init [this context]
        (log/info "Initializing hello-servlet-service")
        (add-servlet-handler (MyServlet. "Hi there!") "/hello")
        (add-servlet-handler (MyServlet. "See you later!") "/goodbye")
        context)
  (stop [this context]
        (log/info "Shutting down hello-servlet-service")
        context))

