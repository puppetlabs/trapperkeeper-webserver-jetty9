(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-override-webserver-settings-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as http-client]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
              :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
              :refer [with-app-with-cli-data]]))

(def test-resources-dir        "./test-resources/")

(def test-resources-config-dir (str test-resources-dir "config/jetty/"))

(def default-keystore-pass     "Kq8lG9LkISky9cDIYysiadxRx")

(def default-options-for-https
  {:keystore         (str test-resources-config-dir "ssl/keystore.jks")
   :keystore-type    "JKS"
   :keystore-pass    default-keystore-pass
   :trust-store      (str test-resources-config-dir "ssl/truststore.jks")
   :trust-store-type "JKS"
   :trust-store-pass default-keystore-pass
   ; The default server's certificate in this case uses a CN of
   ; "localhost-puppetdb" whereas the URL being reached is "localhost".  The
   ; insecure? value of true directs the client to ignore the mismatch.
   :insecure?        true})

(defprotocol Service1)

(deftest test-override-webserver-settings!
  (let [ssl-port  9001
        overrides {:ssl-port ssl-port
                   :ssl-host "0.0.0.0"
                   :ssl-cert
                             (str test-resources-config-dir
                                  "ssl/certs/localhost.pem")
                   :ssl-key
                             (str test-resources-config-dir
                                  "ssl/private_keys/localhost.pem")
                   :ssl-ca-cert
                             (str test-resources-config-dir
                                  "ssl/certs/ca.pem")}]
    (testing "config override of all SSL settings before webserver starts is
              successful"
      (let [override-result (atom nil)
            service1        (tk-services/service Service1
                              [[:WebserverService override-webserver-settings!]]
                              (init [this context]
                                    (reset! override-result
                                            (override-webserver-settings!
                                              overrides))
                                    context))]
        (with-app-with-cli-data
          app
          [jetty9-service service1]
          {:config (str test-resources-config-dir
                        "jetty-plaintext-http.ini")}
          (let [s                (get-service app :WebserverService)
                add-ring-handler (partial add-ring-handler s)
                body             "Hi World"
                path             "/hi_world"
                ring-handler     (fn [req] {:status 200 :body body})]
            (add-ring-handler ring-handler path)
            (let [response (http-client/get
                             (format "https://localhost:%d/%s" ssl-port path)
                             default-options-for-https)]
              (is (= (:status response) 200)
                  "Unsuccessful http response code ring handler response.")
              (is (= (:body response) body)
                  "Unexpected body in ring handler response."))))
        (is (= overrides @override-result)
            "Unexpected response to override-webserver-settings! call.")))
    (testing "config override of SSL certificate settings before webserver
              starts is successful"
      (let [override-result (atom nil)
            overrides       {:ssl-cert
                              (str test-resources-config-dir
                                   "ssl/certs/localhost.pem")
                             :ssl-key
                              (str test-resources-config-dir
                                   "ssl/private_keys/localhost.pem")
                             :ssl-ca-cert
                              (str test-resources-config-dir
                                   "ssl/certs/ca.pem")}
            service1        (tk-services/service Service1
                              [[:WebserverService override-webserver-settings!]]
                              (init [this context]
                                    (reset! override-result
                                            (override-webserver-settings!
                                              overrides))
                                    context))]
        (with-app-with-cli-data
          app
          [jetty9-service service1]
          {:config (str test-resources-config-dir
                        "jetty-ssl-no-certs.ini")}
          (let [s                (get-service app :WebserverService)
                add-ring-handler (partial add-ring-handler s)
                body             "Hi World"
                path             "/hi_world"
                ring-handler     (fn [req] {:status 200 :body body})]
            (add-ring-handler ring-handler path)
            (let [response (http-client/get
                             (format "https://localhost:%d/%s" ssl-port path)
                             default-options-for-https)]
              (is (= (:status response) 200)
                  "Unsuccessful http response code ring handler response.")
              (is (= (:body response) body)
                  "Unexpected body in ring handler response."))))
        (is (= overrides @override-result)
            "Unexpected response to override-webserver-settings! call.")))
    (testing "attempt to override SSL settings fails when override call made
              after webserver has already started"
      (let [override-result (atom nil)
            service1        (tk-services/service Service1
                                                 [])]
        (with-app-with-cli-data
          app
          [jetty9-service service1]
          {:config (str test-resources-config-dir
                        "jetty-plaintext-http.ini")}
          (let [s                            (get-service app :WebserverService)
                override-webserver-settings! (partial
                                               override-webserver-settings!
                                               s)]
            (is (thrown-with-msg? java.lang.IllegalStateException
                                  #"overrides cannot be set because webserver has already processed the config"
                                  (override-webserver-settings! overrides)))))))
    (testing "second attempt to override SSL settings fails"
      (let [second-override-throws? (atom nil)
            service1                (tk-services/service
                                      Service1
                                      [[:WebserverService
                                        override-webserver-settings!]]
                                      (init [this context]
                                            (override-webserver-settings!
                                              overrides)
                                            (reset!
                                              second-override-throws?
                                              (is
                                                (thrown-with-msg?
                                                  java.lang.IllegalStateException
                                                  #"overrides cannot be set because they have already been set"
                                                  (override-webserver-settings!
                                                    overrides))))
                                            context))]
        (with-app-with-cli-data
          app
          [jetty9-service service1]
          {:config (str test-resources-config-dir
                        "jetty-plaintext-http.ini")}
          (let [s                (get-service app :WebserverService)
                add-ring-handler (partial add-ring-handler s)
                body             "Hi World"
                path             "/hi_world"
                ring-handler     (fn [req] {:status 200 :body body})]
            (add-ring-handler ring-handler path)
            (let [response (http-client/get
                             (format "https://localhost:%d/%s" ssl-port path)
                             default-options-for-https)]
              (is (= (:status response) 200)
                  "Unsuccessful http response code ring handler response.")
              (is (= (:body response) body)
                  "Unexpected body in ring handler response."))))
        (is (not (nil? @second-override-throws?))
            "Second call to setting overrides not made")))))