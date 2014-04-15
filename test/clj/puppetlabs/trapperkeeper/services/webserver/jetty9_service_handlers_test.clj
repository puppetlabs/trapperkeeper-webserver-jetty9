(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-handlers-test
  (:import (servlet SimpleServlet)
           (javax.servlet ServletContextListener))
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]))

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
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n")))))))
