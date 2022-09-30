(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
  (:import (org.apache.http ConnectionClosedException)
           (java.io IOException)
           (java.security.cert CRLException)
           (java.net SocketTimeoutException)
           (java.nio.file Paths Files)
           (java.nio.file.attribute FileAttribute)
           (appender TestListAppender)
           (javax.net.ssl SSLException)
           (org.slf4j MDC)
           (com.puppetlabs.ssl_utils SSLUtils))
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [puppetlabs.http.client.async :as async]
            [puppetlabs.http.client.common :as http-client-common]
            [puppetlabs.kitchensink.testutils.fixtures :as ks-test-fixtures]
            [puppetlabs.trapperkeeper.app :as tk-app]
            [puppetlabs.trapperkeeper.core :as tk-core]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
             :refer :all]
            [puppetlabs.trapperkeeper.services.watcher.filesystem-watch-service
             :as filesystem-watch-service]
            [puppetlabs.trapperkeeper.testutils.webserver :as testutils]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
             :refer [with-app-with-empty-config
                     with-app-with-config]
             :as tk-bootstrap]
            [puppetlabs.trapperkeeper.logging :as tk-log]
            [puppetlabs.trapperkeeper.testutils.logging :as tk-log-testutils]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
            [schema.core :as schema]
            [schema.test :as schema-test]
            [puppetlabs.kitchensink.core :as ks]
            [ring.middleware.params :as ring-params]
            [me.raynes.fs :as fs]))

(use-fixtures :once
  ks-test-fixtures/with-no-jvm-shutdown-hooks
  schema-test/validate-schemas
  testutils/assert-clean-shutdown
  (fn [f] (tk-log/reset-logging) (f)))

(def default-server-config
  {:webserver {:foo {:port 8080}
               :bar {:port 9000
                     :default-server true}}})

(def no-default-config
  {:webserver {:foo {:port 8080}
               :bar {:port 9000}}})

(def static-content-single-config
  {:webserver {:port 8080
               :static-content [{:resource "./dev-resources"
                                 :path "/resources"
                                 :follow-links true},
                                {:resource "./dev-resources"
                                 :path "/resources2"}]}})

(def static-content-multi-config
  {:webserver {:foo {:port 8080
                     :static-content [{:resource "./dev-resources"
                                       :path "/resources"},
                                      {:resource "./dev-resources"
                                       :path "/resources2"}]}
               :bar {:port 9000
                     :static-content [{:resource "./dev-resources"
                                       :path "/resources"},
                                      {:resource "./dev-resources"
                                       :path "/resources2"}]}}})

