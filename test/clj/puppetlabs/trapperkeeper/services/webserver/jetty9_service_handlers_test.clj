(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-handlers-test
  (:import (servlet SimpleServlet)
           (javax.servlet ServletContextListener)
           (java.nio.file Paths Files)
           (java.nio.file.attribute FileAttribute))
  (:require [clojure.test :refer :all]
            [gniazdo.core :as ws-client]
            [puppetlabs.experimental.websockets.client :as ws-session]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-test-logging]]
            [schema.test :as schema-test]
            [clojure.tools.logging :as log]))

(use-fixtures :once schema-test/validate-schemas)

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
            add-context-handler (partial add-context-handler s)
            path                   "/resources"
            resource               "logback.xml"]
        (add-context-handler dev-resources-dir path {:server-id :foo})
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
            servlet             (SimpleServlet. body)
            context-listeners   [(reify ServletContextListener
                                   (contextInitialized [this event]
                                     (doto (.addServlet (.getServletContext event) "simple" servlet)
                                       (.addMapping (into-array [servlet-path]))))
                                   (contextDestroyed [this event]))]]
        (add-context-handler dev-resources-dir path {:context-listeners context-listeners})
        (let [response (http-get (str "http://localhost:8080" path servlet-path))]
          (is (= (:status response) 200))
          (is (= (:body response) body)))))))

(deftest add-context-handler-symlinks-test
  (let [resource  "logback.xml"
        resource-link "logback-link.xml"
        logback (slurp (str dev-resources-dir resource))
        link (Paths/get (str dev-resources-dir resource-link) (into-array java.lang.String []))
        file (Paths/get resource (into-array java.lang.String []))]
    (try
      (Files/createSymbolicLink link file (into-array FileAttribute []))

      (testing "symlinks served when :follow-links is true"
        (with-app-with-config app
          [jetty9-service]
          jetty-plaintext-config
          (let [s (get-service app :WebserverService)
                add-context-handler (partial add-context-handler s)
                path "/resources"]
            (add-context-handler dev-resources-dir path {:follow-links true})
            (let [response (http-get (str "http://localhost:8080" path "/" resource))]
              (is (= (:status response) 200))
              (is (= (:body response) logback)))
            (let [response (http-get (str "http://localhost:8080" path "/" resource-link))]
              (is (= (:status response) 200))
              (is (= (:body response) logback))))))

      (testing "symlinks not served when :follow-links is false"
        (with-app-with-config app
          [jetty9-service]
          jetty-plaintext-config
          (let [s (get-service app :WebserverService)
                add-context-handler (partial add-context-handler s)
                path "/resources"]
            (add-context-handler dev-resources-dir path {:follow-links false})
            (let [response (http-get (str "http://localhost:8080" path "/" resource))]
              (is (= (:status response) 200))
              (is (= (:body response) logback)))
            (let [response (http-get (str "http://localhost:8080" path "/" resource-link))]
              (is (= (:status response) 404))))))

      (finally
        (Files/delete link)))))

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
            add-servlet-handler    (partial add-servlet-handler s)
            body                   "Hey there"
            path                   "/hey"
            servlet                (SimpleServlet. body)]
        (add-servlet-handler servlet path {:server-id :foo})
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
        (add-servlet-handler servlet path {:servlet-init-params {}})
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
                             {:servlet-init-params {"init-param-one" init-param-one
                                                    "init-param-two" init-param-two}})
        (let [response (http-get
                         (str "http://localhost:8080" path "/init-param-one"))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-one)))
        (let [response (http-get
                         (str "http://localhost:8080" path "/init-param-two"))]
          (is (= (:status response) 200))
          (is (= (:body response) init-param-two)))))))

