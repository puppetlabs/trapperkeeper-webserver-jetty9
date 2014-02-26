(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
  (:import  (java.net ConnectException)
            (javax.net.ssl SSLPeerUnverifiedException)
            [servlet SimpleServlet])
  (:require [clojure.test :refer :all]
            [clj-http.client :as http-client]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.core :refer [defservice]]
            [puppetlabs.trapperkeeper.services :refer [stop service-context]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
               :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
               :refer [bootstrap-services-with-empty-config
                       bootstrap-services-with-cli-data]]
            [puppetlabs.trapperkeeper.app :as app]
            [puppetlabs.kitchensink.testutils.fixtures
               :refer [with-no-jvm-shutdown-hooks]]))

(use-fixtures :once with-no-jvm-shutdown-hooks)

(def test-resources-config-dir "./test-resources/config/jetty/")

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

(defn bootstrap-and-validate-ring-handler
  ([base-url config-file-name]
    (bootstrap-and-validate-ring-handler base-url config-file-name {}))
  ([base-url config-file-name http-get-options]
    (let [app               (bootstrap-services-with-cli-data
                              [jetty9-service]
                                {:config
                                  (str
                                    test-resources-config-dir
                                    config-file-name)})
           s                 (get-service app :WebserverService)
           add-ring-handler  (partial add-ring-handler s)
           shutdown          (partial stop s (service-context s))
           body              "Hi World"
           path              "/hi_world"
           ring-handler      (fn [req] {:status 200 :body body})]
       (try
         (add-ring-handler ring-handler path)
         (let [response (http-client/get
                          (format "%s/%s/" base-url path)
                          http-get-options)]
           (is (= (:status response) 200))
           (is (= (:body response) body)))
         (finally
           (shutdown))))))

(defservice hello-test-service
  [[:WebserverService add-war-handler]]
  (init [this context]
        (add-war-handler "test-resources/helloWorld.war" "/test")
        context)
  (stop [this context]
        context))

