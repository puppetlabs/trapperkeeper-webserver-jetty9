(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-handlers-test
  (:import (servlet SimpleServlet)
           (javax.servlet ServletContextListener))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-test-logging]]))

(def dev-resources-dir        "./dev-resources/")

(deftest static-content-test
  (testing "static content context"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                   (get-service app :WebserverService)
            add-context-handler (partial add-context-handler s)
            path                "/resources"
            resource            "logback.xml"]
        (add-context-handler dev-resources-dir path)
        (let [response (http-get (str "http://localhost:8080" path "/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str dev-resources-dir resource))))))))

  (testing "static content context with add-context-handler-to"
    (with-app-with-config app
      [jetty9-service]
      jetty-multiserver-plaintext-config
      (let [s                      (get-service app :WebserverService)
            add-context-handler-to (partial add-context-handler-to s)
            path                   "/resources"
            resource               "logback.xml"]
        (add-context-handler-to dev-resources-dir path :ziggy)
        (let [response (http-get (str "http://localhost:8085" path "/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str dev-resources-dir resource))))))))

  (testing "customization of static content context"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                   (get-service app :WebserverService)
            add-context-handler (partial add-context-handler s)
            path                "/resources"
            body                "Hey there"
            servlet-path        "/hey"
            servlet             (SimpleServlet. body)]
        (add-context-handler dev-resources-dir path
                             [(reify ServletContextListener
                                (contextInitialized [this event]
                                  (doto (.addServlet (.getServletContext event) "simple" servlet)
                                    (.addMapping (into-array [servlet-path]))))
                                (contextDestroyed [this event]))])
        (let [response (http-get (str "http://localhost:8080" path servlet-path))]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))


(deftest servlet-test
  (testing "request to servlet over http succeeds"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                   (get-service app :WebserverService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            path                "/hey"
            servlet             (SimpleServlet. body)]
        (add-servlet-handler servlet path)
        (let [response (http-get
                         (str "http://localhost:8080" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet over http succeeds with add-servlet-handler-to"
    (with-app-with-config app
      [jetty9-service]
      jetty-multiserver-plaintext-config
      (let [s                      (get-service app :WebserverService)
            add-servlet-handler-to (partial add-servlet-handler-to s)
            body                   "Hey there"
            path                   "/hey"
            servlet                (SimpleServlet. body)]
        (add-servlet-handler-to servlet path :ziggy)
        (let [response (http-get
                         (str "http://localhost:8085" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet initialized with empty param succeeds"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                   (get-service app :WebserverService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            path                "/hey"
            servlet             (SimpleServlet. body)]
        (add-servlet-handler servlet path {})
        (let [response (http-get (str "http://localhost:8080" path))]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet initialized with non-empty params succeeds"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
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
        (let [response (http-get
                         (str "http://localhost:8080" path "/init-param-one"))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-one)))
        (let [response (http-get
                         (str "http://localhost:8080" path "/init-param-two"))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-two)))))))

(deftest war-test
  (testing "WAR support"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s               (get-service app :WebserverService)
            add-war-handler (partial add-war-handler s)
            path            "/test"
            war             "helloWorld.war"]
        (add-war-handler (str dev-resources-dir war) path)
        (let [response (http-get (str "http://localhost:8080" path "/hello"))]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n"))))))

  (testing "WAR support with add-war-handler-to"
    (with-app-with-config app
      [jetty9-service]
      jetty-multiserver-plaintext-config
      (let [s                  (get-service app :WebserverService)
            add-war-handler-to (partial add-war-handler-to s)
            path               "/test"
            war                "helloWorld.war"]
        (add-war-handler-to (str dev-resources-dir war) path :ziggy)
        (let [response (http-get (str "http://localhost:8085" path "/hello"))]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n")))))))

(deftest endpoints-test
  (testing "Retrieve all endpoints"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                        (get-service app :WebserverService)
            path-context             "/ernie"
            path-context2            "/gonzo"
            path-context3            "/goblinking"
            path-ring                "/bert"
            path-servlet             "/foo"
            path-servlet2            "/misspiggy"
            path-war                 "/bar"
            path-proxy               "/baz"
            get-registered-endpoints (partial get-registered-endpoints s)
            add-context-handler      (partial add-context-handler s)
            add-ring-handler         (partial add-ring-handler s)
            add-servlet-handler      (partial add-servlet-handler s)
            add-war-handler          (partial add-war-handler s)
            add-proxy-route          (partial add-proxy-route s)
            ring-handler             (fn [req] {:status 200 :body "Hi world"})
            body                     "This is a test"
            servlet                  (SimpleServlet. body)
            context-listeners        [(reify ServletContextListener
                                        (contextInitialized [this event]
                                          (doto (.addServlet (.getServletContext event) "simple" servlet)
                                            (.addMapping (into-array [path-servlet]))))
                                        (contextDestroyed [this event]))]
            war                      "helloWorld.war"
            target                   {:host "0.0.0.0"
                                      :port 9000
                                      :path "/ernie"}
            target2                  {:host "localhost"
                                      :port 10000
                                      :path "/kermit"}]
        (add-context-handler dev-resources-dir path-context)
        (add-context-handler dev-resources-dir path-context2 [])
        (add-context-handler dev-resources-dir path-context3 context-listeners)
        (add-ring-handler ring-handler path-ring)
        (add-servlet-handler servlet path-servlet)
        (add-servlet-handler servlet path-servlet2 {})
        (add-war-handler (str dev-resources-dir war) path-war)
        (add-proxy-route target path-proxy)
        (add-proxy-route target2 path-proxy {})
        (let [endpoints (get-registered-endpoints)]
          (is (= endpoints #{{:type :context :base-path dev-resources-dir
                              :endpoint path-context}
                             {:type :context :base-path dev-resources-dir
                              :context-listeners [] :endpoint path-context2}
                             {:type :context :base-path dev-resources-dir
                              :context-listeners context-listeners :endpoint path-context3}
                             {:type :ring :endpoint path-ring}
                             {:type :servlet :servlet (type servlet) :endpoint path-servlet}
                             {:type :servlet :servlet (type servlet) :endpoint path-servlet2}
                             {:type :war :war-path (str dev-resources-dir war) :endpoint path-war}
                             {:type :proxy :target-host "0.0.0.0" :target-port 9000
                              :endpoint path-proxy :target-path "/ernie"}
                             {:type :proxy :target-host "localhost" :target-port 10000
                              :endpoint path-proxy :target-path "/kermit"}}))))))

  (testing "Log endpoints"
    (with-test-logging
      (with-app-with-config app
        [jetty9-service]
        jetty-plaintext-config
        (let [s                        (get-service app :WebserverService)
              log-registered-endpoints (partial log-registered-endpoints s)
              add-ring-handler         (partial add-ring-handler s)
              ring-handler             (fn [req] {:status 200 :body "Hi world"})
              path-ring                "/bert"]
          (add-ring-handler ring-handler path-ring)
          (log-registered-endpoints)
          (is (logged? #"^\#\{\{:type :ring, :endpoint \"\/bert\"\}\}$"))
          (is (logged? #"^\#\{\{:type :ring, :endpoint \"\/bert\"\}\}$" :info)))))))