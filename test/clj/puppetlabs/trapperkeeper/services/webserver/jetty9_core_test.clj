(ns puppetlabs.trapperkeeper.services.webserver.jetty9-core-test
  (:import
    (org.eclipse.jetty.server.handler ContextHandler ContextHandlerCollection))
  (:require [clojure.test :refer :all]
            [ring.util.response :as rr]
            [clj-http.client :as http-client]
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
      (is (= (alength (.getHandlers handlers)) 1)))))

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
            (is (= (get-in resp [:headers "content-encoding"]) "gzip")
                (format "Expected gzipped response, got this response: %s" resp))))

        (testing "an uncompressed response by default"
          ;; The client/get function asks for compression by default
          (let [resp (http-client/get (format "http://localhost:%d/" port) {:decompress-body false})]
            (is (= (resp :body) body))
            ;; We should not receive a content-encoding header in the uncompressed case
            (is (nil? (get-in resp [:headers "content-encoding"]))
                (format "Expected uncompressed response, got this response: %s" resp))))))))
