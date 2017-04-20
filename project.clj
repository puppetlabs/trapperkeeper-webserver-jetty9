(def jetty-version "9.4.4.v20170414")

(defproject puppetlabs/trapperkeeper-webserver-jetty9 "2.0.0-SNAPSHOT"
  :description "A jetty9-based webserver implementation for use with the puppetlabs/trapperkeeper service framework."
  :url "https://github.com/puppetlabs/trapperkeeper-webserver-jetty9"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :min-lein-version "2.7.1"

  :parent-project {:coords [puppetlabs/clj-parent "0.6.1"]
                   :inherit [:managed-dependencies]}

  ;; Abort when version ranges or version conflicts are detected in
  ;; dependencies. Also supports :warn to simply emit warnings.
  ;; requires lein 2.2.0+.
  :pedantic? :abort
  :dependencies [[org.clojure/clojure]
                 [org.clojure/java.jmx]
                 [org.clojure/tools.logging]

                 [org.codehaus.janino/janino]

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

                 [prismatic/schema]
                 [ring/ring-servlet]
                 [ring/ring-codec]

                 [puppetlabs/ssl-utils]
                 [puppetlabs/kitchensink]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/i18n]
                 ]

  :source-paths  ["src"]
  :java-source-paths  ["java"]

  :plugins [[lein-parent "0.3.1"]
            [puppetlabs/i18n "0.8.0"]]

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
                   :dependencies [[puppetlabs/http-client]
                                  [puppetlabs/kitchensink nil :classifier "test"]
                                  [puppetlabs/trapperkeeper nil :classifier "test"]
                                  [org.clojure/tools.namespace]
                                  [compojure]
                                  [stylefruits/gniazdo "0.4.0" :exclusions [org.eclipse.jetty.websocket/websocket-api
                                                                            org.eclipse.jetty.websocket/websocket-client
                                                                            org.eclipse.jetty/jetty-util]]
                                  [ring/ring-core]]
                    ;; Enable SSLv3 for unit tests that exercise SSLv3
                    :jvm-opts ["-Djava.security.properties=./dev-resources/java.security"]}

             :testutils {:source-paths ^:replace ["test/clj"]
                         :java-source-paths ^:replace ["test/java"]}}

  :main puppetlabs.trapperkeeper.main
  )
