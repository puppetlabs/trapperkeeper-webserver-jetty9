(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
  (:import  (java.util.concurrent ExecutionException)
            (javax.net.ssl SSLHandshakeException)
            (org.httpkit ProtocolException))
  (:require [clojure.test :refer :all]
            [puppetlabs.http.client.sync :as http-client]
            [clojure.tools.logging :as log]
            [puppetlabs.kitchensink.testutils.fixtures :as ks-test-fixtures]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.core :as tk-core]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
              :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
              :refer [with-app-with-empty-config
                      with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
              :refer [with-test-logging]]))

(use-fixtures :once ks-test-fixtures/with-no-jvm-shutdown-hooks)

(def unauthorized-pem-options-for-https
  (-> default-options-for-https-client
      (assoc :ssl-cert "./dev-resources/config/jetty/ssl/certs/unauthorized.pem")
      (assoc :ssl-key "./dev-resources/config/jetty/ssl/private_keys/unauthorized.pem")))

(defn validate-ring-handler
  ([base-url config]
    (validate-ring-handler base-url config {:as :text}))
  ([base-url config http-get-options]
    (with-app-with-config app
      [jetty9-service]
      config
      (let [s                (get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            path             "/hi_world"
            ring-handler     (fn [req] {:status 200 :body body})]
        (add-ring-handler ring-handler path)
        (let [response (http-get
                         (format "%s%s/" base-url path)
                         http-get-options)]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))

(deftest basic-ring-test
  (testing "ring request over http succeeds"
    (validate-ring-handler
      "http://localhost:8080"
      jetty-plaintext-config)))

(deftest port-test
  (testing "webserver bootstrap throws IllegalArgumentException when neither
            port nor ssl-port specified in the config"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Either port or ssl-port must be specified on the config in order for a port binding to be opened"
          (with-test-logging
            (with-app-with-empty-config app [jetty9-service])))
      "Did not encounter expected exception when no port specified in config")))

(deftest ssl-success-test
  (testing "ring request over SSL successful for both .jks and .pem
            implementations with the server's client-auth setting not set and
            the client configured provide a certificate which the CA can
            validate"
    ; Note that if the 'client-auth' setting is not set that the server
    ; should default to 'need' to validate the client certificate.  In this
    ; case, the validation should be successful because the client is
    ; providing a certificate which the CA can validate.
    (doseq [config [jetty-ssl-jks-config jetty-ssl-pem-config]]
        (validate-ring-handler
          "https://localhost:8081"
          config
          default-options-for-https-client)))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'need' and the client configured to provide a certificate which
            the CA can validate"
    (validate-ring-handler
      "https://localhost:8081"
      jetty-ssl-client-need-config
      default-options-for-https-client))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'want' and the client configured to provide a certificate which
            the CA can validate"
    (validate-ring-handler
      "https://localhost:8081"
      jetty-ssl-client-want-config
      default-options-for-https-client))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'want' and the client configured to not provide a certificate"
    (validate-ring-handler
      "https://localhost:8081"
      jetty-ssl-client-want-config
      (dissoc default-options-for-https-client :ssl-cert :ssl-key)))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'none' and the client configured to provide a certificate which
            the CA can validate"
    (validate-ring-handler
      "https://localhost:8081"
      jetty-ssl-client-none-config
      default-options-for-https-client))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'none' and the client configured to not provide a certificate"
    (validate-ring-handler
      "https://localhost:8081"
      jetty-ssl-client-none-config
      (dissoc default-options-for-https-client :ssl-cert :ssl-key)))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'none' and the client configured to provide a certificate which
            the CA cannot validate"
    (validate-ring-handler
      "https://localhost:8081"
      jetty-ssl-client-none-config
      unauthorized-pem-options-for-https)))

(deftest ssl-failure-test
  (testing "ring request over SSL fails with the server's client-auth setting
            not set and the client configured to provide a certificate which
            the CA cannot validate"
    ; Note that if the 'client-auth' setting is not set that the server
    ; should default to 'need' to validate the client certificate.  In this
    ; case, the validation should fail because the client is providing a
    ; certificate which the CA cannot validate.
    (is (thrown?
          ProtocolException
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-pem-config
            unauthorized-pem-options-for-https))))

  (testing "ring request over SSL fails with the server's client-auth setting
            not set and the client configured to not provide a certificate"
    ; Note that if the 'client-auth' setting is not set that the server
    ; should default to 'need' to validate the client certificate.  In this
    ; case, the validation should fail because the client is not providing a
    ; certificate
    (is (thrown?
          ProtocolException
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-pem-config
            (dissoc default-options-for-https-client :ssl-cert :ssl-key)))))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'need' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (thrown?
          ProtocolException
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-client-need-config
            unauthorized-pem-options-for-https))))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'need' and the client configured to not provide a certificate"
    (is (thrown?
          ProtocolException
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-client-need-config
            (dissoc default-options-for-https-client :ssl-cert :ssl-key)))))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'want' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (thrown?
          ProtocolException
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-client-want-config
            unauthorized-pem-options-for-https)))))

(defn boot-service-and-jetty-with-default-config
  [service]
  (tk-core/boot-services-with-config
    [service jetty9-service]
    jetty-plaintext-config))

(deftest jetty-and-dependent-service-shutdown-after-service-error
  (testing (str "jetty and any dependent services are shutdown after a"
                "service throws an error from its start function")
    (with-test-logging
     (let [shutdown-called? (atom false)
           test-service     (tk-services/service
                              [[:WebserverService]]
                              (start [this context]
                                     (throw (Throwable. "oops"))
                                     context)
                              (stop [this context]
                                    (reset! shutdown-called? true)
                                    context))
           app              (boot-service-and-jetty-with-default-config
                              test-service)
           jetty-server     (-> (get-service app :WebserverService)
                                (tk-services/service-context)
                                (:jetty9-server)
                                (:server))]
       (is (.isStarted jetty-server)
           "Jetty server was never started")
       (is (thrown-with-msg?
             Throwable
             #"oops"
             (tk-core/run-app app))
           "tk run-app did not die with expected exception.")
       (is (true? @shutdown-called?)
           "Service shutdown was not called.")
       (is (.isStopped jetty-server)
           "Jetty server was not stopped at shutdown."))))
  (testing (str "jetty and any dependent services are shutdown after a"
                "service throws an error from its init function")
    (with-test-logging
      (let [shutdown-called? (atom false)
            test-service     (tk-services/service
                               [[:WebserverService]]
                               (init [this context]
                                     (throw (Throwable. "oops"))
                                     context)
                               (stop [this context]
                                     (reset! shutdown-called? true)
                                     context))
            app              (boot-service-and-jetty-with-default-config
                               test-service)]
        (is (thrown-with-msg?
              Throwable
              #"oops"
              (tk-core/run-app app))
            "tk run-app did not die with expected exception.")
        (is (true? @shutdown-called?)
            "Service shutdown was not called.")))))