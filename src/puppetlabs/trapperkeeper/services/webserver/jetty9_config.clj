(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config
  (:import [java.security KeyStore]
           (java.io FileInputStream)
           (clojure.lang ExceptionInfo))
  (:require [clojure.tools.logging :as log]
            [schema.core :as schema]
            [schema.macros :as sm]
            [puppetlabs.certificate-authority.core :as ssl]
            [puppetlabs.kitchensink.core :refer [missing? num-cpus uuid]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Constants / Defaults

(def acceptable-ciphers
  ["TLS_RSA_WITH_AES_256_CBC_SHA256"
   "TLS_RSA_WITH_AES_256_CBC_SHA"
   "TLS_RSA_WITH_AES_128_CBC_SHA256"
   "TLS_RSA_WITH_AES_128_CBC_SHA"
   "SSL_RSA_WITH_RC4_128_SHA"
   "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
   "SSL_RSA_WITH_RC4_128_MD5"])
(def default-protocols nil)

(def default-http-port 8080)
(def default-https-port 8081)
(def default-host "localhost")
(def default-max-threads 100)
(def default-client-auth :need)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def WebserverServiceRawConfig
  {(schema/optional-key :port)            schema/Int
   (schema/optional-key :host)            schema/Str
   (schema/optional-key :max-threads)     schema/Int
   (schema/optional-key :ssl-port)        schema/Int
   (schema/optional-key :ssl-host)        schema/Str
   (schema/optional-key :ssl-key)         schema/Str
   (schema/optional-key :ssl-cert)        schema/Str
   (schema/optional-key :ssl-ca-cert)     schema/Str
   (schema/optional-key :keystore)        schema/Str
   (schema/optional-key :truststore)      schema/Str
   (schema/optional-key :key-password)    schema/Str
   (schema/optional-key :trust-password)  schema/Str
   (schema/optional-key :cipher-suites)   [schema/Str]
   (schema/optional-key :ssl-protocols)   [schema/Str]
   (schema/optional-key :client-auth)     schema/Str})

(def WebserverSslPemConfig
  {:ssl-key      schema/Str
   :ssl-cert     schema/Str
   :ssl-ca-cert  schema/Str})

(def WebserverSslKeystoreConfig
  {:keystore        KeyStore
   :key-password    schema/Str
   :truststore      KeyStore
   (schema/optional-key :trust-password) schema/Str})

(def WebserverSslClientAuth
  (schema/enum :need :want :none))

(def WebserverConnector
  {:host schema/Str
   :port schema/Int})

(def WebserverSslConnector
  {:host schema/Str
   :port schema/Int
   :keystore-config WebserverSslKeystoreConfig
   :cipher-suites [schema/Str]
   :protocols (schema/maybe [schema/Str])
   :client-auth WebserverSslClientAuth})

(def HasConnector
  (schema/either
    (schema/pred #(contains? % :http) 'has-http-connector?)
    (schema/pred #(contains? % :https) 'has-https-connector?)))

(def WebserverServiceConfig
  (schema/both
    HasConnector
    {(schema/optional-key :http)  WebserverConnector
     (schema/optional-key :https) WebserverSslConnector
     :max-threads                 schema/Int}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Conversion functions (raw config -> schema)

(sm/defn ^:always-validate
  maybe-get-pem-config! :- (schema/maybe WebserverSslPemConfig)
  [config :- WebserverServiceRawConfig]
  (let [pem-required-keys [:ssl-key :ssl-cert :ssl-ca-cert]
        pem-config (select-keys config pem-required-keys)]
    (condp = (count pem-config)
      3 pem-config
      0 nil
      (throw (IllegalArgumentException.
               (format "Found SSL config options: %s; If configuring SSL from PEM files, you must provide all of the following options: %s"
                       (keys pem-config) pem-required-keys))))))

(sm/defn ^:always-validate
  pem-ssl-config->keystore-ssl-config :- WebserverSslKeystoreConfig
  [{:keys [ssl-ca-cert ssl-key ssl-cert]} :- WebserverSslPemConfig]
  (let [key-password (uuid)]
    {:truststore    (-> (ssl/keystore)
                        (ssl/assoc-certs-from-file!
                          "CA Certificate" ssl-ca-cert))
     :key-password  key-password
     :keystore      (-> (ssl/keystore)
                        (ssl/assoc-private-key-file!
                          "Private Key" ssl-key key-password ssl-cert))}))

(sm/defn ^:always-validate
  warn-if-keystore-ssl-configs-found!
  [config :- WebserverServiceRawConfig]
  (let [keystore-ssl-config-keys [:keystore :truststore :key-password :trust-password]
        keystore-ssl-config (select-keys config keystore-ssl-config-keys)]
    (when (pos? (count keystore-ssl-config))
      (log/warn (format "Found settings for both keystore-based and PEM-based SSL; using PEM-based settings, ignoring %s"
                        (keys keystore-ssl-config))))))

(sm/defn ^:always-validate
  get-jks-keystore-config! :- WebserverSslKeystoreConfig
  [{:keys [truststore keystore key-password trust-password]}
      :- WebserverServiceRawConfig]
  (when (some nil? [truststore keystore key-password trust-password])
    (throw (IllegalArgumentException.
             (str "Missing some SSL configuration; must provide either :ssl-cert, "
                  ":ssl-key, and :ssl-ca-cert, OR :truststore, :trust-password, "
                  ":keystore, and :key-password."))))
  (let [result {:keystore     (doto (ssl/keystore)
                                (.load (FileInputStream. keystore)
                                       (.toCharArray key-password)))
                :truststore   (doto (ssl/keystore)
                                (.load (FileInputStream. truststore)
                                       (.toCharArray trust-password)))
                :key-password key-password}]
    (if trust-password
      (assoc result :trust-password trust-password)
      result)))

(sm/defn ^:always-validate
  get-keystore-config! :- WebserverSslKeystoreConfig
  [config :- WebserverServiceRawConfig]
  (if-let [pem-config (maybe-get-pem-config! config)]
    (do
      (warn-if-keystore-ssl-configs-found! config)
      (pem-ssl-config->keystore-ssl-config pem-config))
    (get-jks-keystore-config! config)))

(sm/defn ^:always-validate
  get-client-auth! :- WebserverSslClientAuth
  [config :- WebserverServiceRawConfig]
  (let [client-auth (:client-auth config)]
    (cond
      (nil? client-auth) :need
      (contains? #{"need" "want" "none"} client-auth) (keyword client-auth)
      :else (throw
              (IllegalArgumentException.
                (format
                  "Unexpected value found for client auth config option: %s.  Expected need, want, or none."
                  client-auth))))))

(sm/defn ^:always-validate
  maybe-get-http-connector :- (schema/maybe WebserverConnector)
  [config :- WebserverServiceRawConfig]
  (if (some #(contains? config %) #{:port :host})
    {:host (or (:host config) default-host)
     :port (or (:port config) default-http-port)}))

(sm/defn ^:always-validate
  maybe-get-https-connector :- (schema/maybe WebserverSslConnector)
  [config :- WebserverServiceRawConfig]
  (if (some #(contains? config %) #{:ssl-port :ssl-host})
    {:host (or (:ssl-host config) default-host)
     :port (or (:ssl-port config) default-https-port)
     :keystore-config (get-keystore-config! config)
     :cipher-suites (or (:cipher-suites config) acceptable-ciphers)
     :protocols (:ssl-protocols config)
     :client-auth (get-client-auth! config)}))

(sm/defn ^:always-validate
  maybe-add-http-connector :- {(schema/optional-key :http) WebserverConnector
                               schema/Keyword              schema/Any}
  [acc config :- WebserverServiceRawConfig]
  (if-let [http-connector (maybe-get-http-connector config)]
    (assoc acc :http http-connector)
    acc))

(sm/defn ^:always-validate
  maybe-add-https-connector :- {(schema/optional-key :http) WebserverConnector
                                schema/Keyword              schema/Any}
  [acc config :- WebserverServiceRawConfig]
  (if-let [https-connector (maybe-get-https-connector config)]
    (assoc acc :https https-connector)
    acc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(sm/defn ^:always-validate
  process-config :- WebserverServiceConfig
  [config :- WebserverServiceRawConfig]
  (let [result (-> {}
                   (maybe-add-http-connector config)
                   (maybe-add-https-connector config)
                   (assoc :max-threads (get config :max-threads default-max-threads)))]
    (when-not (some #(contains? result %) [:http :https])
      (throw (IllegalArgumentException.
               "Either host, port, ssl-host, or ssl-port must be specified on the config in order for the server to be started")))
    result))