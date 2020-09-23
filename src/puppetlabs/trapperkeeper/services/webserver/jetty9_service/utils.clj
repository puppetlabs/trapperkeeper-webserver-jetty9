(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service.utils
  (:refer-clojure :exclude [satisfies?])
  (:require
   [schema.core :as schema])
  (:import (clojure.lang IMeta)))

(defn satisfies? [{:keys [extend-via-metadata method-builders] :as protocol}
                  val]
  (or (and extend-via-metadata
           (instance? IMeta val)
           (some (partial contains? (meta val))
                 (map symbol (keys method-builders))))
      (clojure.core/satisfies? protocol val)))

(defmacro protocol [p]
  (let [pn (-> p str symbol)]
    `(schema/pred (fn ~(symbol (str (some-> pn namespace str (str "--"))
                                    (-> pn name str)))
                    [~'x]
                    (satisfies? ~p ~'x)))))
