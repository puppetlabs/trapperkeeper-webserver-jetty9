(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config-test
  (:import (clojure.lang ExceptionInfo)
           (java.util Arrays)
           (com.puppetlabs.ssl_utils SSLUtils))
  (:require [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [me.raynes.fs :as fs]
            [puppetlabs.ssl-utils.core :as ssl]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :refer :all]
            [puppetlabs.trapperkeeper.testutils.logging :refer [with-test-logging]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as jetty9]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service
             :refer [jetty9-service add-ring-handler]]
            [puppetlabs.trapperkeeper.app :as tk-app]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer [http-get]]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.testutils.webserver :as testutils]))

(use-fixtures :once
  schema-test/validate-schemas
  testutils/assert-clean-shutdown)

(defn valid-ssl-pem-config
  []
  {:ssl-cert    "./dev-resources/config/jetty/ssl/certs/localhost.pem"
   :ssl-key     "./dev-resources/config/jetty/ssl/private_keys/localhost.pem"
   :ssl-ca-cert "./dev-resources/config/jetty/ssl/certs/ca.pem"})

(defn valid-ssl-keystore-config
  []
  {:keystore (str "./dev-resources/config/jetty/ssl/keystore." (if (SSLUtils/isFIPS) "bcfks" "jks"))
   :truststore (str "./dev-resources/config/jetty/ssl/truststore." (if (SSLUtils/isFIPS) "bcfks" "jks"))
   :key-password "Kq8lG9LkISky9cDIYysiadxRx"
   :trust-password "Kq8lG9LkISky9cDIYysiadxRx"})

(defn munge-actual-http-config
  [config]
  (process-config config))

(defn munge-expected-common-config
  [expected scheme]
  (-> expected
      (update-in [:max-threads] identity)
      (update-in [:queue-max-size] identity)
      (update-in [:jmx-enable] (fnil ks/parse-bool default-jmx-enable))
      (update-in [scheme :request-header-max-size] identity)
      (update-in [scheme :idle-timeout-milliseconds] identity)
      (update-in [scheme :acceptor-threads] identity)
      (update-in [scheme :selector-threads] identity)))

(defn munge-expected-http-config
  [expected]
  (munge-expected-common-config expected :http))

(defn munge-actual-https-config
  [config]
  (let [actual (process-config config)]
    (-> actual
        (update-in [:https] dissoc :keystore-config))))

(defn munge-expected-https-config
  [expected]
  (-> (munge-expected-common-config expected :https)
      (update-in [:https :cipher-suites] (fnil identity (if (SSLUtils/isFIPS)
                                                          acceptable-ciphers-fips
                                                          acceptable-ciphers)))
      (update-in [:https :protocols] (fnil identity default-protocols))
      (update-in [:https :client-auth] (fnil identity default-client-auth))
      (update-in [:https :allow-renegotiation] (fnil identity default-allow-renegotiation))
      (update-in [:https :ssl-crl-path] identity)))

(deftest process-config-http-test
  (testing "process-config successfully builds a WebserverConfig for plaintext connector"
    (is (= (munge-actual-http-config
             {:port 8000})
           (munge-expected-http-config
             {:http {:host default-host :port 8000}})))

    (is (= (munge-actual-http-config
             {:port 8000 :host "foo.local"})
           (munge-expected-http-config
             {:http {:host "foo.local" :port 8000}})))

    (is (= (munge-actual-http-config
             {:host "foo.local"})
           (munge-expected-http-config
             {:http {:host "foo.local" :port default-http-port}})))

    (is (= (munge-actual-http-config
             {:port 8000 :request-header-max-size 16192})
           (munge-expected-http-config
             {:http {:host                    default-host
                     :port                    8000
                     :request-header-max-size 16192}})))

    (is (= (munge-actual-http-config
             {:port 8000 :max-threads 500})
           (munge-expected-http-config
             {:http        {:host default-host :port 8000}
              :max-threads 500})))

    (is (= (munge-actual-http-config
             {:port 8000 :queue-max-size 123})
           (munge-expected-http-config
             {:http           {:host default-host :port 8000}
              :queue-max-size 123})))

    (is (= (munge-actual-http-config
             {:port 8000 :idle-timeout-milliseconds 6000})
           (munge-expected-http-config
             {:http {:host                      default-host
                     :port                      8000
                     :idle-timeout-milliseconds 6000}})))

    (is (= (munge-actual-http-config
             {:port 8000 :acceptor-threads 32})
           (munge-expected-http-config
             {:http {:host             default-host
                     :port             8000
                     :acceptor-threads 32}})))

    (is (= (munge-actual-http-config
             {:port 8000 :selector-threads 52})
           (munge-expected-http-config
             {:http {:host      default-host
                     :port      8000
                     :selector-threads 52}})))))

