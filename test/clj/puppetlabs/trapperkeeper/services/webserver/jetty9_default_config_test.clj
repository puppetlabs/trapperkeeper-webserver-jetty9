(ns puppetlabs.trapperkeeper.services.webserver.jetty9-default-config-test
  "
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; VALIDATION OF DEFAULT JETTY CONFIGURATION VALUES
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

NOTE: IF A TEST IN THIS NAMESPACE FAILS, AND YOU ALTER THE VALUE TO MAKE IT
PASS, IT IS YOUR RESPONSIBILITY TO DOUBLE-CHECK THE DOCS TO SEE IF THERE
IS ANYWHERE IN THEM THAT THE NEW VALUE NEEDS TO BE ADDED.

This namespace is a little different than most of our test namespaces.  It's
not really intended to test any of our own code, it's just here to provide
us with a warning in the event that Jetty changes any of the default
configuration values.

In the conversation leading up to https://tickets.puppetlabs.com/browse/TK-168
we decided that it was generally not a good idea to be hard-coding our own
default values for the settings that we exposed, and that it would be a better
idea to allow Jetty to use its implicit default values for any settings that
are not explicitly set in a TK config file.  Otherwise, we're at risk of
the Jetty authors coming up with a really compelling reason to change a
default value between releases, and us not picking up that change.

Therefore, we decided that all the settings we expose should just fall
through to Jetty's implicit defaults, and that individual TK application
authors can override any appropriate settings in their packaging if needed.

However, there was some concern that if an upstream Jetty default were to
change without us knowing about it, it could have other implications for our
applications that we ought to be aware of.  Therefore, we agreed that it
would be best if we had some way of making sure we could identify when
that situation arose.

That is the purpose of this namespace.  It basically provides assertions
to validate that we know what Jetty's implicit default value is for all of
the settings we expose.  If we bump to a new version of Jetty in the future
and any of these implicit defaults have changed, these tests will fail.  If
that happens, we can attempt to evaluate the impact of the change and
react accordingly."
  (:require [clojure.test :refer :all]
            [schema.test :as schema-test]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core])
  (:import (org.eclipse.jetty.server HttpConfiguration ServerConnector Server)
           (org.eclipse.jetty.util.thread QueuedThreadPool)))

(use-fixtures :once schema-test/validate-schemas)

(deftest default-request-header-max-size-test
  (let [http-config (HttpConfiguration.)]
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-server/src/main/java/org/eclipse/jetty/server/HttpConfiguration.java#L49
    (is (= 8192 (.getRequestHeaderSize http-config))
        "Unexpected default for 'request-header-max-size'")))

(deftest default-proxy-http-client-settings-test
  (with-app-with-config app
    [jetty9-service]
    {:webserver {:host "localhost" :port 8080}}
    (let [s (get-service app :WebserverService)
          server-context (get-in (service-context s) [:jetty9-servers :default])
          proxy-servlet (core/proxy-servlet
                          server-context
                          {:host "localhost"
                           :path "/foo"
                           :port 8080}
                          {})
          _             (core/add-servlet-handler
                          server-context
                          proxy-servlet
                          "/proxy"
                          {}
                          true)
          client        (.createHttpClient proxy-servlet)]
      ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-client/src/main/java/org/eclipse/jetty/client/HttpClient.java#L129
      (is (= 4096 (.getRequestBufferSize client))
          "Unexpected default for proxy 'request-buffer-size'")
      ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-proxy/src/main/java/org/eclipse/jetty/proxy/AbstractProxyServlet.java#L268-L271
      (is (= 30000 (.getIdleTimeout client))
          "Unexpected default for proxy 'idle-timeout'")
      (.stop client))))

(def selector-thread-count
  "The number of selector threads that should be allocated per connector.  See:
   https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-server/src/main/java/org/eclipse/jetty/server/ServerConnector.java#L229"
  (max 1 (min 4 (int (/ (ks/num-cpus) 2)))))

(def acceptor-thread-count
  "The number of acceptor threads that should be allocated per connector.  See:
   https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-server/src/main/java/org/eclipse/jetty/server/AbstractConnector.java#L190"
  (max 1 (min 4 (int (/ (ks/num-cpus) 8)))))

