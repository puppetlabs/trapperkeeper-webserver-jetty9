(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-proxy-test
  (:import  [java.net URI])
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [ring.middleware.params :as ring-params]))

(defn query-params-handler
  [req]
  {:status 200
   :body   (str (:query-params req))})

(def app-wrapped
  (ring-params/wrap-params query-params-handler))

(defn proxy-ring-handler
  [req]
  (condp = (:uri req)
    "/hello/world" {:status 200 :body (str "Hello, World!"
                                        ((:headers req) "x-fancy-proxy-header")
                                        ((:headers req) "cookie"))}
    "/hello/earth" {:status 200 :body (str "Hello, Earth!"
                                           ((:headers req) "x-fancy-proxy-header")
                                           ((:headers req) "cookie"))}
    {:status 404 :body "D'oh"}))

(defn redirect-test-handler
  [req]
  (condp = (:uri req)
    "/hello/world" {:status 200 :body "Hello, World!"}
    "/hello/"       {:status 302
                     :headers {"Location" "/hello/world"}
                     :body    ""}
    {:status 404 :body "D'oh"}))

(defn redirect-wrong-host
  [req]
  {:status 302
   :headers {"Location" "http://fakehost:5/hello"}
   :body ""})

(defn redirect-same-host
  [req]
  (condp = (:uri req)
    "/hello/world" {:status 200 :body "Hello, World!"}
    "/hello/"       {:status 302
                     :headers {"Location" "http://localhost:9000/hello/world"}
                     :body    ""}
    {:status 404 :body "D'oh"}))

(defn redirect-different-proxy-path
  [req]
  (condp = (:uri req)
    "/goodbye/world" {:status 200 :body "Hello, World!"}
    "/hello/"        {:status 302
                      :headers {"Location" "http://localhost:9000/goodbye/world"}
                      :body    ""}
    {:status 404 :body "D'oh"}))

