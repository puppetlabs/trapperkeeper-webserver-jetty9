(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config-test
  (:import (clojure.lang ExceptionInfo))
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [schema.core :as schema]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :refer :all]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-log-output logs-matching]]))

(def valid-ssl-pem-config
  {:ssl-cert    "./dev-resources/config/jetty/ssl/certs/localhost.pem"
   :ssl-key     "./dev-resources/config/jetty/ssl/private_keys/localhost.pem"
   :ssl-ca-cert "./dev-resources/config/jetty/ssl/certs/ca.pem"})

(def valid-ssl-keystore-config
  {:keystore        "./dev-resources/config/jetty/ssl/keystore.jks"
   :truststore      "./dev-resources/config/jetty/ssl/truststore.jks"
   :key-password    "Kq8lG9LkISky9cDIYysiadxRx"
   :trust-password  "Kq8lG9LkISky9cDIYysiadxRx"})

(defn expected-http-config?
  [config expected]
  (= (-> expected
         (update-in [:max-threads] (fnil identity default-max-threads)))
     (process-config config)))

(defn expected-https-config?
  [config expected]
  (let [actual (process-config config)]
    (= (-> expected
           (update-in [:max-threads] (fnil identity default-max-threads))
           (update-in [:https :cipher-suites] (fnil identity acceptable-ciphers))
           (update-in [:https :protocols] (fnil identity default-protocols))
           (update-in [:https :client-auth] (fnil identity default-client-auth)))
       (-> actual
           (update-in [:https] dissoc :keystore-config)))))

(deftest process-config-test
  (testing "process-config successfully builds a WebserverServiceConfig for plaintext connector"
    (is (expected-http-config?
          {:port 8000}
          {:http {:host default-host :port 8000}}))

    (is (expected-http-config?
          {:port 8000 :host "foo.local"}
          {:http {:host "foo.local" :port 8000}}))

    (is (expected-http-config?
          {:host "foo.local"}
          {:http {:host "foo.local" :port default-http-port}}))

    (is (expected-http-config?
          {:port 8000 :max-threads 500}
          {:http        {:host default-host :port 8000}
           :max-threads 500})))

  (testing "process-config successfully builds a WebserverServiceConfig for ssl connector"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local"})
          {:https {:host "foo.local" :port default-https-port}}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001})
          {:https {:host default-host :port 8001}}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local" :ssl-port 8001})
          {:https {:host "foo.local" :port 8001}}))

    (is (expected-https-config?
          (merge valid-ssl-keystore-config
                 {:ssl-port 8001})
          {:https {:host default-host :port 8001}}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :cipher-suites ["FOO" "BAR"]})
          {:https {:host default-host :port 8001 :cipher-suites ["FOO" "BAR"]}}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :ssl-protocols ["FOO" "BAR"]})
          {:https {:host default-host :port 8001 :protocols ["FOO" "BAR"]}}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :client-auth "want"})
          {:https {:host default-host :port 8001 :client-auth :want}})))

  (testing "process-config successfully builds a WebserverServiceConfig for plaintext+ssl"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local" :port 8000})
          {:http  {:host default-host :port 8000}
           :https {:host "foo.local" :port default-https-port}})))

  ;; TODO: better checking of the exceptions here
  (testing "process-config fails for invalid server config"
    (are [config]
      (thrown? ExceptionInfo
               (process-config config))
      {:port "foo"}
      {:port 8000 :badkey "hi"}
      ))

  (testing "process-config fails for incomplete ssl context config"
    (are [config]
      (thrown? IllegalArgumentException
               (process-config config))
      {}
      {:ssl-port 8001}
      {:ssl-port 8001 :ssl-host "foo.local"}
      {:ssl-host "foo.local"}
      valid-ssl-pem-config)))

;; TODO: revisit all of these tests
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

