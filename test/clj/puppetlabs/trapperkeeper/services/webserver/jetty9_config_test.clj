(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :refer :all]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-log-output logs-matching]]))

(deftest http-configuration
  (testing "configure-web-server should set client-auth to a value of :need
            if not specified in options"
    (let [config (configure-web-server {:port 8080})]
      (is (= (get config :client-auth) :need))))

  (testing "configure-web-server should convert client-auth string to
            appropriate corresponding keyword value in configure-web-server
            options"
    (let [config (configure-web-server {:port 8080 :client-auth "need"})]
      (is (= (get config :client-auth) :need)))
    (let [config (configure-web-server {:port 8080 :client-auth "want"})]
      (is (= (get config :client-auth) :want)))
    (let [config (configure-web-server {:port 8080 :client-auth "none"})]
      (is (= (get config :client-auth) :none))))

  (testing "configure-web-server should throw IllegalArgumentException if an
            unsupported value is specified for the client-auth option"
    (is (thrown-with-msg? java.lang.IllegalArgumentException
                          #"Unexpected value found for client auth config option: bogus.  Expected need, want, or none."
                          (configure-web-server
                            {:port 8080 :client-auth "bogus"}))))

  (let [old-config {:keystore       "/some/path"
                    :key-password   "pw"
                    :port           8080
                    :truststore     "/some/other/path"
                    :trust-password "otherpw"}]
    (testing "should not muck with keystore/truststore settings if PEM-based SSL settings are not provided"
      (let [processed-config (configure-web-server old-config)]
        (is (= old-config
               (select-keys processed-config [:keystore
                                              :key-password
                                              :port
                                              :truststore
                                              :trust-password])))))

    (testing "should fail if some but not all of the PEM-based SSL settings are found"
      (let [partial-pem-config (merge old-config {:ssl-ca-cert "/some/path"})]
        (is (thrown-with-msg? java.lang.IllegalArgumentException
              #"If configuring SSL from PEM files, you must provide all of the following options"
              (configure-web-server partial-pem-config)))))

    (let [pem-config (merge old-config
                            {:ssl-key     (resource "config/jetty/ssl/private_keys/localhost.pem")
                             :ssl-cert    (resource "config/jetty/ssl/certs/localhost.pem")
                             :ssl-ca-cert (resource "config/jetty/ssl/certs/ca.pem")})]
      (testing "should warn if both keystore-based and PEM-based SSL settings are found"
        (with-log-output logs
          (configure-web-server pem-config)
          (is (= 1 (count (logs-matching #"Found settings for both keystore-based and PEM-based SSL" @logs))))))

      (testing "should prefer PEM-based SSL settings, override old keystore settings
                  with instances of java.security.KeyStore, and remove PEM settings
                  from final jetty config hash"
        (let [processed-config (configure-web-server pem-config)]
          (is (instance? java.security.KeyStore (:keystore processed-config)))
          (is (instance? java.security.KeyStore (:truststore processed-config)))
          (is (string? (:key-password processed-config)))
          (is (not (contains? processed-config :trust-password)))
          (is (not (contains? processed-config :ssl-key)))
          (is (not (contains? processed-config :ssl-cert)))
          (is (not (contains? processed-config :ssl-ca-cert)))))))

  (testing "should set max-threads"
    (let [config (configure-web-server {:port 8080})]
      (is (contains? config :max-threads))))

  (testing "should merge configuration with initial-configs correctly"
    (let [user-config {:port 8080 :truststore "foo"}
          config      (configure-web-server user-config)]
      (is (= config {:truststore "foo"
                     :max-threads 100
                     :port 8080
                     :client-auth :need})))
    (let [user-config {:max-threads 500 :truststore "foo" :port 8000}
          config      (configure-web-server user-config)]
      (is (= config {:truststore "foo"
                     :max-threads 500
                     :client-auth :need
                     :port 8000})))))

