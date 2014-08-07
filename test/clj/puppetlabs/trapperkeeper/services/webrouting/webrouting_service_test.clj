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
            [schema.core :as schema]))

(use-fixtures :once ks-test-fixtures/with-no-jvm-shutdown-hooks)

(defprotocol TestService
  (hello [this]))

(defprotocol NotReal
  (dummy [this]))

(tk-services/defservice test-service
  TestService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (let [svc (get-service this :TestService)
          body "Hello World!"
          ring-handler (fn [req] {:status 200 :body body})]
      (add-ring-handler svc ring-handler)
      (add-ring-handler svc ring-handler {:route-id :bar})
      (add-ring-handler svc ring-handler {:server-id :foo})
      (add-ring-handler svc ring-handler {:server-id :foo
                                          :route-id :bar}))
    context)
  (hello [this]
         "This is a dummy function. Please disregard."))

(tk-services/defservice not-real
  NotReal
  []
  (dummy [this]
         "This is a dummy function. Please disregard."))

(def webrouting-plaintext-multiserver-multiroute-config
  {:webserver {:default {:port 8080}
               :foo   {:port 9000}}
   :web-router-service
     {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test/test-service
       {:default "/foo"
        :bar   "/bar"}}})

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
      (let [s (tk-app/get-service app :WebroutingService)
            add-ring-handler (partial add-ring-handler s)
            body "Hello World!"
            ring-handler (fn [req] {:status 200 :body body})
            svc (tk-app/get-service app :NotReal)]
        (is (thrown? IllegalArgumentException (add-ring-handler svc ring-handler)))))))

