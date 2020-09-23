(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-handlers-test
  (:import (servlet SimpleServlet))
  (:require [clojure.test :refer :all]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.webrouting.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-test-logging]]
            [puppetlabs.trapperkeeper.testutils.webserver :as testutils]))

(use-fixtures :once
  schema-test/validate-schemas
  testutils/assert-clean-shutdown)

(def dev-resources-dir        "./dev-resources/")

(defprotocol TestDummy
  :extend-via-metadata true
  (dummy [this]))

(tk-services/defservice test-dummy
  TestDummy
  []
  (dummy [this]
         "This is a dummy function. Please ignore."))

(def webrouting-plaintext-multiserver-config
  {:webserver {:bar {:port           8080
                     :default-server true}
               :foo {:port 9000}}
   :web-router-service
     {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-handlers-test/test-dummy
       {:route  "/foo"
        :server "foo"}}})

(def webrouting-plaintext-multiroute-config
  {:webserver {:port 8080}
   :web-router-service
     {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-handlers-test/test-dummy
        {:quux "/foo"
         :foo  "/bar"}}})

(deftest add-context-handler-test
  (testing "static content context with web routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-config
      (let [s                   (get-service app :WebroutingService)
            add-context-handler (partial add-context-handler s)
            resource            "logback.xml"
            svc                 (get-service app :TestDummy)]
        (add-context-handler svc dev-resources-dir)
        (let [response (http-get (str "http://localhost:8080/foo/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str dev-resources-dir resource))))))))

  (testing "static content context with multiple routes"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-multiroute-config
      (let [s                   (get-service app :WebroutingService)
            add-context-handler (partial add-context-handler s)
            resource            "logback.xml"
            svc                 (get-service app :TestDummy)]
        (add-context-handler svc dev-resources-dir {:route-id :quux})
        (add-context-handler svc dev-resources-dir {:route-id :foo})
        (let [response (http-get (str "http://localhost:8080/foo/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str dev-resources-dir resource)))))
        (let [response (http-get (str "http://localhost:8080/bar/" resource))]
          (is (= (:status response) 200))
          (is (= (:body response) (slurp (str dev-resources-dir resource)))))))))

(deftest ring-handler-test-web-routing
  (testing "ring request over http succeeds with web-routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-config
      (let [s                (get-service app :WebroutingService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            ring-handler     (fn [req] {:status 200 :body body})
            svc              (get-service app :TestDummy)]
        (add-ring-handler svc ring-handler)
        (let [response (http-get "http://localhost:8080/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "ring request over http succeeds with multiple web-routes"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-multiroute-config
      (let [s                (get-service app :WebroutingService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hi World"
            ring-handler     (fn [req] {:status 200 :body body})
            svc              (get-service app :TestDummy)]
        (add-ring-handler svc ring-handler {:route-id :quux})
        (add-ring-handler svc ring-handler {:route-id :foo})
        (let [response (http-get "http://localhost:8080/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body)))
        (let [response (http-get "http://localhost:8080/bar")]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))

(deftest servlet-test-web-routing
  (testing "request to servlet over http succeeds with web routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-config
      (let [s                   (get-service app :WebroutingService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            servlet             (SimpleServlet. body)
            svc                 (get-service app :TestDummy)]
        (add-servlet-handler svc servlet)
        (let [response (http-get "http://localhost:8080/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body))))))

  (testing "request to servlet over http succeeds with multiple web routes"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-multiroute-config
      (let [s                   (get-service app :WebroutingService)
            add-servlet-handler (partial add-servlet-handler s)
            body                "Hey there"
            servlet             (SimpleServlet. body)
            svc                 (get-service app :TestDummy)]
        (add-servlet-handler svc servlet {:route-id :quux})
        (add-servlet-handler svc servlet {:route-id :foo})
        (let [response (http-get "http://localhost:8080/foo")]
          (is (= (:status response) 200))
          (is (= (:body response) body)))
        (let [response (http-get "http://localhost:8080/bar")]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))

(deftest war-test-web-routing
  (testing "WAR support with web routing"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-config
      (let [s               (get-service app :WebroutingService)
            add-war-handler (partial add-war-handler s)
            war             "helloWorld.war"
            svc             (get-service app :TestDummy)]
        (add-war-handler svc (str dev-resources-dir war))
        (let [response (http-get "http://localhost:8080/foo/hello")]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n"))))))

  (testing "WAR support with multiple web routes"
    (with-app-with-config app
      [jetty9-service
       webrouting-service
       test-dummy]
      webrouting-plaintext-multiroute-config
      (let [s               (get-service app :WebroutingService)
            add-war-handler (partial add-war-handler s)
            war             "helloWorld.war"
            svc             (get-service app :TestDummy)]
        (add-war-handler svc (str dev-resources-dir war) {:route-id :quux})
        (add-war-handler svc (str dev-resources-dir war) {:route-id :foo})
        (let [response (http-get "http://localhost:8080/foo/hello")]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n")))
        (let [response (http-get "http://localhost:8080/bar/hello")]
          (is (= (:status response) 200))
          (is (= (:body response)
                 "<html>\n<head><title>Hello World Servlet</title></head>\n<body>Hello World!!</body>\n</html>\n")))))))

(deftest endpoints-test-web-routing
  (testing (str "get-registered-endpoints and log-registered-endpoints are "
                "successful with the web-routing service")
    (with-test-logging
      (with-app-with-config app
        [jetty9-service
         webrouting-service
         test-dummy]
        webrouting-plaintext-config
        (let [s                        (get-service app :WebroutingService)
              get-registered-endpoints (partial get-registered-endpoints s)
              log-registered-endpoints (partial log-registered-endpoints s)
              add-ring-handler         (partial add-ring-handler s)
              ring-handler             (fn [req] {:status 200 :body "Hi world"})
              svc                      (get-service app :TestDummy)]
          (add-ring-handler svc ring-handler)
          (let [endpoints (get-registered-endpoints)]
            (is (= endpoints {"/foo" [{:type :ring}]})))
          (log-registered-endpoints)
          (is (logged? #"^\{\"\/foo\" \[\{:type :ring}\]\}$"))
          (is (logged? #"^\{\"\/foo\" \[\{:type :ring}\]\}$" :info)))))))
