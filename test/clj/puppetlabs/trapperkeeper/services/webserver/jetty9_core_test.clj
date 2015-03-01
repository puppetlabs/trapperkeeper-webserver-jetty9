(ns puppetlabs.trapperkeeper.services.webserver.jetty9-core-test
  (:import
    (org.eclipse.jetty.server.handler ContextHandlerCollection)
    (java.security KeyStore)
    (java.net SocketTimeoutException Socket)
    (java.io InputStreamReader BufferedReader PrintWriter))
  (:require [clojure.test :refer :all]
            [clojure.java.jmx :as jmx]
            [ring.util.response :as rr]
            [puppetlabs.http.client.sync :as http-sync]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty]
            [puppetlabs.trapperkeeper.testutils.webserver
             :refer [with-test-webserver with-test-webserver-and-config]]
            [puppetlabs.trapperkeeper.app :as tk-app]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
             :refer [jetty9-service add-ring-handler]]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-test-logging]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]))

(deftest handlers
  (testing "create-handlers should allow for handlers to be added"
    (let [webserver-context (jetty/initialize-context)
          handlers          (:handlers webserver-context)]
      (jetty/add-ring-handler webserver-context
                              (fn [req] {:status 200
                                         :body "I am a handler"})
                              "/"
                              true)
      (is (= (count (.getHandlers handlers)) 1)))))

(defn validate-gzip-encoding-when-gzip-requested
  [body port]
  ;; The client/get function asks for compression by default
  (let [resp (http-sync/get (format "http://localhost:%d/" port))]
    (is (= (slurp (resp :body)) body))
    (is (= (get-in resp [:orig-content-encoding]) "gzip")
        (format "Expected gzipped response, got this response: %s"
                resp))))

(defn validate-no-gzip-encoding-when-gzip-not-requested
  [body port]
  ;; The client/get function asks for compression by default
  (let [resp (http-sync/get (format "http://localhost:%d/" port)
                              {:decompress-body false})]
    (is (= (slurp (resp :body)) body))
    ;; We should not receive a content-encoding header in the
    ;; uncompressed case
    (is (nil? (get-in resp [:headers "content-encoding"]))
        (format "Expected uncompressed response, got this response: %s"
                resp))))

(defn validate-no-gzip-encoding-even-though-gzip-requested
  [body port]
  ;; The client/get function asks for compression by default
  (let [resp (http-sync/get (format "http://localhost:%d/" port))]
    (is (= (slurp (resp :body)) body))
    ;; We should not receive a content-encoding header in the
    ;; uncompressed case
    (is (nil? (get-in resp [:headers "content-encoding"]))
        (format "Expected uncompressed response, got this response: %s"
                resp))))

(deftest compression
  (testing "should return"
    ;; Jetty may not Gzip encode a response body if the size of the response
    ;; is less than 256 bytes, so returning a larger body to ensure that Gzip
    ;; encoding is used where desired for these tests
    (let [body (apply str (repeat 1000 "f"))
          app  (fn [req]
                 (-> body
                     (rr/response)
                     (rr/status 200)
                     (rr/content-type "text/plain")
                     (rr/charset "UTF-8")))]
      (with-test-webserver app port
        (testing "a gzipped response when request wants a compressed one and
                  server not configured with a default for gzip-enable"
          (validate-gzip-encoding-when-gzip-requested body port))

        (testing "an uncompressed response when request doesn't ask for a
                  compressed one and server not configured with a default for
                  gzip-enable"
          (validate-no-gzip-encoding-when-gzip-not-requested body port)))

      (with-test-webserver-and-config app port {:gzip-enable true}
         (testing "a gzipped response when request wants a compressed one and
                   server configured with a true value for gzip-enable"
           (validate-gzip-encoding-when-gzip-requested body port))

         (testing "an uncompressed response when request doesn't ask for a
                   compressed one and server configured with a true value for
                   gzip-enable"
           (validate-no-gzip-encoding-when-gzip-not-requested body port)))

      (with-test-webserver-and-config app port {:gzip-enable false}
         (testing "an uncompressed response when request wants a compressed one
                   but server configured with a false value for gzip-enable"
           (validate-no-gzip-encoding-even-though-gzip-requested body port))

         (testing "an uncompressed response when request doesn't ask for a
                   compressed one and server configured with a false value for
                   gzip-enable"
           (validate-no-gzip-encoding-when-gzip-not-requested body port))))))

(deftest jmx
  (testing "by default Jetty JMX support is enabled"
    (with-test-webserver #() _
      (testing "and should return a valid Jetty MBeans object"
        (let [mbeans (jmx/mbean-names "org.eclipse.jetty.jmx:*")]
          (is (not (empty? mbeans)))))

      (testing "and should not return data when we query for something unexpected"
        (let [mbeans (jmx/mbean-names "foobarbaz:*")]
          (is (empty? mbeans)))))))

