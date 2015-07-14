(ns puppetlabs.trapperkeeper.services.webserver.jetty9-core
  (:import (org.eclipse.jetty.server Handler Server Request ServerConnector
                                     HttpConfiguration HttpConnectionFactory
                                     ConnectionFactory AbstractConnectionFactory)
           (org.eclipse.jetty.server.handler AbstractHandler ContextHandler HandlerCollection
                                             ContextHandlerCollection AllowSymLinkAliasChecker StatisticsHandler)
           (org.eclipse.jetty.util.resource Resource)
           (org.eclipse.jetty.util.thread QueuedThreadPool)
           (org.eclipse.jetty.util.ssl SslContextFactory)
           (javax.servlet.http HttpServletResponse)
           (java.util.concurrent TimeoutException)
           (org.eclipse.jetty.servlets.gzip GzipHandler)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHolder DefaultServlet)
           (org.eclipse.jetty.webapp WebAppContext)
           (java.util HashSet)
           (org.eclipse.jetty.http MimeTypes HttpHeader HttpHeaderValue)
           (javax.servlet Servlet ServletContextListener)
           (org.eclipse.jetty.proxy ProxyServlet)
           (java.net URI)
           (java.security Security)
           (org.eclipse.jetty.client HttpClient)
           (clojure.lang Atom)
           (java.lang.management ManagementFactory)
           (org.eclipse.jetty.jmx MBeanContainer)
           (org.eclipse.jetty.util URIUtil BlockingArrayQueue)
           (java.io IOException))

  (:require [ring.util.servlet :as servlet]
            [ring.util.codec :as codec]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
            [puppetlabs.trapperkeeper.services.webserver.experimental.jetty9-websockets :as websockets]
            [schema.core :as schema]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; JDK SecurityProvider Hack

;; Work around an issue with OpenJDK's PKCS11 implementation preventing TLSv1
;; connections from working correctly
;;
;; http://stackoverflow.com/questions/9586162/openjdk-and-php-ssl-connection-fails
;; https://bugs.launchpad.net/ubuntu/+source/openjdk-6/+bug/948875
(if (re-find #"OpenJDK" (System/getProperty "java.vm.name"))
  (try
    (let [klass     (Class/forName "sun.security.pkcs11.SunPKCS11")
          blacklist (filter #(instance? klass %) (Security/getProviders))]
      (doseq [provider blacklist]
        (log/info (str "Removing buggy security provider " provider))
        (Security/removeProvider (.getName provider))))
    (catch ClassNotFoundException e)
    (catch Throwable e
      (log/error e "Could not remove security providers; HTTPS may not work!"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schemas

(def ProxyTarget
  {:host schema/Str
   :path schema/Str
   :port schema/Int})

(def CommonOptions
  {(schema/optional-key :server-id) schema/Keyword
   (schema/optional-key :redirect-if-no-trailing-slash) schema/Bool})

(def ContextHandlerOptions
  (assoc CommonOptions (schema/optional-key :context-listeners) [ServletContextListener]
                        (schema/optional-key :follow-links) schema/Bool))

(def ServletHandlerOptions
  (assoc CommonOptions (schema/optional-key :servlet-init-params) {schema/Str schema/Str}))

(def ProxySslConfig
  (merge config/WebserverSslPemConfig
         {(schema/optional-key :cipher-suites) [schema/Str]
          (schema/optional-key :protocols)     (schema/maybe [schema/Str])}))

(def ProxyOptions
  (assoc CommonOptions
    (schema/optional-key :scheme) (schema/enum :orig :http :https
                                               "orig" "http" "https")
    (schema/optional-key :ssl-config) (schema/conditional
                                        keyword? (schema/eq :use-server-config)
                                        map?     ProxySslConfig)
    (schema/optional-key :rewrite-uri-callback-fn) (schema/pred ifn?)
    (schema/optional-key :callback-fn) (schema/pred ifn?)
    (schema/optional-key :failure-callback-fn) (schema/pred ifn?)
    (schema/optional-key :request-buffer-size) schema/Int
    (schema/optional-key :follow-redirects) schema/Bool
    (schema/optional-key :idle-timeout) (schema/both schema/Int
                                                     (schema/pred pos?))))

(def ServerContext
  {:state     Atom
   :handlers  ContextHandlerCollection
   :server    (schema/maybe Server)})

(def ContextEndpoint
  {:type                                    (schema/eq :context)
   :base-path                               schema/Str
   (schema/optional-key :context-listeners) (schema/maybe [ServletContextListener])})

(def RingEndpoint
  {:type     (schema/eq :ring)})

(def WebsocketEndpoint
  {:type     (schema/eq :websocket)})

(def ServletEndpoint
  {:type     (schema/eq :servlet)
   :servlet  java.lang.Class})

(def WarEndpoint
  {:type     (schema/eq :war)
   :war-path schema/Str})

(def ProxyEndpoint
  {:type        (schema/eq :proxy)
   :target-host schema/Str
   :target-port schema/Int
   :target-path schema/Str})

(def Endpoint
  (schema/conditional
    #(-> % :type (= :context)) ContextEndpoint
    #(-> % :type (= :ring)) RingEndpoint
    #(-> % :type (= :websocket)) WebsocketEndpoint
    #(-> % :type (= :servlet)) ServletEndpoint
    #(-> % :type (= :war)) WarEndpoint
    #(-> % :type (= :proxy)) ProxyEndpoint))

(def RegisteredEndpoints
  {schema/Str [Endpoint]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Utility Functions

(defn- remove-leading-slash
  [s]
  (str/replace s #"^\/" ""))

(defn- with-leading-slash
  [s]
  (if (.startsWith s "/")
    s
    (str "/" s)))

(schema/defn ^:always-validate started? :- Boolean
  "A predicate that indicates whether or not the webserver-context contains a Jetty
  Server object."
  [webserver-context :- ServerContext]
  (instance? Server (:server webserver-context)))

(schema/defn ^:always-validate
  merge-webserver-overrides-with-options :- config/WebserverRawConfig
  "Merge any overrides made to the webserver config settings with the supplied
   options."
  [webserver-context :- ServerContext
   options :- config/WebserverRawConfig]
  (let [overrides (:overrides (swap! (:state webserver-context)
                                     assoc
                                     :overrides-read-by-webserver
                                     true))]
    (doseq [key (keys overrides)]
      (log/info (str "webserver config overridden for key '" (name key) "'")))
    (merge options overrides)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; SSL Context Functions

(schema/defn ^:always-validate
  ssl-context-factory :- SslContextFactory
  "Creates a new SslContextFactory instance from a map of SSL config options."
  [{:keys [keystore-config client-auth ssl-crl-path cipher-suites protocols]}
   :- config/WebserverSslContextFactory]
  (if (some #(= "sslv3" %) (map str/lower-case protocols))
    (log/warn (str "`ssl-protocols` contains SSLv3, a protocol with known "
                  "vulnerabilities; we recommend removing it from the "
                  "`ssl-protocols` list")))

  (let [context (doto (SslContextFactory.)
                  (.setKeyStore (:keystore keystore-config))
                  (.setKeyStorePassword (:key-password keystore-config))
                  (.setTrustStore (:truststore keystore-config))
                  ;; Need to clear out the default cipher suite exclude list so
                  ;; that Jetty doesn't potentially remove one or more ciphers
                  ;; that we want to be included.
                  (.setExcludeCipherSuites (into-array String []))
                  (.setIncludeCipherSuites (into-array String cipher-suites))
                  ;; Need to clear out the default protocols exclude list so
                  ;; that Jetty doesn't potentially remove one or more protocols
                  ;; that we want to be included.
                  (.setExcludeProtocols (into-array String []))
                  (.setIncludeProtocols (into-array String protocols)))]
    (if (:trust-password keystore-config)
      (.setTrustStorePassword context (:trust-password keystore-config)))
    (case client-auth
      :need (.setNeedClientAuth context true)
      :want (.setWantClientAuth context true)
      nil)
    (when ssl-crl-path
      (.setCrlPath context ssl-crl-path)
      ; .setValidatePeerCerts needs to be called with a value of 'true' in
      ; order to force Jetty to actually use the CRL when validating client
      ; certificates for a connection.
      (.setValidatePeerCerts context true))
    context))

(schema/defn ^:always-validate
  get-proxy-client-context-factory :- SslContextFactory
  [ssl-config :- ProxySslConfig]
  (ssl-context-factory {:keystore-config
                         (config/pem-ssl-config->keystore-ssl-config
                           ssl-config)
                        :client-auth :none
                        :cipher-suites (or (:cipher-suites ssl-config) config/acceptable-ciphers)
                        :protocols     (or (:protocols ssl-config) config/default-protocols)}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Jetty Server / Connector Functions

(defn- http-configuration
  [request-header-size]
  (let [http-config (doto (HttpConfiguration.)
                      (.setSendDateHeader true))]
    (if request-header-size
      (.setRequestHeaderSize http-config request-header-size))
    http-config))

(defn- connection-factories
  [request-header-size ssl-ctxt-factory]
  (let [http-config (http-configuration request-header-size)
        factories   (into-array ConnectionFactory
                                [(HttpConnectionFactory. http-config)])]
    (if ssl-ctxt-factory
      (AbstractConnectionFactory/getFactories
        ssl-ctxt-factory factories)
      factories)))

(defn- thread-count
  [setting]
  (if setting setting -1))

(schema/defn ^:always-validate
  connector* :- ServerConnector
  [server :- Server
   config :- (merge config/WebserverConnector
                    {schema/Keyword schema/Any})
   ssl-ctxt-factory :- (schema/maybe SslContextFactory)]
  (let [request-size (:request-header-max-size config)
        connector   (doto (ServerConnector.
                            server
                            nil nil nil
                            (thread-count (:acceptor-threads config))
                            (thread-count (:selector-threads config))
                            (connection-factories request-size ssl-ctxt-factory))
                      (.setPort (:port config))
                      (.setHost (:host config)))]
    (when-let [linger-millis (:so-linger-milliseconds config)]
      (.setSoLingerTime connector linger-millis))
    (when-let [idle-timeout (:idle-timeout-milliseconds config)]
      (.setIdleTimeout connector idle-timeout))
    connector))

(schema/defn ^:always-validate
  ssl-connector  :- ServerConnector
  "Creates a ssl ServerConnector instance."
  [server            :- Server
   ssl-ctxt-factory  :- SslContextFactory
   config :- config/WebserverSslConnector]
  (connector* server config ssl-ctxt-factory))

(schema/defn ^:always-validate
  plaintext-connector :- ServerConnector
  [server :- Server
   config :- config/WebserverConnector]
  (connector* server config nil))

(schema/defn ^:always-validate
  queue-thread-pool :- (schema/maybe QueuedThreadPool)
  [max-threads :- (schema/maybe schema/Int)
   queue-max-size :- (schema/maybe schema/Int)]
  (if (or max-threads queue-max-size)
    (let [thread-pool (if max-threads
                        (QueuedThreadPool. max-threads)
                        (QueuedThreadPool.))]
      (if queue-max-size
        ;; The code below is definitely not ideal, but there isn't a way to set
        ;; the maximum capacity of the QueuedThreadPool's BlockingArrayQueue
        ;; after construction.  We're trying to avoid hard-coding our own
        ;; defaults for other settings that we want Jetty to control, e.g., the
        ;; initial capacity of the queue and minimum number of threads.  By
        ;; reconstructing the QueuedThreadPool here, we can use Jetty's defaults
        ;; for settings unrelated to `queue-max-size`.
        ;;
        ;; QueuedThreadPool and BlockingArrayQueue construction isn't too
        ;; expensive.  It mostly involves some initial memory allocations.  The
        ;; more expensive work - where threads are actually started and the
        ;; queue expands to fulfill new requests - would only happen after the
        ;; QueuedThreadPool were started.  That won't happen for the
        ;; `thread-pool` instance from above, which just gets thrown away as
        ;; this function falls out of scope without ever having been started.
        ;; Also, this function would likely only be called once per server
        ;; startup where a `queue-max-size` were configured.
        ;;
        ;; The QueuedThreadPool constructor sets the `queue-capacity` and
        ;; `queue-grow-by` based on the minimum number of threads available
        ;; in the pool.  See https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-util/src/main/java/org/eclipse/jetty/util/thread/QueuedThreadPool.java#L92-L96.
        ;; That algorithm is essentially duplicated here, with the only
        ;; difference being that if `queue-max-size` is smaller than the
        ;; minimum number of threads, the `queue-capacity` and `queue-grow-by`
        ;; are reduced to the `queue-max-size` in order to avoid the
        ;; BlockingArrayQueue constructor throwing an IllegalArgumentException.
        (let [min-threads    (.getMinThreads thread-pool)
              queue-capacity (min queue-max-size min-threads)
              queue-grow-by  (min queue-max-size min-threads)]
          (QueuedThreadPool. (.getMaxThreads thread-pool)
                             min-threads
                             (.getIdleTimeout thread-pool)
                             (BlockingArrayQueue.
                               queue-capacity
                               queue-grow-by
                               queue-max-size)))
        thread-pool))))

(schema/defn ^:always-validate
  create-server :- Server
  "Construct a Jetty Server instance."
  [webserver-context :- ServerContext
   config :- config/WebserverConfig]
  (let [server (if-let [pool (queue-thread-pool (:max-threads config)
                                                (:queue-max-size config))]
                 (Server. pool)
                 (Server.))]
    (when (:jmx-enable config)
      (let [mb-container (MBeanContainer. (ManagementFactory/getPlatformMBeanServer))]
        (doto server
          (.addEventListener mb-container)
          (.addBean mb-container))))
    (when (:http config)
      (let [connector (plaintext-connector server (:http config))]
        (.addConnector server connector)))
    (when-let [https (:https config)]
      (let [ssl-ctxt-factory (ssl-context-factory
                               {:keystore-config (:keystore-config https)
                                :client-auth     (:client-auth https)
                                :ssl-crl-path    (:ssl-crl-path https)
                                :cipher-suites   (:cipher-suites https)
                                :protocols       (:protocols https)})
            connector        (ssl-connector server ssl-ctxt-factory https)]

        (.addConnector server connector)
        (swap! (:state webserver-context) assoc :ssl-context-factory ssl-ctxt-factory)))
    server))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; GZip Functions

;; TODO: make all of this gzip-mime-type stuff configurable
(defn- gzip-excluded-mime-types
  "Build up a list of mime types that should not be candidates for
  gzip compression in responses."
  []
  (->
    ;; This code is ported from Jetty 9.0.5's GzipFilter class.  In
    ;; Jetty 7, this behavior was the default for GzipHandler as well
    ;; as GzipFilter, but in Jetty 9.0.5 the GzipHandler no longer
    ;; includes this, so we need to do it by hand.
    (filter #(or (.startsWith % "image/")
                 (.startsWith % "audio/")
                 (.startsWith % "video/"))
            (MimeTypes/getKnownMimeTypes))
    (conj "application/compress" "application/zip" "application/gzip" "text/event-stream")
    (HashSet.)))

(defn- gzip-handler
  "Given a handler, wrap it with a GzipHandler that will compress the response
  when appropriate."
  [handler]
  (doto (GzipHandler.)
    (.setHandler handler)
    (.setMimeTypes (gzip-excluded-mime-types))
    (.setExcludeMimeTypes true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Handler Helper Functions

(schema/defn ^:always-validate
  add-handler :- ContextHandler
  [webserver-context :- ServerContext
   handler :- ContextHandler
   enable-trailing-slash-redirect?]
  (.setAllowNullPathInfo handler (not enable-trailing-slash-redirect?))
  (.addHandler (:handlers webserver-context) handler)
  ;; If this handler is being added after the server has been started, we
  ;; need to mark the handler as "managed" so that the server will stop the
  ;; handler when the server is stopped.  We also need to manually start the
  ;; handler.  The server takes care of marking handlers as managed and starting
  ;; them if the handlers are already registered when the server is started.
  (if-let [server (.getServer handler)]
    (when (and (.isRunning server) (not (.isRunning handler)))
      (.manage (:handlers webserver-context) handler)
      (.start handler)))
  handler)

(defn- ring-handler
  "Returns an Jetty Handler implementation for the given Ring handler."
  [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request request response]
      (let [request-map  (servlet/build-request-map request)
            response-map (handler request-map)]
        (when response-map
          (servlet/update-servlet-response response response-map)
          (.setHandled base-request true))))))

(schema/defn ^:always-validate
  proxy-servlet :- ProxyServlet
  "Create an instance of Jetty's `ProxyServlet` that will proxy requests at
  a given context to another host."
  [webserver-context :- ServerContext
   target :- ProxyTarget
   options :- ProxyOptions]
  (let [custom-ssl-ctxt-factory (when (map? (:ssl-config options))
                                  (get-proxy-client-context-factory
                                    (:ssl-config options)))
        {:keys [request-buffer-size idle-timeout]} options]
    (proxy [ProxyServlet] []
      (rewriteURI [req]
        (let [query (.getQueryString req)
              scheme (let [target-scheme (:scheme options)]
                       (condp = target-scheme
                         nil (.getScheme req)
                         :orig (.getScheme req)
                         "orig" (.getScheme req)
                         :http "http"
                         :https "https"
                         "http" target-scheme
                         "https" target-scheme))
              context-path (.getPathInfo req)]
          (let [target-uri (URI. scheme
                                 nil
                                 (:host target)
                                 (:port target)
                                 (with-leading-slash
                                   (URIUtil/addPaths (:path target) context-path))
                                 (codec/url-decode (str query))
                                 nil)]
            (if-let [rewrite-uri-callback-fn (:rewrite-uri-callback-fn options)]
              (rewrite-uri-callback-fn target-uri req)
              target-uri))))

      (newHttpClient []
        (let [client (if custom-ssl-ctxt-factory
                       (HttpClient. custom-ssl-ctxt-factory)
                       (if-let [ssl-ctxt-factory (:ssl-context-factory
                                                   @(:state webserver-context))]
                         (HttpClient. ssl-ctxt-factory)
                         (HttpClient.)))]
          (when request-buffer-size
            (.setRequestBufferSize client request-buffer-size))
          client))

      (createHttpClient []
        (let [client (proxy-super createHttpClient)
              timeout (when idle-timeout
                        (* 1000 idle-timeout))]
          (if (:follow-redirects options)
            (.setFollowRedirects client true)
            (.setFollowRedirects client false))
          (when timeout
            (.setIdleTimeout client timeout))
          client))

      (customizeProxyRequest [proxy-req req]
        (if-let [callback-fn (:callback-fn options)]
         (callback-fn proxy-req req)))

      ;; The implementation of onResponseFailure is duplicated heavily from:
      ;; https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/jetty-proxy/src/main/java/org/eclipse/jetty/proxy/AbstractProxyServlet.java#L576-L607
      ;; The only significant difference is that a 'failure-callback-fn', if
      ;; defined in options, is invoked just prior to completing the async
      ;; context for cases that the response was not already committed upstream.
      (onResponseFailure [req resp proxy-resp failure]
        (do
          (let [request-id    (.getRequestId this req)
                async-context (.getAsyncContext req)]
            (log/debug failure (str request-id " proxying failed"))
            (if (.isCommitted resp)
              (try
                (.sendError resp -1)
                (.complete async-context)
                (catch IOException _
                  (log/debug failure (str request-id
                                          " could not close the connection"))))
              (do
                (.resetBuffer resp)
                (if (instance? TimeoutException failure)
                  (.setStatus resp HttpServletResponse/SC_GATEWAY_TIMEOUT)
                  (.setStatus resp HttpServletResponse/SC_BAD_GATEWAY))
                (.setHeader resp
                            (.asString HttpHeader/CONNECTION)
                            (.asString HttpHeaderValue/CLOSE))
                (when-let [failure-callback-fn (:failure-callback-fn options)]
                  (failure-callback-fn req resp proxy-resp failure))
                (.complete async-context)))))))))

(schema/defn ^:always-validate
  register-endpoint!
  [state :- Atom
   endpoint-map :- Endpoint
   endpoint :- schema/Str]
  (swap! state update-in [:endpoints endpoint] #(if (nil? %)
                                                   [endpoint-map]
                                                   (conj % endpoint-map))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(schema/defn ^:always-validate
  initialize-context :- ServerContext
  "Create a webserver-context which contains a HandlerCollection and a
  ContextHandlerCollection which can accept the addition of new handlers
  before the webserver is started."
  []
  (let [^ContextHandlerCollection chc (ContextHandlerCollection.)]
    {:handlers chc
     :state (atom {:endpoints {}})
     :server nil}))

; TODO move out of public
(schema/defn ^:always-validate
  merge-webserver-overrides-with-options :- config/WebserverRawConfig
  "Merge any overrides made to the webserver config settings with the supplied
   options."
  [webserver-context :- ServerContext
   options :- config/WebserverRawConfig]
  {:post [(map? %)]}
  (let [overrides (:overrides (swap! (:state webserver-context)
                                     assoc
                                     :overrides-read-by-webserver
                                     true))]
    (doseq [key (keys overrides)]
      (log/info (str "webserver config overridden for key '" (name key) "'")))
    (merge options overrides)))

(schema/defn ^:always-validate shutdown
  [webserver-context :- ServerContext]
  (when (started? webserver-context)
    (log/info "Shutting down web server.")
    (.stop (:server webserver-context))
    (log/info "Web server shutdown")))

(schema/defn ^:always-validate
  create-webserver :- ServerContext
    "Create a Jetty webserver according to the supplied options:

    :host                     - the hostname to listen on
    :port                     - the port to listen on (defaults to 8080)
    :ssl-host                 - the hostname to listen on for SSL connections
    :ssl-port                 - the SSL port to listen on (defaults to 8081)
    :max-threads              - the maximum number of threads to use (default 100)
    :request-header-max-size  - the maximum size of an HTTP request header (default 8192)
    :gzip-enable              - whether or not gzip compression can be applied
                                to the body of a response (default true)

    SSL may be configured via PEM files by providing all three of the following
    settings:

    :ssl-key      - a PEM file containing the host's private key
    :ssl-cert     - a PEM file containing the host's certificate or chain
    :ssl-ca-cert  - a PEM file containing the CA certificate

    or via JKS keystore files by providing all four of the following settings:

    :keystore       - the keystore to use for SSL connections
    :key-password   - the password to the keystore
    :truststore     - a truststore to use for SSL connections
    :trust-password - the password to the truststore

    Note that if SSL is being configured via PEM files, an optional
    :ssl-cert-chain setting may be included to specify a supplemental set
    of certificates to be appended to the first certificate from the :ssl-cert
    setting in order to construct the certificate chain.

    Other SSL settings:

    :client-auth   - SSL client certificate authenticate, may be set to :need,
                     :want or :none (defaults to :need)
    :cipher-suites - list of cryptographic ciphers to allow for incoming SSL connections
    :ssl-protocols - list of protocols to allow for incoming SSL connections"
  [webserver-context :- ServerContext
   options :- config/WebserverRawConfig]
  {:pre  [(map? options)]
   :post [(started? %)]}
  (let [config                (config/process-config
                                (merge-webserver-overrides-with-options
                                  webserver-context
                                  options))
        ^Server s             (create-server webserver-context config)
        ^HandlerCollection hc (HandlerCollection.)
        log-handler (config/maybe-init-log-handler options)]
    (.setHandlers hc (into-array Handler [(:handlers webserver-context)]))
    (let [shutdown-timeout (when (not (nil? (:shutdown-timeout-seconds options)))
                             (* 1000 (:shutdown-timeout-seconds options)))
          maybe-zipped (if (or (not (contains? options :gzip-enable))
                               (:gzip-enable options))
                         (gzip-handler hc)
                         hc)
          maybe-logged (if log-handler
                         (doto log-handler (.setHandler hc))
                         maybe-zipped)
          statistics-handler (if (or (nil? shutdown-timeout) (pos? shutdown-timeout))
                               (doto (StatisticsHandler.)
                                 (.setHandler maybe-logged))
                               maybe-logged)]
      (.setHandler s statistics-handler)
      (when shutdown-timeout
        (.setStopTimeout s shutdown-timeout))
      (when-let [script (:post-config-script options)]
        (config/execute-post-config-script! s script))
      (assoc webserver-context :server s))))

(schema/defn ^:always-validate start-webserver! :- ServerContext
  "Creates and starts a webserver.  Returns an updated context map containing
  the Server object."
  [webserver-context :- ServerContext
   config :- config/WebserverRawConfig]
  (let [webserver-context (create-webserver webserver-context config)]
    (log/info "Starting web server.")
    (try
      (.start (:server webserver-context))
      (catch Exception e
        (log/error
          e
          "Encountered error starting web server, so shutting down")
        (shutdown webserver-context)
        (throw e)))
    webserver-context))

(schema/defn ^:always-validate
  add-context-handler :- ContextHandler
  "Add a static content context handler (allow for customization of the context handler through javax.servlet.ServletContextListener implementations)"
  ([webserver-context base-path context-path]
   (add-context-handler webserver-context base-path context-path nil {:follow-links? false
                                                                      :enable-trailing-slash-redirect? false}))
  ([webserver-context :- ServerContext
    base-path :- schema/Str
    context-path :- schema/Str
    context-listeners :- (schema/maybe [ServletContextListener])
    options]
   (let [handler (ServletContextHandler. nil context-path ServletContextHandler/NO_SESSIONS)
         follow-links? (:follow-links? options)
         enable-trailing-slash-redirect? (:enable-trailing-slash-redirect? options)]
     (.setBaseResource handler (Resource/newResource base-path))
     (when follow-links?
       (.addAliasCheck handler (AllowSymLinkAliasChecker.)))
     ;; register servlet context listeners (if any)
     (when-not (nil? context-listeners)
       (dorun (map #(.addEventListener handler %) context-listeners)))
     (.addServlet handler (ServletHolder. (DefaultServlet.)) "/")
     (add-handler webserver-context handler enable-trailing-slash-redirect?))))

(schema/defn ^:always-validate
  add-ring-handler :- ContextHandler
  [webserver-context :- ServerContext
   handler :- (schema/pred ifn? 'ifn?)
   path :- schema/Str
   enable-trailing-slash-redirect?]
  (let [ctxt-handler (doto (ContextHandler. path)
                       (.setHandler (ring-handler handler)))]
    (add-handler webserver-context ctxt-handler enable-trailing-slash-redirect?)))

(schema/defn ^:always-validate
  add-websocket-handler :- ContextHandler
  [webserver-context :- ServerContext
   handlers :- websockets/WebsocketHandlers
   path :- schema/Str
   enable-trailing-slash-redirect?]
  (let [ctxt-handler (doto (ContextHandler. path)
                       (.setHandler (websockets/websocket-handler handlers)))]
    (add-handler webserver-context ctxt-handler enable-trailing-slash-redirect?)))

(schema/defn ^:always-validate
  add-servlet-handler :- ContextHandler
  [webserver-context :- ServerContext
   servlet :- Servlet
   path :- schema/Str
   servlet-init-params :- {schema/Any schema/Any}
   enable-trailing-slash-redirect?]
  (let [holder (doto (ServletHolder. servlet)
                 (.setInitParameters servlet-init-params))
        handler (doto (ServletContextHandler. ServletContextHandler/SESSIONS)
                  (.setContextPath path)
                  (.addServlet holder "/*"))]
    (add-handler webserver-context handler enable-trailing-slash-redirect?)))

(schema/defn ^:always-validate
  add-war-handler :- ContextHandler
  "Registers a WAR to Jetty. It takes two arguments: `[war path]`.
  - `war` is the file path or the URL to a WAR file
  - `path` is the URL prefix at which the WAR will be registered"
  [webserver-context :- ServerContext
   war :- schema/Str
   path :- schema/Str
   disable-redirects-no-slash?]
  (let [handler (doto (WebAppContext.)
                  (.setContextPath path)
                  (.setWar war))]
    (add-handler webserver-context handler disable-redirects-no-slash?)))

(schema/defn ^:always-validate
  add-proxy-route
  "Configures the Jetty server to proxy a given URL path to another host.

  `target` should be a map containing the keys :host, :port, and :path; where
  :path specifies the URL prefix to proxy to on the target host.

  `options` may contain the keys :scheme (legal values are :orig, :http, and
  :https), :ssl-config (value may be :use-server-config or a map containing
  :ssl-ca-cert, :ssl-cert, and :ssl-key), :rewrite-uri-callback-fn (a function
  taking two arguments, `[target-uri req]`, see README.md/#rewrite-uri-callback-fn),
  :callback-fn (a function taking two arguments, `[proxy-req req]`, see
  README.md/#callback-fn) and :failure-callback-fn (a function taking four arguments,
  `[req resp proxy-resp failure]`, see README.md/#failure-callback-fn).
  "
  [webserver-context :- ServerContext
   target :- ProxyTarget
   path :- schema/Str
   options :- ProxyOptions
   disable-redirects-no-slash?]
  (let [target (update-in target [:path] remove-leading-slash)]
    (add-servlet-handler webserver-context
                         (proxy-servlet webserver-context target options)
                         path
                         {}
                         disable-redirects-no-slash?)))

(schema/defn ^:always-validate
   get-registered-endpoints :- RegisteredEndpoints
   "Returns a map of registered endpoints for the given ServerContext.
    Each endpoint is registered as a key in the map, with its value
    being an array of maps, each representing a handler registered
    at that endpoint. Each of these maps contains the type of the
    handler under the :type key, and may contain additional information
    as well.

    When the value of :type is :context, the endpoint information will
    be an instance of ContextEndpoint.

    When the value of :type is :ring, the endpoint information will be
    an instance of RingEndpoint.

    When the value of :type is :servlet, the endpoint information will
    be an instance of ServletEndpoint.

    When the value of :type is :war, the endpoint information will be
    an instance of WarEndpoint.

    When the value of :type is :proxy, the endpoint information will be
    an instance of ProxyEndpoint."
   [webserver-context :- ServerContext]
   (:endpoints @(:state webserver-context)))

(schema/defn ^:always-validate
  override-webserver-settings! :- config/WebserverRawConfig
  "Override the settings in the webserver section of the service's config file
   with the set of options in the supplied overrides map.

   The map should contain a key/value pair for each setting to be overridden.
   The name of the setting to override should be expressed as a Clojure keyword.
   For any setting expressed in the service config which is not overridden, the
   setting value from the config will be used.

   For example, the webserver config may contain:

     [webserver]
     ssl-host    = 0.0.0.0
     ssl-port    = 9001
     ssl-cert    = mycert.pem
     ssl-key     = mykey.pem
     ssl-ca-cert = myca.pem

   The following overrides map may be supplied as an argument to the function:

     {:ssl-port 9002
      :ssl-cert \"myoverriddencert.pem\"
      :ssl-key  \"myoverriddenkey.pem\"}

   The effective settings used during webserver startup will be:

     {:ssl-host    \"0.0.0.0\"
      :ssl-port    9002
      :ssl-cert    \"myoverriddencert.pem\"
      :ssl-key     \"myoverriddenkey.pem\"
      :ssl-ca-cert \"myca.pem\"}

   The overridden webserver settings will be considered only at the point the
   webserver is being started -- during the start lifecycle phase of the
   webserver service.  For this reason, a call to this function must be made
   during a service's init lifecycle phase in order for the overridden
   settings to be considered.

   Only one call from a service may be made to this function during application
   startup.

   If a call is made to this function after webserver startup or after another
   call has already been made to this function (e.g., from other service),
   a java.lang.IllegalStateException will be thrown."
  [webserver-context :- ServerContext
   overrides :- config/WebserverRawConfig]
  ; Might be worth considering an implementation that only fails if the caller
  ; is trying to override a specific option that has been overridden already
  ; rather than blindly failing if an attempt is made to override any option.
  ; Could allow different services to override options that don't conflict with
  ; one another or for the same service to make multiple calls to this function
  ; for different settings.  Hard to know, though, when one setting has an
  ; adverse effect on another without putting a bunch of key-specific semantic
  ; setting parsing in this implementation.
  (:overrides
    (swap! (:state webserver-context)
           #(cond
             (:overrides-read-by-webserver %)
               (if (nil? (:overrides %))
                 (throw
                   (IllegalStateException.
                     (str "overrides cannot be set because webserver has "
                          "already processed the config")))
                 (throw
                   (IllegalStateException.
                     (str "overrides cannot be set because they have "
                          "already been set and webserver has already "
                          "processed the config"))))
             (nil? (:overrides %))
               (assoc % :overrides overrides)
             :else
               (throw
                 (IllegalStateException.
                   (str "overrides cannot be set because they have "
                        "already been set")))))))

(schema/defn ^:always-validate join
  [webserver-context :- ServerContext]
  {:pre [(started? webserver-context)]}
  (.join (:server webserver-context)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service Implementation Helper Functions

(defn start-server-single-default
  [context config]
  (let [default-context     (:default (:jetty9-servers context))
        webserver           (start-webserver! default-context config)
        server-context-list (assoc (:jetty9-servers context) :default webserver)]
    (assoc context :jetty9-servers server-context-list)))

(defn start-server-multiple
  [context config]
  (let [context-seq (for [[server-id server-context] (:jetty9-servers context)]
                      [server-id (start-webserver! server-context (server-id config))])]
    (assoc context :jetty9-servers (into {} context-seq))))

(defn get-server-context
  [service-context server-id]
  (let [server-id (if (nil? server-id)
                    (:default-server service-context)
                    server-id)]
    (when-not server-id
      (throw (IllegalArgumentException.
               (str "no server-id was specified for this operation and "
                    "no default server was specified in the configuration"))))
    (server-id (:jetty9-servers service-context))))

(defn get-default-server-from-config
  [config]
  (first (flatten (filter #(:default-server (second %)) config))))

(defn build-server-contexts
  [context config]
  (assoc context :jetty9-servers (into {} (for [[server-id] config]
                                            [server-id (initialize-context)]))
                 :default-server (get-default-server-from-config config)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Service Function Implementations

(schema/defn ^:always-validate add-context-handler!
  [context base-path context-path options :- ContextHandlerOptions]
  (let [defaults          {:context-listeners []}
        opts              (merge defaults options)
        server-id         (:server-id opts)
        context-listeners (:context-listeners opts)
        s                 (get-server-context context server-id)
        state             (:state s)
        endpoint-map      {:type              :context
                           :base-path         base-path
                           :context-listeners context-listeners}
        enable-redirect  (:redirect-if-no-trailing-slash options)]
    (register-endpoint! state endpoint-map context-path)
    (add-context-handler s base-path context-path
                         context-listeners
                         {:follow-links?                   (:follow-links options)
                          :enable-trailing-slash-redirect? enable-redirect})))

(schema/defn ^:always-validate init!
  [context config :- config/WebserverServiceRawConfig]
  (let [old-config (schema/check config/WebserverRawConfig config)
        new-config (schema/check config/MultiWebserverRawConfig config)]
    (cond
      (nil? old-config)
        (let [context (assoc context :jetty9-servers {:default (initialize-context)}
                                     :default-server :default)]
          (doseq [content (:static-content config)]
            (add-context-handler! context (:resource content)
                                  (:path content) {:follow-links (true? (:follow-links content))}))
          context)
      (nil? new-config)
        (let [context (build-server-contexts context config)]
          (doseq [[server-id server-config] config
                  content (:static-content server-config)]
            (add-context-handler! context (:resource content)
                                  (:path content) {:server-id server-id
                                                   :follow-links (true? (:follow-links content))}))
          context))))

(schema/defn ^:always-validate start!
  [context config :- config/WebserverServiceRawConfig]
  (let [old-config (schema/check config/WebserverRawConfig config)
        new-config (schema/check config/MultiWebserverRawConfig config)]
    (cond
      (nil? old-config) (start-server-single-default context config)
      (nil? new-config) (start-server-multiple context config))))

(schema/defn ^:always-validate add-ring-handler!
  [context handler path options :- CommonOptions]
  (let [server-id     (:server-id options)
        s             (get-server-context context server-id)
        state         (:state s)
        endpoint-map  {:type     :ring}

        enable-redirect  (:redirect-if-no-trailing-slash options)]
    (register-endpoint! state endpoint-map path)
    (add-ring-handler s handler path enable-redirect)))

(schema/defn ^:always-validate add-websocket-handler!
  [context
   handlers :- websockets/WebsocketHandlers
   path :- schema/Str
   options :- CommonOptions]
  (let [server-id     (:server-id options)
        s             (get-server-context context server-id)
        state         (:state s)
        endpoint-map  {:type     :websocket}
        enable-redirect  (:redirect-if-no-trailing-slash options)]
    (register-endpoint! state endpoint-map path)
    (add-websocket-handler s handlers path enable-redirect)))

(schema/defn ^:always-validate add-servlet-handler!
  [context servlet path options :- ServletHandlerOptions]
  (let [defaults            {:servlet-init-params {}}
        opts                (merge defaults options)
        server-id           (:server-id opts)
        servlet-init-params (:servlet-init-params opts)
        s                   (get-server-context context server-id)
        state               (:state s)
        endpoint-map        {:type     :servlet
                             :servlet  (type servlet)}

        enable-redirect  (:redirect-if-no-trailing-slash options)]
    (register-endpoint! state endpoint-map path)
    (add-servlet-handler s servlet path servlet-init-params enable-redirect)))

(schema/defn ^:always-validate add-war-handler!
  [context war path options :- CommonOptions]
  (let [server-id     (:server-id options)
        s             (get-server-context context server-id)
        state         (:state s)
        endpoint-map  {:type     :war
                       :war-path war}

        enable-redirect  (:redirect-if-no-trailing-slash options)]
    (register-endpoint! state endpoint-map path)
    (add-war-handler s war path enable-redirect)))

(schema/defn ^:always-validate add-proxy-route!
  [context target path options]
  (let [server-id     (:server-id options)
        s             (get-server-context context server-id)
        state         (:state s)
        endpoint-map  {:type        :proxy
                       :target-host (:host target)
                       :target-port (:port target)
                       :target-path (:path target)}
        enable-redirect  (:redirect-if-no-trailing-slash options)]
    (register-endpoint! state endpoint-map path)
    (add-proxy-route s target path options enable-redirect)))