(deftest jetty-jetty9-service
  (testing "ring request over http succeeds")
    (bootstrap-and-validate-ring-handler
      "http://localhost:8080"
      "jetty-plaintext-http.ini")

  (testing "request to servlet over http succeeds"
    (let [app                 (bootstrap-services-with-cli-data [jetty9-service]
                                {:config
                                  (str
                                    test-resources-config-dir
                                    "jetty-plaintext-http.ini")})
          s                   (get-service app :WebserverService)
          add-servlet-handler (partial add-servlet-handler s)
          shutdown            (partial stop s (service-context s))
          body                "Hey there"
          path                "/hey"
          servlet             (SimpleServlet. body)]
      (try
        (add-servlet-handler servlet path)
        (let [response (http-client/get
                         (format "http://localhost:8080/%s" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body)))
        (finally
          (shutdown)))))

  (testing "request to servlet initialized with empty param succeeds"
    (let [app                 (bootstrap-services-with-cli-data [jetty9-service]
                                {:config
                                  (str
                                    test-resources-config-dir
                                    "jetty-plaintext-http.ini")})
          s                   (get-service app :WebserverService)
          add-servlet-handler (partial add-servlet-handler s)
          shutdown            (partial stop s (service-context s))
          body                "Hey there"
          path                "/hey"
          servlet             (SimpleServlet. body)]
      (try
        (add-servlet-handler servlet path {})
        (let [response (http-client/get (format "http://localhost:8080/%s" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body)))
        (finally
          (shutdown)))))

  (testing "request to servlet initialized with non-empty params succeeds"
    (let [app                 (bootstrap-services-with-cli-data [jetty9-service]
                                {:config
                                  (str
                                    test-resources-config-dir
                                    "jetty-plaintext-http.ini")})
          s                   (get-service app :WebserverService)
          add-servlet-handler (partial add-servlet-handler s)
          shutdown            (partial stop s (service-context s))
          body                "Hey there"
          path                "/hey"
          init-param-one      "value of init param one"
          init-param-two      "value of init param two"
          servlet             (SimpleServlet. body)]
      (try
        (add-servlet-handler servlet
                             path
                             {"init-param-one" init-param-one
                              "init-param-two" init-param-two})
        (let [response (http-client/get
                         (format "http://localhost:8080/%s/init-param-one"
                                 path))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-one)))
        (let [response (http-client/get
                         (format "http://localhost:8080/%s/init-param-two"
                                 path))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-two)))
        (finally
          (shutdown)))))

  (testing "WAR support"
    (let [app (bootstrap-services-with-cli-data [jetty9-service hello-test-service]
                {:config
                  (str
                    test-resources-config-dir
                    "jetty-plaintext-http.ini")})]
      (try
        (let [response (http-client/get "http://localhost:8080/test/hello")]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n")))
        (finally
          (app/stop app)))))

  (testing "webserver bootstrap throws IllegalArgumentException when neither
            port nor ssl-port specified in the config"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Either port or ssl-port must be specified on the config in order for a port binding to be opened"
          (bootstrap-services-with-empty-config [jetty9-service]))
      "Did not encounter expected exception when no port specified in config"))

  (testing "ring request over SSL successful for both .jks and .pem
            implementations with the server's client-auth setting not set and
            the client configured provide a certificate which the CA can
            validate"
    ; Note that if the 'client-auth' setting is not set that the server
    ; should default to 'need' to validate the client certificate.  In this
    ; case, the validation should be successful because the client is
    ; providing a certificate which the CA can validate.
    (doseq [config ["jetty-ssl-jks.ini" "jetty-ssl-pem.ini"]]
        (bootstrap-and-validate-ring-handler
          "https://localhost:8081"
          config
          default-options-for-https)))

  (testing "ring request over SSL fails with the server's client-auth setting
            not set and the client configured to provide a certificate which
            the CA cannot validate"
    ; Note that if the 'client-auth' setting is not set that the server
    ; should default to 'need' to validate the client certificate.  In this
    ; case, the validation should fail because the client is providing a
    ; certificate which the CA cannot validate.
    (is (thrown?
          SSLPeerUnverifiedException
          (bootstrap-and-validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem.ini"
            (assoc default-options-for-https
                   :keystore
                   (str test-resources-config-dir
                        "ssl/unauthorized_keystore.jks"))))))

  (testing "ring request over SSL fails with the server's client-auth setting
            not set and the client configured to not provide a certificate"
    ; Note that if the 'client-auth' setting is not set that the server
    ; should default to 'need' to validate the client certificate.  In this
    ; case, the validation should fail because the client is providing a
    ; certificate which the CA cannot validate.
    (is (thrown?
          SSLPeerUnverifiedException
          (bootstrap-and-validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem.ini"
            (assoc default-options-for-https
              :keystore nil)))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'need' and the client configured to provide a certificate which
            the CA can validate"
    (bootstrap-and-validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-need.ini"
      default-options-for-https))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'need' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (thrown?
          SSLPeerUnverifiedException
          (bootstrap-and-validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem-client-auth-need.ini"
            (assoc default-options-for-https
                   :keystore
                   (str test-resources-config-dir
                        "ssl/unauthorized_keystore.jks"))))))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'need' and the client configured to not provide a certificate"
    (is (thrown?
          SSLPeerUnverifiedException
          (bootstrap-and-validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem-client-auth-need.ini"))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'want' and the client configured to provide a certificate which
            the CA can validate"
    (bootstrap-and-validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-want.ini"
      default-options-for-https))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'want' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (thrown?
          SSLPeerUnverifiedException
          (bootstrap-and-validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem-client-auth-need.ini"
            (assoc default-options-for-https
                   :keystore
                   (str test-resources-config-dir
                        "ssl/unauthorized_keystore.jks"))))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'want' and the client configured to not provide a certificate"
    (bootstrap-and-validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-want.ini"
      (assoc default-options-for-https
             :keystore nil)))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'none' and the client configured to provide a certificate which
            the CA can validate"
    (bootstrap-and-validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-none.ini"
      default-options-for-https))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'none' and the client configured to provide a certificate which
            the CA cannot validate"
    (bootstrap-and-validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-none.ini"
      (assoc default-options-for-https
             :keystore
             (str test-resources-config-dir
                  "ssl/unauthorized_keystore.jks"))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'none' and the client configured to not provide a certificate"
    (bootstrap-and-validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-none.ini"
      (assoc default-options-for-https
        :keystore nil))))