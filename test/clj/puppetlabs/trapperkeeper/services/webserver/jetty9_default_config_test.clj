(ns puppetlabs.trapperkeeper.services.webserver.jetty9-default-config-test
  "
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; VALIDATION OF DEFAULT JETTY CONFIGURATION VALUES
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

NOTE: IF A TEST IN THIS NAMESPACE FAILS, AND YOU ALTER THE VALUE TO MAKE IT
PASS, IT IS YOUR RESPONSIBILITY TO DOUBLE-CHECK THE DOCS TO SEE IF THERE
IS ANYWHERE IN THEM THAT THE NEW VALUE NEEDS TO BE ADDED.

This namespace is a little different than most of our test namespaces.  It's
not really intended to test any of our own code, it's just here to provide
us with a warning in the event that Jetty changes any of the default
configuration values.

In the conversation leading up to https://tickets.puppetlabs.com/browse/TK-168
we decided that it was generally not a good idea to be hard-coding our own
default values for the settings that we exposed, and that it would be a better
idea to allow Jetty to use its implicit default values for any settings that
are not explicitly set in a TK config file.  Otherwise, we're at risk of
the Jetty authors coming up with a really compelling reason to change a
default value between releases, and us not picking up that change.

Therefore, we decided that all the settings we expose should just fall
through to Jetty's implicit defaults, and that individual TK application
authors can override any appropriate settings in their packaging if needed.

However, there was some concern that if an upstream Jetty default were to
change without us knowing about it, it could have other implications for our
applications that we ought to be aware of.  Therefore, we agreed that it
would be best if we had some way of making sure we could identify when
that situation arose.

That is the purpose of this namespace.  It basically provides assertions
to validate that we know what Jetty's implicit default value is for all of
the settings we expose.  If we bump to a new version of Jetty in the future
and any of these implicit defaults have changed, these tests will fail.  If
that happens, we can attempt to evaluate the impact of the change and
react accordingly."
  (:require [clojure.test :refer :all]
            [schema.test :as schema-test]
            [puppetlabs.trapperkeeper.testutils.bootstrap :refer [with-app-with-config]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-service :refer [jetty9-service]]
            [puppetlabs.trapperkeeper.app :refer [get-service]]
            [puppetlabs.trapperkeeper.services :refer [service-context]]
            [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core])
  (:import (org.eclipse.jetty.server HttpConfiguration ServerConnector Server)))

(use-fixtures :once schema-test/validate-schemas)

(deftest default-request-header-size-test
  (let [http-config (HttpConfiguration.)]
    (is (= 8192 (.getRequestHeaderSize http-config)))))

(deftest default-proxy-http-client-settings-test
  (with-app-with-config app
    [jetty9-service]
    {:webserver {:host "localhost" :port 8080}}
    (let [s (get-service app :WebserverService)
          server-context (get-in (service-context s) [:jetty9-servers :default])
          proxy-servlet (core/proxy-servlet
                          server-context
                          {:host "localhost"
                           :path "/foo"
                           :port 8080}
                          {})
          _             (core/add-servlet-handler
                          server-context
                          proxy-servlet
                          "/proxy"
                          {}
                          true)
          client        (.createHttpClient proxy-servlet)]
      (is (= 4096 (.getRequestBufferSize client)))
      (is (= 30000 (.getIdleTimeout client)))
      (.stop client))))

(deftest default-connector-settings-test
  (let [connector (ServerConnector. (Server.))]
    (is (= -1 (.getSoLingerTime connector)))
    (is (= 30000 (.getIdleTimeout connector)))))

(deftest default-server-settings-test
  (let [server (Server.)]
    (is (= 30000 (.getStopTimeout server)))))




