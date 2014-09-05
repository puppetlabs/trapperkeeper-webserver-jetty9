(ns puppetlabs.trapperkeeper.services.webserver.jetty9-config
  (:import [java.security KeyStore]
           (java.io FileInputStream)
           (clojure.lang ExceptionInfo))
  (:require [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [puppetlabs.certificate-authority.core :as ssl]
            [puppetlabs.kitchensink.core :refer [missing? num-cpus uuid parse-bool]]))

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
(def default-jmx-enable "true")
(def default-request-header-buffer-size 8192)
(def default-request-header-size 8192)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def StaticContent
  {:resource                          schema/Str
   :path                              schema/Str
   (schema/optional-key :follow-links) schema/Bool})

(def WebserverRawConfig
  {(schema/optional-key :port)                       schema/Int
   (schema/optional-key :host)                       schema/Str
   (schema/optional-key :max-threads)                schema/Int
   (schema/optional-key :request-header-max-size)    schema/Int
   (schema/optional-key :ssl-port)                   schema/Int
   (schema/optional-key :ssl-host)                   schema/Str
   (schema/optional-key :ssl-key)                    schema/Str
   (schema/optional-key :ssl-cert)                   schema/Str
   (schema/optional-key :ssl-cert-chain)             schema/Str
   (schema/optional-key :ssl-ca-cert)                schema/Str
   (schema/optional-key :keystore)                   schema/Str
   (schema/optional-key :truststore)                 schema/Str
   (schema/optional-key :key-password)               schema/Str
   (schema/optional-key :trust-password)             schema/Str
   (schema/optional-key :cipher-suites)              [schema/Str]
   (schema/optional-key :ssl-protocols)              [schema/Str]
   (schema/optional-key :client-auth)                schema/Str
   (schema/optional-key :ssl-crl-path)               schema/Str
   (schema/optional-key :jmx-enable)                 schema/Str
   (schema/optional-key :default-server)             schema/Bool
   (schema/optional-key :static-content)             [StaticContent]
   (schema/optional-key :gzip-enable)                schema/Bool})

(def MultiWebserverRawConfigUnvalidated
  {schema/Keyword  WebserverRawConfig})

(defn one-default?
  [config]
  (->> config
       vals
       (filter :default-server)
       count
       (>= 1)))

