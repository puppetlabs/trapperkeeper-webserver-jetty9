(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
  (:import  (java.net ConnectException)
            [servlet SimpleServlet])
  (:require [clojure.test :refer :all]
            [clj-http.client :as http-client]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :refer [stop service-context]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [bootstrap-services-with-empty-config
                                                                  bootstrap-services-with-cli-data]]
            [puppetlabs.kitchensink.testutils.fixtures :refer [with-no-jvm-shutdown-hooks]]))

(use-fixtures :once with-no-jvm-shutdown-hooks)

(deftest jetty-jetty9-service
  (testing "ring support"
    (let [app              (bootstrap-services-with-cli-data [jetty9-service]
                             {:config "./test-resources/config/jetty/jetty-ssl-jks.ini"})
          s                 (get-service app :WebserverService)
          add-ring-handler  (partial add-ring-handler s)
          shutdown          (partial stop s (service-context s))
          body             "Hello World"
          path             "/hello_world"
          ring-handler     (fn [req] {:status 200 :body body})]
      (try
        (add-ring-handler ring-handler path)
        ;; host and port are defined in config file used above
        (let [response (http-client/get (format "http://localhost:8080/%s/" path))]
          (is (= (response :status) 200))
          (is (= (response :body) body)))
        (finally
          (shutdown)))))

  (testing "servlet support with plaintext only port binding"
    (let [app                 (bootstrap-services-with-cli-data [jetty9-service]
                                {:config
                                  "./test-resources/config/jetty/jetty-plaintext-http.ini"})
          s                   (get-service app :WebserverService)
          add-servlet-handler (partial add-servlet-handler s)
          shutdown            (partial stop s (service-context s))
          body                "Hey there"
          path                "/hey"
          servlet             (SimpleServlet. body)]
      (try
        (add-servlet-handler servlet path)
        (let [response (http-client/get (format "http://localhost:8080/%s" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body)))
        (finally
          (shutdown)))))

  (testing "servlet support with empty init param"
    (let [app                 (bootstrap-services-with-cli-data [jetty9-service]
                                {:config
                                  "./test-resources/config/jetty/jetty-plaintext-http.ini"})
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

  (testing "servlet support with non-empty init params"
    (let [app                 (bootstrap-services-with-cli-data [jetty9-service]
                                {:config
                                  "./test-resources/config/jetty/jetty-plaintext-http.ini"})
          s                   (get-service app :WebserverService)
          add-servlet-handler (partial add-servlet-handler s)
          shutdown            (partial stop s (service-context s))
          body                "Hey there"
          path                "/hey"
          init-param-one      "value of init param one"
          init-param-two      "value of init param two"
          servlet             (SimpleServlet. body)]
      (try
        (add-servlet-handler servlet path {"init-param-one" init-param-one "init-param-two" init-param-two})
        (let [response (http-client/get (format "http://localhost:8080/%s/init-param-one" path))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-one)))
        (let [response (http-client/get (format "http://localhost:8080/%s/init-param-two" path))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-two)))
        (finally
          (shutdown)))))

  (testing "webserver bootstrap throws IllegalArgumentException when neither
            port nor ssl-port specified in the config"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Either port or ssl-port must be specified on the config in order for a port binding to be opened"
          (bootstrap-services-with-empty-config [jetty9-service]))
      "Did not encounter expected exception when no port specified in config"))

  (testing "SSL initialization is supported for both .jks and .pem implementations"
    (doseq [config ["./test-resources/config/jetty/jetty-ssl-jks.ini"
                    "./test-resources/config/jetty/jetty-ssl-pem.ini"]]
      (let [app               (bootstrap-services-with-cli-data [jetty9-service]
                                {:config config})
            s                 (get-service app :WebserverService)
            add-ring-handler  (partial add-ring-handler s)
            shutdown          (partial stop s (service-context s))
            body              "Hi World"
            path              "/hi_world"
            ring-handler      (fn [req] {:status 200 :body body})]
        (try
          (add-ring-handler ring-handler path)
          (let [response (http-client/get
                           (format "https://localhost:8081/%s/" path)
                           {:keystore         "./test-resources/config/jetty/ssl/keystore.jks"
                            :keystore-type    "JKS"
                            :keystore-pass    "Kq8lG9LkISky9cDIYysiadxRx"
                            :trust-store      "./test-resources/config/jetty/ssl/truststore.jks"
                            :trust-store-type "JKS"
                            :trust-store-pass "Kq8lG9LkISky9cDIYysiadxRx"
                            ; The server's cert in this case uses a CN of
                            ; "localhost-puppetdb" whereas the URL being reached
                            ; is "localhost".  The insecure? value of true
                            ; directs the client to ignore the mismatch.
                            :insecure?        true})]
            (is (= (:status response) 200))
            (is (= (:body response) body)))
          (finally
            (shutdown)))))))