(deftest process-config-https-test
  (testing "process-config successfully builds a WebserverConfig for ssl connector"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host "foo.local"}))
           (munge-expected-https-config
             {:https {:host "foo.local"
                      :port default-https-port}})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-port 8001}))
           (munge-expected-https-config
             {:https {:host default-host
                      :port 8001}}))) 

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host "foo.local" :ssl-port 8001}))
           (munge-expected-https-config
             {:https {:host "foo.local"
                      :port 8001}})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host                "foo.local"
                     :ssl-port                8001
                     :request-header-max-size 16192}))
           (munge-expected-https-config
             {:https {:host                    "foo.local"
                      :port                    8001
                      :request-header-max-size 16192}})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host    "foo.local"
                     :ssl-port    8001
                     :max-threads 93}))
           (munge-expected-https-config
             {:https       {:host "foo.local"
                            :port 8001}
              :max-threads 93})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host       "foo.local"
                     :ssl-port       8001
                     :queue-max-size 99}))
           (munge-expected-https-config
             {:https          {:host "foo.local"
                               :port 8001}
              :queue-max-size 99})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host                  "foo.local"
                     :ssl-port                  8001
                     :idle-timeout-milliseconds 4200}))
           (munge-expected-https-config
             {:https {:host                      "foo.local"
                      :port                      8001
                      :idle-timeout-milliseconds 4200}})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host            "foo.local"
                     :ssl-port             8001
                     :ssl-selector-threads 4242}))
           (munge-expected-https-config
             {:https {:host             "foo.local"
                      :port             8001
                      :selector-threads 4242}})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host             "foo.local"
                     :ssl-port             8001
                     :allow-renegotiation true}))
           (munge-expected-https-config
             {:https {:host             "foo.local"
                      :port             8001
                      :allow-renegotiation true}})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host             "foo.local"
                     :ssl-port             8001
                     :allow-renegotiation false}))
           (munge-expected-https-config
             {:https {:host             "foo.local"
                      :port             8001
                      :allow-renegotiation false}})))

    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host             "foo.local"
                     :ssl-port             8001
                     :ssl-acceptor-threads 9193}))
           (munge-expected-https-config
             {:https {:host             "foo.local"
                      :port             8001
                      :acceptor-threads 9193}})))))


(deftest process-config-jks-test
  (testing "jks ssl config"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-keystore-config)
                    {:ssl-port 8001}))
           (munge-expected-https-config
             {:https {:host default-host
                      :port 8001}})))))

(deftest process-config-ciphers-test
  (testing "cipher suites"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-port 8001 :cipher-suites ["FOO" "BAR"]}))
           (munge-expected-https-config
             {:https
              {:host          default-host
               :port          8001
               :cipher-suites ["FOO" "BAR"]}}))))

  (testing "cipher suites as a comma and space-separated string"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-port 8001 :cipher-suites "FOO, BAR"}))
           (munge-expected-https-config
             {:https
              {:host          default-host
               :port          8001
               :cipher-suites ["FOO" "BAR"]}})))))

(deftest process-config-protocols-test
  (testing "protocols"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-port 8001 :ssl-protocols ["FOO" "BAR"]}))
           (munge-expected-https-config
             {:https
              {:host      default-host
               :port      8001
               :protocols ["FOO" "BAR"]}}))))

  (testing "protocols as a comma and space-separated string"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-port 8001 :ssl-protocols "FOO, BAR"}))
           (munge-expected-https-config
             {:https
              {:host      default-host
               :port      8001
               :protocols ["FOO" "BAR"]}})))))

