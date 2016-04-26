(ns puppetlabs.trapperkeeper.testutils.webserver
  (:require [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty9]
            [clojure.test :refer [is]]
            [clojure.java.jmx :as jmx])
  (:import (javax.management ObjectName)
           (java.lang.management ManagementFactory)))

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
         _#        (jetty9/add-ring-handler srv# ~app "/" true false)
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

(defn get-jetty-mbean-object-names
  "Queries the JVM MBean Registry and returns the ObjectNames of all of the
   Jetty MBean container objects.  For the purposes of tk-j9, there should be
   one ObjectName per Jetty Server instance, *if* the jmx-enabled setting is set
   to 'true'."
  []
  (jmx/mbean-names "org.eclipse.jetty.jmx:type=mbeancontainer,*"))

(defn assert-clean-shutdown
  "A test fixture that can be used to ensure that all of the Jetty instances have
  been cleaned up properly by the tests in a particular test namespace."
  [f]
  (f)
  ;; This sucks, because if this assertion fails, it will not give any clue as to
  ;; which test caused it to fail beyond just the test namespace.  However, I tried
  ;; several things (such as throwing an exception here rather than having an assertion),
  ;; and nothing gave any better debugging info because the metadata about which
  ;; test we're running and the call stack for the test function itself are simply
  ;; not available from inside a fixture.  So I decided that even those these aren't
  ;; super fun to debug, it was better than any other option in terms of enforcing
  ;; that the tests clean up after themselves properly.
  (is (= 0 (count (get-jetty-mbean-object-names)))))
