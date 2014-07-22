(ns puppetlabs.trapperkeeper.testutils.webserver.common
  (:require [puppetlabs.http.client.sync :as http-client]))

(defn http-get
  ([url]
   (http-get url {:as :text}))
  ([url options]
   (http-client/get url options)))

(def jetty-plaintext-config
  {:webserver {:port 8080}})

(def jetty-multiserver-plaintext-config
  {:webserver {:ziggy   {:port 8085}
               :default {:port 8080}}})

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

(def absurdly-large-cookie
  (str
    "puppet_enterprise_console=BAh7DEkiD3Nlc3Npb25faWQGOgZFRiJFMWZjMjVjMzk2NzM5MW"
    "ZmZDQ3NDEx%0AYzE2ZDNjNWQ5OTlmNWExYTVjOGU5NzA2OGQ0OWZhMDdjZWMwYzY3NzI5NUki%0AH"
    "kNPTlNPTEVfQVVUSF9BQ0NPVU5UX05BTUUGOwBGSSIbbGluZHNleUBwdXBw%0AZXRsYWJzLmNvbQY"
    "7AFRJIh5DT05TT0xFX0FVVEhfQUNDT1VOVF9ST0xFBjsA%0ARkkiD3JlYWQtd3JpdGUGOwBUSSIfQ"
    "09OU09MRV9BVVRIX0FVVEhFTlRJQ0FU%0AT1IGOwBGSSILZ29vZ2xlBjsAVEkiGEFDQ0VTU19DT05"
    "UUk9MX1JPTEUGOwBG%0ASSIOUkVBRF9PTkxZBjsARkkiD2NzcmYudG9rZW4GOwBGSSIxaDBMSkUwM"
    "S9r%0AZXFUWThjVXVzSXRNTzFPUDNKell3bmRCZ2pjUXVZQWJxVT0GOwBGSSIQX2Nz%0AcmZfdG9r"
    "ZW4GOwBGSSIxZnl2Sm5scUxEQkhKVDlWelR2NnlMTmkyM3JtWWpj%0AdjdNYm9YR2dsdW9mTT0GOw"
    "BG%0A--15b097175979539c53f06ca7caf018cfabecaef9; rack.session=BAh7B0kiD3Nlc3N"
    "pb25faWQGOgZFRiJFMDk2ZTJiZmY1MzllYzI4MDM4ZjYw%0AOTFlNTAzYmViYjE4ODk1ZTUzNjFmM"
    "2U5ZWNjZGNjNGFhM2JiYTI0NWI3N0ki%0AD2NzcmYudG9rZW4GOwBGSSIxVXFxMk0zVE1EYXBJMHY"
    "5eWhpOHJ4TVdVYjM5%0AeWxWVVVvbEhZelk5cHdIND0GOwBG%0A; _session_id=BAh7DEkiD3Nl"
    "c3Npb25faWQGOgZFRkkiJTk1NmUxYzdjNGZhMjJhZGY0N2ZkMDBhYjYxYjBkYzRkBjsAVEkiD2Nzc"
    "mYudG9rZW4GOwBGSSIxRFlYdktwVjdDSVBXclZWbW1ja2toVnpYT1ZEeU1Ic0UrNFo5SCtHM0V3az"
    "0GOwBGSSIQX2NzcmZfdG9rZW4GOwBGSSIxZUY4bWdzcmtyeTlRekViK2kvOTdxVkNOdjdTK3UwZlh"
    "JMjhxRGZYdXJyST0GOwBGSSIeQ09OU09MRV9BVVRIX0FDQ09VTlRfTkFNRQY7AEZJIhh0ZXN0QHB1"
    "cHBldGxhYnMuY29tBjsAVEkiHkNPTlNPTEVfQVVUSF9BQ0NPVU5UX1JPTEUGOwBGSSIKYWRtaW4GO"
    "wBUSSIYQUNDRVNTX0NPTlRST0xfUk9MRQY7AEZJIg9SRUFEX1dSSVRFBjsARkkiCGNhcwY7AEZ7CE"
    "kiFmxhc3RfdmFsaWRfdGlja2V0BjsARm86HUNBU0NsaWVudDo6U2VydmljZVRpY2tldAk6DEB0aWN"
    "rZXRJIiVTVC0xMzk4MTIyOTIwcnpGRzdqQllYWkVZUHZ2Tlc4NwY7AFQ6DUBzZXJ2aWNlSSIjaHR0"
    "cDovL2xvY2FsaG9zdDozMDAwL2V2ZW50cy8%2FBjsARjoLQHJlbmV3MDoOQHJlc3BvbnNlbzoiQ0F"
    "TQ2xpZW50OjpWYWxpZGF0aW9uUmVzcG9uc2UKOhRAcGFyc2VfZGF0ZXRpbWVJdToJVGltZQ23jhyA"
    "%2BlGNcgc6C0Bfem9uZUkiCFBEVAY7AFQ6C29mZnNldGn%2BkJ06CUB4bWxvOhNSRVhNTDo6RWxlbW"
    "VudA86DEBwYXJlbnRvOxEPOxJvOhRSRVhNTDo6RG9jdW1lbnQROhxAZW50aXR5X2V4cGFuc2lvbl9j"
    "b3VudGkAOxIwOg5AY2hpbGRyZW5bCW86E1JFWE1MOjpYTUxEZWNsCzoPQHdyaXRldGhpc1Q6E0B3cm"
    "l0ZWVuY29kaW5nVDsSQB06DUB2ZXJzaW9uSSIIMS4wBjsAVDoOQGVuY29kaW5nSSIKVVRGLTgGOwBU"
    "OhBAc3RhbmRhbG9uZTBvOhBSRVhNTDo6VGV4dAs6CUByYXdUOxJAHToTQGVudGl0eV9maWx0ZXIwOh"
    "JAdW5ub3JtYWxpemVkMDoQQG5vcm1hbGl6ZWQwOgxAc3RyaW5nSSIGCgY7AFRAHG87HAs7HVQ7EkAd"
    "Ox4wOx8wOyAwOyFJIgYKBjsAVDoOQGVsZW1lbnRzbzoUUkVYTUw6OkVsZW1lbnRzBjoNQGVsZW1lbn"
    "RAHToQQGF0dHJpYnV0ZXNJQzoWUkVYTUw6OkF0dHJpYnV0ZXN7AAY7JEAdOg1AY29udGV4dHsAOhNA"
    "ZXhwYW5kZWRfbmFtZUkiDlVOREVGSU5FRAY7AEY6DEBwcmVmaXhJIgAGOwBGOg9AbmFtZXNwYWNlSS"
    "IABjsARjoKQG5hbWVJIg5VTkRFRklORUQGOwBGOh1AaWdub3JlX3doaXRlc3BhY2Vfbm9kZXNGOhBA"
    "d2hpdGVzcGFjZVQ7J0AoOxVbCG87HAs7HVQ7EkAcOx4wOx8wOyAwOyFJIggKICAGOwBUQBtvOxwLOx"
    "1UOxJAHDseMDsfMDsgMDshSSIGCgY7AFQ7Im87IwY7JEAcOyVJQzsmewZJIghjYXMGOwBUbzoVUkVY"
    "TUw6OkF0dHJpYnV0ZQs7JEAcOx9JIh9odHRwOi8vd3d3LnlhbGUuZWR1L3RwL2NhcwY7AFQ7IDA7KE"
    "kiDnhtbG5zOmNhcwY7AFQ7KUkiCnhtbG5zBjsAVDsrSSIIY2FzBjsAVAY7JEAcOyhJIhhjYXM6c2Vy"
    "dmljZVJlc3BvbnNlBjsAVDspSSIIY2FzBjsAVDsrSSIUc2VydmljZVJlc3BvbnNlBjsAVDssRjstVD"
    "snQCg7FVsKbzscCzsdVDsSQBs7HjA7HzA7IDA7IUkiCgogICAgBjsAVG87EQ87EkAbOydAKDsVWwZv"
    "OxwLOx1UOxJAQDseMDsfSSIYdGVzdEBwdXBwZXRsYWJzLmNvbQY7AFQ7IDA7IUkiGHRlc3RAcHVwcG"
    "V0bGFicy5jb20GOwBUOyJvOyMGOyRAQDslSUM7JnsABjskQEA7KEkiDWNhczp1c2VyBjsAVDspSSII"
    "Y2FzBjsAVDsrSSIJdXNlcgY7AFQ7LEY7LVRvOxwLOx1UOxJAGzseMDsfMDsgMDshSSIKCiAgICAGOw"
    "BUbzsRDzsSQBs7J0AoOxVbBm87HAs7HVQ7EkBMOx4wOx9JIixDQVNTZXJ2ZXI6OkF1dGhlbnRpY2F0"
    "b3JzOjpTUUxFbmNyeXB0ZWQGOwBUOyAwOyFJIixDQVNTZXJ2ZXI6OkF1dGhlbnRpY2F0b3JzOjpTUU"
    "xFbmNyeXB0ZWQGOwBUOyJvOyMGOyRATDslSUM7JnsABjskQEw7KEkiFmNhczphdXRoZW50aWNhdG9y"
    "BjsAVDspSSIIY2FzBjsAVDsrSSISYXV0aGVudGljYXRvcgY7AFQ7LEY7LVRvOxwLOx1UOxJAGzseMD"
    "sfMDsgMDshSSIICiAgBjsAVDsibzsjBjskQBs7JUlDOyZ7AAY7JEAbOyhJIh5jYXM6YXV0aGVudGlj"
    "YXRpb25TdWNjZXNzBjsAVDspSSIIY2FzBjsAVDsrSSIaYXV0aGVudGljYXRpb25TdWNjZXNzBjsAVD"
    "ssRjstVDoOQHByb3RvY29sZgYyOgpAdXNlckkiGHRlc3RAcHVwcGV0bGFicy5jb20GOwBUOhZAZXh0"
    "cmFfYXR0cmlidXRlc3sASSIPZmlsdGVydXNlcgY7AEZAXkkiGXVzZXJuYW1lX3Nlc3Npb25fa2V5Bj"
    "sARkBe--4aaa7f88f10137c85d99a851bc08490934a2da80"))
