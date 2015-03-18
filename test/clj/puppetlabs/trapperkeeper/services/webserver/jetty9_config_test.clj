(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config-test
  (:import (clojure.lang ExceptionInfo)
           (java.util Arrays)
           (org.eclipse.jetty.server Server))
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
            [puppetlabs.trapperkeeper.testutils.webserver.common :refer [http-get]]))

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
         (update-in [:queue-max-size] (fnil identity default-queue-max-size))
         (update-in [:jmx-enable] (fnil ks/parse-bool default-jmx-enable))
         (update-in [:http :request-header-max-size] (fnil identity default-request-header-size))
         (update-in [:http :so-linger-milliseconds] (fnil identity
                                                          default-so-linger-in-milliseconds))
         (update-in [:http :idle-timeout-milliseconds] identity))
     (process-config config)))

(defn expected-https-config?
  [config expected]
  (let [actual (process-config config)]
    (= (-> expected
           (update-in [:max-threads] (fnil identity default-max-threads))
           (update-in [:queue-max-size] (fnil identity default-queue-max-size))
           (update-in [:jmx-enable] (fnil ks/parse-bool default-jmx-enable))
           (update-in [:https :cipher-suites] (fnil identity acceptable-ciphers))
           (update-in [:https :protocols] (fnil identity default-protocols))
           (update-in [:https :client-auth] (fnil identity default-client-auth))
           (update-in [:https :request-header-max-size] (fnil identity default-request-header-size))
           (update-in [:https :so-linger-milliseconds] (fnil identity
                                                             default-so-linger-in-milliseconds))
           (update-in [:https :idle-timeout-milliseconds] identity)
           (update-in [:https :ssl-crl-path] identity))
       (-> actual
           (update-in [:https] dissoc :keystore-config)))))

(deftest process-config-http-test
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
          {:port 8000 :request-header-max-size 16192}
          {:http {:host default-host
                  :port 8000
                  :request-header-max-size 16192}}))

    (is (expected-http-config?
          {:port 8000 :so-linger-seconds 7}
          {:http {:host default-host
                  :port 8000
                  :so-linger-milliseconds 7000}}))

    (is (expected-http-config?
          {:port 8000 :max-threads 500}
          {:http        {:host default-host :port 8000}
           :max-threads 500}))

    (is (expected-http-config?
          {:port 8000 :queue-max-size 123}
          {:http {:host default-host :port 8000}
           :queue-max-size 123}))

    (is (expected-http-config?
          {:port 8000 :idle-timeout-milliseconds 6000}
          {:http {:host default-host :port 8000
                  :idle-timeout-milliseconds 6000}}))))

(deftest process-config-https-test
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
                 {:ssl-host "foo.local"
                  :ssl-port 8001
                  :request-header-max-size 16192})
          {:https {:host "foo.local"
                   :port 8001
                   :request-header-max-size 16192}}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local"
                  :ssl-port 8001
                  :so-linger-seconds 22})
          {:https {:host "foo.local"
                   :port 8001
                   :so-linger-milliseconds 22000}}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local"
                  :ssl-port 8001
                  :max-threads 93})
          {:https {:host "foo.local" :port 8001}
           :max-threads 93}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local"
                  :ssl-port 8001
                  :queue-max-size 99})
          {:https {:host "foo.local" :port 8001}
           :queue-max-size 99}))

    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local"
                  :ssl-port 8001
                  :idle-timeout-milliseconds 4200})
          {:https {:host "foo.local" :port 8001
                   :idle-timeout-milliseconds 4200}}))))

(deftest process-config-jks-test
  (testing "jks ssl config"
    (is (expected-https-config?
          (merge valid-ssl-keystore-config
                 {:ssl-port 8001})
          {:https {:host default-host :port 8001}}))))

(deftest process-config-ciphers-test
  (testing "cipher suites"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :cipher-suites ["FOO" "BAR"]})
          {:https
            {:host default-host
             :port 8001
             :cipher-suites ["FOO" "BAR"]}})))

  (testing "cipher suites as a comma and space-separated string"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :cipher-suites "FOO, BAR"})
          {:https
            {:host default-host
             :port 8001
             :cipher-suites ["FOO" "BAR"]}}))))

