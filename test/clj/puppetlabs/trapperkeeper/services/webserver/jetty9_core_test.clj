(ns puppetlabs.trapperkeeper.services.webserver.jetty9-core-test
  (:import
    (org.eclipse.jetty.server.handler ContextHandler ContextHandlerCollection))
  (:require [clojure.test :refer :all]
            [ring.util.response :as rr]
            [puppetlabs.http.client.sync :as http-client]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty]
            [puppetlabs.trapperkeeper.testutils.webserver :refer [with-test-webserver]]))

(deftest handlers
  (testing "create-handlers should allow for handlers to be added"
    (let [webserver-context (jetty/create-handlers)
          handlers          (:handlers webserver-context)]
      (jetty/add-ring-handler webserver-context
                              (fn [req] {:status 200
                                         :body "I am a handler"})
                              "/")
      (is (= (count (.getHandlers handlers)) 1)))))

(deftest compression
  (testing "should return"
    (let [body (apply str (repeat 1000 "f"))
          app  (fn [req]
                 (-> body
                     (rr/response)
                     (rr/status 200)
                     (rr/content-type "text/plain")
                     (rr/charset "UTF-8")))]
      (with-test-webserver app port
        (testing "a gzipped response when requests"
          ;; The client/get function asks for compression by default
          (let [resp (http-client/get (format "http://localhost:%d/" port))]
            (is (= (resp :body) body))
            (is (= (get-in resp [:headers :content-encoding]) "gzip")
                (format "Expected gzipped response, got this response: %s" resp))))

        (testing "an uncompressed response by default"
          ;; The client/get function asks for compression by default
          (let [resp (http-client/get (format "http://localhost:%d/" port) {:decompress-body false})]
            (is (= (resp :body) body))
            ;; We should not receive a content-encoding header in the uncompressed case
            (is (nil? (get-in resp [:headers "content-encoding"]))
                (format "Expected uncompressed response, got this response: %s" resp))))))))

(deftest override-webserver-settings!-tests
  (testing "able to associate overrides when overrides not already set"
    (let [context {:state
                    (atom {:some-other-config-setting "some-other-value"})}]
      (is (= {:override-1 "override-value-1"
              :override-2 "override-value-2"}
             (jetty/override-webserver-settings!
               context
               {:override-1 "override-value-1"
                :override-2 "override-value-2"}))
          "Unexpected overrides returned from override-webserver-settings!")
      (is (= @(:state context)
             {:some-other-config-setting "some-other-value"
              :overrides {:override-1 "override-value-1"
                          :override-2 "override-value-2"}})
          "Unexpected config set for override-webserver-settings!")))
  (testing "unable to associate overrides when overrides already processed by
            webserver but overrides were not present"
    (let [context {:state
                    (atom {:some-other-config-setting "some-other-value"
                           :overrides-read-by-webserver true})}]
      (is (thrown-with-msg? java.lang.IllegalStateException
                            #"overrides cannot be set because webserver has already processed the config"
                            (jetty/override-webserver-settings!
                              context
                              {:override-1 "override-value-1"
                               :override-2 "override-value-2"}))
          "Call to override-webserver-settings! did not fail as expected.")
      (is (= {:some-other-config-setting "some-other-value"
              :overrides-read-by-webserver true}
             @(:state context))
          "Config unexpectedly changed for override-webserver-settings!")))
  (testing "unable to associate override when overrides already processed by
            webserver and overrides were previously set"
    (let [context {:state
                    (atom {:some-other-config-setting "some-other-value"
                           :overrides {:myoverride "my-override-value"}
                           :overrides-read-by-webserver true})}]
      (is (thrown-with-msg? java.lang.IllegalStateException
                            #"overrides cannot be set because they have already been set and webserver has already processed the config"
                            (jetty/override-webserver-settings!
                              context
                              {:override-1 "override-value-1"
                               :override-2 "override-value-2"}))
          "Call to override-webserver-settings! did not fail as expected.")
      (is (= {:some-other-config-setting "some-other-value"
              :overrides {:myoverride "my-override-value"}
              :overrides-read-by-webserver true}
             @(:state context))
          "Config unexpectedly changed for override-webserver-settings!")))
  (testing "unable to associate override when overrides were previously set"
    (let [context {:state
                    (atom {:some-other-config-setting "some-other-value"
                           :overrides {:myoverride "my-override-value"}})}]
      (is (thrown-with-msg? java.lang.IllegalStateException
                            #"overrides cannot be set because they have already been set"
                            (jetty/override-webserver-settings!
                              context
                              {:override-1 "override-value-1"
                               :override-2 "override-value-2"}))
          "Call to override-webserver-settings! did not fail as expected.")
      (is (= {:some-other-config-setting "some-other-value"
              :overrides {:myoverride "my-override-value"}}
             @(:state context))
          "config unexpectedly changed for override-webserver-settings!")))
  (testing "Confirm that when a webserver context with no config is passed
            into override-webserver-settings! that an exception is thrown."
    (is (thrown? AssertionError (jetty/override-webserver-settings!
                                  {:not-a-config "whatever"}
                                  {:override-1 "override-value-1"}))
        "Did not encounter expected exception for invalid config argument."))
  (testing "Confirm that when a bad overrides is passed
            into override-webserver-settings! that an exception is thrown."
    (is (thrown? AssertionError (jetty/override-webserver-settings!
                                  {:state (atom {})}
                                  nil))
        "Did not encounter expected exception for invalid override argument.")))
