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

(tk-services/defservice test-service
  TestService
  [[:WebroutingService add-ring-handler]]
  (init [this context]
    (let [service (get-service this :TestService)
          svc (keyword (tk-services/service-symbol service))
          body "Hello World!"
          ring-handler (fn [req] {:status 200 :body body})]
      (add-ring-handler svc ring-handler)
      (add-ring-handler svc ring-handler {:route-id :bowie})
      (add-ring-handler svc ring-handler {:server-id :ziggy})
      (add-ring-handler svc ring-handler {:server-id :ziggy
                                          :route-id :bowie}))
    context)
  (hello [this]
         "This is a dummy function. Please disregard."))

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
      [jetty9-service webrouting-service]
      webrouting-plaintext-config
      (let [s (tk-app/get-service app :WebroutingService)
            add-ring-handler (partial add-ring-handler s)
            body "Hello World!"
            ring-handler (fn [req] {:status 200 :body body})
            svc :this-isn't-real]
        (is (thrown? clojure.lang.ExceptionInfo (add-ring-handler svc ring-handler)))))))

