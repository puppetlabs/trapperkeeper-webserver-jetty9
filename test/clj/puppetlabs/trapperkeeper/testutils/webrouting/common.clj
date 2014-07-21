(ns puppetlabs.trapperkeeper.testutils.webrouting.common
  (:require [puppetlabs.http.client.sync :as http-client]))

(defn http-get
  ([url]
   (http-get url {:as :text}))
  ([url options]
   (http-client/get url options)))

(def webrouting-plaintext-config
  {:webserver {:port 8080}
   :web-router-service {:puppetlabs.foo/foo-service "/foo"
                        :puppetlabs.bar/bar-service "/bar"}})