(deftest process-config-protocols-test
  (testing "protocols"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :ssl-protocols ["FOO" "BAR"]})
          {:https
            {:host default-host
             :port 8001
             :protocols ["FOO" "BAR"]}})))

  (testing "protocols as a comma and space-separated string"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001 :ssl-protocols "FOO, BAR"})
          {:https
            {:host default-host
             :port 8001
             :protocols ["FOO" "BAR"]}}))))

(deftest process-config-crl-test
  (testing "ssl-crl-path"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-port 8001
                  :ssl-crl-path
                            "./dev-resources/config/jetty/ssl/certs/ca.pem"})
          {:https
            {:host default-host
             :port 8001
             :ssl-crl-path "./dev-resources/config/jetty/ssl/certs/ca.pem"}}))))

(deftest process-config-client-auth-test
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
                              (get-client-auth {:ssl-port 8081 :client-auth "bogus"})))))))

(deftest process-config-http-plus-https-test
  (testing "process-config successfully builds a WebserverConfig for plaintext+ssl"
    (is (expected-https-config?
          (merge valid-ssl-pem-config
                 {:ssl-host "foo.local" :port 8000})
          {:http  {:host default-host
                   :port 8000
                   :request-header-max-size default-request-header-size
                   :so-linger-milliseconds -1
                   :idle-timeout-milliseconds nil}
           :https {:host "foo.local" :port default-https-port}}))))

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
    (is (= 2 (threads-per-connector 1)))
    (is (= 2 (threads-per-connector 2)))
    (is (= 2 (threads-per-connector 3)))
    (is (= 3 (threads-per-connector 4)))
    (is (= 5 (threads-per-connector 8)))
    (is (= 6 (threads-per-connector 16)))
    (is (= 7 (threads-per-connector 24)))
    (is (= 8 (threads-per-connector 32)))
    (is (= 8 (threads-per-connector 64))))

  (testing "Number of acceptors per cpu"
    (is (= 1 (acceptors-count 1)))
    (is (= 1 (acceptors-count 2)))
    (is (= 1 (acceptors-count 3)))
    (is (= 1 (acceptors-count 4)))
    (is (= 1 (acceptors-count 8)))
    (is (= 2 (acceptors-count 16)))
    (is (= 3 (acceptors-count 24)))
    (is (= 4 (acceptors-count 32)))
    (is (= 4 (acceptors-count 64))))

  (testing "Number of selectors per cpu"
    (is (= 1 (selectors-count 1)))
    (is (= 1 (selectors-count 2)))
    (is (= 1 (selectors-count 3)))
    (is (= 2 (selectors-count 4)))
    (is (= 4 (selectors-count 8)))
    (is (= 4 (selectors-count 16)))
    (is (= 4 (selectors-count 24)))
    (is (= 4 (selectors-count 32)))
    (is (= 4 (selectors-count 64))))

  (testing "The default number of threads are returned."
    (is (= default-max-threads (determine-max-threads {} 1)))
    (is (= default-max-threads (determine-max-threads
                                 {:port 8000 :host "foo.local"} 1))))

  (testing "The max threads set in the config is used."
    (let [max-threads 42]
      (is (= max-threads (determine-max-threads
                           {:max-threads max-threads} 1)))))

  (testing (str "More than the default max threads are returned with a lot of "
                "cores.")
    (with-test-logging
      ;; We're defining the number of selectors and acceptors based on the
      ;; defaults that Jetty 9.2.10.v20150310 uses and those are capped at 4
      ;; each.  For a single connector, the minimum number of threads that
      ;; Jetty needs to start will never be larger than 9 - 4 selectors +
      ;; 4 acceptors + 1 base thread - regardless of the number of CPU cores
      ;; on the host.  Since the default-max-threads is '100', then, it isn't
      ;; currently possible to have the number of needed threads exceed the
      ;; default-max-threads for a 'real-world' configuration.  To prove that
      ;; the thread adjustment code works, this test artificially adjusts the
      ;; default-max-threads down to a number lower than Jetty would compute
      ;; as the minimum needed.
      (with-redefs [default-max-threads 1]
        (let [config {:port 8000 :host "foo.local"}
              num-cpus 100]
        (is (= (calculate-required-threads config num-cpus)
               (determine-max-threads config num-cpus))))))))

