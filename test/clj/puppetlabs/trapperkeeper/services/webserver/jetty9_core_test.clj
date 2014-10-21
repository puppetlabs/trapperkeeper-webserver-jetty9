(ns puppetlabs.trapperkeeper.services.webserver.jetty9-core-test
  (:import
    (org.eclipse.jetty.server.handler ContextHandler ContextHandlerCollection))
  (:require [clojure.test :refer :all]
            [clojure.java.jmx :as jmx]
            [ring.util.response :as rr]
            [puppetlabs.http.client.sync :as http-client]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty]
            [puppetlabs.trapperkeeper.testutils.webserver
             :refer [with-test-webserver with-test-webserver-and-config with-test-webserver-with-cors-config]]))

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
  (let [resp (http-client/get (format "http://localhost:%d/" port))]
    (is (= (slurp (resp :body)) body))
    (is (= (get-in resp [:orig-content-encoding]) "gzip")
        (format "Expected gzipped response, got this response: %s"
                resp))))

(defn validate-no-gzip-encoding-when-gzip-not-requested
  [body port]
  ;; The client/get function asks for compression by default
  (let [resp (http-client/get (format "http://localhost:%d/" port)
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
  (let [resp (http-client/get (format "http://localhost:%d/" port))]
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

(defn validate-no-cors-headers
  [http-verb port options http-status]
  ;; The client/get function asks for compression by default
  (let [resp (http-verb (format "http://localhost:%d/test" port) options)]
    (is (= (get-in resp [:status]) http-status)
      (format "Expected an http status of '%d' but got this response: %s" http-status resp))
    (is (nil? (get-in resp [:headers :access-control-allow-origin]))
      (format "Expected no access-control-allow-origin header but got this response: %s" resp))
    (is (nil? (get-in resp [:headers :access-control-allow-methods]))
      (format "Expected no access-control-allow-methods header but got this response: %s" resp))
    ))

(defn validate-cors-headers
  ([http-verb port options origin methods]
   (validate-cors-headers http-verb port options origin methods nil))
  ([http-verb port options origin methods body]
   ;; The client/get function asks for compression by default
   (let [resp (http-verb (format "http://localhost:%d/test" port) options)]
     (is (= (get-in resp [:headers "access-control-allow-origin"]) origin)
       (format "Expected an access-control-allow-origin header with the value '%s' but got this response: %s"
         origin resp))
     (is (= (get-in resp [:headers "access-control-allow-methods"]) methods)
       (format "Expected an access-control-allow-methods header with the value '%s' but got this response: %s"
         methods resp)))))

(deftest cors
  (testing "CORS support: "
    (let [app (fn [req] (rr/response (str "I'm the response to request:" req)))
          origin "http://example.com"
          methods "GET, OPTIONS, POST"]

      ; CORS disabled
      (with-test-webserver-with-cors-config app port {} nil
        (testing "a request with no origin gives a 200 response with no CORS headers"
          (validate-no-cors-headers http-client/get port {} 200))
        (testing "a request with an origin gives a 200 response with no CORS headers"
          (validate-no-cors-headers http-client/get port {:headers {"Origin" origin}} 200)))

      ; CORS enabled
      (with-test-webserver-with-cors-config app port {}
        [:access-control-allow-origin (re-pattern origin)
         :access-control-allow-methods [:get :post :options]]
        (testing "a request with no origin gives a response with no CORS headers"
          (validate-no-cors-headers http-client/get port {} 200))
        (testing "a request with a valid origin and method gives a 200 response with expected CORS headers"
          (validate-cors-headers http-client/get port {:headers {"Origin" origin}} origin methods))
        (testing "a request with a valid origin and invalid method gives a 404 with no CORS headers"
          (validate-no-cors-headers http-client/put port {:headers {"Origin" origin}} 404))
        (testing "a request with an invalid origin and valid method gives a 404 with no CORS headers"
          (validate-no-cors-headers http-client/get port {:headers {"Origin" "bad.example.com"}} 404))
        (testing "a request with an invalid origin and invalid method gives a 404 with no CORS headers"
          (validate-no-cors-headers http-client/put port {:headers {"Origin" "bad.example.com"}} 404))
        (testing "a preflight with a valid origin gives a 200 response with expected CORS headers"
          (validate-cors-headers http-client/options port {:headers {"Origin" origin}} origin methods))))))
