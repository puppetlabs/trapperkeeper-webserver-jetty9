(ns puppetlabs.trapperkeeper.services.webserver.jetty9-core-test
  (:import
    (org.eclipse.jetty.server.handler ContextHandlerCollection)
    (org.apache.http ConnectionClosedException)
    (java.util.concurrent ExecutionException))
  (:require [clojure.test :refer :all]
            [clojure.java.jmx :as jmx]
            [ring.util.response :as rr]
            [puppetlabs.http.client.sync :as http-sync]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty]
            [puppetlabs.trapperkeeper.testutils.webserver
              :refer [with-test-webserver with-test-webserver-and-config]]
            [puppetlabs.kitchensink.core :as ks]))

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

(defn get-thread-pool-and-queue-for-create-server
  [max-threads queue-max-size]
  (let [server           (jetty/create-server {:state (atom nil)
                                               :handlers
                                                 (ContextHandlerCollection.)
                                               :server nil}
                                              {:http {:port 0
                                                      :host "0.0.0.0"
                                                      :request-header-max-size
                                                        100}
                                               :jmx-enable false
                                               :max-threads max-threads
                                               :queue-max-size queue-max-size})
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
            "Unexpected max capacity for queue")))))

(defn get-responses
  [num-requests port]
  (doall (for [_ (range num-requests)]
           (future
             (http-sync/get
               (str "http://localhost:"
                    port
                    "/hello")
               {:as :text})))))

(deftest queue-max-size-tests
  (testing "queue-max-size"
    (let [response-body  "hello world"
          app            (fn [_]
                           (Thread/sleep 5000)
                           (-> response-body
                               (rr/response)
                               (rr/status 200)
                               (rr/content-type "text/plain")
                               (rr/charset "UTF-8")))
          ;; Jetty needs at least num-cpus entries (and likely a bit more) in
          ;; the queue in order to boot up successfully.  Jetty does some
          ;; priming of selector threads using the queue independently of
          ;; any incoming requests, for example.
          queue-max-size (* (ks/num-cpus) 2)
          ;; For the fail case, need to generate some number of concurrent
          ;; requests in excess of the size of the queue in order for the queue
          ;; to be completely filled and for some requests to start being
          ;; dropped
          num-requests   (+ queue-max-size 30)]
      (testing "of default (infinite) size doesn't lead to any request fails"
        (with-test-webserver-and-config
          app port {:shutdown-timeout-seconds 0}
          (let [responses (get-responses num-requests port)]
            (doseq [response responses]
              (is (= 200 (:status @response))
                  "Unsuccessful response status for one request")
              (is (= response-body (:body @response))
                  "Unsuccessful response body for one request")))))
      (testing "of sufficiently low queue-max-size causes some requests to fail"
        (with-test-webserver-and-config
          app port {:queue-max-size queue-max-size
                    :shutdown-timeout-seconds 0}
          (let [responses (doall (get-responses num-requests port))]
            (is (some #(try
                        @%
                        false
                        (catch ExecutionException e
                          (instance? ConnectionClosedException
                                     (.. e getCause))))
                      responses)
                "Didn't encounter connection close for any requests")))))))