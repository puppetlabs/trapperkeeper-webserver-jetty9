;; NOTE: this code is an adaptation of ring-jetty-handler.
;;  It adds some SSL utility functions, and
;;  provides the ability to dynamically register ring handlers.

(ns puppetlabs.trapperkeeper.services.webserver.jetty9-core
  "Adapter for the Jetty webserver."
  (:import (org.eclipse.jetty.server Handler Server Request ServerConnector
                                     HttpConfiguration HttpConnectionFactory
                                     ConnectionFactory)
           (org.eclipse.jetty.server.handler AbstractHandler ContextHandler HandlerCollection ContextHandlerCollection)
           (org.eclipse.jetty.util.resource Resource)
           (org.eclipse.jetty.util.thread QueuedThreadPool)
           (org.eclipse.jetty.util.ssl SslContextFactory)
           (javax.servlet.http HttpServletRequest HttpServletResponse)
           (java.util.concurrent Executors)
           (org.eclipse.jetty.servlets.gzip GzipHandler)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHolder DefaultServlet)
           (org.eclipse.jetty.webapp WebAppContext)
           (java.util HashSet)
           (org.eclipse.jetty.http MimeTypes)
           (javax.servlet Servlet ServletContextListener)
           (java.io File)
           (org.eclipse.jetty.proxy ProxyServlet)
           (java.net URI)
           (java.security Security)
           (org.eclipse.jetty.client HttpClient))
  (:require [ring.util.servlet :as servlet]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log]
            [puppetlabs.kitchensink.core :as ks]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as jetty-config]))

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

;; Due to weird issues between JSSE and OpenSSL clients on some 1.7
;; jdks when using Diffie-Hellman exchange, we need to only enable
;; RSA-based ciphers.
;;
;; https://forums.oracle.com/forums/thread.jspa?messageID=10999587
;; https://issues.apache.org/jira/browse/APLO-287
;;
;; This also applies to all JDK's with regards to EC algorithms causing
;; issues.
;;
(def acceptable-ciphers
  ["TLS_RSA_WITH_AES_256_CBC_SHA256"
   "TLS_RSA_WITH_AES_256_CBC_SHA"
   "TLS_RSA_WITH_AES_128_CBC_SHA256"
   "TLS_RSA_WITH_AES_128_CBC_SHA"
   "SSL_RSA_WITH_RC4_128_SHA"
   "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
   "SSL_RSA_WITH_RC4_128_MD5"])

