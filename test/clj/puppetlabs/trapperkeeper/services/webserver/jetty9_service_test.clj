(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
  (:import  (javax.net.ssl SSLPeerUnverifiedException)
            (javax.servlet ServletContextListener)
            (servlet SimpleServlet))
  (:require [clojure.test :refer :all]
            [clj-http.client :as http-client]
            [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
               :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
               :refer [with-app-with-empty-config
                       with-app-with-cli-data
                       with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
               :refer [with-test-logging]]))

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

(defmacro with-target-and-proxy-servers
  [{:keys [target proxy proxy-config proxy-opts]} & body]
  `(with-app-with-config proxy-target-app#
      [jetty9-service]
      {:webserver ~target}
      (let [target-webserver# (get-service proxy-target-app# :WebserverService)]
        (add-ring-handler
          target-webserver#
          (fn [req#]
            (if (= "/hello/world" (:uri req#))
              {:status 200 :body "Hello, World!"}
              {:status 404 :body "D'oh"}))
          "/hello"))
      (with-app-with-config proxy-app#
        [jetty9-service]
        {:webserver ~proxy}
        (let [proxy-webserver# (get-service proxy-app# :WebserverService)]
          (if ~proxy-opts
             (add-proxy-route proxy-webserver# ~proxy-config "/hello-proxy" ~proxy-opts)
             (add-proxy-route proxy-webserver# ~proxy-config "/hello-proxy")))
        ~@body)))

(defprotocol Service1)

(defn validate-ring-handler
  ([base-url config-file-name]
    (validate-ring-handler base-url config-file-name {}))
  ([base-url config-file-name http-get-options]
    (with-app-with-cli-data app
      [jetty9-service]
      {:config (str test-resources-config-dir config-file-name)}
      (let [s                (get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            path             "/hi_world"
            ring-handler     (fn [req] {:status 200 :body body})]
        (add-ring-handler ring-handler path)
        (let [response (http-client/get
                         (format "%s/%s/" base-url path)
                         http-get-options)]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))

(deftest jetty-jetty9-service
  (testing "static content context"
    (with-app-with-cli-data app
      [jetty9-service]
      {:config (str test-resources-config-dir "jetty-plaintext-http.ini")}
      (let [s                   (get-service app :WebserverService)
            add-context-handler (partial add-context-handler s)
            path                "/resources"
            resource            "logback.xml"]
        (add-context-handler test-resources-dir path)
        (let [response (http-client/get (str "http://localhost:8080" path "/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str test-resources-dir resource))))))))

  (testing "customization of static content context"
    (with-app-with-cli-data app
      [jetty9-service]
      {:config (str test-resources-config-dir "jetty-plaintext-http.ini")}
      (let [s                   (get-service app :WebserverService)
            add-context-handler (partial add-context-handler s)
            path                "/resources"
            body                "Hey there"
            servlet-path        "/hey"
            servlet             (SimpleServlet. body)]
        (add-context-handler test-resources-dir path
                             [(reify ServletContextListener
                                (contextInitialized [this event]
                                  (doto (.addServlet (.getServletContext event) "simple" servlet)
                                    (.addMapping (into-array [servlet-path]))))
                                (contextDestroyed [this event]))])
        (let [response (http-client/get (str "http://localhost:8080" path servlet-path))]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "ring request over http succeeds")
    (validate-ring-handler
      "http://localhost:8080"
      "jetty-plaintext-http.ini")

  (testing "request to servlet over http succeeds"
    (with-app-with-cli-data app
      [jetty9-service]
      {:config (str test-resources-config-dir "jetty-plaintext-http.ini")}
      (let [s                   (get-service app :WebserverService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            path                "/hey"
            servlet             (SimpleServlet. body)]
        (add-servlet-handler servlet path)
        (let [response (http-client/get
                         (format "http://localhost:8080/%s" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet initialized with empty param succeeds"
    (with-app-with-cli-data app
      [jetty9-service]
      {:config (str test-resources-config-dir "jetty-plaintext-http.ini")}
      (let [s                   (get-service app :WebserverService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            path                "/hey"
            servlet             (SimpleServlet. body)]
        (add-servlet-handler servlet path {})
        (let [response (http-client/get (format "http://localhost:8080/%s" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet initialized with non-empty params succeeds"
    (with-app-with-cli-data app
      [jetty9-service]
      {:config (str test-resources-config-dir "jetty-plaintext-http.ini")}
      (let [s                   (get-service app :WebserverService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            path                "/hey"
            init-param-one      "value of init param one"
            init-param-two      "value of init param two"
            servlet             (SimpleServlet. body)]
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
          (is (= (:body response) init-param-two))))))

  (testing "WAR support"
    (with-app-with-cli-data app
      [jetty9-service]
      {:config (str test-resources-config-dir "jetty-plaintext-http.ini")}
      (let [s               (get-service app :WebserverService)
            add-war-handler (partial add-war-handler s)
            path            "/test"
            war             "helloWorld.war"]
        (add-war-handler (str test-resources-dir war) path)
        (let [response (http-client/get (str "http://localhost:8080" path "/hello"))]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n"))))))

  (testing "webserver bootstrap throws IllegalArgumentException when neither
            port nor ssl-port specified in the config"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Either port or ssl-port must be specified on the config in order for a port binding to be opened"
          (with-test-logging
            (with-app-with-empty-config app [jetty9-service])))
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
        (validate-ring-handler
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
          (validate-ring-handler
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
          (validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem.ini"
            (assoc default-options-for-https
              :keystore nil)))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'need' and the client configured to provide a certificate which
            the CA can validate"
    (validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-need.ini"
      default-options-for-https))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'need' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (thrown?
          SSLPeerUnverifiedException
          (validate-ring-handler
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
          (validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem-client-auth-need.ini"))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'want' and the client configured to provide a certificate which
            the CA can validate"
    (validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-want.ini"
      default-options-for-https))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'want' and the client configured to provide a certificate which
            the CA cannot validate"
    (is (thrown?
          SSLPeerUnverifiedException
          (validate-ring-handler
            "https://localhost:8081"
            "jetty-ssl-pem-client-auth-need.ini"
            (assoc default-options-for-https
                   :keystore
                   (str test-resources-config-dir
                        "ssl/unauthorized_keystore.jks"))))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'want' and the client configured to not provide a certificate"
    (validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-want.ini"
      (assoc default-options-for-https
             :keystore nil)))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'none' and the client configured to provide a certificate which
            the CA can validate"
    (validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-none.ini"
      default-options-for-https))

  (testing "ring request over SSL fails with a server client-auth setting
            of 'none' and the client configured to provide a certificate which
            the CA cannot validate"
    (validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-none.ini"
      (assoc default-options-for-https
             :keystore
             (str test-resources-config-dir
                  "ssl/unauthorized_keystore.jks"))))

  (testing "ring request over SSL succeeds with a server client-auth setting
            of 'none' and the client configured to not provide a certificate"
    (validate-ring-handler
      "https://localhost:8081"
      "jetty-ssl-pem-client-auth-none.ini"
      (assoc default-options-for-https
        :keystore nil))))

(deftest test-proxy-servlet
  (let [common-ssl-config {:ssl-cert    "./test-resources/config/jetty/ssl/certs/localhost.pem"
                           :ssl-key     "./test-resources/config/jetty/ssl/private_keys/localhost.pem"
                           :ssl-ca-cert "./test-resources/config/jetty/ssl/certs/ca.pem"}]
    (testing "basic proxy support"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}}
        (let [response (http-client/get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-client/get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic proxy support with explicit :orig scheme"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:scheme :orig}}
        (let [response (http-client/get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-client/get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic https proxy support (pass-through https config)"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}}
        (let [response (http-client/get "https://localhost:9001/hello/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-client/get "https://localhost:10001/hello-proxy/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic https proxy support (pass-through https config) with explicit :orig scheme"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:scheme :orig}}
        (let [response (http-client/get "https://localhost:9001/hello/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-client/get "https://localhost:10001/hello-proxy/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic https proxy support (pass-through https config via explicit :use-server-config)"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:ssl-config :use-server-config}}
        (let [response (http-client/get "https://localhost:9001/hello/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-client/get "https://localhost:10001/hello-proxy/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "http->https proxy support with explicit ssl config for proxy"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9000})
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:scheme     :https
                        :ssl-config common-ssl-config}}
        (let [response (http-client/get "https://localhost:9000/hello/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-client/get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "https->http proxy support"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9001}
         :proxy        (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:scheme :http}}
        (let [response (http-client/get "http://localhost:9001/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-client/get "https://localhost:10001/hello-proxy/world" default-options-for-https)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))))

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