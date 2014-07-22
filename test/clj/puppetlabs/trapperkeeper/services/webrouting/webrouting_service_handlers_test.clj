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