(def MultiWebserverRawConfig
  (schema/both MultiWebserverRawConfigUnvalidated (schema/pred one-default? 'one-default?)))

(def WebserverServiceRawConfig
  (schema/either WebserverRawConfig MultiWebserverRawConfig))

(def WebserverSslPemConfig
  {:ssl-key                              schema/Str
   :ssl-cert                             schema/Str
   (schema/optional-key :ssl-cert-chain) schema/Str
   :ssl-ca-cert                          schema/Str})

(def WebserverSslKeystoreConfig
  {:keystore        KeyStore
   :key-password    schema/Str
   :truststore      KeyStore
   (schema/optional-key :trust-password) schema/Str})

(def WebserverSslClientAuth
  (schema/enum :need :want :none))

(def WebserverConnector
  {:host         schema/Str
   :port         schema/Int
   :request-header-max-size schema/Int})

(def WebserverSslContextFactory
  {:keystore-config                    WebserverSslKeystoreConfig
   :client-auth                        WebserverSslClientAuth
   (schema/optional-key :ssl-crl-path) (schema/maybe schema/Str)})

(def WebserverSslConnector
  (merge
    WebserverConnector
    WebserverSslContextFactory
    {:cipher-suites [schema/Str]
     :protocols     (schema/maybe [schema/Str])}))

(def HasConnector
  (schema/either
    (schema/pred #(contains? % :http) 'has-http-connector?)
    (schema/pred #(contains? % :https) 'has-https-connector?)))

(def WebserverConfig
  (schema/both
    HasConnector
    {(schema/optional-key :http)  WebserverConnector
     (schema/optional-key :https) WebserverSslConnector
     :max-threads                 schema/Int
     :jmx-enable                  schema/Bool}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Conversion functions (raw config -> schema)

(schema/defn ^:always-validate
  maybe-get-pem-config! :- (schema/maybe WebserverSslPemConfig)
  [config :- WebserverRawConfig]
  (let [pem-required-keys [:ssl-key :ssl-cert :ssl-ca-cert]
        pem-config (select-keys config pem-required-keys)]
    (condp = (count pem-config)
      3 (if-let [ssl-cert-chain (:ssl-cert-chain config)]
          (assoc pem-config :ssl-cert-chain ssl-cert-chain)
          pem-config)
      0 nil
      (throw (IllegalArgumentException.
               (format "Found SSL config options: %s; If configuring SSL from PEM files, you must provide all of the following options: %s"
                       (keys pem-config) pem-required-keys))))))

(schema/defn ^:always-validate
  get-x509s-from-ssl-cert-pem :- (schema/pred ssl/certificate-list?)
  [ssl-cert :- schema/Str
   ssl-cert-chain :- (schema/maybe schema/Str)]
  (if-not (fs/readable? ssl-cert)
    (throw (IllegalArgumentException.
             (format "Unable to open 'ssl-cert' file: %s"
                     ssl-cert))))
  (let [certs (ssl/pem->certs ssl-cert)]
    (if (= 0 (count certs))
      (throw (Exception.
               (format "No certs found in 'ssl-cert' file: %s"
                       ssl-cert))))
    (if ssl-cert-chain
      [(first certs)]
      certs)))

(schema/defn ^:always-validate
  get-x509s-from-ssl-cert-chain-pem :- (schema/pred ssl/certificate-list?)
  [ssl-cert-chain :- (schema/maybe schema/Str)]
  (if ssl-cert-chain
    (do
      (if-not (fs/readable? ssl-cert-chain)
        (throw (IllegalArgumentException.
                 (format "Unable to open 'ssl-cert-chain' file: %s"
                         ssl-cert-chain))))
      (ssl/pem->certs ssl-cert-chain))
    []))

(schema/defn ^:always-validate
  construct-ssl-x509-cert-chain :- (schema/pred ssl/certificate-list?)
  [ssl-cert :- schema/Str
   ssl-cert-chain :- (schema/maybe schema/Str)]
  (let [ssl-cert-x509s       (get-x509s-from-ssl-cert-pem ssl-cert ssl-cert-chain)
        ssl-cert-chain-x509s (get-x509s-from-ssl-cert-chain-pem ssl-cert-chain)]
    (into [] (concat ssl-cert-x509s ssl-cert-chain-x509s))))

(schema/defn ^:always-validate
  pem-ssl-config->keystore-ssl-config :- WebserverSslKeystoreConfig
  [{:keys [ssl-ca-cert ssl-key ssl-cert ssl-cert-chain]} :- WebserverSslPemConfig]
  (let [key-password   (uuid)
        ssl-x509-chain (construct-ssl-x509-cert-chain ssl-cert
                                                      ssl-cert-chain)]
    {:truststore    (-> (ssl/keystore)
                        (ssl/assoc-certs-from-file!
                          "CA Certificate" ssl-ca-cert))
     :key-password  key-password
     :keystore      (-> (ssl/keystore)
                        (ssl/assoc-private-key!
                          "Private Key"
                          (ssl/pem->private-key ssl-key)
                          key-password
                          ssl-x509-chain))}))

(schema/defn ^:always-validate
  warn-if-keystore-ssl-configs-found!
  [config :- WebserverRawConfig]
  (let [keystore-ssl-config-keys [:keystore :truststore :key-password :trust-password]
        keystore-ssl-config (select-keys config keystore-ssl-config-keys)]
    (when (pos? (count keystore-ssl-config))
      (log/warn (format "Found settings for both keystore-based and PEM-based SSL; using PEM-based settings, ignoring %s"
                        (keys keystore-ssl-config))))))

(schema/defn ^:always-validate
  get-jks-keystore-config! :- WebserverSslKeystoreConfig
  [{:keys [truststore keystore key-password trust-password]}
      :- WebserverRawConfig]
  (when (some nil? [truststore keystore key-password trust-password])
    (throw (IllegalArgumentException.
             (str "Missing some SSL configuration; must provide either :ssl-cert, "
                  ":ssl-key, and :ssl-ca-cert, OR :truststore, :trust-password, "
                  ":keystore, and :key-password."))))
  {:keystore       (doto (ssl/keystore)
                     (.load (FileInputStream. keystore)
                            (.toCharArray key-password)))
   :truststore     (doto (ssl/keystore)
                     (.load (FileInputStream. truststore)
                            (.toCharArray trust-password)))
   :key-password   key-password
   :trust-password trust-password})

(schema/defn ^:always-validate
  get-keystore-config! :- WebserverSslKeystoreConfig
  [config :- WebserverRawConfig]
  (if-let [pem-config (maybe-get-pem-config! config)]
    (do
      (warn-if-keystore-ssl-configs-found! config)
      (pem-ssl-config->keystore-ssl-config pem-config))
    (get-jks-keystore-config! config)))

(schema/defn ^:always-validate
  get-client-auth! :- WebserverSslClientAuth
  [config :- WebserverRawConfig]
  (let [client-auth (:client-auth config)]
    (cond
      (nil? client-auth) :need
      (contains? #{"need" "want" "none"} client-auth) (keyword client-auth)
      :else (throw
              (IllegalArgumentException.
                (format
                  "Unexpected value found for client auth config option: %s.  Expected need, want, or none."
                  client-auth))))))

(schema/defn ^:always-validate
  get-ssl-crl-path! :- (schema/maybe schema/Str)
  [config :- WebserverRawConfig]
  (if-let [ssl-crl-path (:ssl-crl-path config)]
    (if (fs/readable? ssl-crl-path)
      ssl-crl-path
      (throw (IllegalArgumentException.
               (format
                 "Non-readable path specified for ssl-crl-path option: %s"
                 ssl-crl-path))))))

(schema/defn ^:always-validate
  maybe-get-http-connector :- (schema/maybe WebserverConnector)
  [config :- WebserverRawConfig]
  (if (some #(contains? config %) #{:port :host})
    {:host         (or (:host config) default-host)
     :port         (or (:port config) default-http-port)
     :request-header-max-size (or (:request-header-max-size config) default-request-header-size)}))

(schema/defn ^:always-validate
  maybe-get-https-connector :- (schema/maybe WebserverSslConnector)
  [config :- WebserverRawConfig]
  (if (some #(contains? config %) #{:ssl-port :ssl-host})
    {:host (or (:ssl-host config) default-host)
     :port (or (:ssl-port config) default-https-port)
     :request-header-max-size (or (:request-header-max-size config) default-request-header-size)
     :keystore-config (get-keystore-config! config)
     :cipher-suites (or (:cipher-suites config) acceptable-ciphers)
     :protocols (:ssl-protocols config)
     :client-auth (get-client-auth! config)
     :ssl-crl-path (get-ssl-crl-path! config)}))

(schema/defn ^:always-validate
  maybe-add-http-connector :- {(schema/optional-key :http) WebserverConnector
                               schema/Keyword              schema/Any}
  [acc config :- WebserverRawConfig]
  (if-let [http-connector (maybe-get-http-connector config)]
    (assoc acc :http http-connector)
    acc))

(schema/defn ^:always-validate
  maybe-add-https-connector :- {(schema/optional-key :https) WebserverSslConnector
                                schema/Keyword              schema/Any}
  [acc config :- WebserverRawConfig]
  (if-let [https-connector (maybe-get-https-connector config)]
    (assoc acc :https https-connector)
    acc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate
  process-config :- WebserverConfig
  [config :- WebserverRawConfig]
  (let [result (-> {}
                   (maybe-add-http-connector config)
                   (maybe-add-https-connector config)
                   (assoc :max-threads (get config :max-threads default-max-threads))
                   (assoc :jmx-enable (parse-bool (get config :jmx-enable default-jmx-enable))))]
    (when-not (some #(contains? result %) [:http :https])
      (throw (IllegalArgumentException.
               "Either host, port, ssl-host, or ssl-port must be specified on the config in order for the server to be started")))
    result))
