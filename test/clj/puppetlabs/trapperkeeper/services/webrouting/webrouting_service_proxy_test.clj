(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-proxy-test
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.testutils.webrouting.common :refer :all]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]))

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
