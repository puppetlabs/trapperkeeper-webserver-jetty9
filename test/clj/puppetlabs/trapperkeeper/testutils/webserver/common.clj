(ns puppetlabs.trapperkeeper.testutils.webserver.common
  (:require [puppetlabs.http.client.sync :as http-client]))

(defn http-get
  ([url]
   (http-get url {:as :text}))
  ([url options]
   (http-client/get url options)))

(def jetty-plaintext-config
  {:webserver {:port 8080}})

(def jetty-ssl-jks-config
  {:webserver {:port            8080
               :ssl-host        "0.0.0.0"
               :ssl-port        8081
               :keystore        "./dev-resources/config/jetty/ssl/keystore.jks"
               :truststore      "./dev-resources/config/jetty/ssl/truststore.jks"
               :key-password    "Kq8lG9LkISky9cDIYysiadxRx"
               :trust-password  "Kq8lG9LkISky9cDIYysiadxRx"}})

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