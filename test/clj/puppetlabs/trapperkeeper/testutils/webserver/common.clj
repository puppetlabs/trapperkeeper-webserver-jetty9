(ns puppetlabs.trapperkeeper.testutils.webserver.common
  (:require
    [puppetlabs.http.client.sync :as http-client])
  (:import
   (appender TestListAppender)
   (com.puppetlabs.ssl_utils SSLUtils)))

(defn http-get
  ([url]
   (http-get url {:as :text}))
  ([url options]
   (http-client/get url options)))

(defn http-put
  [url options]
  (http-client/put url options))

(def jetty-plaintext-config
  {:webserver {:port 8080}})

(def jetty-plaintext-large-request-config
  {:webserver {:port 8080
               :request-header-max-size 16192}})

(def jetty-multiserver-plaintext-config
  {:webserver {:foo            {:port 8085}
               :bar            {:port           8080
                                :default-server true}}})

(defn jetty-ssl-jks-config
  []
  {:webserver {:port 8080
               :ssl-host "0.0.0.0"
               :ssl-port 8081
               :keystore (str "./dev-resources/config/jetty/ssl/keystore." (if (SSLUtils/isFIPS) "bcfks" "jks"))
               :truststore (str "./dev-resources/config/jetty/ssl/truststore." (if (SSLUtils/isFIPS) "bcfks" "jks"))
               :key-password "Kq8lG9LkISky9cDIYysiadxRx"
               :trust-password "Kq8lG9LkISky9cDIYysiadxRx"}})

(def jetty-ssl-pem-config
  {:webserver {:port        8080
               :ssl-host    "0.0.0.0"
               :ssl-port    8081
               :ssl-cert    "./dev-resources/config/jetty/ssl/certs/localhost.pem"
               :ssl-key     "./dev-resources/config/jetty/ssl/private_keys/localhost.pem"
               :ssl-ca-cert "./dev-resources/config/jetty/ssl/certs/ca.pem"}})

(def jetty-ssl-client-need-config
  (assoc-in jetty-ssl-pem-config [:webserver :client-auth] "need"))

(def jetty-ssl-client-want-config
  (assoc-in jetty-ssl-pem-config [:webserver :client-auth] "want"))

(def jetty-ssl-client-none-config
  (assoc-in jetty-ssl-pem-config [:webserver :client-auth] "none"))

(def default-options-for-https-client
  {:ssl-cert "./dev-resources/config/jetty/ssl/certs/localhost.pem"
   :ssl-key  "./dev-resources/config/jetty/ssl/private_keys/localhost.pem"
   :ssl-ca-cert "./dev-resources/config/jetty/ssl/certs/ca.pem"
   :as :text})

(def absurdly-large-cookie
  (apply str (repeat 8192 "a")))

(def dev-resources-dir        "./dev-resources/")


(defmacro with-test-access-logging
  "Executes a test block and clears any messages saved to the TestListAppender
  before and afterwards."
  [& body]
  `(do
     (.clear (TestListAppender/list))
     (try
       (do ~@body)
       (finally
         (.clear (TestListAppender/list))))))
