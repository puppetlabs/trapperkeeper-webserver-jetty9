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
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
            [puppetlabs.trapperkeeper.testutils.webserver :as testutils]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-test-logging]])
  (:import (org.eclipse.jetty.server HttpConfiguration ServerConnector Server)
           (org.eclipse.jetty.util.thread QueuedThreadPool)))

(use-fixtures :once
  schema-test/validate-schemas
  testutils/assert-clean-shutdown)

(deftest default-request-header-max-size-test
  (let [http-config (HttpConfiguration.)]
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.4.1.v20170120/jetty-server/src/main/java/org/eclipse/jetty/server/HttpConfiguration.java#L55
    (is (= 8192 (.getRequestHeaderSize http-config))
        "Unexpected default for 'request-header-max-size'")))

(deftest default-proxy-http-client-settings-test
  (with-test-logging
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
                            true
                            false)
            client        (.createHttpClient proxy-servlet)]
        ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.4.1.v20170120/jetty-client/src/main/java/org/eclipse/jetty/client/HttpClient.java#L135
        (is (= 4096 (.getRequestBufferSize client))
            "Unexpected default for proxy 'request-buffer-size'")
        ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.4.1.v20170120/jetty-proxy/src/main/java/org/eclipse/jetty/proxy/AbstractProxyServlet.java#L304-L307
        (is (= 30000 (.getIdleTimeout client))
            "Unexpected default for proxy 'idle-timeout'")
        (.stop client)))))

(defn selector-thread-count
  [max-threads]
  "The number of selector threads that should be allocated per connector.
   https://github.com/eclipse/jetty.project/blob/jetty-9.4.11.v20180605/jetty-io/src/main/java/org/eclipse/jetty/io/SelectorManager.java#L70-L74"
  (max 1
       (min (int (/ max-threads 16))
            (int (/ (ks/num-cpus) 2)))))

(def acceptor-thread-count
  "The number of acceptor threads that should be allocated per connector. See:
   https://github.com/eclipse/jetty.project/blob/jetty-9.4.11.v20180605/jetty-server/src/main/java/org/eclipse/jetty/server/AbstractConnector.java#L202"
  (max 1 (min 4 (int (/ (ks/num-cpus) 8)))))

(defn reserved-thread-count
  "Jetty will reserve threads for future connector work on start. See
  https://github.com/eclipse/jetty.project/blob/jetty-9.4.11.v20180605/jetty-util/src/main/java/org/eclipse/jetty/util/thread/ReservedThreadExecutor.java#L104"
  [max-threads]
  (max 1
       (min (ks/num-cpus)
            (int (/ max-threads 10)))))

(deftest default-connector-settings-test
  (let [connector (ServerConnector. (Server.))]
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.4.1.v20170120/jetty-server/src/main/java/org/eclipse/jetty/server/AbstractConnector.java#L150
    (is (= 30000 (.getIdleTimeout connector))
        "Unexpected default for 'idle-timeout-milliseconds'")
    (is (= acceptor-thread-count (.getAcceptors connector))
        "Unexpected default for 'acceptor-threads' and 'ssl-acceptor-threads'")
    (is (= (selector-thread-count (-> connector .getExecutor .getMaxThreads))
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
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.4.1.v20170120/jetty-util/src/main/java/org/eclipse/jetty/util/component/AbstractLifeCycle.java#L48
    (is (= 30000 (.getStopTimeout server))
        "Unexpected default for 'shutdown-timeout-seconds'")
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.4.1.v20170120/jetty-util/src/main/java/org/eclipse/jetty/util/thread/QueuedThreadPool.java#L71
    (is (= 200 (get-max-threads-for-server server))
        "Unexpected default for 'max-threads'")
    ;; See: https://github.com/eclipse/jetty.project/blob/jetty-9.4.1.v20170120/jetty-util/src/main/java/org/eclipse/jetty/util/BlockingArrayQueue.java#L92
    (is (= (Integer/MAX_VALUE) (.getMaxCapacity
                                 (get-server-thread-pool-queue server)))
        "Unexpected default for 'queue-max-size'")))

(defn required-threads-for-sized-threadpool-per-connector
  [threadpool-size]
  "The total number of threads needed per attached connector. This scales
  with the threadpool size and acceptor threads are not allocated until after
  reserved and selector threads have be allocated. For an overview see:
  https://support.sonatype.com/hc/en-us/articles/360000744687-Understanding-Eclipse-Jetty-9-4-8-Thread-Allocation
  This is unused below so we can configure the needed threads in a trivial
  case without referencing the max threads. Left for documentation."
  (+ (reserved-thread-count threadpool-size)
     (selector-thread-count threadpool-size)
     acceptor-thread-count))

