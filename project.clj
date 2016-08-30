(def tk-version "1.3.1")
(def ks-version "1.3.0")
(def jetty-version "9.2.10.v20150310")

(defproject puppetlabs/trapperkeeper-webserver-jetty9 "1.5.10-SNAPSHOT"
  :description "A jetty9-based webserver implementation for use with the puppetlabs/trapperkeeper service framework."
  :url "https://github.com/puppetlabs/trapperkeeper-webserver-jetty9"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort
  :dependencies [[org.clojure/clojure "1.7.0"]

                 ;; begin version conflict resolution dependencies
                 [clj-time "0.9.0"]
                 [org.clojure/tools.reader "1.0.0-beta1"]
                 ;; end version conflict resolution dependencies

                 [org.clojure/java.jmx "0.3.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [prismatic/schema "1.0.4"]

                 [puppetlabs/ssl-utils "0.8.1"]
                 [puppetlabs/kitchensink ~ks-version]
                 [puppetlabs/trapperkeeper ~tk-version]

                 [org.codehaus.janino/janino "2.7.8"]

                 [javax.servlet/javax.servlet-api "3.1.0"]

                 ;; Jetty Webserver
                 [org.eclipse.jetty/jetty-server ~jetty-version
                  :exclusions [org.eclipse.jetty.orbit/javax.servlet]]
                 [org.eclipse.jetty/jetty-servlet ~jetty-version]
                 [org.eclipse.jetty/jetty-servlets ~jetty-version]
                 [org.eclipse.jetty/jetty-webapp ~jetty-version]
                 [org.eclipse.jetty/jetty-proxy ~jetty-version]
                 [org.eclipse.jetty/jetty-jmx ~jetty-version]
                 [org.eclipse.jetty.websocket/websocket-server ~jetty-version]

                 [ring/ring-servlet "1.4.0"]
                 [ring/ring-codec "1.0.0"]]

  :source-paths  ["src"]
  :java-source-paths  ["java"]

  :plugins [[lein-release "1.0.5" :exclusions [org.clojure/clojure]]]

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
                   :dependencies [[puppetlabs/http-client "0.5.0"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test"]
                                  [puppetlabs/trapperkeeper ~tk-version :classifier "test"]
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [spyscope "0.1.4"]
                                  [compojure "1.1.8" :exclusions [ring/ring-core
                                                                  commons-io
                                                                  org.clojure/tools.macro]]
                                  [stylefruits/gniazdo "0.4.0" :exclusions [org.eclipse.jetty.websocket/websocket-api
                                                                            org.eclipse.jetty.websocket/websocket-client
                                                                            org.eclipse.jetty/jetty-util]]
                                  [ring/ring-core "1.4.0"]]
                    :injections [(require 'spyscope.core)]
                    ;; Enable SSLv3 for unit tests that exercise SSLv3
                    :jvm-opts ["-Djava.security.properties=./dev-resources/java.security"]}

             :testutils {:source-paths ^:replace ["test/clj"]
                         :java-source-paths ^:replace ["test/java"]}
             :uberjar {:aot [puppetlabs.trapperkeeper.main]
                       :classifiers ^:replace []
                       :dependencies [[org.clojure/tools.nrepl "0.2.6" :scope "test" :exclusions [[org.clojure/clojure]]]]}}

  :main puppetlabs.trapperkeeper.main
  )
