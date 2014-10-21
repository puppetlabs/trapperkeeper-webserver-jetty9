(ns puppetlabs.trapperkeeper.testutils.webserver
  (:require [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty9]))

(defmacro with-test-webserver-with-cors-config
  "Constructs and starts an embedded Jetty on a random port with a custom
  configuration and evaluates `body` inside a try/finally block that takes
  care of tearing down the webserver.

  `app` - The ring application the webserver should serve

  `port-var` - Inside of `body`, the variable named `port-var`
  contains the port number the webserver is listening on

  `config` - Configuration to use for the webserver

  Example:

      (let [app (constantly {:status 200 :headers {} :body \"OK\"})]
        (with-test-webserver app port {:request-header-max-size 1024}
          ;; Hit the embedded webserver
          (http-client/get (format \"http://localhost:%s\" port))))"
  [app port-var config cors-config & body]
  `(let [srv#      (jetty9/start-webserver!
                     (jetty9/initialize-context)
                     (assoc ~config :port 0))
         _#        (jetty9/add-ring-handler srv# ~app "/" true ~cors-config)
         ~port-var (-> (:server srv#)
                     (.getConnectors)
                     (first)
                     (.getLocalPort))]
     (try
       ~@body
       (finally
         (jetty9/shutdown srv#)))))

(defmacro with-test-webserver-and-config
  "Constructs and starts an embedded Jetty on a random port with a custom
  configuration and evaluates `body` inside a try/finally block that takes
  care of tearing down the webserver.

  `app` - The ring application the webserver should serve

  `port-var` - Inside of `body`, the variable named `port-var`
  contains the port number the webserver is listening on

  `config` - Configuration to use for the webserver

  Example:

      (let [app (constantly {:status 200 :headers {} :body \"OK\"})]
        (with-test-webserver app port {:request-header-max-size 1024}
          ;; Hit the embedded webserver
          (http-client/get (format \"http://localhost:%s\" port))))"
  [app port-var config & body]
  `(let [srv#      (jetty9/start-webserver!
                     (jetty9/initialize-context)
                     (assoc ~config :port 0))
         _#        (jetty9/add-ring-handler srv# ~app "/" true)
         ~port-var (-> (:server srv#)
                       (.getConnectors)
                       (first)
                       (.getLocalPort))]
     (try
       ~@body
       (finally
         (jetty9/shutdown srv#)))))

(defmacro with-test-webserver
  "Constructs and starts an embedded Jetty on a random port, and
  evaluates `body` inside a try/finally block that takes care of
  tearing down the webserver.

  `app` - The ring application the webserver should serve

  `port-var` - Inside of `body`, the variable named `port-var`
  contains the port number the webserver is listening on

  Example:

      (let [app (constantly {:status 200 :headers {} :body \"OK\"})]
        (with-test-webserver app port
          ;; Hit the embedded webserver
          (http-client/get (format \"http://localhost:%s\" port))))"
  [app port-var & body]
  `(with-test-webserver-and-config
     ~app
     ~port-var
     {}
     ~@body))
