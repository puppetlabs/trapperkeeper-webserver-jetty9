[![Build Status](https://travis-ci.org/puppetlabs/trapperkeeper-webserver-jetty9.png?branch=master)](https://travis-ci.org/puppetlabs/trapperkeeper-webserver-jetty9)

## Trapperkeeper Webserver Service

This project provides a webserver service for use with the
[trapperkeeper service framework](https://github.com/puppetlabs/trapperkeeper)
To use this service in your trapperkeeper application, simply add this
project as a dependency in your leiningen project file:

[![Clojars Project](http://clojars.org/puppetlabs/trapperkeeper-webserver-jetty9/latest-version.svg)](http://clojars.org/puppetlabs/trapperkeeper-webserver-jetty9)

Then add the webserver service to your [`bootstrap.cfg`](https://github.com/puppetlabs/trapperkeeper#bootstrapping)
file, via:

    puppetlabs.trapperkeeper.services.webserver.jetty9-service/jetty9-service

Note that this implementation of the
`:WebserverService` interface is based on Jetty 9, which contains performance
improvements over previous versions of Jetty that may be significant depending on
your application.  This service requires JRE 1.7 or greater;
however, the interface is intended to be agnostic to the underlying web server
implementation.  We also provide a
[Jetty 7 version of the service](https://github.com/puppetlabs/trapperkeeper-webserver-jetty7),
which can be used interchangeably with this one and will support older JDKs.
You should only need to change your lein dependencies and your `bootstrap.cfg`
file--no code changes.

The web server is configured via the
[trapperkeeper configuration service](https://github.com/puppetlabs/trapperkeeper#configuration-service);
so, you can control various properties of the server (ports, SSL, etc.) by adding a `webserver`
section to one of your Trapperkeeper configuration files, and setting various properties
therein.  For more info, see [Configuring the Webserver](doc/jetty-config.md). It is possible to configure
both a single webserver or multiple webservers.

The `webserver-service` currently supports web applications built using
Clojure's [Ring](https://github.com/ring-clojure/ring) library and Java's Servlet
API.  There is also an experimental webserver service that supports loading
ruby Rack applications via JRuby; for more info, see the
[trapperkeeper-ruby](https://github.com/puppetlabs/trapperkeeper-ruby) project.

### Example code

Four examples are included with this project:

* A Ring example ([source code](./examples/ring_app))
* A Java servlet example ([source code](./examples/servlet_app))
* A WAR example ([source code](./examples/war_app))
* A multiserver configuration example ([source code](./examples/multiserver_app))

### Service Protocol

This is the protocol for the current implementation of the `:WebserverService`:

```clj
(defprotocol WebserverService
  (add-context-handler [this base-path context-path] [this base-path context-path options])
  (add-ring-handler [this handler path] [this handler path options])
  (add-websocket-handler [this handlers path] [this handler path options])
  (add-servlet-handler [this servlet path] [this servlet path options])
  (add-war-handler [this war path] [this war path options])
  (add-proxy-route [this target path] [this target path options])
  (override-webserver-settings! [this overrides] [this server-id overrides])
  (get-registered-endpoints [this] [this server-id])
  (log-registered-endpoints [this] [this server-id])
  (join [this] [this server-id])
```

Here is a bit more info about each of these functions:

#### `add-ring-handler`

`add-ring-handler` takes two arguments: `[handler path]`.  The `handler` argument
is just a normal Ring application (the same as what you would pass to `run-jetty`
if you were using the `ring-jetty-adapter`).  The `path` is a URL prefix / context
string that will be prepended to all your handler's URLs; this is key to allowing
the registration of multiple handlers in the same web server without the possibility
of URL collisions.  So, for example, if your ring handler has routes `/foo` and
`/bar`, and you call:

```clj
(add-ring-handler my-app "/my-app")
```

Then your routes will be served at `/my-app/foo` and `my-app/bar`.

You may specify `""` as the value for `path` if you are only registering a single
handler and do not need to prefix the URL.

There is also a three argument version of this function which takes these arguments:
`[handler path options]`. `options` is a map containing two optional keys. The first is
`:server-id`, which specifies which server you want to add the ring-handler to. If
`:server-id` is specified, the ring handler will be added to the server with id
`:server-id`. If no `:server-id` is specified, or the two argument version is called,
the ring handler will be added to the default server. Calling the two-argument version or
leaving out `:server-id` will not work in a multiserver set-up if no default server is specified.

The second optional argument is `:redirect-if-no-trailing-slash`. When set to `true`,
all requests made to the endpoint at which the ring-handler was registered will, if
no trailing slash is present, return a 302 redirect response to the same URL but with a trailing slash
added. If the option is set to `false`, no redirect will occur, and the request will be
routed through to the registered handler. This option defaults to `false`.

Here's an example of how to use the `:WebserverService`:

```clj
(defservice MyWebService
   [[:WebserverService add-ring-handler]]
   ;; initialization
   (init [this context]
      (add-ring-handler my-app "/my-app")
      context))
```

This would add your ring handler to the default server at endpoint "/my-app".
Alternatively, if you did this:

```clj
(defservice MyWebService
   [[:WebserverService add-ring-handler]]
   ;; initialization
   (init [this context]
      (add-ring-handler my-app "/my-app" {:server-id :foo})
      context))
```
it would add your ring handler to the server with id `:foo` at endpoint "/my-app",
rather than the default server.

*NOTE FOR COMPOJURE APPS*: If you are using compojure, it's important to note
that compojure requires use of the [`context` macro](https://github.com/weavejester/compojure/wiki/Nesting-routes)
in order to support nested routes.  So, if you're not already using `context`,
you will need to do something like this:

```clj
(ns foo
   (:require [compojure.core :as c]
   ;;...
   ))

(defservice MyWebService
   [[:WebserverService add-ring-handler]]
   ;; initialization
   (init [this svc-context]
        (let [context-path "/my-app"
              context-app  (c/context context-path [] my-compojure-app)]
            (add-ring-handler context-app context-path))
        svc-context))
```

#### `add-context-handler`

`add-context-handler` takes two arguments: `[base-path context-path]`.  The `base-path`
argument is a URL string pointing to a location containing static resources which are
made accessible at the `context-path` URL prefix.

For example, to make your CSS files stored in the `resources/css` directory available
at `/css`:

```clj
(defservice MyWebService
   [[:WebserverService add-context-handler]]
   ;; initialization
   (init [this context]
      (add-context-handler "resources/css" "/css")
      context))
```

There is also a three argument version of the function which takes these arguments:
`[base-path context-path options]`, where the first two arguments are the
same as in the two argument version and `options` is a map containing four optional keys,
`:server-id`, `:redirect-if-no-trailing-slash`, `:follow-links`, and `:context-listeners`.
The value stored in `:server-id` specifies which server
to add the context handler to, similar to how it is done in `add-ring-handler`. Again, like
`add-ring-handler`, if this key is absent or the two argument version is called, the context handler
will be added to the default server. Calling the two-argument version or leaving out `:server-id`
will not work in a multiserver set-up if no default server is specified.
The value stored in `:redirect-if-no-trailing-slash` is a boolean indicating whether or not
to redirect when a request is made to this handler without a trailing slash, just like with
`add-ring-handler`. Again, this defaults to false.
The value stored in `:follow-links` is a boolean indicating whether or not symbolic links
should be served. The service does NOT serve symbolic links by default.
The value stored in `:context-listeners` is a list of objects implementing the
[ServletContextListener] (http://docs.oracle.com/javaee/7/api/javax/servlet/ServletContextListener.html)
interface. These listeners are registered with the context created for serving the
static content and receive notifications about the lifecycle events in the context
as defined in the ServletContextListener interface. Of particular interest is the
`contextInitialized` event notification as it provides access to the configuration
of the context through the methods defined in the [ServletContext]
(http://docs.oracle.com/javaee/7/api/javax/servlet/ServletContext.html)
interface. This opens up wide possibilities for customizing the context - in an
extreme case the context originally capable of serving just the static content can
be changed through this mechanism to a fully dynamic web application (in fact this
very mechanism is used in the [trapperkeeper-ruby]
(https://github.com/puppetlabs/trapperkeeper-ruby) project to turn the context into
a container for hosting an arbitrary ruby rack application - see [here]
(https://github.com/puppetlabs/trapperkeeper-ruby/blob/master/src/clojure/puppetlabs/trapperkeeper/services/rack_jetty/rack_jetty_service.clj)).

#### `add-servlet-handler`

`add-servlet-handler` takes two arguments: `[servlet path]`.  The `servlet` argument
is a normal Java [Servlet](http://docs.oracle.com/javaee/7/api/javax/servlet/Servlet.html).
The `path` is the URL prefix at which the servlet will be registered.  
There is also a three argument version of the function which takes these arguments:
`[servlet path options]`, where the first two arguments are the same as
in the two argument version and options is a map containing three optional keys, `:server-id`,
`:redirect-if-no-trailing-slash`, and
`:servlet-init-params`. As in `add-ring-handler`, `:server-id` specifies which server to add
the handler to. If `:server-id` is absent or the two-argument function is called, the servlet
handler will be added to the default server. Calling the two-argument version or leaving out
`:server-id` will not work in a multiserver set-up if no default server is specified.
The value stored in `:redirect-if-no-trailing-slash` is a boolean indicating whether or not
to redirect when a request is made to this handler without a trailing slash, just like with
`add-ring-handler`. Again, this defaults to false.
The value stored at the `:servlet-init-params` key is a map of servlet init parameters.

For example, to host a servlet at `/my-app`:

```clj
(ns foo
    ;; ...
    (:import [bar.baz SomeServlet]))

(defservice MyWebService
  [[:WebserverService add-servlet-handler]]
  ;; initialization
  (init [this context]
    (add-servlet-handler (SomeServlet. "some config") "/my-app")
    context))
```

For more information see the [example servlet app](examples/servlet_app).

#### `add-websocket-handler`

`add-websocket-handler` takes two arguments: `[handlers path]`.
The `handlers` is a map of callbacks to invoke when handling a websocket session.
The `path` is the URL prefix where this websocket servlet will be registered.

The possible callbacks for the `handlers` map are:

```clj
{:on-connect (fn [ws])
 :on-error   (fn [ws error])
 :on-close   (fn [ws status-code reason])
 :on-text    (fn [ws text])
 :on-bytes   (fn [ws bytes offset len])}
```

Querying data or sending messages over the websocket is supported by
the functions of WebSocketProtocol protocol from the
`puppetlabs.experimental.websocket.client` namespace:

```clj
(connected? [this]
  "Returns a boolean indicating if the session is currently connected")
(send! [this msg]
  "Send a message to the websocket client")
(close! [this]
  "Close the websocket session")
(remote-addr [this]
  "Find the remote address of a websocket client")
(ssl? [this]
  "Returns a boolean indicating if the session was established by wss://")
(peer-certs [this]
  "Returns an array of X509Certs presented by the ssl peer, if any"))
```

For example, to provide a simple websockets echo service as `/wsecho`:

```clj
(ns foo
   (:require [puppetlabs.experimental.websockets.client :as ws-client]))

(def echo-handlers
  {:on-text (fn [ws text] (ws-client/send! ws text))})

(defservice wsecho-webservice
  [[:WebserverService add-websocket-handler]]
  (init [this context]
    (add-websocket-handler echo-handlers "/wsecho")
    context))
```

#### `add-war-handler`

`add-war-handler` takes two arguments: `[war path]`.
The `war` is the file path or the URL to a WAR file.
The `path` is the URL prefix at which the WAR will be registered.

For example, to host `resources/cas.war` WAR at `/cas`:

```clj
(defservice cas-webservice
  [[:WebserverService add-war-handler]]
  (init [this context]
    (add-war-handler "resources/cas.war" "/cas")
    context))
```

There is also a three-argument version that takes these parameters:
`[war path options]`. `options` is a map containing two optional
keys, `:server-id` and `:redirect-if-no-trailing-slash`. As with `add-ring-handler`,
this determines which server the handler is added to. If this key is absent or the two argument
version is called, the handler will be added to the default server. Calling
the two-argument version or leaving out `:server-id` will not work in a
multiserver set-up if no default server is specified.
The value stored in `:redirect-if-no-trailing-slash` is a boolean indicating whether or not
to redirect when a request is made to this handler without a trailing slash, just like with
`add-ring-handler`. Again, this defaults to false.

#### `add-proxy-route`

`add-proxy-route` is used to configure certain the server as a reverse proxy for
certain routes.  This function will accept two or three arguments: `[target path]`, or
`[target path options]`.

`path` is the URL prefix for requests that you wish to proxy.

`target` is a map that controls how matching requests will be proxied; here are
the keys required in the `target` map:

* `:host`: required; a string representing the host or IP to proxy requests to.
* `:port`: required; an integer representing the port on the remote host that requests
  should be proxied to.
* `:path`: required; the URL prefix that should be prepended to all proxied requests.

`options`, if provided, is a map containing optional configuration for the proxy
route:

* `:scheme`: optional; legal values are `:orig`, `:http`, and `:https`.  If you
  specify `:http` or `:https`, then all proxied requests will use the specified
  scheme.  The default value is `:orig`, which means that proxied requests will
  use the same scheme as the original request.
* `:ssl-config`: optional; may be set to either `:use-server-config` (default) or
  to a map containing the keys `:ssl-cert`, `:ssl-key`, and `:ssl-ca-cert`, as
  well as the optional keys `:cipher-suites` and `:protocols`.  If
  `:use-server-config`, then any proxied requests that use HTTPS will use the same
  SSL context/configuration that the web server is configured with.  If you specify
  a map, then the entries must point to the PEM files that should be used for the
  SSL context.  These keys have the same meaning as they do for the SSL configuration
  of the main web server.
* `:rewrite-uri-callback-fn`: optional; a function to manipulate the rewritten target
  URI (e.g. change the port, or even change the entire URI) before Jetty continues on
  with the proxy. The function must accept two arguments, `[target-uri req]`. For more
  information, see [below](#rewrite-uri-callback-fn).
* `:callback-fn`: optional; a function to manipulate the request object (e.g.
  to add additional headers) before Jetty continues on with the proxy. The
  function must accept two arguments, `[proxy-req req]`. For more information,
  see [below](#callback-fn).
* `:failure-callback-fn`: optional; a function to manipulate the response object in case of failure.
  The function must accept four arguments, `[req resp proxy-resp failure]`. For more information,
  see [below](#failure-callback-fn).
* `:request-buffer-size`: optional; an integer value to which to set the size
  of the request buffer used by the HTTP Client. This allows HTTP requests with
  very large cookies to go through, as a large cookie can cause the request
  buffer to overflow unless the size is increased. The default is 4096 bytes.
* `:follow-redirects`: optional; a boolean that indicates whether or not the HttpClient
  created by a ProxyServlet should follow redirects. Defaults to `false`.
* `:server-id`: optional; the id of the server to which to add the proxy handler. If absent,
  the handler will be added to the default server. If the two argument version of this function
  is called, the handler will also be added to the default server. Leaving out `:server-id` or calling
  the two argument version of this function will not work in a multiserver set-up if no default server
  is specified.
* `:redirect-if-no-trailing-slash`: optional; a boolean indicating whether or not to redirect
  when a request is made to this proxy route without a trailing slash, as with `add-ring-handler`.
  Defaults to false.
* `:idle-timeout`: optional; sets the maximum amount of time, measured in seconds, for the proxy to 
  wait for a response from the upstream server. If no response is received within the time
  specified, then an HTTP 504 error is returned. If this option is not specified then a value
  of 30 seconds is used.

Simple example:

```clj
(defservice foo-service
  [[:WebserverService add-proxy-route]]
  (init [this context]
    (add-proxy-route
        {:host "localhost"
         :port 10000
         :path "/bar"}
        "/foo")
    context))
```

In this example, all incoming requests with a prefix of `/foo` will be proxied
to `localhost:10000`, with a prefix of `/bar`, using the same scheme (HTTP/HTTPS)
that the original request used, and using the SSL context of the main webserver.

So, e.g., an HTTPS request to the main webserver at `/foo/hello-world` would be
proxied to `https://localhost:10000/bar/hello-world`.

A slightly more complex example:

```clj
(defservice foo-service
  [[:WebserverService add-proxy-route]]
  (init [this context]
    (add-proxy-route
        {:host "localhost"
         :port 10000
         :path "/bar"}
        "/foo"
        {:scheme :https
         :ssl-config {:ssl-cert    "/tmp/cert.pem"
                      :ssl-key     "/tmp/key.pem"
                      :ssl-ca-cert "/tmp/ca.pem"}})
    context))
```

In this example, all incoming requests with a prefix of `foo` will be proxied
to `https://localhost:10000/bar`.  We'll proxy using HTTPS even if the original
request was HTTP, and we'll use the three pem files in `/tmp` to configure the
HTTPS client, regardless of the SSL configuration of the main web server.

#####`:rewrite-uri-callback-fn`

This option lets you provide a function to manipulate the rewritten target URI. The
function is called in the overridden implementation of
[`rewriteURI`](http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/proxy/ProxyServlet.html#rewriteURI(javax.servlet.http.HttpServletRequest))
method after the target URI is computed. It must take two arguments, `[target-uri req]`, where `target-uri` is a
[`URI`](http://docs.oracle.com/javase/7/docs/api/java/net/URI.html)
and `req` is an
[`HttpServletRequest`](http://docs.oracle.com/javaee/6/api/javax/servlet/http/HttpServletRequest.html).
`target-uri` will be modified and returned by the function.

An example with a rewrite URI callback function:

```clj
(defservice foo-service
  [[:WebserverService add-proxy-route]]
  (init [this context]
    (add-proxy-route
        {:host "localhost"
         :port 10000
         :path "/bar"}
        "/foo"
        {:rewrite-uri-callback-fn (fn [target-uri req]
          (if-not (= "GET" (.getMethod req))
            (URI. "http://localhost:11111/errors/unsupported-method")
            target-uri))})
    context))
```

In this example, all incoming requests with a method other than `GET` will be proxied
to `http://localhost:11111/errors/unsupported-method`.

#####`:callback-fn`

This option lets you provide a function to manipulate the request object.  The
function will be passed to the
[`customizeProxyRequest`](http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/proxy/ProxyServlet.html#customizeProxyRequest%28org.eclipse.jetty.client.api.Request,%20javax.servlet.http.HttpServletRequest%29)
method. It must take two arguments, `[proxy-req req]`, where `proxy-req` is a
[`Request`](http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/client/api/Request.html)
and `req` is an
[`HttpServletRequest`](http://docs.oracle.com/javaee/6/api/javax/servlet/http/HttpServletRequest.html).
`proxy-req` will be modified and returned by the function.

An example with a callback function:

```clj
(defservice foo-service
  [[:WebserverService add-proxy-route]]
  (init [this context]
    (add-proxy-route
        {:host "localhost"
         :port 10000
         :path "/bar"}
        "/foo"
        {:callback-fn (fn [proxy-req req]
          (.header proxy-req "x-example" "baz"))})
    context))
```

In this example, all incoming requests with a prefix of `foo` will be proxied
to `https://localhost:10000/bar`, using the same scheme (HTTP/HTTPS) that the
original request used, and using the SSL context of the main webserver. In
addition, a header `"x-example"` with the value `"baz"` will be added to the
request before it is proxied, using the
[`header`](http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/client/api/Request.html#header%28java.lang.String,%20java.lang.String%29)
method.

#####`:failure-callback-fn`

This option lets you provide a function to manipulate the response object in case of failure. It must take
four arguments, `[req resp proxy-resp failure]`, where `req` is the original
[`HttpServletRequest`](http://docs.oracle.com/javaee/6/api/javax/servlet/http/HttpServletRequest.html),
`resp` is an [`HttpServletResponse`](http://docs.oracle.com/javaee/6/api/javax/servlet/http/HttpServletResponse.html),
`proxy-req` a [`Response`](http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/client/api/Response.html)
and `failure` is a [`Throwable`](http://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html) explaining the
cause of the problem.
`resp` may be modified, the function does not return any value.

An example with an on-failure function:

```clj
(defservice foo-service
  [[:WebserverService add-proxy-route]]
  (init [this context]
    (add-proxy-route
        {:host "localhost"
         :port 10000
         :path "/bar"}
        "/foo"
        {:failure-callback-fn (fn [req resp proxy-resp failure]
          (.println (.getWriter resp) (str "Proxying failed: " (.getMessage failure))))})
    context))
```

In this example, in case of proxying failure the response body will be augmented by an error message explaining
what the cause of the problem was.

#### `override-webserver-settings!`

`override-webserver-settings!` is used to override settings in the `webserver`
section of the webserver service's config file.  This function will accept one
argument, `[overrides]`.  `overrides` is a map which should contain a
key/value pair for each setting to be overridden.  The name of the setting to
override should be expressed as a Clojure keyword.  For any setting expressed in
the service config which is not overridden, the setting value from the config
will be used.

For example, the webserver config may contain:

```
webserver {
    ssl-host:    0.0.0.0
    ssl-port:    9001
    ssl-cert:    mycert.pem
    ssl-key:     mykey.pem
    ssl-ca-cert: myca.pem
}
```

Overrides may be supplied from the service using code like the following:

```clj
(defservice foo-service
  [[:WebserverService override-webserver-settings!]]
  (init [this context]
    (override-webserver-settings!
      {:ssl-port    9002
       :ssl-cert    "myoverriddencert.pem"
       :ssl-key     "myoverriddenkey.pem"})
    context))
```

For this example, the effective settings used during webserver startup would be:

```clj
{:ssl-host    "0.0.0.0"
 :ssl-port    9002
 :ssl-cert    "myoverriddencert.pem"
 :ssl-key     "myoverriddenkey.pem"
 :ssl-ca-cert "myca.pem"}
```

The overridden webserver settings will be considered only at the point the
webserver is being started -- during the start lifecycle phase of the
webserver service.  For this reason, a call to this function must be made
during a service's init lifecycle phase in order for the overridden
settings to be considered.

Only one call from a service may be made to this function during application
startup.

If a call is made to this function after webserver startup or after another
call has already been made to this function (e.g., from other service),
a java.lang.IllegalStateException will be thrown.

A three argument version is available which takes these parameters: `[server-id overrides]`.
`server-id` is the id of the server for which you wish to override the settings. If the
two argument version is called, they will be overridden for the default server. The one-argument
version of this function will not work in a multiserver set-up if no default server is specified.

#### `get-registered-endpoints`

This function returns a map containing information on each URL endpoint
registered by the Jetty9 service on the default server. Each key in the map is a URL
endpoint, with each value being an array of maps containing information on each handler
registered at that URL endpoint. The possible keys appearing in these maps are:

* `:type`: The type of the registered endpoint. The possible types are `:context`,
  `:ring`, `:servlet`, `:war`, and `:proxy`. Returned for every endpoint.
* `:base-path`: The base-path of a context handler. Returned only for endpoints of
  type `:context`.
* `:context-listeners`: The context listeners for a context handler. Returned only
  for endpoints of type `:context` that have context listeners.
* `:servlet`: The servlet for a servlet handler. Only returned for endpoints of type
  `:servlet`.
* `:war-path`: The local path of the war registered by a war handler. Only returned
  for endpoints of type `:war`.
* `:target-host`: The targeted host of a proxy request. Only returned for endpoints
  of type `:proxy`.
* `:target-port`: The targeted port of a proxy request. Only returned for endpoints
  of type `:proxy`.
* `:target-path`: The targeted prefix of a proxy request. Only returned for endpoints
  of type `:proxy`.

The schema for the various types of handler maps can be viewed [here](https://github.com/puppetlabs/trapperkeeper-webserver-jetty9/blob/master/src/puppetlabs/trapperkeeper/services/webserver/jetty9_core.clj#L71-L96).

There is also a version that takes one argument, `[server-id]`, which specifies which server
for which you want to pull the endpoints. If this parameter is absent, the endpoints will be
pulled for the default server. The no-argument version of this function will not work in a
multiserver set-up if no default server is specified.

#### `log-registered-endpoints`

This function logs the data returned by `get-registered-endpoints` at the info level.

There is a version of this function that takes a single argument, `[server-id]`. This
specifies which server for which you want to log the endpoints. If this is absent,
the endpoints registered on the default server will be logged. The no-argument version
of this function will not work in a multiserver set-up if no default server is specified.

#### `join`

This function is not recommended for normal use, but is provided for compatibility
with the `ring-jetty-adapter`.  `ring-jetty-adapter/run-jetty`, by default,
calls `join` on the underlying Jetty server instance.  This allows your thread
to block until Jetty shuts down.  This should not be necessary for normal
trapperkeeper usage, because trapperkeeper already blocks the main thread and
waits for a termination condition before allowing the process to exit.  However,
if you do need this functionality for some reason, you can simply call `(join)`
to cause your thread to wait for the Jetty server to shut down.

There is another version of this function that takes a single argument, `[server-id]`.
This is the id of the server you want to join. If this is not specified, then
the default server will be joined. The no-argument version of this function will not
work in a multi-server set-up if no default server is specified.

### Service lifecycle phases

The Trapperkeeper service manipulates the Java Jetty code in the following ways during
these lifecycle phases.

#### `init`

A `ContextHandlerCollection` is created during the `init` lifecycle which allows for
consumers to use the `add-*-handler` and `add-proxy-route` functions,
but the Jetty server itself has not started yet. This allows the service
consumer to setup SSL keys and perform other operations needed before the server is started.

#### `start`

In the start lifecycle phase the Jetty server object is created, the `ContextHandlerCollection` is added to it, and the server is then started. Adding handlers 
after this phase should still work fine, but it is recommended that handlers be added 
during the consuming service's `init` phase.

## Webrouting Service

This project provides a secondary Webrouting Service, which in many cases
is preferable for use over the Webserver Service. Documentation is available for it
[here](doc/webrouting-service.md).

## TrapperKeeper Webserver Service Test Utils

This project provides some utility code for testing. Documentation on these test utils
is available [here](doc/test-utils.md).

## Support

We use the [Trapperkeeper project on JIRA](https://tickets.puppetlabs.com/browse/TK)
for tickets on the Trapperkeeper Webserver Service, although Github issues are
welcome too.
