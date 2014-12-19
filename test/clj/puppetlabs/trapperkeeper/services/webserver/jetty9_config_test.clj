(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config-test
  (:import (clojure.lang ExceptionInfo)
           (java.util Arrays))
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [puppetlabs.certificate-authority.core :as ssl]
            [puppetlabs.kitchensink.core :as ks]
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
         (update-in [:max-threads] (fnil identity default-max-threads))
         (update-in [:jmx-enable] (fnil ks/parse-bool default-jmx-enable))
         (update-in [:http :request-header-max-size] (fnil identity default-request-header-size)))
     (process-config config)))

(defn expected-https-config?
  [config expected]
  (let [actual (process-config config)]
    (= (-> expected
           (update-in [:max-threads] (fnil identity default-max-threads))
           (update-in [:jmx-enable] (fnil ks/parse-bool default-jmx-enable))
           (update-in [:https :cipher-suites] (fnil identity acceptable-ciphers))
           (update-in [:https :protocols] (fnil identity default-protocols))
           (update-in [:https :client-auth] (fnil identity default-client-auth))
           (update-in [:https :request-header-max-size] (fnil identity default-request-header-size))
           (update-in [:https :ssl-crl-path] identity))
       (-> actual
           (update-in [:https] dissoc :keystore-config)))))