(deftest default-connector-settings-test
  (let [connector (ServerConnector. (Server.))]
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-server/src/main/java/org/eclipse/jetty/server/ServerConnector.java#L85
    (is (= -1 (.getSoLingerTime connector))
        "Unexpected default for 'so-linger-seconds'")
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-server/src/main/java/org/eclipse/jetty/server/AbstractConnector.java#L146
    (is (= 30000 (.getIdleTimeout connector))
        "Unexpected default for 'idle-timeout-milliseconds'")
    (is (= acceptor-thread-count (.getAcceptors connector))
        "Unexpected default for 'acceptor-threads' and 'ssl-acceptor-threads'")
    (is (= selector-thread-count
           (.getSelectorCount (.getSelectorManager connector)))
        "Unexpected default for 'selector-threads' and 'ssl-selector-threads'")))

(defn get-max-threads-for-server
  [server]
  (.getMaxThreads (.getThreadPool server)))

(defn get-server-thread-pool-queue
  [server]
  (let [thread-pool      (.getThreadPool server)
        ;; Using reflection here because the .getQueue method is protected and I
        ;; didn't see any other way to pull the queue back from the thread pool.
        get-queue-method (-> thread-pool
                             (.getClass)
                             (.getDeclaredMethod "getQueue" nil))
        _                (.setAccessible get-queue-method true)]
    (.invoke get-queue-method thread-pool nil)))

(deftest default-server-settings-test
  (let [server (Server.)]
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-util/src/main/java/org/eclipse/jetty/util/component/AbstractLifeCycle.java#L48
    (is (= 30000 (.getStopTimeout server))
        "Unexpected default for 'shutdown-timeout-seconds'")
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-util/src/main/java/org/eclipse/jetty/util/thread/QueuedThreadPool.java#L67
    (is (= 200 (get-max-threads-for-server server))
        "Unexpected default for 'max-threads'")
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-util/src/main/java/org/eclipse/jetty/util/BlockingArrayQueue.java#L117
    (is (= (Integer/MAX_VALUE) (.getMaxCapacity
                                 (get-server-thread-pool-queue server)))
        "Unexpected default for 'queue-max-size'")))

(def threads-per-connector
  "The total number of threads needed per attached connector."
  (+ acceptor-thread-count selector-thread-count))

(defn calculate-minimum-required-threads
  "Calculate the minimum number threads that a single Jetty Server instance
  needs.  See: https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-server/src/main/java/org/eclipse/jetty/server/Server.java#L334-L350"
  [connectors]
  (+ 1 (* connectors threads-per-connector)))

(deftest default-min-threads-settings-test
  ;; This test just exists to validate the advice we give for the bare
  ;; minimum number of threads that one should account for when setting the
  ;; 'max-threads' setting for a server instance.
  (letfn [(get-server [max-threads connectors]
            (let [server (Server. (QueuedThreadPool. max-threads))]
              (dotimes [_ connectors]
                (.addConnector server (ServerConnector. server)))
              server))
          (insufficient-threads-msg [server]
            (let [connectors (count (.getConnectors server))]
              (re-pattern (str "Insufficient threads: max="
                               (get-max-threads-for-server server)
                               " < needed\\(acceptors="
                               (* acceptor-thread-count connectors)
                               " \\+ selectors="
                               (* selector-thread-count connectors)
                               " \\+ request=1\\)"))))]
    (dotimes [x 5]
      (let [connectors       (inc x)
            required-threads (calculate-minimum-required-threads connectors)]
        (testing (str "server with too few threads for " x " connector(s) "
                      "fail(s) to start with expected error")
          (let [server (-> required-threads
                           dec
                           (get-server connectors))]
            (is (thrown-with-msg? IllegalStateException
                                  (insufficient-threads-msg server)
                                  (.start server)))))
        (testing (str "server with minimum required threads for " x
                      "connector(s) start(s) successfully")
          (let [server (get-server required-threads connectors)]
            (try
              (.start server)
              (is (.isStarted server))
              (finally
                (.stop server)))))))))
