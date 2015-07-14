(ns puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test
  (:require [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [puppetlabs.kitchensink.testutils.fixtures :as ks-test-fixtures]
            [puppetlabs.trapperkeeper.app :as tk-app]
            [puppetlabs.trapperkeeper.core :as tk-core]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [puppetlabs.trapperkeeper.services.webrouting.webrouting-service
             :refer :all]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
             :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.testutils.webrouting.common :refer :all]
            [puppetlabs.trapperkeeper.testutils.bootstrap
             :refer [with-app-with-empty-config
                     with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.logging
             :refer [with-test-logging]]
            [schema.core :as schema]
            [schema.test :as schema-test]))

(use-fixtures :once
              ks-test-fixtures/with-no-jvm-shutdown-hooks
              schema-test/validate-schemas)

(defprotocol TestService
  (hello [this]))

(defprotocol TestService2)

(defprotocol NotReal
  (dummy [this]))

(tk-services/defservice test-service
  TestService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (let [body "Hello World!"
          ring-handler (fn [req] {:status 200 :body body})]
      (add-ring-handler this ring-handler {:route-id :bert})
      (add-ring-handler this ring-handler {:route-id :bar})
      (add-ring-handler this ring-handler {:route-id :baz})
      (add-ring-handler this ring-handler {:route-id :quux}))
    context)
  (hello [this]
         "This is a dummy function. Please disregard."))

(tk-services/defservice test-service-2
  TestService2
  [[:WebroutingService add-ring-handler]])

(tk-services/defservice not-real
  NotReal
  []
  (dummy [this]
         "This is a dummy function. Please disregard."))

(def webrouting-plaintext-multiserver-multiroute-config
  {:webserver {:bar {:port           8080
                     :default-server true}
               :foo {:port 9000}}
   :web-router-service
     {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test/test-service
       {:bert "/foo"
        :bar  "/bar"
        :baz  {:route "/foo"
               :server "foo"}
        :quux {:route "/bar"
               :server "foo"}}
      :puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test/test-service-2
       "/foo"}})

(def no-default-config
  {:webserver {:bar {:port 8080}
               :foo {:port 9000}}
   :web-router-service
     {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test/test-service-2
       "/foo"}})

(def default-route-config
  {:webserver {:port 8080}
   :web-router-service
     {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test/test-service-2
       {:default "/foo"
        :bar     "/bar"}}})

(deftest webrouting-service-test
  (testing "Other services can successfully use webrouting service"
    (with-app-with-config
      app
      [jetty9-service webrouting-service test-service]
      webrouting-plaintext-multiserver-multiroute-config
      (let [response (http-get "http://localhost:8080/foo/")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello World!")))
      (let [response (http-get "http://localhost:8080/bar/")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello World!")))
      (let [response (http-get "http://localhost:9000/foo/")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello World!")))
      (let [response (http-get "http://localhost:9000/bar/")]
        (is (= (:status response) 200))
        (is (= (:body response) "Hello World!")))))

  (testing "Error occurs when specifying service that does not exist in config file"
    (with-app-with-config
      app
      [jetty9-service webrouting-service not-real]
      webrouting-plaintext-config
      (let [s                (tk-app/get-service app :WebroutingService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hello World!"
            ring-handler     (fn [req] {:status 200 :body body})
            svc              (tk-app/get-service app :NotReal)]
        (is (thrown? IllegalArgumentException (add-ring-handler svc ring-handler))))))

  (testing "Error occurs when endpoints don't have servers and no default is set"
    (with-app-with-config
      app
      [jetty9-service webrouting-service test-service-2]
      no-default-config
      (let [s                (tk-app/get-service app :WebroutingService)
            add-ring-handler (partial add-ring-handler s)
            body             "Hello World!"
            ring-handler     (fn [req] {:status 200 :body body})
            svc              (tk-app/get-service app :TestService2)]
        (is (thrown? IllegalArgumentException (add-ring-handler svc ring-handler))))))

  (testing "Error occurs when not specifying a route-id for a multi-route config"
    (with-app-with-config
      app
      [jetty9-service webrouting-service test-service-2]
      default-route-config
      (let [s                (tk-app/get-service app :WebroutingService)
            svc              (tk-app/get-service app :TestService2)
            add-ring-handler (partial add-ring-handler s)
            ring-handler     (fn [req] {:status 200 :body ""})]
        (is (thrown? IllegalArgumentException (add-ring-handler svc ring-handler))))))

  (testing "Can access route-ids for a service"
    (with-app-with-config
      app
      [jetty9-service webrouting-service test-service test-service-2]
      webrouting-plaintext-multiserver-multiroute-config
      (let [s         (tk-app/get-service app :WebroutingService)
            svc       (tk-app/get-service app :TestService)
            svc2      (tk-app/get-service app :TestService2)
            get-route (partial get-route s)]
        (is (= "/foo" (get-route svc :bert)))
        (is (= "/bar" (get-route svc :bar)))
        (is (= "/foo" (get-route svc :baz)))
        (is (= "/bar" (get-route svc :quux)))
        (is (= "/foo" (get-route svc2)))))))