(deftest process-config-test
  (testing "process-config successfully builds a WebserverConfig for plaintext connector"
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
           :max-threads 500}))

    (is (expected-http-config?
          {:port 8000 :request-header-max-size 16192}
          {:http {:host default-host :port 8000 :request-header-max-size 16192}})))

  (testing "process-config successfully builds a WebserverConfig for ssl connector"
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
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local" :ssl-port 8001 :request-header-max-size 16192})
          {:https {:host "foo.local" :port 8001 :request-header-max-size 16192}})))

  (testing "jks ssl config"
    (is (expected-https-config?
          (merge valid-ssl-keystore-config
                 {:ssl-port 8001})
          {:https {:host default-host :port 8001}})))

  (testing "cipher suites"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :cipher-suites ["FOO" "BAR"]})
          {:https
            {:host default-host
             :port 8001
             :cipher-suites ["FOO" "BAR"]}})))

  (testing "protocols"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :ssl-protocols ["FOO" "BAR"]})
          {:https
            {:host default-host
             :port 8001
             :protocols ["FOO" "BAR"]}})))

  (testing "ssl-crl-path"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001
                  :ssl-crl-path
                            "./dev-resources/config/jetty/ssl/certs/ca.pem"})
          {:https
            {:host default-host
             :port 8001
             :ssl-crl-path "./dev-resources/config/jetty/ssl/certs/ca.pem"}})))

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

  (testing "process-config successfully builds a WebserverConfig for plaintext+ssl"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local" :port 8000})
          {:http  {:host default-host :port 8000 :request-header-max-size default-request-header-size}
           :https {:host "foo.local" :port default-https-port}})))

  (testing "process-config fails for invalid server config"
    (are [config]
      (thrown? ExceptionInfo
               (process-config config))
      {:port "foo"}
      {:port 8000 :badkey "hi"}))

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
    (with-test-logging
      (process-config (merge {:ssl-port 8001}
                             valid-ssl-pem-config
                             valid-ssl-keystore-config))
      (is (logged? #"Found settings for both keystore-based and PEM-based SSL")))))

(defn- validate-cert-lists-equal
  [pem-with-expected-certs ssl-cert ssl-cert-chain]
  (let [expected-certs (ssl/pem->certs pem-with-expected-certs)
        actual-certs   (construct-ssl-x509-cert-chain ssl-cert ssl-cert-chain)]
    (is (= (count expected-certs) (count actual-certs))
        "Number of expected certs do not match number of actual certs")
    (dotimes [n (count expected-certs)]
      (is (Arrays/equals (.getEncoded (nth expected-certs n))
                         (.getEncoded (nth actual-certs n)))
        (str "Expected cert # " n " from  does not match actual cert")))))

(deftest construct-ssl-x509-cert-chain-test
  (testing "non-existent ssl-cert throws expected exception"
    (let [tmp-file (ks/temp-file)]
      (fs/delete tmp-file)
      (is (thrown-with-msg? IllegalArgumentException
                            #"^Unable to open 'ssl-cert' file:"
                            (construct-ssl-x509-cert-chain
                              (.getAbsolutePath tmp-file)
                              nil)))))

  (testing "no content in ssl-cert throws expected exception"
    (let [tmp-file (ks/temp-file)]
      (is (thrown-with-msg? Exception
                            #"^No certs found in 'ssl-cert' file:"
                            (construct-ssl-x509-cert-chain
                              (.getAbsolutePath tmp-file)
                              nil)))))

  (testing "non-existent ssl-cert-chain throws expected exception"
    (let [tmp-file (ks/temp-file)]
      (fs/delete tmp-file)
      (is (thrown-with-msg? IllegalArgumentException
                            #"^Unable to open 'ssl-cert-chain' file:"
                            (construct-ssl-x509-cert-chain
                              "./dev-resources/config/jetty/ssl/certs/localhost.pem"
                              (.getAbsolutePath tmp-file))))))

  (testing "ssl-cert with single cert loaded into list"
    (validate-cert-lists-equal
      "./dev-resources/config/jetty/ssl/certs/localhost.pem"
      "./dev-resources/config/jetty/ssl/certs/localhost.pem"
      nil))

  (testing "ssl-cert with multiple certs loaded into list"
    (validate-cert-lists-equal
      "./dev-resources/config/jetty/ssl/certs/master-with-all-cas.pem"
      "./dev-resources/config/jetty/ssl/certs/master-with-all-cas.pem"
      nil))

  (testing (str "ssl-cert with single cert and ssl-cert-chain with "
                "multiple certs loaded into list")
    (validate-cert-lists-equal
      "./dev-resources/config/jetty/ssl/certs/master-with-all-cas.pem"
      "./dev-resources/config/jetty/ssl/certs/master.pem"
      "./dev-resources/config/jetty/ssl/certs/ca-master-intermediate-and-root.pem"))

  (testing (str "for ssl-cert with multiple certs and ssl-cert-chain with "
                "with one cert, only the first cert from ssl-cert is "
                "loaded into list with cert from ssl-cert-chain")
    (validate-cert-lists-equal
      "./dev-resources/config/jetty/ssl/certs/master-with-root-ca.pem"
      "./dev-resources/config/jetty/ssl/certs/master-with-intermediate-ca.pem"
      "./dev-resources/config/jetty/ssl/certs/ca-root.pem")))

(deftest determine-max-threads-test
  (testing "Determining the number of connectors."
    (is (= 0 (connector-count {})))
    (is (= 1 (connector-count {:port 8000 :host "foo.local"})))
    (is (= 1 (connector-count (assoc valid-ssl-pem-config
                                     :ssl-port 8001))))
    (is (= 2 (connector-count (merge {:port 8000 :host "foo.local"}
                                     {:ssl-port 8001}
                                     valid-ssl-pem-config)))))

  (testing "The number of threads per connector"
    (is (= 3 (threads-per-connector 1)))
    (is (= 4 (threads-per-connector 2)))
    (is (= 5 (threads-per-connector 3)))
    (is (= 7 (threads-per-connector 4))))

  (testing "Number of acceptors per cpu"
    (is (= 1 (acceptors-count 1)))
    (is (= 1 (acceptors-count 2)))
    (is (= 1 (acceptors-count 3)))
    (is (= 2 (acceptors-count 4))))

  (testing "The default number of threads are returned."
    (is (= default-max-threads (determine-max-threads {} 1)))
    (is (= default-max-threads (determine-max-threads
                                 {:port 8000 :host "foo.local"} 1))))

  (testing "The max threads set in the config is used."
    (let [max-threads 42]
      (is (= max-threads (determine-max-threads
                           {:max-threads max-threads} 1)))))

  (testing (str "More than the default max threads are returned with a lot of "
                "cores and a warning is logged.")
    (with-test-logging
      (is (< default-max-threads (determine-max-threads
                                   {:port 8000 :host "foo.local"} 100)))
      (is (logged? #"Thread pool size not configured so using a size of")))))