(defn- remove-leading-slash
  [s]
  (str/replace s #"^\/" ""))

(defn has-config?
  "A predicate that indicates whether or not the webserver-context contains a
  ':config' map, used for capturing any configuration settings that can be
  atomically updated at run-time."
  [webserver-context]
  (and
    (map? webserver-context)
    (contains? webserver-context :config)
    (map? @(:config webserver-context))))

(defn has-handlers?
  "A predicate that indicates whether or not the webserver-context contains a
  ContextHandlerCollection which can have handlers attached to it."
  [webserver-context]
  (and
    (map? webserver-context)
    (instance? HandlerCollection (:handler-collection webserver-context))
    (instance? ContextHandlerCollection (:handlers webserver-context))))

(defn has-webserver?
  "A predicate that indicates whether or not the webserver-context contains a Jetty
  Server object."
  [webserver-context]
  (and
    (has-handlers? webserver-context)
    (instance? Server (:server webserver-context))))

(defn proxy-target?
  "A predicate that validates the format of a proxy target configuration map"
  [target]
  ;; TODO: should probably be using prismatic schema here (PE-3409)
  (and
    (map? target)
    (string? (:host target))
    (string? (:path target))
    (integer? (:port target))
    (> (:port target) 0)
    (<= (:port target) 65535)))

(defn proxy-options?
  "A predicate that validates the format of a proxy options configuration map"
  [options]
  ;; TODO: should probably be using prismatic schema here (PE-3409)
  (and
    (empty? (set/difference (ks/keyset options) #{:scheme :ssl-config}))
    (contains? #{nil :orig :http :https} (:scheme options))
    ((some-fn nil? map? #(= :use-server-config %)) (:ssl-config options))
    (or (not (map? (:ssl-config options)))
        (= #{:ssl-ca-cert :ssl-cert :ssl-key} (ks/keyset (:ssl-config options))))))

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

(defn- ssl-context-factory
  "Creates a new SslContextFactory instance from a map of options."
  [options]
  (let [context (SslContextFactory.)]
    (if (string? (options :keystore))
      (.setKeyStorePath context (options :keystore))
      (.setKeyStore context (options :keystore)))
    (.setKeyStorePassword context (options :key-password))
    (when (options :truststore)
      (if (string? (options :truststore))
        (.setTrustStorePath context (options :truststore))
        (.setTrustStore context (options :truststore))))
    (when (options :trust-password)
      (.setTrustStorePassword context (options :trust-password)))
    (case (options :client-auth)
      :need (.setNeedClientAuth context true)
      :want (.setWantClientAuth context true)
      nil)
    context))

(defn- connection-factory
  []
  (let [http-config (doto (HttpConfiguration.)
                      (.setSendDateHeader true))]
    (into-array ConnectionFactory
                [(HttpConnectionFactory. http-config)])))

(defn- ssl-connector
  "Creates a ssl ServerConnector instance."
  [server ssl-ctxt-factory options]
  (doto (ServerConnector. server ssl-ctxt-factory (connection-factory))
    (.setPort (options :ssl-port 443))
    (.setHost (options :host))))

(defn- plaintext-connector
  [server options]
  (doto (ServerConnector. server (connection-factory))
    (.setPort (options :port 80))
    (.setHost (options :host "localhost"))))

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

(defn- proxy-servlet
  "Create an instance of Jetty's `ProxyServlet` that will proxy requests at
  a given context to another host."
  [webserver-context target path options]
  {:pre [(has-config? webserver-context)
         (has-handlers? webserver-context)
         (proxy-target? target)
         (proxy-options? options)]}
  (let [custom-ssl-ctxt-factory (when (map? (:ssl-config options))
                                  (-> (:ssl-config options)
                                      jetty-config/configure-web-server-ssl-from-pems
                                      ssl-context-factory))]
    (proxy [ProxyServlet] []
      (rewriteURI [req]
        (let [query (.getQueryString req)
              scheme (let [target-scheme (:scheme options)]
                       (condp = target-scheme
                         nil (.getScheme req)
                         :orig (.getScheme req)
                         :http "http"
                         :https "https"))
              context-path (.getPathInfo req)
              uri (str scheme "://" (:host target) ":" (:port target)
                       "/" (:path target) context-path)]
          (when query
            (.append uri "?")
            (.append uri query))
          (URI/create (.toString uri))))

      (newHttpClient []
        (if custom-ssl-ctxt-factory
          (HttpClient. custom-ssl-ctxt-factory)
          (if-let [ssl-ctxt-factory (:ssl-context-factory @(:config webserver-context))]
            (HttpClient. ssl-ctxt-factory)
            (HttpClient.)))))))

(defn- create-server
  "Construct a Jetty Server instance."
  [webserver-context options]
  (let [server (Server. (QueuedThreadPool. (options :max-threads)))]
    (when (options :port)
      (let [connector (plaintext-connector server options)]
        (.addConnector server connector)))
    (when (or (options :ssl?) (options :ssl-port))
      (let [ssl-host          (options :ssl-host (options :host "localhost"))
            options           (assoc options :host ssl-host)
            ssl-ctxt-factory  (ssl-context-factory options)
            connector         (ssl-connector server ssl-ctxt-factory options)
            ciphers           (if-let [txt (options :cipher-suites)]
                                (map str/trim (str/split txt #","))
                                acceptable-ciphers)
            protocols         (if-let [txt (options :ssl-protocols)]
                                (map str/trim (str/split txt #",")))]
        (when ciphers
          (.setIncludeCipherSuites ssl-ctxt-factory (into-array ciphers))
          (when protocols
            (.setIncludeProtocols ssl-ctxt-factory (into-array protocols))))
        (.addConnector server connector)
        (swap! (:config webserver-context) assoc :ssl-context-factory ssl-ctxt-factory)))
    server))

(defn- merge-webserver-overrides-with-options
  "Merge any overrides made to the webserver config settings with the supplied
   options."
  [options webserver-context]
  {:pre  [(has-config? webserver-context)]
   :post [(map? %)]}
  (let [overrides (:overrides (swap! (:config webserver-context)
                                     assoc
                                     :overrides-read-by-webserver
                                     true))]
    (doseq [key (keys overrides)]
      (log/info (str "webserver config overridden for key '" (name key) "'")))
    (merge options overrides)))

;; Functions for trapperkeeper 'webserver' interface

(defn create-webserver
    "Create a Jetty webserver according to the supplied options:

    :configurator - a function called with the Jetty Server instance
    :port         - the port to listen on (defaults to 8080)
    :host         - the hostname to listen on
    :join?        - blocks the thread until server ends (defaults to true)
    :ssl?         - allow connections over HTTPS
    :ssl-port     - the SSL port to listen on (defaults to 443, implies :ssl?)
    :keystore     - the keystore to use for SSL connections
    :key-password - the password to the keystore
    :truststore   - a truststore to use for SSL connections
    :trust-password - the password to the truststore
    :max-threads  - the maximum number of threads to use (default 50)
    :client-auth  - SSL client certificate authenticate, may be set to :need,
                    :want or :none (defaults to :none)

    ws is a map containing the :handlers collection which should have been previously
    created by create-handlers."
  [options webserver-context]
  {:pre [(map? options)
         (has-config? webserver-context)
         (has-handlers? webserver-context)]
   :post [(has-webserver? %)]}
  (let [options   (jetty-config/configure-web-server
                    (merge-webserver-overrides-with-options options
                                                            webserver-context))
        ^Server s (create-server webserver-context (dissoc options :configurator))]
    (.setHandler s (gzip-handler (:handler-collection webserver-context)))
    (when-let [configurator (:configurator options)]
      (configurator s))
    (assoc webserver-context :server s)))

(defn create-handlers
  "Create a webserver-context which contains a HandlerCollection and a
  ContextHandlerCollection which can accept the addition of new handlers
  before the webserver is started."
  []
  {:post [(has-handlers? %)]}
  (let [^ContextHandlerCollection chc (ContextHandlerCollection.)
        ^HandlerCollection hc         (HandlerCollection.)]
    (.setHandlers hc (into-array Handler [chc]))
    {:handler-collection hc
     :handlers chc
     :config (atom {})}))

(defn start-webserver
  "Starts a webserver that has been previously created and added to the
  webserver-context by `create-webserver`"
  [webserver-context]
  {:pre [(has-webserver? webserver-context)]}
  (.start (:server webserver-context)))

(defn add-handler
  [webserver-context handler]
  {:pre [(has-handlers? webserver-context)
         (instance? ContextHandler handler)]}
  (.addHandler (:handlers webserver-context) handler)
  handler)

(defn add-context-handler
  "Add a static content context handler (allow for customization of the context handler through javax.servlet.ServletContextListener implementations)"
  ([webserver-context base-path context-path]
   (add-context-handler webserver-context base-path context-path nil))
  ([webserver-context base-path context-path context-listeners]
   {:pre [(has-handlers? webserver-context)
          (string? base-path)
          (string? context-path)
          (or (nil? context-listeners)
              (and (sequential? context-listeners)
                   (every? #(instance? ServletContextListener %) context-listeners)))]}
   (let [handler (ServletContextHandler. nil context-path ServletContextHandler/NO_SESSIONS)]
     (.setBaseResource handler (Resource/newResource base-path))
     ;; register servlet context listeners (if any)
     (when-not (nil? context-listeners)
       (dorun (map #(.addEventListener handler %) context-listeners)))
     (.addServlet handler (ServletHolder. (DefaultServlet.)) "/")
     (add-handler webserver-context handler))))

(defn add-ring-handler
  [webserver-context handler path]
  {:pre [(has-handlers? webserver-context)
         (ifn? handler)
         (string? path)]}
  (let [ctxt-handler (doto (ContextHandler. path)
                       (.setHandler (ring-handler handler)))]
    (add-handler webserver-context ctxt-handler)))

(defn add-servlet-handler
  ([webserver-context servlet path]
   (add-servlet-handler webserver-context servlet path {}))
  ([webserver-context servlet path servlet-init-params]
   {:pre [(has-handlers? webserver-context)
          (instance? Servlet servlet)
          (string? path)
          (map? servlet-init-params)]}
   (let [holder   (doto (ServletHolder. servlet)
                    (.setInitParameters servlet-init-params))
         handler  (doto (ServletContextHandler. ServletContextHandler/SESSIONS)
                    (.setContextPath path)
                    (.addServlet holder "/*"))]
     (add-handler webserver-context handler))))

(defn add-war-handler
  "Registers a WAR to Jetty. It takes two arguments: `[war path]`.
  - `war` is the file path or the URL to a WAR file
  - `path` is the URL prefix at which the WAR will be registered"
  [webserver-context war path]
  {:pre [(has-handlers? webserver-context)
         (string? war)
         (string? path)]}
  (let [handler (doto (WebAppContext.)
                  (.setContextPath path)
                  (.setWar war))]
    (add-handler webserver-context handler)))

(defn add-proxy-route
  "Configures the Jetty server to proxy a given URL path to another host.

  `target` should be a map containing the keys :host, :port, and :path; where
  :path specifies the URL prefix to proxy to on the target host.

  `options` may contain the keys :scheme (legal values are :orig, :http, and :https)
  and :ssl-config (value may be :use-server-config or a map containing :ssl-ca-cert,
  :ssl-cert, and :ssl-key).
  "
  [webserver-context target path options]
  {:pre [(has-handlers? webserver-context)
         (proxy-target? target)
         (proxy-options? options)
         (string? path)]}
  (let [target (update-in target [:path] remove-leading-slash)]
    (add-servlet-handler webserver-context
                         (proxy-servlet webserver-context target path options)
                         path)))

(defn override-webserver-settings!
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
  [webserver-context overrides]
  {:pre  [(has-config? webserver-context)
          (map? overrides)]
   :post [(map? %)]}
  ; Might be worth considering an implementation that only fails if the caller
  ; is trying to override a specific option that has been overridden already
  ; rather than blindly failing if an attempt is made to override any option.
  ; Could allow different services to override options that don't conflict with
  ; one another or for the same service to make multiple calls to this function
  ; for different settings.  Hard to know, though, when one setting has an
  ; adverse effect on another without putting a bunch of key-specific semantic
  ; setting parsing in this implementation.
  (:overrides
    (swap! (:config webserver-context)
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

(defn join
  [webserver-context]
  {:pre [(has-webserver? webserver-context)]}
  (.join (:server webserver-context)))

(defn shutdown
  [webserver-context]
  {:pre [((some-fn nil? map?) webserver-context)]}
  (when (has-webserver? webserver-context)
    (log/info "Stopping Jetty server.")
    (.stop (:server webserver-context))))