(deftest override-webserver-settings!-tests
  (letfn [(webserver-context [state]
                             {:handlers (ContextHandlerCollection.)
                              :server   nil
                              :state    (atom state)})]
    (testing "able to associate overrides when overrides not already set"
      (let [context (webserver-context
                      {:some-other-state "some-other-value"})]
        (is (= {:host     "override-value-1"
                :ssl-host "override-value-2"}
               (jetty/override-webserver-settings!
                 context
                 {:host     "override-value-1"
                  :ssl-host "override-value-2"}))
            "Unexpected overrides returned from override-webserver-settings!")
        (is (= @(:state context)
               {:some-other-state "some-other-value"
                :overrides        {:host     "override-value-1"
                                   :ssl-host "override-value-2"}})
            "Unexpected config set for override-webserver-settings!")))
    (testing "unable to associate overrides when overrides already processed by
            webserver but overrides were not present"
      (let [context (webserver-context
                      {:some-other-config-setting   "some-other-value"
                       :overrides-read-by-webserver true})]
        (is (thrown-with-msg? java.lang.IllegalStateException
                              #"overrides cannot be set because webserver has already processed the config"
                              (jetty/override-webserver-settings!
                                context
                                {:host     "override-value-1"
                                 :ssl-host "override-value-2"}))
            "Call to override-webserver-settings! did not fail as expected.")
        (is (= {:some-other-config-setting   "some-other-value"
                :overrides-read-by-webserver true}
               @(:state context))
            "Config unexpectedly changed for override-webserver-settings!")))
    (testing "unable to associate override when overrides already processed by
            webserver and overrides were previously set"
      (let [context (webserver-context
                      {:some-other-config-setting   "some-other-value"
                       :overrides                   {:myoverride "my-override-value"}
                       :overrides-read-by-webserver true})]
        (is (thrown-with-msg? java.lang.IllegalStateException
                              #"overrides cannot be set because they have already been set and webserver has already processed the config"
                              (jetty/override-webserver-settings!
                                context
                                {:host     "override-value-1"
                                 :ssl-host "override-value-2"}))
            "Call to override-webserver-settings! did not fail as expected.")
        (is (= {:some-other-config-setting   "some-other-value"
                :overrides                   {:myoverride "my-override-value"}
                :overrides-read-by-webserver true}
               @(:state context))
            "Config unexpectedly changed for override-webserver-settings!")))
    (testing "unable to associate override when overrides were previously set"
      (let [context (webserver-context
                      {:some-other-config-setting "some-other-value"
                       :overrides                 {:myoverride "my-override-value"}})]
        (is (thrown-with-msg? java.lang.IllegalStateException
                              #"overrides cannot be set because they have already been set"
                              (jetty/override-webserver-settings!
                                context
                                {:host "override-value-1"
                                 :ssl-host "override-value-2"}))
            "Call to override-webserver-settings! did not fail as expected.")
        (is (= {:some-other-config-setting "some-other-value"
                :overrides                 {:myoverride "my-override-value"}}
               @(:state context))
            "config unexpectedly changed for override-webserver-settings!")))))

(defn get-webserver-context-for-server
  []
  {:state (atom nil)
   :handlers
           (ContextHandlerCollection.)
   :server nil})

(defn get-http-config-for-server
  [max-threads queue-max-size so-linger-milliseconds]
  {:http {:port 0
          :host "0.0.0.0"
          :request-header-max-size 100
          :so-linger-milliseconds so-linger-milliseconds
          :idle-timeout-milliseconds nil}
   :jmx-enable false
   :max-threads max-threads
   :queue-max-size queue-max-size})

(defn get-thread-pool-and-queue-for-create-server
  [max-threads queue-max-size]
  (let [server           (jetty/create-server
                           (get-webserver-context-for-server)
                           (get-http-config-for-server max-threads
                                                       queue-max-size
                                                       0))
        thread-pool      (.getThreadPool server)
        ;; Using reflection here because the .getQueue method is protected
        ;; and I didn't see any other way to pull the queue back from
        ;; the thread pool.
        get-queue-method (-> thread-pool
                             (.getClass)
                             (.getDeclaredMethod "getQueue" nil))
        _                (.setAccessible get-queue-method true)
        queue            (.invoke get-queue-method thread-pool nil)]
    {:thread-pool thread-pool
     :queue queue}))

