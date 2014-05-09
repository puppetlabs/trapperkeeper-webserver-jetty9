(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-override-settings-test
  (:require [clojure.test :refer :all]
            [puppetlabs.http.client.sync :as http-client]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
              :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
              :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
              :refer [with-test-logging]]))

(def dev-resources-dir        "./dev-resources/")

(def dev-resources-config-dir (str dev-resources-dir "config/jetty/"))

(def jetty-ssl-no-certs-config
  {:webserver {:ssl-host "0.0.0.0"
               :ssl-port 9001}})

(deftest test-override-webserver-settings!
  (let [ssl-port  9001
        overrides {:ssl-port ssl-port
                   :ssl-host "0.0.0.0"
                   :ssl-cert
                             (str dev-resources-config-dir
                                  "ssl/certs/localhost.pem")
                   :ssl-key
                             (str dev-resources-config-dir
                                  "ssl/private_keys/localhost.pem")
                   :ssl-ca-cert
                             (str dev-resources-config-dir
                                  "ssl/certs/ca.pem")

                   :crl-path (str dev-resources-config-dir
                                  "ssl/crl.pem")}]
    (testing "config override of all SSL settings before webserver starts is
              successful"
      (let [override-result (atom nil)
            service1        (tk-services/service
                              [[:WebserverService override-webserver-settings!]]
                              (init [this context]
                                    (reset! override-result
                                            (override-webserver-settings!
                                              overrides))
                                    context))]
        (with-test-logging
          (with-app-with-config
            app
            [jetty9-service service1]
            jetty-plaintext-config
            (let [s                (get-service app :WebserverService)
                  add-ring-handler (partial add-ring-handler s)
                  body             "Hi World"
                  path             "/hi_world"
                  ring-handler     (fn [req] {:status 200 :body body})]
              (add-ring-handler ring-handler path)
              (let [response (http-get
                               (format "https://localhost:%d%s/" ssl-port path)
                               default-options-for-https-client)]
                (is (= (:status response) 200)
                    "Unsuccessful http response code ring handler response.")
                (is (= (:body response) body)
                    "Unexpected body in ring handler response."))))
              (is (logged? #"^webserver config overridden for key 'ssl-port'")
                  "Didn't find log message for override of 'ssl-port'")
              (is (logged? #"^webserver config overridden for key 'ssl-host'")
                  "Didn't find log message for override of 'ssl-host'")
              (is (logged? #"^webserver config overridden for key 'ssl-cert'")
                  "Didn't find log message for override of 'ssl-cert'")
              (is (logged? #"^webserver config overridden for key 'ssl-key'")
                  "Didn't find log message for override of 'ssl-key'")
              (is (logged? #"^webserver config overridden for key 'ssl-ca-cert'")
                  "Didn't find log message for override of 'ssl-ca-cert'")

              (is (logged? #"^webserver config overridden for key 'crl-path'")
                  "Didn't find log message for override of 'crl-path'"))

        (is (= overrides @override-result)
            "Unexpected response to override-webserver-settings! call.")))
    (testing "SSL certificate settings can be overridden while other settings
              from the config are still honored -- ssl-port and ssl-host"
      (let [override-result (atom nil)
            overrides       {:ssl-cert
                              (str dev-resources-config-dir
                                   "ssl/certs/localhost.pem")
                             :ssl-key
                              (str dev-resources-config-dir
                                   "ssl/private_keys/localhost.pem")
                             :ssl-ca-cert
                              (str dev-resources-config-dir
                                   "ssl/certs/ca.pem")}
            service1        (tk-services/service
                              [[:WebserverService override-webserver-settings!]]
                              (init [this context]
                                    (reset! override-result
                                            (override-webserver-settings!
                                              overrides))
                                    context))]
        (with-app-with-config
          app
          [jetty9-service service1]
          jetty-ssl-no-certs-config
          (let [s                (get-service app :WebserverService)
                add-ring-handler (partial add-ring-handler s)
                body             "Hi World"
                path             "/hi_world"
                ring-handler     (fn [req] {:status 200 :body body})]
            (add-ring-handler ring-handler path)
            (let [response (http-get
                             (format "https://localhost:%d%s/" ssl-port path)
                             default-options-for-https-client)]
              (is (= (:status response) 200)
                  "Unsuccessful http response code ring handler response.")
              (is (= (:body response) body)
                  "Unexpected body in ring handler response."))))
        (is (= overrides @override-result)
            "Unexpected response to override-webserver-settings! call.")))
    (testing "attempt to override SSL settings fails when override call made
              after webserver has already started"
      (let [override-result (atom nil)
            service1        (tk-services/service [])]
        (with-app-with-config
          app
          [jetty9-service service1]
          jetty-plaintext-config
          (let [s                            (get-service app :WebserverService)
                override-webserver-settings! (partial
                                               override-webserver-settings!
                                               s)]
            (is (thrown-with-msg? java.lang.IllegalStateException
                                  #"overrides cannot be set because webserver has already processed the config"
                                  (override-webserver-settings! overrides)))))))
    (testing "second attempt to override SSL settings fails"
      (let [second-override-result (atom nil)
            service1                (tk-services/service
                                      [[:WebserverService
                                        override-webserver-settings!]]
                                      (init [this context]
                                            (override-webserver-settings!
                                              overrides)
                                            (reset!
                                              second-override-result
                                              (is
                                                (thrown-with-msg?
                                                  IllegalStateException
                                                  #"overrides cannot be set because they have already been set"
                                                  (override-webserver-settings!
                                                    overrides))))
                                            context))]
        (with-app-with-config
          app
          [jetty9-service service1]
          jetty-plaintext-config
          (let [s                (get-service app :WebserverService)
                add-ring-handler (partial add-ring-handler s)
                body             "Hi World"
                path             "/hi_world"
                ring-handler     (fn [req] {:status 200 :body body})]
            (add-ring-handler ring-handler path)
            (let [response (http-get
                             (format "https://localhost:%d%s/" ssl-port path)
                             default-options-for-https-client)]
              (is (= (:status response) 200)
                  "Unsuccessful http response code ring handler response.")
              (is (= (:body response) body)
                  "Unexpected body in ring handler response."))))
        (is (instance? IllegalStateException @second-override-result)
            "Second call to setting overrides did not throw expected exception.")))))