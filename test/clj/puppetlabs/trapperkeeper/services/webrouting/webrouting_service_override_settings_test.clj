(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-override-settings-test
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.webrouting.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-test-logging]]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.testutils.webserver :as testutils]))

(use-fixtures :once
  schema-test/validate-schemas
  testutils/assert-clean-shutdown)

(def dev-resources-dir        "./dev-resources/")

(def dev-resources-config-dir (str dev-resources-dir "config/jetty/"))

(defprotocol TestDummy
  :extend-via-metadata true
  (dummy [this]))

(tk-services/defservice test-dummy
  TestDummy
  []
  (dummy [this]
         "This is a dummy function. Please ignore."))

(def webrouting-plaintext-override-config
  {:webserver {:port 8080}
   :web-router-service
     {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-override-settings-test/test-dummy "/foo"}})

(deftest test-override-webserver-settings!-with-web-routing
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
                   :ssl-crl-path
                             (str dev-resources-config-dir
                                  "ssl/crls/crls_none_revoked.pem")}]
    (testing "config override of all SSL settings before webserver starts is
              successful with web-routing"
      (let [override-result (atom nil)
            service1        (tk-services/service
                              [[:WebroutingService override-webserver-settings!]]
                              (init [this context]
                                (reset! override-result
                                        (override-webserver-settings!
                                        overrides))
                                 context))]
        (with-test-logging
          (with-app-with-config
            app
            [jetty9-service webrouting-service service1 test-dummy]
            webrouting-plaintext-override-config
            (let [s                (get-service app :WebroutingService)
                  add-ring-handler (partial add-ring-handler s)
                  body             "Hi World"
                  path             "/foo"
                  ring-handler     (fn [req] {:status 200 :body body})
                  svc              (get-service app :TestDummy)]
              (add-ring-handler svc ring-handler)
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
          (is (logged? #"^webserver config overridden for key 'ssl-crl-path'")
              "Didn't find log message for override of 'ssl-crl-path'"))
        (is (= overrides @override-result)
            "Unexpected response to override-webserver-settings! call.")))))