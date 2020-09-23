(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service-proxy-test
  (:import [java.net URI])
  (:require [clojure.test :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer :all]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer :all]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :refer [service]]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [ring.middleware.params :as ring-params]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.testutils.webserver :as testutils]))

(use-fixtures :once
  schema-test/validate-schemas
  testutils/assert-clean-shutdown)

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

(defn ring-handler-with-sleep
  "Makes a ring handler which sleeps for a set amount of milliseconds before
  responding. This is used to test timeout settings."
  [sleep-time]
  (fn [_]
    (Thread/sleep sleep-time)
    {:status 200
     :body   "This should have timed out."}))

(defprotocol TkProxyService
  :extend-via-metadata true)

(defn proxy-service
  [proxy-config proxy-opts proxy-path]
  (service TkProxyService
    [[:WebserverService add-proxy-route]]
    (init [this context]
      (if proxy-opts
        (add-proxy-route proxy-config
                         proxy-path
                         proxy-opts)
        (add-proxy-route proxy-config
                         proxy-path))
      context)))

(defmacro with-target-and-proxy-servers
  [{:keys [target proxy proxy-config proxy-opts ring-handler
           register-proxy-route-before-server-start?]} & body]
  (let [proxy-path "/hello-proxy"]
    `(with-app-with-config proxy-target-app#
      [jetty9-service]
      {:webserver ~target}
      (let [target-webserver# (get-service proxy-target-app# :WebserverService)]
        (add-ring-handler
          target-webserver#
          ~ring-handler
          "/hello")
        (add-ring-handler
          target-webserver#
          ~ring-handler
          "/goodbye"))
      (if ~register-proxy-route-before-server-start?
        (let [proxy-service# (proxy-service ~proxy-config
                                            ~proxy-opts
                                            ~proxy-path)]
          (with-app-with-config proxy-app#
            [jetty9-service proxy-service#]
            {:webserver ~proxy}
            ~@body))
        (with-app-with-config proxy-app#
          [jetty9-service]
          {:webserver ~proxy}
          (let [proxy-webserver# (get-service proxy-app# :WebserverService)]
            (if ~proxy-opts
              (add-proxy-route proxy-webserver#
                               ~proxy-config
                               ~proxy-path
                               ~proxy-opts)
              (add-proxy-route proxy-webserver#
                               ~proxy-config
                               ~proxy-path)))
          ~@body)))))

(def common-ssl-config
  {:ssl-cert    "./dev-resources/config/jetty/ssl/certs/localhost.pem"
   :ssl-key     "./dev-resources/config/jetty/ssl/private_keys/localhost.pem"
   :ssl-ca-cert "./dev-resources/config/jetty/ssl/certs/ca.pem"})

(defn rewrite-uri-callback-fn
  [target-uri req]
  (URI.
    (.getScheme target-uri)
    nil
    (.getHost target-uri)
    (.getPort target-uri)
    "/hello/earth"
    nil nil))

(defn callback-fn
  [proxy-req req]
  (.header proxy-req "x-fancy-proxy-header" "!!!"))

(defn failure-callback-fn
  [req resp proxy-resp failure]
  (.setStatus resp 500)
  (.print (.getWriter resp) (str "Proxying failed: " (.getMessage failure))))

(deftest test-basic-proxy-support
  (testing "basic proxy support when proxy handler registered after server start"
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

  (testing "basic proxy support when proxy handler registered before server start"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :ring-handler proxy-ring-handler
       :register-proxy-route-before-server-start? true}
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
       :proxy        {:foo {:host "0.0.0.0"
                            :port 10000}
                      :bar {:host           "0.0.0.0"
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
        (is (= (:body response) "Hello, World!"))))))

(deftest proxy-large-cookie
  (testing "proxy does not explode on a large cookie when properly configured"
    (with-target-and-proxy-servers
      {:target       {:host                    "0.0.0.0"
                      :port                    9000
                      :request-header-max-size 16192}
       :proxy        {:host                    "0.0.0.0"
                      :port                    10000
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
        (is (= (:body response) (str "Hello, World!" absurdly-large-cookie)))))))

(deftest proxy-with-orig-scheme
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

  (testing "basic proxy support with explicit \"orig\" scheme as string"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:scheme "orig"}
       :ring-handler proxy-ring-handler}
      (let [response (http-get "http://localhost:9000/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!"))))))

(deftest basic-https-proxy
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
                            {:ssl-host "0.0.0.0"
                             :ssl-port 9001})
       :proxy        (merge common-ssl-config
                            {:ssl-host "0.0.0.0"
                             :ssl-port 10001})
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
                            {:ssl-host "0.0.0.0"
                             :ssl-port 9001})
       :proxy        (merge common-ssl-config
                            {:ssl-host "0.0.0.0"
                             :ssl-port 10001})
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
        (is (= (:body response) "Hello, World!"))))))

(deftest http-https-proxy-support
  (testing "http->https proxy support with explicit ssl config for proxy"
    (with-target-and-proxy-servers
      {:target       (merge common-ssl-config
                            {:ssl-host "0.0.0.0"
                             :ssl-port 9000})
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

  (testing "http->https proxy support with scheme as string value"
    (with-target-and-proxy-servers
      {:target       (merge common-ssl-config
                            {:ssl-host "0.0.0.0"
                             :ssl-port 9000})
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:scheme     "https"
                      :ssl-config common-ssl-config}
       :ring-handler proxy-ring-handler}
      (let [response (http-get "https://localhost:9000/hello/world" default-options-for-https-client)]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!"))))))

(deftest https-http-proxy-support

  (testing "https->http proxy support"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9001}
       :proxy        (merge common-ssl-config
                            {:ssl-host "0.0.0.0"
                             :ssl-port 10001})
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

  (testing "https->http proxy support with scheme as string"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9001}
       :proxy        (merge common-ssl-config
                            {:ssl-host "0.0.0.0"
                             :ssl-port 10001})
       :proxy-config {:host "localhost"
                      :port 9001
                      :path "/hello"}
       :proxy-opts   {:scheme "http"}
       :ring-handler proxy-ring-handler}
      (let [response (http-get "http://localhost:9001/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "https://localhost:10001/hello-proxy/world" default-options-for-https-client)]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!"))))))

(deftest proxy-support-with-callback
  (testing "basic http proxy support with callback function"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:callback-fn callback-fn}
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
       :proxy-opts   {:callback-fn callback-fn}
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
                            {:ssl-host "0.0.0.0"
                             :ssl-port 9000})
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:scheme      :https
                      :ssl-config  common-ssl-config
                      :callback-fn callback-fn}
       :ring-handler proxy-ring-handler}
      (let [response (http-get "https://localhost:9000/hello/world" default-options-for-https-client)]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!!!!"))))))

(deftest proxy-with-rewrite-uri-callback
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
                            {:ssl-host "0.0.0.0"
                             :ssl-port 9000})
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:scheme                  :https
                      :ssl-config              common-ssl-config
                      :rewrite-uri-callback-fn rewrite-uri-callback-fn}
       :ring-handler proxy-ring-handler}
      (let [response (http-get "https://localhost:9000/hello/world" default-options-for-https-client)]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, Earth!"))))))

(deftest proxy-with-query-params
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

  (testing "basic proxy support with url encodable query parameters"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :ring-handler app-wrapped}
      (let [response (http-get "http://localhost:9000/hello?hello%5B%5D=hello%20world")]
        (is (= (:status response) 200))
        (is (= (:body response) (str {"hello[]" "hello world"}))))
      (let [response (http-get "http://localhost:10000/hello-proxy?hello%5B%5D=hello%20world")]
        (is (= (:status response) 200))
        (is (= (:body response) (str {"hello[]" "hello world"}))))))

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
          (is (= (read-string (:body response)) params)))))))

(deftest proxy-with-redirect
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
      (let [response (http-get "http://localhost:9000/hello/")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:9000/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
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
      (let [response (http-get "http://localhost:10000/hello-proxy")]
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
      (let [response (http-get "http://localhost:10000/hello-proxy")]
        (is (= (:status response) 502)))))

  (testing "proxy redirect to correct host in fully qualified url works"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:follow-redirects true}
       :ring-handler redirect-same-host}
      (let [response (http-get "http://localhost:9000/hello/")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:9000/hello/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))))

  (testing "proxy-redirect to non-proxied path on correct host succeeds"
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
      (let [response (http-get "http://localhost:9000/hello/")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!")))
      (let [response (http-get "http://localhost:10000/hello-proxy")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello, World!"))))))

(deftest proxy-failure
  (testing "proxying failure - default handler"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 123456789                       ; illegal port number
                      :path "/hello"}
       :ring-handler proxy-ring-handler}
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 502))
        (is (= (:body response) "")))))

  (testing "proxying failure - custom handler"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 123456789                       ; illegal port number
                      :path "/hello"}
       :proxy-opts   {:failure-callback-fn failure-callback-fn}
       :ring-handler proxy-ring-handler}
      (let [response (http-get "http://localhost:10000/hello-proxy/world")]
        (is (= (:status response) 500))
        (is (= (:body response) "Proxying failed: port out of range:123456789")))))

  (testing "setting an idle timeout fails properly"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:idle-timeout 1}
       :ring-handler (ring-handler-with-sleep 1250)}
      (let [response (http-get (str "http://localhost:10000/hello-proxy"))]
        (is (= 504 (:status response))))))

  (testing  "a response before a timeout occurs succeeds"
    (with-target-and-proxy-servers
      {:target       {:host "0.0.0.0"
                      :port 9000}
       :proxy        {:host "0.0.0.0"
                      :port 10000}
       :proxy-config {:host "localhost"
                      :port 9000
                      :path "/hello"}
       :proxy-opts   {:idle-timeout 1}
       :ring-handler (ring-handler-with-sleep 100)}
      (let [response (http-get (str "http://localhost:10000/hello-proxy"))]
        (is (= 200 (:status response)))))))

; Modified from proxy-ring-handler
(defn count-ring-handler
  "Increments counter if the endpoint is /goodbye"
  [counter req]
  (condp = (:uri req)
    "/hello/world/" {:status 200 :body (str "Hello, World!")}
    "/hello/" {:status 200 :body (str "Hello, You!")}
    "/goodbye/" {:status 200 :body (str "Goodbye! Count: " (swap! counter inc))}
    {:status 404 :body "count-ring-handler couldn't find the route"}))

(deftest test-path-traversal-attacks
  (testing "proxies aren't vulnerable to basic path traversal attacks"
    ; goodbye-counter is used to make sure that no requests to the proxy
    ; endpoint, /hello-proxy, end up hitting /goodbye, as they should only be
    ; able to reach /hello
    (let [goodbye-counter (atom 0)
          hello-goodbye-count-ring-handler (partial count-ring-handler goodbye-counter)
          ; Should not succeed in hitting the goodbye endpoint
          bad-proxy-requests [; Encodings of '../'
                              "https://localhost:10001/hello-proxy/../goodbye/"
                              "https://localhost:10001/hello-proxy/%2e%2e%2fgoodbye/"
                              "https://localhost:10001/hello-proxy/%2e%2e/goodbye/"
                              "https://localhost:10001/hello-proxy/..%2fgoodbye/"
                              ; Encodings of '../../'
                              "https://localhost:10001/hello-proxy/world/../../goodbye/"
                              "https://localhost:10001/hello-proxy/world/%2e%2e%2f%2e%2e%2fgoodbye/"
                              "https://localhost:10001/hello-proxy/world/%2e%2e/%2e%2e/goodbye/"
                              "https://localhost:10001/hello-proxy/world/..%2f..%2fgoodbye/"]]
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
         :ring-handler hello-goodbye-count-ring-handler}
        (testing "proxy is up and running"
          (let [response (http-get "https://localhost:10001/hello-proxy/" default-options-for-https-client)]
            (is (= 200 (:status response)))
            (is (= "Hello, You!" (:body response))))
          (let [response (http-get "https://localhost:10001/hello-proxy/world/" default-options-for-https-client)]
            (is (= 200 (:status response)))
            (is (= "Hello, World!" (:body response)))))
        (testing "non-proxied endpoint doesn't see any traffic"
          (doall (for [bad-request bad-proxy-requests]
                   (let [response (http-get bad-request default-options-for-https-client)]
                     (is (= 404 (:status response))))))
          ; Counter should still be at 0
          (is (= 0 (deref goodbye-counter))))
        (testing "counter is working correctly"
          ; A valid request to bump the counter
          (http-get "https://localhost:9001/goodbye/" default-options-for-https-client)
          (is (= 1 (deref goodbye-counter))))))))