(defmacro ssl-exception-thrown?
  [& body]
  `(try
    ~@body
    (throw (IllegalStateException. "Expected SSL Exception to be thrown!"))
    (catch ConnectionClosedException e#
      true)
    (catch SSLException e#
      true)
    (catch IOException e#
      (if (= "Connection reset by peer" (.getMessage e#))
        true
        (throw e#)))))

(def unauthorized-pem-options-for-https
  (-> default-options-for-https-client
      (assoc :ssl-cert "./dev-resources/config/jetty/ssl/certs/unauthorized.pem")
      (assoc :ssl-key "./dev-resources/config/jetty/ssl/private_keys/unauthorized.pem")))

(def hello-path "/hi_world")
(def hello-body "Hi World")
(defn hello-handler
  [req]
  {:status 200 :body hello-body})

(def mdc-path "/mdc")
(defn mdc-handler
  [req]
  (let [mdc-key (get-in req [:params "mdc_key"])]
    (case (:request-method req)
      :get {:status 200
            :body (MDC/get mdc-key)}
      :put (do
             (MDC/put mdc-key (slurp (:body req)))
             {:status 201
              :body "OK."}))))

(tk-core/defservice hello-webservice
  [[:WebserverService add-ring-handler]]
  (init [this context]
        (add-ring-handler hello-handler hello-path)
        (add-ring-handler (ring-params/wrap-params mdc-handler) mdc-path)
        context))

(defn validate-ring-handler
  ([base-url config]
   (validate-ring-handler base-url config {:as :text} :default))
  ([base-url config http-get-options]
   (validate-ring-handler base-url config http-get-options :default))
  ([base-url config http-get-options server-id]
   (with-app-with-config app
     [jetty9-service
      hello-webservice]
     config
     (let [response (http-get
                     (format "%s%s/" base-url hello-path)
                     http-get-options)]
       (is (= (:status response) 200))
       (is (= (:body response) hello-body))))))

(defn validate-ring-handler-default
  ([base-url config]
   (validate-ring-handler-default base-url config {:as :text}))
  ([base-url config http-get-options]
   (with-app-with-config app
     [jetty9-service
      hello-webservice]
     config
     (let [response (http-get
                     (format "%s%s/" base-url hello-path)
                     http-get-options)]
       (is (= (:status response) 200))
       (is (= (:body response) hello-body))))))

(deftest basic-ring-test
  (testing "ring request over http succeeds"
    (validate-ring-handler
      "http://localhost:8080"
      jetty-plaintext-config)))

(deftest basic-default-ring-test
  (testing "ring request over http succeeds with default add-ring-handler"
    (validate-ring-handler-default
      "http://localhost:8080"
      jetty-plaintext-config))
  (testing "ring request on single server with new syntax over http succeeds"
    (validate-ring-handler
      "http://localhost:8080"
      {:webserver {:default        {:port           8080
                                    :default-server true}}}
      {:as :text}
      :default)))

(deftest single-server-jmx-cleanup-test
  (testing "no jetty mbeancontainers are registered prior to starting servers"
    (is (empty? (testutils/get-jetty-mbean-object-names))))
  (with-app-with-config app
    [jetty9-service]
    jetty-plaintext-config
    (testing "one jetty mbean container is registered per server"
      (is (= 1 (count (testutils/get-jetty-mbean-object-names))))))
  (testing "jetty mbean containers are unregistered after server is stopped"
    (is (empty? (testutils/get-jetty-mbean-object-names)))))

(deftest multiserver-ring-test
  (testing "no jetty mbeancontainers are registered prior to starting servers"
    (is (empty? (testutils/get-jetty-mbean-object-names))))

  (testing "ring requests on multiple servers succeed"
    (with-app-with-config app
      [jetty9-service]
      jetty-multiserver-plaintext-config
      (let [s                (tk-app/get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)]
        (add-ring-handler hello-handler hello-path {:server-id :foo})
        (add-ring-handler hello-handler hello-path {:server-id :bar})
        (let [response1 (http-get "http://localhost:8080/hi_world/" {:as :text})
              response2 (http-get "http://localhost:8085/hi_world/" {:as :text})]
          (is (= (:status response1) 200))
          (is (= (:status response2) 200))
          (is (= (:body response1) hello-body))
          (is (= (:body response2) hello-body)))

        (testing "one jetty mbean container is registered per server"
          (is (= 2 (count (testutils/get-jetty-mbean-object-names))))))))

  (testing "jetty mbean containers are unregistered after server is stopped"
    (is (empty? (testutils/get-jetty-mbean-object-names))))

  (testing "ring request succeeds with multiple servers and default add-ring-handler"
    (validate-ring-handler-default
      "http://localhost:8080"
      jetty-multiserver-plaintext-config)))

(deftest port-test
  (testing "webserver bootstrap throws IllegalArgumentException when neither
            port nor ssl-port specified in the config"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Either host, port, ssl-host, or ssl-port must be specified"
          (tk-log-testutils/with-test-logging
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
    (doseq [config [(jetty-ssl-jks-config) jetty-ssl-pem-config]]
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
      unauthorized-pem-options-for-https))

  (testing "ring request over SSL succeeds with the server configured to use
            both an ssl-cert and an ssl-cert-chain"
    (validate-ring-handler
      "https://localhost:8081"
      (assoc-in jetty-ssl-client-need-config
                [:webserver :ssl-cert-chain]
                (:ssl-ca-cert default-options-for-https-client))
      default-options-for-https-client)))

(deftest ssl-failure-test
  (testing "ring request over SSL fails with the server's client-auth setting
            not set and the client configured to provide a certificate which
            the CA cannot validate"
    ; Note that if the 'client-auth' setting is not set that the server
    ; should default to 'need' to validate the client certificate.  In this
    ; case, the validation should fail because the client is providing a
    ; certificate which the CA cannot validate.
    (is (ssl-exception-thrown?
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
    (is (ssl-exception-thrown?
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-pem-config
            (dissoc default-options-for-https-client :ssl-cert :ssl-key)))))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'need' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (ssl-exception-thrown?
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-client-need-config
            unauthorized-pem-options-for-https))))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'need' and the client configured to not provide a certificate"
    (is (ssl-exception-thrown?
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-client-need-config
            (dissoc default-options-for-https-client :ssl-cert :ssl-key)))))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'want' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (ssl-exception-thrown?
          (validate-ring-handler
            "https://localhost:8081"
            jetty-ssl-client-want-config
            unauthorized-pem-options-for-https)))))

(deftest crl-success-test
  (testing (str "ring request over SSL succeeds when no client certificates "
                "have been revoked")
    (validate-ring-handler
      "https://localhost:8081"
      (assoc-in jetty-ssl-client-need-config
        [:webserver :ssl-crl-path]
        "./dev-resources/config/jetty/ssl/crls/crls_none_revoked.pem")
      default-options-for-https-client))

  (testing (str "ring request over SSL succeeds when a different client "
                "certificate than the one used in the request has been revoked")
    (validate-ring-handler
      "https://localhost:8081"
      (assoc-in jetty-ssl-client-need-config
                [:webserver :ssl-crl-path]
                (str "./dev-resources/config/jetty/ssl/crls/"
                     "crls_localhost-compromised_revoked.pem"))
      default-options-for-https-client)))

(deftest crl-failure-test
  (testing (str "ring request over SSL fails when the client certificate has "
                "been revoked")
    (is (ssl-exception-thrown?
          (validate-ring-handler
            "https://localhost:8081"
            (assoc-in
              jetty-ssl-client-need-config
              [:webserver :ssl-crl-path]
              "./dev-resources/config/jetty/ssl/crls/crls_localhost_revoked.pem")
            default-options-for-https-client))))

  ;; the Bouncy Castle FIPS provider does not throw an exception for this use-case
  ;; whereas the SunX509 provider does.
  (when-not (SSLUtils/isFIPS)
   (testing (str "jetty throws startup exception if non-CRL PEM is specified "
                 "as ssl-crl-path")
     (tk-log-testutils/with-test-logging
      (is (thrown?
           CRLException
           (with-app-with-config
            app
            [jetty9-service]
            (assoc-in
             jetty-ssl-client-need-config
             [:webserver :ssl-crl-path]
             "./dev-resources/config/jetty/ssl/certs/ca.pem")))))))

  (testing (str "jetty throws startup exception if ssl-crl-path refers to a "
                "non-existent file")
    (tk-log-testutils/with-test-logging
      (is (thrown-with-msg?
            IllegalArgumentException
            #"Non-readable path specified for ssl-crl-path option"
            (with-app-with-config
              app
              [jetty9-service]
              (assoc-in
                jetty-ssl-client-need-config
                [:webserver :ssl-crl-path]
                "./dev-resources/config/jetty/ssl/crls/crls_bogus.pem")))))))

(deftest crl-reloaded-without-server-restart-test
  (let [tmp-dir (ks/temp-dir)
        tmp-file (fs/file tmp-dir "mycrl.pem")
        get-request #(http-get
                      (str "https://localhost:8081" hello-path)
                      default-options-for-https-client)]
    (fs/copy "./dev-resources/config/jetty/ssl/crls/crls_none_revoked.pem"
             tmp-file)
    (with-app-with-config
     app
     [jetty9-service
      hello-webservice
      filesystem-watch-service/filesystem-watch-service]

     (assoc-in
      jetty-ssl-client-need-config
      [:webserver :ssl-crl-path]
      (str tmp-file))

     (testing "request to jetty successful before cert revoked"
       (let [response (get-request)]
         (is (= (:status response) 200))
         (is (= (:body response) hello-body))))

     (testing "request fails after cert revoked"
       ;; Sleep a bit to wait for the file watcher to be ready to poll/notify
       ;; for change events on the CRL. Ideally wouldn't have to do this but
       ;; seems like the initial event notification doesn't propagate if the
       ;; file is changed too soon after initialization.
       (Thread/sleep 1000)
       (fs/copy "./dev-resources/config/jetty/ssl/crls/crls_localhost_revoked.pem"
                tmp-file)
      (is
        (loop [times 30]
          (cond
            (try
              (ssl-exception-thrown? (get-request))
              (catch IllegalStateException _
                false))
            true

            (zero? times)
            (ssl-exception-thrown? (get-request))

            :else
            (do
              (Thread/sleep 500)
              (recur (dec times))))))))))

(defn boot-service-and-jetty-with-default-config
  [service]
  (tk-core/boot-services-with-config
    [service jetty9-service]
    jetty-plaintext-config))

(defn boot-service-and-jetty-with-multiserver-config
  [service]
  (tk-core/boot-services-with-config
    [service jetty9-service]
    jetty-multiserver-plaintext-config))

(defn get-jetty-server-from-app-context
  ([app]
   (get-jetty-server-from-app-context app :default))
  ([app server-id]
   (-> (tk-app/get-service app :WebserverService)
       (tk-services/service-context)
       (:jetty9-servers)
       (server-id)
       (:server))))

(deftest jetty-and-dependent-service-shutdown-after-service-error
  (testing (str "jetty and any dependent services are shutdown after a"
                "service throws an error from its start function")
    (tk-log-testutils/with-test-logging
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
           jetty-server     (get-jetty-server-from-app-context app)]
       (is (.isStarted jetty-server)
           "Jetty server was never started before call to run-app")
       (is (not (.isStopped jetty-server))
           "Jetty server was stopped before call to run-app")
       (is (thrown-with-msg?
             Throwable
             #"oops"
             (tk-core/run-app app))
           "tk run-app did not die with expected exception.")
       (is (true? @shutdown-called?)
           "Service shutdown was not called.")
       (is (.isStopped jetty-server)
           "Jetty server was not stopped after call to run-app."))))
  (testing (str "jetty and any dependent services are shutdown after a"
                "service throws an error from its start function"
                "in a multi-server set-up")
    (tk-log-testutils/with-test-logging
      (let [shutdown-called? (atom false)
            test-service     (tk-services/service
                               [[:WebserverService]]
                               (start [this context]
                                      (throw (Throwable. "oops"))
                                      context)
                               (stop [this context]
                                     (reset! shutdown-called? true)
                                     context))
            app              (boot-service-and-jetty-with-multiserver-config
                               test-service)
            jetty-server1     (get-jetty-server-from-app-context app :foo)
            jetty-server2     (get-jetty-server-from-app-context app :bar)]
        (is (.isStarted jetty-server1)
            "First Jetty server was never started before call to run-app")
        (is (.isStarted jetty-server2)
            "Second Jetty server was never started before call to run-app")
        (is (not (.isStopped jetty-server1))
            "First Jetty server was stopped before call to run-app")
        (is (not (.isStopped jetty-server2))
            "Second Jetty server was stopped before call to run-app")
        (is (thrown-with-msg?
              Throwable
              #"oops"
              (tk-core/run-app app))
            "tk run-app did not die with expected exception.")
        (is (true? @shutdown-called?)
            "Service shutdown was not called.")
        (is (.isStopped jetty-server1)
            "First Jetty server was not stopped after call to run-app.")
        (is (.isStopped jetty-server2)
            "Second Jetty server was not stopped after call to run-app."))))
  (testing (str "jetty server instance never attached to the service context "
                "and dependent services are shutdown after a service throws "
                "an error from its init function")
    (tk-log-testutils/with-test-logging
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
                               test-service)
            jetty-server     (get-jetty-server-from-app-context app)]
        (is (thrown-with-msg?
              Throwable
              #"oops"
              (tk-core/run-app app))
            "tk run-app did not die with expected exception.")
        (is (nil? jetty-server)
            (str "Jetty server was unexpectedly attached to the service "
                 "context."))
        (is (true? @shutdown-called?)
            "Service shutdown was not called."))))
  (testing (str "attempt to launch second jetty server on same port as "
                "already running jetty server fails with IOException without "
                "placing second jetty server instance on app context")
    (tk-log-testutils/with-test-logging
      (let [first-app (tk-core/boot-services-with-config
                        [jetty9-service]
                        jetty-plaintext-config)]
        (try
          (let [second-app          (tk-core/boot-services-with-config
                                      [jetty9-service]
                                      jetty-plaintext-config)
                second-jetty-server (get-jetty-server-from-app-context
                                      second-app)]
            (is (logged?
                  #"^Encountered error starting web server, so shutting down")
                "Didn't find log message for port bind error")
            (is (nil? second-jetty-server)
                "Jetty server was unexpectedly attached to the service context")
            (is (thrown?
                  IOException
                  (tk-core/run-app second-app))
                "tk run-app did not die with expected exception."))
          (finally
            (tk-app/stop first-app)))))))

(deftest default-server-test
  (testing (str "specifying a config in the old format will start a server with "
                "a server-id of :default")
    (let [app          (tk-core/boot-services-with-config
                         [jetty9-service]
                         jetty-plaintext-config)
          context-list (-> (tk-app/get-service app :WebserverService)
                           (tk-services/service-context)
                           (:jetty9-servers))
          jetty-server (get-jetty-server-from-app-context app)]
      (is (contains? context-list :default)
          "the default key was not added to the context list")
      (is (nil? (schema/check core/ServerContext (:default context-list)))
          "the value of the default key is not a valid server context")
      (is (.isStarted jetty-server)
          "the default server was never started")
      (tk-app/stop app))))

(deftest large-request-test
  ;; This changed from 413 to 431 in https://github.com/eclipse/jetty.project/commit/e53ea55f480a959a2f1f5e2dbdbfc689d61c94a6
  (testing (str "request to Jetty fails with a 431 error if the request header "
                "is too large and a larger one is not set")
    (with-app-with-config app
      [jetty9-service
       hello-webservice]
      jetty-plaintext-config
      (tk-log-testutils/with-test-logging
       (let [response (http-get "http://localhost:8080/hi_world" {:headers {"Cookie" absurdly-large-cookie}
                                                                  :as :text})]
         (is (= (:status response) 431))))))

  (testing (str "request to Jetty succeeds with a large cookie if the request header "
                "size is properly set")
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-large-request-config
      (let [s                   (tk-app/get-service app :WebserverService)
            add-ring-handler    (partial add-ring-handler s)
            body                "Hi World"
            path                "/hi_world"
            ring-handler        (fn [req] {:status 200 :body (str body ((:headers req) "cookie"))})]
        (add-ring-handler ring-handler path)
        (let [response (http-get "http://localhost:8080/hi_world" {:headers {"Cookie" absurdly-large-cookie}
                                                                   :as      :text})]
          (is (= (:status response) 200))
          (is (= (:body response) (str "Hi World" absurdly-large-cookie))))))))

(deftest default-server-test
  (testing "handler added to user-specified default server if no server-id is given"
    (with-app-with-config app
      [jetty9-service
       hello-webservice]
      default-server-config
      (let [response (http-get "http://localhost:9000/hi_world")]
        (is (= 200 (:status response)))
        (is (= (:body response) hello-body)))))

  (testing (str "exception thrown if user does not specify a "
                "default server and no server-id is given")
    (with-app-with-config app
      [jetty9-service]
      no-default-config
      (let [s                (tk-app/get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)]
        (is (thrown? IllegalArgumentException
                     (add-ring-handler hello-handler hello-path)))))))

(deftest static-content-config-test
  (let [logback (slurp "./dev-resources/logback.xml")]
    (testing "static content can be specified in a single-server configuration"
      (with-app-with-config
        app
        [jetty9-service]
        static-content-single-config
        (let [response (http-get "http://localhost:8080/resources/logback.xml")
              response2 (http-get "http://localhost:8080/resources2/logback.xml")]
          (is (= (:status response) 200))
          (is (= (:body response) logback))
          (is (= (:status response2) 200))
          (is (= (:body response2) logback)))))

    (testing "static content can be specified in a multi-server configuration"
      (with-app-with-config
        app
        [jetty9-service]
        static-content-multi-config
        (let [response (http-get "http://localhost:8080/resources/logback.xml")
              response2 (http-get "http://localhost:8080/resources2/logback.xml")]
          (is (= (:status response) 200))
          (is (= (:body response) logback))
          (is (= (:status response2) 200))
          (is (= (:body response2) logback)))
        (let [response (http-get "http://localhost:9000/resources/logback.xml")
              response2 (http-get "http://localhost:9000/resources2/logback.xml")]
          (is (= (:status response) 200))
          (is (= (:body response) logback))
          (is (= (:status response2) 200))
          (is (= (:body response2) logback)))))))

(deftest static-content-symlink-test
  (let [logback (slurp (str dev-resources-dir "logback.xml"))
        link (Paths/get (str dev-resources-dir "logback-link.xml") (into-array java.lang.String []))
        file (Paths/get "logback.xml" (into-array java.lang.String []))]
    (try
      (Files/createSymbolicLink link file (into-array FileAttribute []))

      (testing "static content can be served with symlinks when option specified in config"
        (with-app-with-config
          app
          [jetty9-service]
          static-content-single-config
          (let [response (http-get "http://localhost:8080/resources/logback.xml")
                response2 (http-get "http://localhost:8080/resources/logback-link.xml")]
            (is (= (:status response) 200))
            (is (= (:body response) logback))
            (is (= (:status response2) 200))
            (is (= (:body response2) logback)))))

      (testing "static content cannot be served with symlinks if option not set"
        (with-app-with-config
          app
          [jetty9-service]
          static-content-single-config
          (let [response (http-get "http://localhost:8080/resources2/logback.xml")
                response2 (http-get "http://localhost:8080/resources2/logback-link.xml")]
            (is (= (:status response) 200))
            (is (= (:body response) logback))
            (is (= (:status response2) 404)))))

      (finally
        (Files/delete link)))))

(deftest request-logging-test
  (with-app-with-config
   app
   [jetty9-service hello-webservice]
   {:webserver {:port 8080
                ;; Restrict the number of threads available to the webserver
                ;; so we can easily test whether thread-local values in the
                ;; MDC are cleaned up.
                :acceptor-threads 1
                :selector-threads 1 ; You actually end up with 2 threads.
                :max-threads 4
                :access-log-config "./dev-resources/puppetlabs/trapperkeeper/services/webserver/request-logging.xml"}}
    (testing "request logging occurs when :access-log-config is configured"
      (with-test-access-logging
       (http-get "http://localhost:8080/hi_world/")
       ; Logging is done in a separate thread from Jetty and this test. As a result,
       ; we have to sleep the thread to avoid a race condition.
       (Thread/sleep 10)
       (let [list (TestListAppender/list)]
         (is (re-find #"\"GET /hi_world/ HTTP/1.1\" 200 8" (first list))))))

    (testing "Mapped Diagnostic Context values are available to the access logger"
      (with-test-access-logging
        (http-put "http://localhost:8080/mdc?mdc_key=mdc-test" {:body "hello"})
        (Thread/sleep 10)
        (let [list (TestListAppender/list)]
          (is (str/ends-with? (first list) "hello\n")))))

    (testing "Mapped Diagnostic Context values are cleared after each request"
      (http-put "http://localhost:8080/mdc?mdc_key=mdc-persist" {:body "foo"})

      ;; Loop to ensure we hit all threads.
      (let [responses (for [n (range 0 10)]
                        (http-get "http://localhost:8080/mdc?mdc_key=mdc-persist"))]
        (is (every? #(not= "foo" %) (map :body responses)))))))

(deftest graceful-shutdown-test
  (testing "jetty9 webservers shut down gracefully by default"
    (with-app-with-config
      app
      [jetty9-service]
      jetty-plaintext-config
      (let [s (tk-app/get-service app :WebserverService)
            add-ring-handler   (partial add-ring-handler s)
            in-request-handler (promise)
            ring-handler       (fn [_]
                                 (deliver in-request-handler true)
                                 (Thread/sleep 3000)
                                 {:status 200 :body "Hello, World!"})]
        (add-ring-handler ring-handler "/hello")
        (with-open [async-client (async/create-client {})]
          (let [response (http-client-common/get async-client "http://localhost:8080/hello" {:as :text})]
            @in-request-handler
            (tk-app/stop app)
            (is (= (:status @response) 200))
            (is (= (:body @response) "Hello, World!")))))))

  (testing "jetty9's stop timeout can be changed from config"
    (with-app-with-config
      app
      [jetty9-service]
      {:webserver {:port 8080 :shutdown-timeout-seconds 1}}
      (let [s (tk-app/get-service app :WebserverService)
            add-ring-handler   (partial add-ring-handler s)
            in-request-handler (promise)
            ring-handler       (fn [_]
                                 (deliver in-request-handler true)
                                 (Thread/sleep 2000)
                                 {:status 200 :body "Hello, World!"})]
        (add-ring-handler ring-handler "/hello")
        (with-open [async-client (async/create-client {})]
          (let [response (http-client-common/get async-client "http://localhost:8080/hello" {:as :text})]
            @in-request-handler
            (tk-log-testutils/with-test-logging
              (tk-app/stop app))
            (is (not (nil? (:error @response)))))))))

; Per [TK-437](https://tickets.puppetlabs.com/browse/TK-437) we've been having issues
; with this test randomly hanging, but the fixes applied for TK-437 didn't seem to resolve
; the issue. This test is disabled until we can work out a reliable fix.
;
;  (testing "no graceful shutdown when stop timeout is set to 0"
;    (let [response (atom nil)]
;      ;; For this test, an active web request should exist at the time the
;      ;; webserver service is stopped.  Because the shutdown timeout is set to
;      ;; 0, though, the request should be terminated immediately as the
;      ;; webserver service is shut down, without waiting for ring handler
;      ;; to complete the request.
;      (with-open [async-client (async/create-client
;                                ;; Setting the socket timeout to something much
;                                ;; larger than the sleep delay done in the ring
;                                ;; handler below.  This timeout allows the HTTP
;                                ;; client request to not hang the test
;                                ;; indefinitely in the event of an unexpected
;                                ;; test failure.
;                                {:socket-timeout-milliseconds 120000})]
;        (tk-log-testutils/with-test-logging
;         (with-app-with-config
;          app
;          [jetty9-service]
;          {:webserver {:port 8080 :shutdown-timeout-seconds 0}}
;          (let [s (tk-app/get-service app :WebserverService)
;                add-ring-handler (partial add-ring-handler s)
;                in-request-handler (promise)
;                ring-handler (fn [_]
;                               (deliver in-request-handler true)
;                               ;; Sleeping for a long time to allow an HTTP
;                               ;; request to still be active at the point the
;                               ;; webserver is shutdown
;                               (Thread/sleep 60000)
;                               {:status 200 :body "Hello, World!"})]
;            (add-ring-handler ring-handler "/hello")
;            (reset! response (http-client-common/get
;                              async-client
;                              "http://localhost:8080/hello"
;                              {:as :text}))
;            @in-request-handler)))
;        ;; The web server should have been stopped by the time we get here.
;        ;; Depending upon timing, the Jetty webserver may provide an HTTP 404
;        ;; response or just terminate the socket for the web request which could
;        ;; not be completed, which should cause a ConnectionClosedException to
;        ;; be thrown.
;        ;;
;        ;; Note that per TK-437, we have occasionally seen this assertion fail
;        ;; due to a SocketTimeoutException being thrown.  In this case, there
;        ;; appears to be a timing-related bug in Jetty where the server socket
;        ;; isn't closed down even though the server otherwise appears to have
;        ;; been stopped properly - leaving the server-side of the connection in
;        ;; an indefinite CLOSE_WAIT state.
;        (let [resp (deref (deref response))
;              error (:error resp)]
;          (is (or
;               (instance? ConnectionClosedException error)
;               (= 404 (:status resp)))
;              (str "request did not error as expected. response: " resp))))))

  (testing "tk app can still restart even if stop timeout expires"
    (let [in-request-handler? (promise)
          unblock-request? (promise)

          sleepy-service (tk-core/service
                          [[:WebserverService add-ring-handler]]
                          (init [this context]
                                (add-ring-handler
                                 (fn [_]
                                   (deliver in-request-handler? true)
                                   @unblock-request?
                                   {:status 200 :body hello-body})
                                 hello-path)
                                context))]
      (tk-log-testutils/with-test-logging
       (with-app-with-config
         app
         [jetty9-service
          sleepy-service]
         {:webserver {:port 8080 :shutdown-timeout-seconds 1}}
         (with-open [async-client (async/create-client {})]
           (let [response (http-client-common/get async-client "http://localhost:8080/hi_world" {:as :text})]
             @in-request-handler?
             (tk-app/restart app)
             (is (not (nil? (:error @response)))))
           (deliver unblock-request? true)
           (let [response (http-client-common/get async-client "http://localhost:8080/hi_world" {:as :text})]
             (is (= 200 (:status @response)))
             (is (= hello-body (:body @response))))))))))

(deftest double-stop-test
  (testing "if the stop lifecycle is called more than once, we handle that gracefully and quietly"
    (tk-log-testutils/with-logged-event-maps log-events
      (let [app (tk-bootstrap/bootstrap-services-with-config
                 [jetty9-service]
                 {:webserver {:port 8080}})]
        (tk-app/stop app)
        (tk-app/stop app))
      ;; we previously had a bug where we could try to unregister mbeans multiple
      ;; times if our tk-j9 stop lifecycle function was called more than once.
      ;; in that case, Jetty would log tons of nasty exceptions at warning level.
      ;; this test just validates that that is not happening.
      (let [log-event-filter #(or (= :warn (:level %))
                                  (= :error (:level %)))
            mbean-err-logs (filter log-event-filter @log-events)]
        (is (= 0 (count mbean-err-logs)))))))

(deftest warn-if-sslv3-supported-test
  (letfn [(start-server [ssl-protocols]
            (let [config (if ssl-protocols
                           (assoc-in jetty-ssl-pem-config [:webserver :ssl-protocols] ssl-protocols)
                           jetty-ssl-pem-config)]
              (with-app-with-config
                app
                [jetty9-service]
                config)))]
    (testing "warns if SSLv3 is in the protocol list"
      (tk-log-testutils/with-test-logging
        (start-server ["SSLv3" "TLSv1"])
        (is (logged? #"known vulnerabilities"))))
    (testing "warns regardless of case"
      (tk-log-testutils/with-test-logging
        (start-server ["sslv3"])
        (is (logged? #"known vulnerabilities"))))
    (testing "does not warn if sslv3 is not in the protocol list"
      (tk-log-testutils/with-log-output logs
        (start-server ["TLSv1"])
        (is (= 0 (count (tk-log-testutils/logs-matching
                         #"known vulnerabilities" @logs))))))
    (testing "does not warn with default settings"
      (tk-log-testutils/with-log-output logs
        (start-server nil)
        (is (= 0 (count (tk-log-testutils/logs-matching
                         #"known vulnerabilities" @logs))))))))

(deftest sslv3-support-test
  (testing "SSLv3 is not supported by default"
    (with-app-with-config
      app
      [jetty9-service
       hello-webservice]
      jetty-ssl-pem-config
      (let [test-fn (fn [] (http-get "https://localhost:8081/hi_world" (merge default-options-for-https-client
                                                                             {:ssl-protocols ["SSLv3"]})) )]

        (is (thrown? SSLException (test-fn))))))
  (testing "SSLv3 is not supported even when configured"
    (tk-log-testutils/with-test-logging
     (with-app-with-config
      app
      [jetty9-service
       hello-webservice]
      (-> jetty-ssl-pem-config
        (assoc-in [:webserver :ssl-protocols] ["SSLv3"])
        (assoc-in [:webserver :cipher-suites] ["TLS_RSA_WITH_AES_128_CBC_SHA"]))
      (is (logged? #"contains SSLv3, a protocol with known vulnerabilities; ignoring"))
      (is (logged? #"When `ssl-protocols` is empty, a default of"))
      (let [test-fn (fn [] (http-get "https://localhost:8081/hi_world" (merge default-options-for-https-client
                                                                              {:ssl-protocols ["SSLv3"]})) )]
        (is (thrown? SSLException (test-fn))))))))
