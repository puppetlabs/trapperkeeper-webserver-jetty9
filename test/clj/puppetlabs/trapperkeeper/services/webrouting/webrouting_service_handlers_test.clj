(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-handlers-test
  (:import (servlet SimpleServlet)
           (javax.servlet ServletContextListener))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.webrouting.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-test-logging]]))

(defmacro with-target-and-proxy-servers
  [{:keys [target proxy proxy-config proxy-opts]} & body]
  `(with-app-with-config proxy-target-app#
     [jetty9-service
      webrouting-service]
     {:webserver ~target
      :web-router-service {:puppetlabs.bar/bar-service "/hello"}}
     (let [target-webserver# (get-service proxy-target-app# :WebroutingService)]
       (add-ring-handler
         target-webserver#
         :puppetlabs.bar/bar-service
         (fn [req#]
           (if (= "/hello/world" (:uri req#))
             {:status 200 :body (str "Hello, World!"
                                     ((:headers req#) "x-fancy-proxy-header"))}
             {:status 404 :body "D'oh"}))))
     (with-app-with-config proxy-app#
       [jetty9-service
        webrouting-service]
       {:webserver ~proxy
        :web-router-service {:puppetlabs.foo/foo-service "/hello-proxy"}}
       (let [proxy-webserver# (get-service proxy-app# :WebroutingService)
             svc#             :puppetlabs.foo/foo-service]
         (if ~proxy-opts
           (add-proxy-route proxy-webserver# svc#  ~proxy-config ~proxy-opts)
           (add-proxy-route proxy-webserver# svc#  ~proxy-config)))
       ~@body)))

(defmacro with-target-and-proxy-servers-variant
  [{:keys [target proxy proxy-config proxy-opts]} server-id & body]
  `(with-app-with-config proxy-target-app#
     [jetty9-service
      webrouting-service]
     {:webserver ~target
      :web-router-service {:puppetlabs.bar/bar-service "/hello"}}
     (let [target-webserver# (get-service proxy-target-app# :WebroutingService)]
       (add-ring-handler
         target-webserver#
         :puppetlabs.bar/bar-service
         (fn [req#]
           (if (= "/hello/world" (:uri req#))
             {:status 200 :body (str "Hello, World!"
                                     ((:headers req#) "x-fancy-proxy-header"))}
             {:status 404 :body "D'oh"}))))
     (with-app-with-config proxy-app#
       [jetty9-service
        webrouting-service]
       {:webserver ~proxy
        :web-router-service {:puppetlabs.foo/foo-service "/hello-proxy"}}
       (let [proxy-webserver# (get-service proxy-app# :WebroutingService)
             svc#             :puppetlabs.foo/foo-service]
         (if ~proxy-opts
           (add-proxy-route-to proxy-webserver# svc# ~server-id ~proxy-config ~proxy-opts)
           (add-proxy-route-to proxy-webserver# svc# ~server-id ~proxy-config)))
       ~@body)))

(def dev-resources-dir        "./dev-resources/")

(def dev-resources-config-dir (str dev-resources-dir "config/jetty/"))

(deftest static-content-test-web-routing
  (testing "static content context with web routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-config
      (let [s                   (get-service app :WebroutingService)
            add-context-handler (partial add-context-handler s)
            resource            "logback.xml"
            svc                 :puppetlabs.foo/foo-service]
        (add-context-handler svc dev-resources-dir)
        (let [response (http-get (str "http://localhost:8080/foo/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str dev-resources-dir resource))))))))

  (testing "static content context with web routing and multiple servers"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-multiserver-config
      (let [s                      (get-service app :WebroutingService)
            add-context-handler-to (partial add-context-handler-to s)
            resource               "logback.xml"
            server-id              :ziggy
            svc                    :puppetlabs.foo/foo-service]
        (add-context-handler-to svc server-id dev-resources-dir)
        (let [response (http-get (str "http://localhost:9000/foo/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str dev-resources-dir resource))))))))

  (testing "customization of static content context with web routing"
    (with-app-with-config app
       [jetty9-service
        webrouting-service]
       webrouting-plaintext-config
       (let [s                   (get-service app :WebroutingService)
             add-context-handler (partial add-context-handler s)
             body                "Hey there"
             servlet-path        "/hey"
             servlet             (SimpleServlet. body)
             svc                 :puppetlabs.foo/foo-service]
         (add-context-handler svc dev-resources-dir
                              [(reify ServletContextListener
                                 (contextInitialized [this event]
                                   (doto (.addServlet (.getServletContext event) "simple" servlet)
                                     (.addMapping (into-array [servlet-path]))))
                                 (contextDestroyed [this event]))])
         (let [response (http-get (str "http://localhost:8080/foo" servlet-path))]
           (is (= (:status response) 200))
           (is (= (:body response) body)))))))

(deftest ring-handler-test-web-routing
  (testing "ring request over http succeeds with web-routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-config
      (let [s                (get-service app :WebroutingService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            ring-handler     (fn [req] {:status 200 :body body})
            svc              :puppetlabs.foo/foo-service]
        (add-ring-handler svc ring-handler)
        (let [response (http-get "http://localhost:8080/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "ring request over http succeeds with add-ring-handler-to and web-routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-multiserver-config
      (let [s                   (get-service app :WebroutingService)
            add-ring-handler-to (partial add-ring-handler-to s)
            body                "Hi World"
            ring-handler        (fn [req] {:status 200 :body body})
            server-id           :ziggy
            svc                 :puppetlabs.foo/foo-service]
        (add-ring-handler-to svc server-id ring-handler)
        (let [response (http-get "http://localhost:9000/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))

(deftest servlet-test-web-routing
  (testing "request to servlet over http succeeds with web routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-config
      (let [s                   (get-service app :WebroutingService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            servlet             (SimpleServlet. body)
            svc                 :puppetlabs.foo/foo-service]
        (add-servlet-handler svc servlet)
        (let [response (http-get "http://localhost:8080/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet over http succeeds with web routing and add-servlet-handler-to"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-multiserver-config
      (let [s                      (get-service app :WebroutingService)
            add-servlet-handler-to (partial add-servlet-handler-to s)
            body                   "Hey there"
            servlet                (SimpleServlet. body)
            server-id              :ziggy
            svc                    :puppetlabs.foo/foo-service]
        (add-servlet-handler-to svc server-id servlet)
        (let [response (http-get "http://localhost:9000/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet initialized with non-empty params succeeds with web routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-config
      (let [s                   (get-service app :WebroutingService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            init-param-one      "value of init param one"
            init-param-two      "value of init param two"
            servlet             (SimpleServlet. body)
            svc                 :puppetlabs.foo/foo-service]
        (add-servlet-handler svc servlet
                             {"init-param-one" init-param-one
                              "init-param-two" init-param-two})
        (let [response (http-get "http://localhost:8080/foo/init-param-one")]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-one)))
        (let [response (http-get "http://localhost:8080/foo/init-param-two")]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-two)))))))

(deftest war-test-web-routing
  (testing "WAR support with web routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-config
      (let [s               (get-service app :WebroutingService)
            add-war-handler (partial add-war-handler s)
            war             "helloWorld.war"
            svc             :puppetlabs.foo/foo-service]
        (add-war-handler svc (str dev-resources-dir war))
        (let [response (http-get "http://localhost:8080/foo/hello")]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n"))))))

  (testing "WAR support with web routing and add-war-handler-to"
    (with-app-with-config app
      [jetty9-service
       webrouting-service]
      webrouting-plaintext-multiserver-config
      (let [s                  (get-service app :WebroutingService)
            add-war-handler-to (partial add-war-handler-to s)
            war                "helloWorld.war"
            server-id          :ziggy
            svc                :puppetlabs.foo/foo-service]
        (add-war-handler-to svc server-id (str dev-resources-dir war))
        (let [response (http-get "http://localhost:9000/foo/hello")]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n")))))))

(deftest proxy-test-web-routing
  (testing "proxy support with web routing"
    (with-target-and-proxy-servers
      {:target {:host "0.0.0.0"
                :port 9000}
       :proxy  {:host "0.0.0.0"
                :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}}
      (let [response (http-get "http://localhost:9000/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))))

  (testing "proxy support with web-routing and add-proxy-route-to"
    (with-target-and-proxy-servers-variant
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:ziggy {:host "0.0.0.0"
                              :port 10000}
                      :default {:host "0.0.0.0"
                                :port 8085}}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}}
      :ziggy
      (let [response (http-get "http://localhost:9000/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))))

  (testing "basic https proxy support with web-routing and empty options"
    (with-target-and-proxy-servers
      {:target {:host "0.0.0.0"
                :port 9000}
       :proxy  {:host "0.0.0.0"
                :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts {}}
      (let [response (http-get "http://localhost:9000/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!"))))))

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
            [jetty9-service webrouting-service service1]
            webrouting-plaintext-config
            (let [s                (get-service app :WebroutingService)
                  add-ring-handler (partial add-ring-handler s)
                  body             "Hi World"
                  path             "/foo"
                  ring-handler     (fn [req] {:status 200 :body body})
                  svc              :puppetlabs.foo/foo-service]
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
              "Unexpected response to override-webserver-settings! call.")))

    (testing "config override of all SSL settings before webserver starts is
              successful with web-routing and override-webserver-settings-for!"
      (let [override-result (atom nil)
            service1        (tk-services/service
                              [[:WebroutingService override-webserver-settings-for!]]
                              (init [this context]
                                    (reset! override-result
                                            (override-webserver-settings-for!
                                              :ziggy
                                              overrides))
                                    context))]
        (with-test-logging
          (with-app-with-config
            app
            [jetty9-service webrouting-service service1]
            webrouting-plaintext-multiserver-config
            (let [s                   (get-service app :WebroutingService)
                  add-ring-handler-to (partial add-ring-handler-to s)
                  body                "Hi World"
                  path                "/foo"
                  ring-handler        (fn [req] {:status 200 :body body})
                  svc                 :puppetlabs.foo/foo-service]
              (add-ring-handler-to svc :ziggy ring-handler)
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

(deftest endpoints-test-web-routing
  (testing "Retrieve all endpoints with web routing"
    (with-test-logging
      (with-app-with-config app
        [jetty9-service
        webrouting-service]
        webrouting-plaintext-config
        (let [s                        (get-service app :WebroutingService)
              get-registered-endpoints (partial get-registered-endpoints s)
              log-registered-endpoints (partial log-registered-endpoints s)
              add-ring-handler         (partial add-ring-handler s)
              ring-handler             (fn [req] {:status 200 :body "Hi world"})
              svc                      :puppetlabs.foo/foo-service]
          (add-ring-handler svc ring-handler)
          (let [endpoints (get-registered-endpoints)]
            (is (= endpoints #{{:type :ring :endpoint "/foo"}})))
          (log-registered-endpoints)
          (is (logged? #"^\#\{\{:type :ring, :endpoint \"\/foo\"\}\}$"))
          (is (logged? #"^\#\{\{:type :ring, :endpoint \"\/foo\"\}\}$" :info))))))
  (testing "Retrieve all endpoints with web-routing (-for versions)"
    (with-test-logging
      (with-app-with-config app
        [jetty9-service
         webrouting-service]
        webrouting-plaintext-multiserver-config
        (let [s                             (get-service app :WebroutingService)
              get-registered-endpoints-from (partial get-registered-endpoints-from s)
              log-registered-endpoints-from (partial log-registered-endpoints-from s)
              add-ring-handler-to           (partial add-ring-handler-to s)
              ring-handler                  (fn [req] {:status 200 :body "Hi world"})
              server-id                     :ziggy
              svc                           :puppetlabs.foo/foo-service]
          (add-ring-handler-to svc server-id ring-handler)
          (let [endpoints (get-registered-endpoints-from server-id)]
            (is (= endpoints #{{:type :ring :endpoint "/foo"}})))
          (log-registered-endpoints-from server-id)
          (is (logged? #"^\#\{\{:type :ring, :endpoint \"\/foo\"\}\}$"))
          (is (logged? #"^\#\{\{:type :ring, :endpoint \"\/foo\"\}\}$" :info)))))))