(deftest create-server-tests
  (testing "thread pool given proper values"
    (testing "max threads passed through"
      (dotimes [x 5]
        (let [{:keys [thread-pool]}
          (get-thread-pool-and-queue-for-create-server x 1)]
          (is (= x (.getMaxThreads thread-pool))
              "Unexpected max threads for queue"))))

    (testing "default idle timeout passed through"
      (let [{:keys [thread-pool]}
             (get-thread-pool-and-queue-for-create-server 42 1)]
        (is (= jetty/default-queue-idle-timeout
               (.getIdleTimeout thread-pool)))))

    (testing "when queue-max-size less than default-queue-min-threads"
      (let [max-threads    23
            queue-min-size (dec jetty/default-queue-min-threads)
            {:keys [thread-pool queue]}
              (get-thread-pool-and-queue-for-create-server max-threads
                                                           queue-min-size)]
        (is (= max-threads (.getMaxThreads thread-pool))
            "Unexpected max threads for thread pool")
        (is (= queue-min-size (.getMinThreads thread-pool))
            "Unexpected min threads for queue")
        (is (= queue-min-size (.getCapacity queue))
            "Unexpected initial capacity for queue")
        (is (= queue-min-size (.getMaxCapacity queue))
            "Unexpected max capacity for queue")))

    (testing "when queue-max-size greater than default-queue-min-threads"
      (let [max-threads    42
            queue-min-size (inc jetty/default-queue-min-threads)
            {:keys [thread-pool queue]}
              (get-thread-pool-and-queue-for-create-server max-threads
                                                           queue-min-size)]
        (is (= max-threads (.getMaxThreads thread-pool))
            "Unexpected max threads for thread pool")
        (is (= jetty/default-queue-min-threads (.getMinThreads thread-pool))
            "Unexpected min threads for queue")
        (is (= jetty/default-queue-min-threads (.getCapacity queue))
            "Unexpected initial capacity for queue")
        (is (= queue-min-size (.getMaxCapacity queue))
            "Unexpected max capacity for queue"))))
  (testing "so-linger-time configured properly for http connector"
    (let [server (jetty/create-server
                   (get-webserver-context-for-server)
                   (get-http-config-for-server 0
                                               0
                                               500))
          connectors (.getConnectors server)]
      (is (= 1 (count connectors))
          "Unexpected number of connectors for server")
      (is (= 500 (.getSoLingerTime (first connectors)))
          "Unexpected so linger time for connector")))
  (testing "so-linger-time configured properly for multiple connectors"
    (let [server (jetty/create-server
                   (get-webserver-context-for-server)
                   {:http {:port 25
                           :host "0.0.0.0"
                           :request-header-max-size 100
                           :so-linger-milliseconds 41
                           :idle-timeout-milliseconds nil}
                    :https {:port 92
                            :host "0.0.0.0"
                            :protocols nil
                            :cipher-suites nil
                            :keystore-config
                              {:truststore (-> (KeyStore/getDefaultType)
                                               (KeyStore/getInstance))
                               :key-password "hello"
                               :keystore (-> (KeyStore/getDefaultType)
                                             (KeyStore/getInstance))}
                            :request-header-max-size 100
                            :so-linger-milliseconds 42
                            :idle-timeout-milliseconds nil
                            :client-auth :want}
                    :jmx-enable false
                    :max-threads 100
                    :queue-max-size 101})
          connectors (.getConnectors server)]
      (is (= 2 (count connectors))
          "Unexpected number of connectors for server")
      (is (= 25 (.getPort (first connectors)))
          "Unexpected port for first connector")
      (is (= 41 (.getSoLingerTime (first connectors)))
          "Unexpected so linger time for first connector")
      (is (= 92 (.getPort (second connectors)))
          "Unexpected port for second connector")
      (is (= 42 (.getSoLingerTime (second connectors)))
          "Unexpected so linger time for second connector"))))

(deftest test-idle-timeout
  (let [read-lines (fn [r]
                     (let [sb (StringBuilder.)]
                       (loop [l (.readLine r)]
                         (when l
                           (.append sb l)
                           (.append sb "\n")
                           ;; readLine will block until the socket is closed,
                           ;; or will throw a SocketTimeoutException if there
                           ;; is no data available within the SoTimeout value.
                           (recur (.readLine r))))
                       (.toString sb)))
        body "Hi World\n"
        path "/hi_world"
        ring-handler (fn [req] {:status 200 :body body})
        read-response (fn [client-so-timeout]
                        (let [s (Socket. "localhost" 9000)
                              out (PrintWriter. (.getOutputStream s) true)]
                          (.setSoTimeout s client-so-timeout)
                          (.println out (str "GET " path " HTTP/1.1\n"
                                             "Host: localhost\n"
                                             "\n"))
                          (let [in (BufferedReader. (InputStreamReader. (.getInputStream s)))]
                            (read-lines in))))]
    (let [config {:webserver {:port 9000
                              :host "localhost"
                              :idle-timeout-milliseconds 500}}]
      (with-test-logging
        (with-app-with-config app
          [jetty9-service]
          config
          (let [s (tk-app/get-service app :WebserverService)
                add-ring-handler (partial add-ring-handler s)]
            (add-ring-handler ring-handler path)

            (testing "Verify that server doesn't close socket before idle timeout"
              ;; if we set the client socket timeout lower than the server
              ;; socket timeout, we should get a timeout exception from the
              ;; client side while attempting to read from the socket.
              (is (thrown-with-msg? SocketTimeoutException #"Read timed out"
                                    (read-response 250))))
            (testing "Verify that server closes the socket after idle timeout"
              ;; if we set the client socket timeout higher than the server,
              ;; then the server should close the socket after its timeout,
              ;; which will cause our read to stop blocking and allow us to
              ;; validate the contents of the data we read from the socket.
              (let [resp (read-response 750)]
                (is (re-find #"(?is)HTTP.*200 OK.*Hi World"
                             resp))))))))))