(deftest process-config-crl-test
  (testing "ssl-crl-path"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-port 8001
                     :ssl-crl-path
                               "./dev-resources/config/jetty/ssl/certs/ca.pem"}))
           (munge-expected-https-config
             {:https
              {:host         default-host
               :port         8001
               :ssl-crl-path "./dev-resources/config/jetty/ssl/certs/ca.pem"}})))))

(deftest process-config-client-auth-test
  (testing "client auth"
    (letfn [(get-client-auth [config]
                             (-> config
                                 (merge (valid-ssl-pem-config))
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
                              (get-client-auth {:ssl-port 8081 :client-auth "bogus"})))))))

(deftest process-config-http-plus-https-test
  (testing "process-config successfully builds a WebserverConfig for plaintext+ssl"
    (is (= (munge-actual-https-config
             (merge (valid-ssl-pem-config)
                    {:ssl-host "foo.local" :port 8000}))
           (munge-expected-https-config
             {:http  {:host                      default-host
                      :port                      8000
                      :request-header-max-size   nil
                      :idle-timeout-milliseconds nil
                      :acceptor-threads          nil
                      :selector-threads          nil}
              :https {:host "foo.local"
                      :port default-https-port}})))))

(deftest process-config-invalid-test
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
      (valid-ssl-pem-config)
      (merge {:ssl-port 8001} (dissoc (valid-ssl-pem-config) :ssl-key))
      (merge {:ssl-port 8001} (dissoc (valid-ssl-keystore-config) :keystore))))

  (testing "should warn if both keystore-based and PEM-based SSL settings are found"
    (with-test-logging
      (process-config (merge {:ssl-port 8001}
                             (valid-ssl-pem-config)
                             (valid-ssl-keystore-config)))
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

(deftest test-advanced-scripting-config
  (testing "Verify that we can use scripting to handle advanced configuration scenarios"
    (let [config {:webserver
                  {:port               9000
                   :host               "localhost"
                   :post-config-script (str "import org.eclipse.jetty.server.ServerConnector;"
                                            "ServerConnector c = (ServerConnector)(server.getConnectors()[0]);\n"
                                            "c.setPort(10000);")}}]
      (with-test-logging
        (with-app-with-config app
          [jetty9-service]
          config
          (let [s (tk-app/get-service app :WebserverService)
                add-ring-handler (partial add-ring-handler s)
                body "Hi World"
                path "/hi_world"
                ring-handler (fn [req] {:status 200 :body body})]
            (testing "A warning is logged when using post-config-script"
              (is (logged? #"The 'post-config-script' setting is for advanced use"
                           :warn)))

            (testing "scripted changes are executed properly"
              (add-ring-handler ring-handler path)
              (let [response (http-get
                               (format "http://localhost:10000%s" path))]
                (is (= (:status response) 200))
                (is (= (:body response) body)))))))))

  (testing "Server fails to start with bad post-config-script"
    (let [base-config {:port 9000
                       :host "localhost"}]
      (testing "Throws an error if the script can't be compiled."
        (is (thrown-with-msg?
              IllegalArgumentException
              #"Invalid script string in webserver 'post-config-script' configuration"
              (let [context (jetty9/initialize-context)]
                (with-test-logging
                 (try
                   (jetty9/start-webserver!
                    context
                    (merge base-config
                           {:post-config-script (str "AHAHHHGHAHAHAHEASD!  OMG!")}))
                   (finally
                     (jetty9/shutdown context))))))))
      (testing "Throws an error if the script can't be executed."
        (is (thrown-with-msg?
              IllegalArgumentException
              #"Invalid script string in webserver 'post-config-script' configuration"
              (let [context (jetty9/initialize-context)]
                (with-test-logging
                 (try
                   (jetty9/start-webserver!
                    context
                    (merge base-config
                           {:post-config-script (str "Object x = null; x.toString();")}))
                   (finally
                     (jetty9/shutdown context)))))))))))
