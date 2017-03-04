(ns puppetlabs.trapperkeeper.services.webserver.normalized-uri-helpers
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.util URIUtil)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.server.handler HandlerWrapper AbstractHandler)
           (com.puppetlabs.trapperkeeper.services.webserver.jetty9.utils
            HttpServletRequestWithAlternateRequestUri)
           (javax.servlet Filter DispatcherType)
           (java.util EnumSet)
           (org.eclipse.jetty.servlet FilterHolder ServletContextHandler))
  (:require [schema.core :as schema]
            [ring.util.servlet :as servlet]
            [clojure.string :as str]
            [puppetlabs.i18n.core :as i18n]))

(schema/defn ^:always-validate normalize-uri-path :- schema/Str
  "Return a 'normalized' version of the uri path represented on the incoming
  request.  The 'normalization' consists of three steps:

  1) URL (percent) decode the path, assuming any percent-encodings represent
     UTF-8 characters.

   An exception may be thrown if the request has malformed content, e.g.,
   partially-formed percent-encoded characters like '%A%B'.

   If a semicolon character, U+003B, is found during the decoding process, it
   and any following characters will be removed from the decoded path.

  2) Check the percent-decoded path for any relative path segments ('..' or
     '.').

   An IllegalArgumentException is thrown if one or more segments are found.

  3) Compact any repeated forward slash characters in a path."
  [request :- HttpServletRequest]
  (let [percent-decoded-uri-path (-> request
                                     (.getRequestURI)
                                     (URIUtil/decodePath))
        canonicalized-uri-path (URIUtil/canonicalPath percent-decoded-uri-path)]
    (if (or (nil? canonicalized-uri-path)
            (not= (.length percent-decoded-uri-path)
                  (.length canonicalized-uri-path)))
      (throw (IllegalArgumentException.
               (i18n/trs "Invalid relative path (.. or .) in: {0}"
                         percent-decoded-uri-path)))
      (URIUtil/compactPath canonicalized-uri-path))))

(schema/defn ^:always-validate
  normalize-uri-handler :- HandlerWrapper
  "Create a `HandlerWrapper` which provides a normalized request URI on to
  its downstream handler for an incoming request.  The normalized URI would
  be returned for a 'getRequestURI' call made by the downstream handler on
  its incoming HttpServletRequest request parameter.  Normalization is done
  per the rules described in the `normalize-uri-path` function.  If an error
  is encountered during request URI normalization, an HTTP 400 (Bad Request)
  response is returned rather than the request being passed on its downstream
  handler."
  []
  (proxy [HandlerWrapper] []
    (handle [^String target ^Request base-request
             ^HttpServletRequest request ^HttpServletResponse response]
      (when-let [handler (proxy-super getHandler)]
        (if-let [normalized-uri
                 (try
                   (normalize-uri-path request)
                   (catch IllegalArgumentException ex
                     (do
                       (servlet/update-servlet-response
                        response
                        {:status 400
                         :body (.getMessage ex)})
                       (.setHandled base-request true))
                     nil))]
          (.handle
           handler
           target
           base-request
           (HttpServletRequestWithAlternateRequestUri.
            request
            normalized-uri)
           response))))))

(schema/defn ^:always-validate normalized-uri-filter :- Filter
  "Create a servlet filter which provides a normalized request URI on to its
  downstream consumers for an incoming request.  The normalized URI would be
  returned for a 'getRequestURI' call on the HttpServletRequest parameter.
  Normalization is done per the rules described in the `normalize-uri-path`
  function.  If an error is encountered during request URI normalization, an
  HTTP 400 (Bad Request) response is returned rather than the request being
  passed on its downstream consumers."
  []
  (reify Filter
    (init [_ _])
    (doFilter [_ request response chain]
     ;; The method signature for a servlet filter has a 'request' of the
     ;; more generic 'ServletRequest' and 'response' of the more generic
     ;; 'ServletResponse'.  While we practically shouldn't see anything
     ;; but the more specific Http types for each, this code explicitly
     ;; checks to see that the requests are Http types as the URI
     ;; normalization would be irrelevant for other types.
      (if (and (instance? HttpServletRequest request)
               (instance? HttpServletResponse response))
        (if-let [normalized-uri
                 (try
                   (normalize-uri-path request)
                   (catch IllegalArgumentException ex
                     (servlet/update-servlet-response
                      response
                      {:status 400
                       :body (.getMessage ex)})
                     nil))]
          (.doFilter chain
                     (HttpServletRequestWithAlternateRequestUri.
                      request
                      normalized-uri)
                     response))
        (.doFilter chain request response)))
    (destroy [_])))

(schema/defn ^:always-validate
  add-normalized-uri-filter-to-servlet-handler!
  "Adds a servlet filter to the servlet handler which provides a normalized
  request URI on to its downstream consumers for an incoming request."
  [handler :- ServletContextHandler]
  (let [filter-holder (FilterHolder. (normalized-uri-filter))]
    (.addFilter handler
                filter-holder
                "/*"
                (EnumSet/of DispatcherType/REQUEST))))

(schema/defn ^:always-validate
  handler-maybe-wrapped-with-normalized-uri :- AbstractHandler
  "If the supplied `normalize-request-uri?` parameter is 'true', return a
  handler that normalizes a request uri before passing it on downstream to
  the supplied handler for an incoming request.  If the supplied
  `normalize-request-uri?` is 'false', return the supplied handler."
  [handler :- AbstractHandler
   normalize-request-uri? :- schema/Bool]
  (if normalize-request-uri?
    (doto (normalize-uri-handler)
      (.setHandler handler))
    handler))