(defmacro with-target-and-proxy-servers
  [{:keys [target proxy proxy-config proxy-opts ring-handler]} & body]
  `(with-app-with-config proxy-target-app#
     [jetty9-service]
     {:webserver ~target}
     (let [target-webserver# (get-service proxy-target-app# :WebserverService)]
       (add-ring-handler
         target-webserver#
         ~ring-handler
         "/hello"))
     (with-app-with-config proxy-app#
       [jetty9-service]
       {:webserver ~proxy}
       (let [proxy-webserver# (get-service proxy-app# :WebserverService)]
         (if ~proxy-opts
           (add-proxy-route proxy-webserver# ~proxy-config "/hello-proxy" ~proxy-opts)
           (add-proxy-route proxy-webserver# ~proxy-config "/hello-proxy")))
       ~@body)))

(defmacro with-target-and-proxy-servers-redirect
  [{:keys [target proxy proxy-config proxy-opts ring-handler-target ring-handler-redirect]} & body]
  `(with-app-with-config proxy-target-app#
     [jetty9-service]
     {:webserver ~target}
     (let [target-webserver# (get-service proxy-target-app# :WebserverService)]
       (add-ring-handler
         target-webserver#
         ~ring-handler-target
         "/hello")
       (add-ring-handler
         target-webserver#
         ~ring-handler-redirect
         "/redirect"))
     (with-app-with-config proxy-app#
       [jetty9-service]
       {:webserver ~proxy}
       (let [proxy-webserver# (get-service proxy-app# :WebserverService)]
         (if ~proxy-opts
           (add-proxy-route proxy-webserver# ~proxy-config "/hello-proxy" ~proxy-opts)
           (add-proxy-route proxy-webserver# ~proxy-config "/hello-proxy")))
       ~@body)))

(deftest test-proxy-servlet
  (let [common-ssl-config       {:ssl-cert    "./dev-resources/config/jetty/ssl/certs/localhost.pem"
                                 :ssl-key     "./dev-resources/config/jetty/ssl/private_keys/localhost.pem"
                                 :ssl-ca-cert "./dev-resources/config/jetty/ssl/certs/ca.pem"}
        rewrite-uri-callback-fn (fn [target-uri req]
                                  (URI.
                                    (.getScheme target-uri)
                                    nil
                                    (.getHost target-uri)
                                    (.getPort target-uri)
                                    "/hello/earth"
                                    nil nil))
        callback-fn             (fn [proxy-req req]
                                  (.header proxy-req "x-fancy-proxy-header" "!!!"))]
    (testing "basic proxy support"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic proxy support with add-proxy-route-to"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:foo            {:host "0.0.0.0"
                                         :port 10000}
                        :bar            {:host           "0.0.0.0"
                                         :port           8085
                                         :default-server true}}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:server-id :foo}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "proxy does not explode on a large cookie when properly configured"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000
                        :request-header-max-size 16192}
         :proxy        {:host "0.0.0.0"
                        :port 10000
                        :request-header-max-size 16192}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:request-buffer-size 16192}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world"
                                 {:headers {"Cookie" absurdly-large-cookie}
                                  :as      :text})]
          (is (= (:status response) 200))
          (is (= (:body response) (str "Hello, World!" absurdly-large-cookie))))))

    (testing "basic proxy support with explicit :orig scheme"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:scheme :orig}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic https proxy support (pass-through https config)"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9001/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "https://localhost:10001/hello-proxy/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic https proxy support (pass-through https config) with explicit :orig scheme"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:scheme :orig}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9001/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "https://localhost:10001/hello-proxy/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic https proxy support (pass-through https config via explicit :use-server-config)"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:ssl-config :use-server-config}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9001/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "https://localhost:10001/hello-proxy/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "http->https proxy support with explicit ssl config for proxy"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9000})
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:scheme     :https
                        :ssl-config common-ssl-config}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9000/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "https->http proxy support"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9001}
         :proxy        (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:scheme :http}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "http://localhost:9001/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "https://localhost:10001/hello-proxy/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "basic http proxy support with callback function"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:callback-fn  callback-fn}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!!!!")))))

    (testing "basic https proxy support (pass-through https config) with callback function"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:callback-fn  callback-fn}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9001/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "https://localhost:10001/hello-proxy/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!!!!")))))

    (testing "http->https proxy support with explicit ssl config and callback function for proxy"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9000})
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:scheme       :https
                        :ssl-config   common-ssl-config
                        :callback-fn  callback-fn}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9000/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!!!!")))))

    (testing "basic http proxy support with rewrite uri callback function"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:rewrite-uri-callback-fn rewrite-uri-callback-fn}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "http://localhost:9000/hello/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, Earth!")))))

    (testing "basic https proxy support (pass-through https config) with rewrite uri callback function"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 9001})
         :proxy        (merge common-ssl-config
                              {:ssl-host "0.0.0.0"
                               :ssl-port 10001})
         :proxy-config {:host "localhost"
                        :port 9001
                        :path "/hello"}
         :proxy-opts   {:rewrite-uri-callback-fn rewrite-uri-callback-fn}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9001/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "https://localhost:10001/hello-proxy/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, Earth!")))))

    (testing "http->https proxy support with explicit ssl config and rewrite uri callback function for proxy"
      (with-target-and-proxy-servers
        {:target       (merge common-ssl-config
                              {:ssl-host    "0.0.0.0"
                               :ssl-port    9000})
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:scheme                   :https
                        :ssl-config               common-ssl-config
                        :rewrite-uri-callback-fn  rewrite-uri-callback-fn}
         :ring-handler proxy-ring-handler}
        (let [response (http-get "https://localhost:9000/hello/world" default-options-for-https-client)]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get "http://localhost:10000/hello-proxy/world")]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, Earth!")))))

    (testing "basic proxy support with query parameters"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :ring-handler app-wrapped}
        (let [response (http-get "http://localhost:9000/hello?foo=bar")]
          (is (= (:status response) 200))
          (is (= (:body response) (str {"foo" "bar"}))))
        (let [response (http-get "http://localhost:10000/hello-proxy?foo=bar")]
          (is (= (:status response) 200))
          (is (= (:body response) (str {"foo" "bar"}))))))

    (testing "basic proxy support with multiple query parameters"
      (let [params {"foo"   "bar"
                    "baz"   "lux"
                    "hello" "world"}
            query "?foo=bar&baz=lux&hello=world"]
        (with-target-and-proxy-servers
          {:target       {:host "0.0.0.0"
                          :port 9000}
           :proxy        {:host "0.0.0.0"
                          :port 10000}
           :proxy-config {:host "localhost"
                          :port 9000
                          :path "/hello"}
           :ring-handler app-wrapped}
          (let [response (http-get (str "http://localhost:9000/hello" query))]
            (is (= (:status response) 200))
            (is (= (read-string (:body response)) params)))
          (let [response (http-get (str "http://localhost:10000/hello-proxy" query))]
            (is (= (:status response) 200))
            (is (= (read-string (:body response)) params))))))

    (testing "redirect test with proxy"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:follow-redirects true}
         :ring-handler redirect-test-handler}
        (let [response (http-get (str "http://localhost:9000/hello"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get (str "http://localhost:9000/hello/world"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get (str "http://localhost:10000/hello-proxy"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get (str "http://localhost:10000/hello-proxy/world"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "proxy redirect fails if :follow-redirects not configured properly"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :ring-handler redirect-test-handler}
        (let [response (http-get (str "http://localhost:10000/hello-proxy"))]
          (is (= (:status response) 404)))))

    (testing "proxy-redirect to non-target host fails"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:follow-redirects true}
         :ring-handler redirect-wrong-host}
        (let [response (http-get (str "http://localhost:10000/hello-proxy"))]
          (is (= (:status response) 502)))))

    (testing "proxy redirect to correct host in fully qualified url works"
      (with-target-and-proxy-servers
        {:target {:host "0.0.0.0"
                  :port 9000}
         :proxy  {:host "0.0.0.0"
                  :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:follow-redirects true}
         :ring-handler redirect-same-host}
        (let [response (http-get (str "http://localhost:9000/hello"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get (str "http://localhost:9000/hello/world"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get (str "http://localhost:10000/hello-proxy"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))
        (let [response (http-get (str "http://localhost:10000/hello-proxy/world"))]
          (is (= (:status response) 200))
          (is (= (:body response) "Hello, World!")))))

    (testing "proxy-redirect to non-proxied path on correct host fails"
      (with-target-and-proxy-servers
        {:target       {:host "0.0.0.0"
                        :port 9000}
         :proxy        {:host "0.0.0.0"
                        :port 10000}
         :proxy-config {:host "localhost"
                        :port 9000
                        :path "/hello"}
         :proxy-opts   {:follow-redirects true}
         :ring-handler redirect-different-proxy-path}
        (let [response (http-get (str "http://localhost:10000/hello-proxy"))]
          (is (= (:status response) 404)))))))
