(ns puppetlabs.trapperkeeper.testutils.webrouting.common
  (:require [puppetlabs.http.client.sync :as http-client]))

(defn http-get
  ([url]
   (http-get url {:as :text}))
  ([url options]
   (http-client/get url options)))

(def webrouting-plaintext-config
  {:webserver {:port 8080}
   :web-router-service {:puppetlabs.foo/foo-service "/foo"
                        :puppetlabs.bar/bar-service "/bar"}})

(def webrouting-plaintext-multiserver-config
  {:webserver {:default {:port 8080}
               :ziggy {:port 9000}}
   :web-router-service {:puppetlabs.foo/foo-service "/foo"}})

(def webrouting-plaintext-multiroute-config
  {:webserver {:port 8080}
   :web-router-service {:puppetlabs.foo/foo-service {:default "/foo"
                                                     :ziggy   "/bar"}}})

(def webrouting-plaintext-multiserver-multiroute-config
  {:webserver {:default {:port 8080}
               :ziggy   {:port 9000}}
   :web-router-service {:puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test/test-service
                                                    {:default "/foo"
                                                     :bowie   "/bar"}}})

(def default-options-for-https-client
  {:ssl-cert "./dev-resources/config/jetty/ssl/certs/localhost.pem"
   :ssl-key  "./dev-resources/config/jetty/ssl/private_keys/localhost.pem"
   :ssl-ca-cert "./dev-resources/config/jetty/ssl/certs/ca.pem"
   :as :text})