(deftest websocket-test
  (testing "Websocket handlers"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                     (get-service app :WebserverService)
            add-websocket-handler (partial add-websocket-handler s)
            path                  "/test"
            connected              (atom 0)
            server-messages        (atom [])
            server-binary-messages (atom [])
            client-messages        (atom [])
            client-binary-messages (atom [])
            binary-client-message  (promise)
            closed                 (promise)
            handlers               {:on-connect (fn [ws]
                                                  (ws-session/send! ws "Hello client!")
                                                  (swap! connected inc))
                                    :on-text    (fn [ws text]
                                                  (ws-session/send! ws (str "You said: " text))
                                                  (swap! server-messages conj text))
                                    :on-bytes   (fn [ws bytes offset len]
                                                  (let [as-vec (vec bytes)]
                                                    (ws-session/send! ws (byte-array (reverse as-vec)))
                                                    (swap! server-binary-messages conj as-vec)))
                                    :on-error   (fn [ws error]) ;; TODO - Add test for on-error behaviour
                                    :on-close   (fn [ws code reason] (swap! connected dec)
                                                  (deliver closed true))}]
        (add-websocket-handler handlers path)
        (let [socket (ws-client/connect (str "ws://localhost:8080" path)
                                        :on-receive (fn [text] (swap! client-messages conj text))
                                        :on-binary  (fn [bytes offset len]
                                                      (let [as-vec (vec bytes)]
                                                        (swap! client-binary-messages conj as-vec)
                                                        (deliver binary-client-message true))))]
          (ws-client/send-msg socket "Hello websocket handler")
          (ws-client/send-msg socket "You look dandy")
          (ws-client/send-msg socket (byte-array [2 1 2 3 3]))
          (deref binary-client-message)
          (is (= @connected 1))
          (ws-client/close socket)
          (deref closed)
          (is (= @connected 0))
          (is (= @server-binary-messages [[2 1 2 3 3]]))
          (is (= @client-binary-messages [[3 3 2 1 2]]))
          (is (= @client-messages ["Hello client!"
                                   "You said: Hello websocket handler"
                                   "You said: You look dandy"]))
          (is (= @server-messages ["Hello websocket handler"
                                   "You look dandy"])))))))

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
            add-war-handler    (partial add-war-handler s)
            path               "/test"
            war                "helloWorld.war"]
        (add-war-handler (str dev-resources-dir war) path {:server-id :foo})
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
            path-war                 "/bar"
            path-proxy               "/baz"
            path-websocket           "/quux"
            get-registered-endpoints (partial get-registered-endpoints s)
            add-context-handler      (partial add-context-handler s)
            add-ring-handler         (partial add-ring-handler s)
            add-servlet-handler      (partial add-servlet-handler s)
            add-war-handler          (partial add-war-handler s)
            add-proxy-route          (partial add-proxy-route s)
            add-websocket-handler    (partial add-websocket-handler s)
            ring-handler             (fn [req] {:status 200 :body "Hi world"})
            body                     "This is a test"
            servlet                  (SimpleServlet. body)
            context-listeners        [(reify ServletContextListener
                                        (contextInitialized [this event]
                                          (doto (.addServlet (.getServletContext event) "simple" servlet)
                                            (.addMapping (into-array [path-servlet]))))
                                        (contextDestroyed [this event]))]
            war                      "helloWorld.war"
            websocket-handlers       {:on-connect (fn [ws])}
            target                   {:host "0.0.0.0"
                                      :port 9000
                                      :path "/ernie"}
            target2                  {:host "localhost"
                                      :port 10000
                                      :path "/kermit"}]
        (add-context-handler dev-resources-dir path-context)
        (add-context-handler dev-resources-dir path-context2 {:context-listeners []})
        (add-context-handler dev-resources-dir path-context3 {:context-listeners context-listeners})
        (add-ring-handler ring-handler path-ring)
        (add-servlet-handler servlet path-servlet)
        (add-war-handler (str dev-resources-dir war) path-war)
        (add-proxy-route target path-proxy)
        (add-proxy-route target2 path-proxy {})
        (add-websocket-handler websocket-handlers path-websocket)
        (let [endpoints (get-registered-endpoints)]
          (is (= endpoints {"/ernie" [{:type :context :base-path dev-resources-dir
                                       :context-listeners []}]
                            "/gonzo" [{:type :context :base-path dev-resources-dir
                                       :context-listeners []}]
                            "/goblinking" [{:type :context :base-path dev-resources-dir
                                            :context-listeners context-listeners}]
                            "/bert" [{:type :ring}]
                            "/foo" [{:type :servlet :servlet (type servlet)}]
                            "/bar" [{:type :war :war-path (str dev-resources-dir war)}]
                            "/baz" [{:type :proxy :target-host "0.0.0.0" :target-port 9000
                                     :target-path "/ernie"}
                                    {:type :proxy :target-host "localhost" :target-port 10000
                                     :target-path "/kermit"}]
                            "/quux" [{:type :websocket}]}))))))

  (testing "Log endpoints"
    (with-test-logging
      (with-app-with-config app
        [jetty9-service]
        jetty-multiserver-plaintext-config
        (let [s                        (get-service app :WebserverService)
              log-registered-endpoints (partial log-registered-endpoints s)
              add-ring-handler         (partial add-ring-handler s)
              ring-handler             (fn [req] {:status 200 :body "Hi world"})
              path-ring                "/bert"]
          (add-ring-handler ring-handler path-ring)
          (log-registered-endpoints)
          (is (logged? #"^\{\"\/bert\" \[\{:type :ring\}\]\}$"))
          (is (logged? #"^\{\"\/bert\" \[\{:type :ring\}\]\}$" :info)))))))

(deftest trailing-slash-redirect-test
  (testing "redirects when no trailing slash is present are disabled by default"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                (get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)
            ring-handler     (fn [req] {:status 200 :body "Hi world"})
            path             "/hello"]
        (add-ring-handler ring-handler path)
        (let [response (http-get "http://localhost:8080/hello" {:as :text
                                                                :follow-redirects false})]
          (is (= (:status response) 200))
          (is (= (:body response) "Hi world"))
          (is (= (get-in response [:opts :url]) "http://localhost:8080/hello"))))))

  (testing "redirects when no trailing slash is present and option is enabled"
    (with-app-with-config app
      [jetty9-service]
      jetty-plaintext-config
      (let [s                (get-service app :WebserverService)
            add-ring-handler (partial add-ring-handler s)
            ring-handler     (fn [req] {:status 200 :body "Hi world"})
            path             "/hello"]
        (add-ring-handler ring-handler path {:redirect-if-no-trailing-slash true})
        (let [response (http-get "http://localhost:8080/hello" {:as :text
                                                                :follow-redirects false})]
          (is (= (:status response) 302))
          (is (= (get-in response [:headers "location"]) "http://localhost:8080/hello/"))
          (is (= (get-in response [:opts :url]) "http://localhost:8080/hello")))))))
