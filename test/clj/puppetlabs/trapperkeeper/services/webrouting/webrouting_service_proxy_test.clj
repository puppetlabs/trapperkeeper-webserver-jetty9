(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-proxy-test
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.testutils.webrouting.common :refer :all]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.services :as tk-services]))

(defprotocol DummyService1
  (dummy1 [this]))

(defprotocol DummyService2
  (dummy2 [this]))

(tk-services/defservice dummy1
  DummyService1
  []
  (dummy1 [this]
         "This is a dummy function. Please ignore."))

(tk-services/defservice dummy2
  DummyService2
  []
  (dummy2 [this]
         "This is a dummy function. Please ignore."))

(defmacro with-target-and-proxy-servers
  [{:keys [target proxy proxy-config proxy-opts]} & body]
  `(with-app-with-config proxy-target-app#
     [jetty9-service
      webrouting-service
      dummy1]
     {:webserver ~target
      :web-router-service {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-proxy-test/dummy1 "/hello"}}
     (let [target-webserver# (get-service proxy-target-app# :WebroutingService)
           svc#              (get-service proxy-target-app# :DummyService1)]
       (add-ring-handler
         target-webserver#
         svc#
         (fn [req#]
           (if (= "/hello/world" (:uri req#))
             {:status 200 :body (str "Hello, World!"
                                     ((:headers req#) "x-fancy-proxy-header"))}
             {:status 404 :body "D'oh"}))))
     (with-app-with-config proxy-app#
       [jetty9-service
        webrouting-service
        dummy2]
       {:webserver ~proxy
        :web-router-service {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-proxy-test/dummy2
                                                         {:default "/hello-proxy"
                                                          :foo   "/goodbye-proxy"}}}
       (let [proxy-webserver# (get-service proxy-app# :WebroutingService)
             svc#             (get-service proxy-app# :DummyService2)]
         (if ~proxy-opts
           (add-proxy-route proxy-webserver# svc#  ~proxy-config ~proxy-opts)
           (add-proxy-route proxy-webserver# svc#  ~proxy-config)))
       ~@body)))

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

  (testing "basic https proxy support with multiple web routes"
    (with-target-and-proxy-servers
      {:target {:host "0.0.0.0"
                :port 9000}
       :proxy  {:host "0.0.0.0"
                :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts {:route-id :foo}}
      (let [response (http-get "http://localhost:9000/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/goodbye-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!"))))))
