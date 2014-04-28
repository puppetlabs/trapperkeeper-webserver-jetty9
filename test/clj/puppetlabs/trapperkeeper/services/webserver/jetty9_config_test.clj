(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config-test
  (:import (clojure.lang ExceptionInfo))
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [schema.core :as schema]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :refer :all]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-test-logging]]))

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
          {:https {:host "foo.local" :port 8001}})))

  (testing "jks ssl config"
    (is (expected-https-config?
          (merge valid-ssl-keystore-config
                 {:ssl-port 8001})
          {:https {:host default-host :port 8001}})))

  (testing "cipher suites"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :cipher-suites ["FOO" "BAR"]})
          {:https {:host default-host :port 8001 :cipher-suites ["FOO" "BAR"]}})))

  (testing "protocols"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :ssl-protocols ["FOO" "BAR"]})
          {:https {:host default-host :port 8001 :protocols ["FOO" "BAR"]}})))

  (testing "client auth"
    (letfn [(get-client-auth [config]
                             (-> config
                                 (merge valid-ssl-pem-config)
                                 process-config
                                 (get-in [:https :client-auth])))]
      (testing "configure-web-server should set client-auth to a value of :need
        if not specified in options"
        (is (= :need (get-client-auth {:ssl-port 8001}))))

      (testing "configure-web-server should convert client-auth string to
            appropriate corresponding keyword value in configure-web-server
            options"
        (is (= :need (get-client-auth {:ssl-port 8081 :client-auth "need"})))
        (is (= :want (get-client-auth {:ssl-port 8081 :client-auth "want"})))
        (is (= :none (get-client-auth {:ssl-port 8081 :client-auth "none"}))))

      (testing "configure-web-server should throw IllegalArgumentException if an
            unsupported value is specified for the client-auth option"
        (is (thrown-with-msg? java.lang.IllegalArgumentException
                              #"Unexpected value found for client auth config option: bogus.  Expected need, want, or none."
                              (get-client-auth {:ssl-port 8081 :client-auth "bogus"}))))))

  (testing "process-config successfully builds a WebserverServiceConfig for plaintext+ssl"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local" :port 8000})
          {:http  {:host default-host :port 8000}
           :https {:host "foo.local" :port default-https-port}})))

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
      valid-ssl-pem-config
      (merge {:ssl-port 8001} (dissoc valid-ssl-pem-config :ssl-key))
      (merge {:ssl-port 8001} (dissoc valid-ssl-keystore-config :keystore))))

  (testing "should warn if both keystore-based and PEM-based SSL settings are found"
    (is (not false))
    (with-test-logging
      (process-config (merge {:ssl-port 8001}
                             valid-ssl-pem-config
                             valid-ssl-keystore-config))
      (is (logged? #"Found settings for both keystore-based and PEM-based SSL")))))
