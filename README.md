[![Build Status](https://travis-ci.org/puppetlabs/trapperkeeper-webserver-jetty7.png?branch=master)](https://travis-ci.org/puppetlabs/trapperkeeper-webserver-jetty7)

## Trapperkeeper Webserver Service

This project provides a webserver service for use with the
[trapperkeeper service framework](https://github.com/puppetlabs/trapperkeeper)
To use this service in your trapperkeeper application, simply add this
project as a dependency in your leinengen project file, and then add the
webserver service to your [`bootstrap.cfg`](https://github.com/puppetlabs/trapperkeeper#bootstrapping)
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
therein.  For more info, see [Configuring the Webserver](doc/jetty-config.md).

The `webserver-service` currently supports web applications built using
Clojure's [Ring](https://github.com/ring-clojure/ring) library and Java's Servlet
API.  There is also an experimental webserver service that supports loading
ruby Rack applications via JRuby; for more info, see the
[trapperkeeper-ruby](https://github.com/puppetlabs/trapperkeeper-ruby) project.

### Example code

Two examples are included with this project:

* A Ring example ([source code](./examples/ring_app))
* A Java servlet example ([source code](./examples/servlet_app))

### Service Protocol

This is the protocol for the current implementation of the `:WebserverService`:

```clj
(defprotocol WebserverService
  (add-ring-handler [this handler path])
  (add-context-handler [this base-path context-path] [this base-path context-path context-listeners])
  (add-servlet-handler [this servlet path] [this servlet path servlet-init-params])
  (add-war-handler [this war path])
  (add-proxy-route [this target path])
  (join [this]))
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

Here's an example of how to use the `:WebserverService`:

```clj
(defservice MyWebService
   [[:WebserverService add-ring-handler]]
   ;; initialization
   (init [this context]
      (add-ring-handler my-app "/my-app")
      context))
```

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
`[base-path context-path context-listeners]`, where the first two arguments are the
same as in the two argument version and the `context-listeners` is a list of objects
implementing the [ServletContextListener]
(http://docs.oracle.com/javaee/7/api/javax/servlet/ServletContextListener.html)
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
`[servlet path servlet-init-params]`, where the first two arguments are the same as
in the two argument version and the `servlet-init-params` is a map of servlet init
parameters.

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
  to a map containing the keys `:ssl-cert`, `:ssl-key`, and `:ssl-ca-cert`.  If
  `:use-server-config`, then any proxied requests that use HTTPS will use the same
  SSL context/configuration that the web server is configured with.  If you specify
  a map, then the entries must point to the PEM files that should be used for the
  SSL context.  These keys have the same meaning as they do for the SSL configuration
  of the main web server.
* `:callback-fn`: optional; a function to manipulate the request object (e.g.
  to add additional headers) before Jetty continues on with the proxy. The
  function must accept two arguments, `[proxy-req req]`.

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
request before it is proxied.

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

#### `join`

This function is not recommended for normal use, but is provided for compatibility
with the `ring-jetty-adapter`.  `ring-jetty-adapter/run-jetty`, by default,
calls `join` on the underlying Jetty server instance.  This allows your thread
to block until Jetty shuts down.  This should not be necessary for normal
trapperkeeper usage, because trapperkeeper already blocks the main thread and
waits for a termination condition before allowing the process to exit.  However,
if you do need this functionality for some reason, you can simply call `(join)`
to cause your thread to wait for the Jetty server to shut down.

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