(defn small-thread-pool-size-test-config
  [raw-config]
  (let [num-cpus   (ks/num-cpus)
        calc-size  (calculate-required-threads raw-config num-cpus)
        connectors (connector-count raw-config)
        low-size   (- calc-size 1)
        config     (assoc raw-config :max-threads low-size)
        exp-re     (re-pattern (str "Insufficient threads: max="
                                    low-size " < needed\\(acceptors="
                                    (* (acceptors-count num-cpus) connectors)
                                    " \\+ selectors="
                                    (* (selectors-count num-cpus) connectors)
                                    " \\+ request=1\\)"))]
    {:config config
     :exp-re exp-re}))

(defn correct-thread-pool-size-test-config
  [raw-config]
  (let [calc-size (calculate-required-threads raw-config (ks/num-cpus))]
    (assoc raw-config :max-threads calc-size)))

(deftest calculated-thread-pool-size
  (testing "Verify that our thread pool size algorithm matches Jetty's"
    (let [test-1   (merge valid-ssl-pem-config {:port 0, :host "0.0.0.0"
                                                :ssl-port 0, :ssl-host "0.0.0.0"})
          config-1 (small-thread-pool-size-test-config test-1)
          test-2   {:port 0, :host "0.0.0.0"}
          config-2 (small-thread-pool-size-test-config test-2)]
      (doseq [{:keys [config exp-re]} [config-1 config-2]]
        (with-test-logging
          (is (thrown-with-msg?
                IllegalStateException exp-re
                (jetty9/start-webserver! (jetty9/initialize-context) config))
              (str "The current method that the Jetty9 service uses to calculate "
                   "the minimum size of a thread pool has drifted from how Jetty "
                   "itself calculates the size. This is most likely due to a "
                   "change of the Jetty version being used. The
                   calculate-required-threads function should be updated."))))))

  (testing "Our thread pool size algo still allows Jetty to start"
    (let [test-1   (merge valid-ssl-pem-config {:port 0, :host "0.0.0.0"
                                                :ssl-port 0, :ssl-host "0.0.0.0"})
          config-1 (correct-thread-pool-size-test-config test-1)
          test-2   {:port 0, :host "0.0.0.0"}
          config-2 (correct-thread-pool-size-test-config test-2)]
      (doseq [config [config-1 config-2]]
        (let [ctxt (jetty9/start-webserver! (jetty9/initialize-context) config)]
          (is (= (type (:server ctxt)) Server))
          (jetty9/shutdown ctxt))))))

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
                               (format "http://localhost:10000/%s" path))]
                (is (= (:status response) 200))
                (is (= (:body response) body)))))))))

  (testing "Server fails to start with bad post-config-script"
    (let [base-config {:port 9000
                       :host "localhost"}]
      (testing "Throws an error if the script can't be compiled."
        (is (thrown-with-msg?
              IllegalArgumentException
              #"Invalid script string in webserver 'post-config-script' configuration"
              (jetty9/start-webserver!
                (jetty9/initialize-context)
                (merge base-config
                       {:post-config-script (str "AHAHHHGHAHAHAHEASD!  OMG!")})))))
      (testing "Throws an error if the script can't be executed."
        (is (thrown-with-msg?
              IllegalArgumentException
              #"Invalid script string in webserver 'post-config-script' configuration"
              (jetty9/start-webserver!
                (jetty9/initialize-context)
                (merge base-config
                       {:post-config-script (str "Object x = null; x.toString();")}))))))))

