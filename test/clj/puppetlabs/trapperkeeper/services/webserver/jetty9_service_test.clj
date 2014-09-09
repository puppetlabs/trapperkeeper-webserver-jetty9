(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
  (:import  (org.eclipse.jetty.server Server)
            (org.apache.http ConnectionClosedException)
            (java.io IOException)
            (java.security.cert CRLException)
            (java.net BindException)
            (java.nio.file Paths Files)
            (java.nio.file.attribute FileAttribute))
  (:require [clojure.test :refer :all]
            [puppetlabs.http.client.sync :as http-client]
            [clojure.tools.logging :as log]
            [puppetlabs.kitchensink.testutils.fixtures :as ks-test-fixtures]
            [puppetlabs.trapperkeeper.app :as tk-app]
            [puppetlabs.trapperkeeper.core :as tk-core]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
              :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
              :refer [with-app-with-empty-config
                      with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
              :refer [with-test-logging]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
            [schema.core :as schema]))

(use-fixtures :once ks-test-fixtures/with-no-jvm-shutdown-hooks)

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
    (catch IOException e#
      (if (= "Connection reset by peer" (.getMessage e#))
        true
        (throw e#)))))

(def unauthorized-pem-options-for-https
  (-> default-options-for-https-client
      (assoc :ssl-cert "./dev-resources/config/jetty/ssl/certs/unauthorized.pem")
      (assoc :ssl-key "./dev-resources/config/jetty/ssl/private_keys/unauthorized.pem")))

(defn validate-ring-handler
  ([base-url config]
    (validate-ring-handler base-url config {:as :text} :default))
  ([base-url config http-get-options]
   (validate-ring-handler base-url config http-get-options :default))
  ([base-url config http-get-options server-id]
    (with-app-with-config app
      [jetty9-service]
      config
      (let [s                   (tk-app/get-service app :WebserverService)
            add-ring-handler    (partial add-ring-handler s)
            body                "Hi World"
            path                "/hi_world"
            ring-handler        (fn [req] {:status 200 :body body})]
        (add-ring-handler ring-handler path {:server-id server-id})
        (let [response (http-get
                         (format "%s%s/" base-url path)
                         http-get-options)]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))

(defn validate-ring-handler-default
  ([base-url config]
   (validate-ring-handler-default base-url config {:as :text}))
  ([base-url config http-get-options]
   (with-app-with-config app
     [jetty9-service]
     config
     (let [s                   (tk-app/get-service app :WebserverService)
           add-ring-handler    (partial add-ring-handler s)
           body                "Hi World"
           path                "/hi_world"
           ring-handler        (fn [req] {:status 200 :body body})]
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

(deftest basic-default-ring-test
  (testing "ring request over http succeeds with default add-ring-handler"
    (validate-ring-handler-default
      "http://localhost:8080"
      jetty-plaintext-config)))

(deftest multiserver-ring-test
  (testing "ring request on single server with new syntax over http succeeds"
    (validate-ring-handler
      "http://localhost:8080"
      {:webserver {:default        {:port           8080
                                    :default-server true}}}
      {:as :text}
      :default))

  (testing "ring requests on multiple servers succeed"
    (with-app-with-config app
      [jetty9-service]
      jetty-multiserver-plaintext-config
      (let [s                (tk-app/get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            path             "/hi_world"
            ring-handler (fn [req] {:status 200 :body body})]
        (add-ring-handler ring-handler path {:server-id :foo})
        (add-ring-handler ring-handler path {:server-id :bar})
        (let [response1 (http-get "http://localhost:8080/hi_world/" {:as :text})
              response2 (http-get "http://localhost:8085/hi_world/" {:as :text})]
          (is (= (:status response1) 200))
          (is (= (:status response2) 200))
          (is (= (:body response1) body))
          (is (= (:body response2) body))))))

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

  (testing (str "jetty throws startup exception if non-CRL PEM is specified "
                "as ssl-crl-path")
    (with-test-logging
      (is (thrown?
            CRLException
            (with-app-with-config
              app
              [jetty9-service]
              (assoc-in
                jetty-ssl-client-need-config
                [:webserver :ssl-crl-path]
                "./dev-resources/config/jetty/ssl/certs/ca.pem"))))))

  (testing (str "jetty throws startup exception if ssl-crl-path refers to a "
                "non-existent file")
    (with-test-logging
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
                "already running jetty server fails with BindException without "
                "placing second jetty server instance on app context")
    (with-test-logging
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
                  BindException
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
  (testing (str "request to Jetty fails with a 413 error if the request header "
                "is too large and a larger one is not set")
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                   (tk-app/get-service app :WebserverService)
            add-ring-handler    (partial add-ring-handler s)
            body                "Hi World"
            path                "/hi_world"
            ring-handler        (fn [req] {:status 200 :body body})]
        (add-ring-handler ring-handler path)
        (let [response (http-get "http://localhost:8080/hi_world" {:headers {"Cookie" absurdly-large-cookie}
                                                                   :as      :text})]
          (is (= (:status response) 413))))))

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
      [jetty9-service]
      default-server-config
      (let [s                (tk-app/get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            path             "/hi_world"
            ring-handler     (fn [req] {:status 200 :body body})]
        (add-ring-handler ring-handler path)
        (let [response (http-get "http://localhost:9000/hi_world")]
          (is (= 200 (:status response)))
          (is (= (:body response) body))))))

  (testing (str "exception thrown if user does not specify a "
                "default server and no server-id is given")
    (with-app-with-config app
      [jetty9-service]
      no-default-config
      (let [s                (tk-app/get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            path             "/hi_world"
            ring-handler     (fn [req] {:status 200 :body body})]
        (is (thrown? IllegalArgumentException
                     (add-ring-handler ring-handler path)))))))

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
