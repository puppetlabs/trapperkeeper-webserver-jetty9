(def tk-version "0.5.1")
(def ks-version "0.7.2")

(defproject puppetlabs/trapperkeeper-webserver-jetty9 "0.9.0"
  :description "We are trapperkeeper.  We are one."
  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [prismatic/schema "0.2.2"]
                 [puppetlabs/kitchensink ~ks-version]
                 [puppetlabs/trapperkeeper ~tk-version]
                 [puppetlabs/certificate-authority "0.5.0"]

                 [ch.qos.logback/logback-access "1.1.1"]

                 [javax.servlet/javax.servlet-api "3.1.0"]

                 ;; Jetty Webserver
                 [org.eclipse.jetty/jetty-server "9.1.0.v20131115"
                  :exclusions [org.eclipse.jetty.orbit/javax.servlet]]
                 [org.eclipse.jetty/jetty-servlet "9.1.0.v20131115"]
                 [org.eclipse.jetty/jetty-servlets "9.1.0.v20131115"]
                 [org.eclipse.jetty/jetty-webapp "9.1.0.v20131115"]
                 [org.eclipse.jetty/jetty-proxy "9.1.0.v20131115"]
                 [org.eclipse.jetty/jetty-jmx "9.1.0.v20131115"]

                 [ring/ring-servlet "1.1.8" :exclusions [javax.servlet/servlet-api]]]

  :plugins [[lein-release "1.0.5"]]

  :lein-release {:scm         :git
                 :deploy-via  :lein-deploy}

  :deploy-repositories [["releases" {:url "https://clojars.org/repo"
                                     :username :env/clojars_jenkins_username
                                     :password :env/clojars_jenkins_password
                                     :sign-releases false}]]

  ;; By declaring a classifier here and a corresponding profile below we'll get an additional jar
  ;; during `lein jar` that has all the code in the test/ directory. Downstream projects can then
  ;; depend on this test jar using a :classifier in their :dependencies to reuse the test utility
  ;; code that we have.
  :classifiers [["test" :testutils]]

  :test-paths ["test/clj"]

  :profiles {:dev {:source-paths ["examples/multiserver_app/src"
                                  "examples/ring_app/src"
                                  "examples/servlet_app/src/clj"
                                  "examples/war_app/src"
                                  "examples/webrouting_app/src"]
                   :java-source-paths ["examples/servlet_app/src/java"
                                       "test/java"]
                   :dependencies [[puppetlabs/http-client "0.2.8"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test"]
                                  [puppetlabs/trapperkeeper ~tk-version :classifier "test"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [org.clojure/java.jmx "0.2.0"]
                                  [spyscope "0.1.4"]
                                  [compojure "1.1.8" :exclusions [ring/ring-core
                                                                  commons-io
                                                                  org.clojure/tools.macro]]]
                    :injections [(require 'spyscope.core)]}

             :testutils {:source-paths ^:replace ["test/clj"]
                         :java-source-paths ^:replace ["test/java"]}
             :uberjar {:aot [puppetlabs.trapperkeeper.main]
                       :classifiers ^:replace []}}

  :main puppetlabs.trapperkeeper.main
  )

