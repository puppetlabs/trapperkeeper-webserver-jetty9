(ns puppetlabs.trapperkeeper.services.webserver.jetty9
  "Currently only provides support for aliased keyword access for
  clojure versions without :as-alias (before 1.11)."
  (:require
   [clojure.spec.alpha :as s])
  (:import
   (org.eclipse.jetty.server Response)))

;; Currently just informational, i.e. not committing to support the
;; spec alpha declaration publicly for now.

(defn- response? [x] (instance? Response x))

(s/def ::response response?)
