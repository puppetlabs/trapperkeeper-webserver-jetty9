(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-handlers-test
  (:import (servlet SimpleServlet)
           (javax.servlet ServletContextListener))
  (:require [clojure.test :refer :all]
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

(def dev-resources-dir        "./dev-resources/")

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
        (let [response (http-get
                         (str "http://localhost:8080/foo"))]
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
        (let [response (http-get
                         (str "http://localhost:8080/foo/init-param-one"))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-one)))
        (let [response (http-get
                         (str "http://localhost:8080/foo/init-param-two"))]
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
        (let [response (http-get (str "http://localhost:8080/foo/hello"))]
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
        (is (= (:body response) "Hello, World!")))))
  )
