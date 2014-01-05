## Trapperkeeper Webserver Service

This project provides a webserver service for use with the
[trapperkeeper service framework](https://github.com/puppetlabs/trapperkeeper)
To use this service in your trapperkeeper application, simply add this
project as a dependency in your leinengen project file, and then add the
webserver service to your [`bootstrap.cfg`](https://github.com/puppetlabs/trapperkeeper#bootstrapping)
file, via:

    puppetlabs.trapperkeeper.services.webserver.jetty9-service/webserver-service

Note that this implementation of the
`webserver-service` interface is based on Jetty 9, which contains performance
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
so, you can control various properties of the server (ports, SSL, etc.) by adding a `[webserver]`
section to one of your configuration ini files, and setting various properties
therein.  For more info, see [Configuring the Webserver](doc/jetty-config.md).

The `webserver-service` currently supports web applications built using
Clojure's [Ring](https://github.com/ring-clojure/ring) library and Java's Servlet
API.  There is also an experimental webserver service that supports loading
ruby Rack applications via JRuby; for more info, see the
[trapperkeeper-ruby](https://github.com/puppetlabs/trapperkeeper-ruby) project.

### Example code

Two examples are included with this project:

* A Ring example ([documentation](./doc/example-web-service.md) /
   [source code](./examples/ring_app))
* A Java servlet example ([source code](./examples/servlet_app))

### Provided functions

The current implementation of the `webserver-service` provides three functions:
`add-ring-handler`, `add-servlet-handler`, and `join`.

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

Here's an example of how to use the `:webserver-service`:

```clj
(defservice my-web-service
   {:depends [[:webserver-service add-ring-handler]]
    :provides []}
   ;; initialization
   (add-ring-handler my-app "/my-app")
   ;; return service function map
   {})
```

*NOTE FOR COMPOJURE APPS*: If you are using compojure, it's important to note
that compojure requires use of the [`context` macro](https://github.com/weavejester/compojure/wiki/Nesting-routes)
in order to support nested routes.  So, if you're not already using `context`,
you will need to do something like this:

```clj
(ns foo
   (:require [compojure.core :refer [context]]
   ;;...
   ))

(defservice my-web-service
   {:depends [[:webserver-service add-ring-handler]]
    :provides []}
   ;; initialization
   (let [context-path "/my-app"
         context-app  (context context-path [] my-compojure-app)]
     (add-ring-handler context-app context-path))
   ;; return service function map
   {})
```

#### `add-servlet-handler`

`add-servlet-handler` takes two arguments: `[servlet path]`.  The `servlet` argument
is a normal Java [Servlet](http://docs.oracle.com/javaee/7/api/javax/servlet/Servlet.html).
The `path` is the URL prefix at which the servlet will be registered.

For example, to host a servlet at `/my-app`:

```clj
(ns foo
    ;; ...
    (:import [bar.baz SomeServlet]))

(defservice my-web-service
  {:depends [[:webserver-service add-servlet-handler]]
   :provides []}
  ;; initialization
  (add-servlet-handler (SomeServlet. "some config") "/my-app")
  ;; return service function map
  {})
```

For more information see the [example servlet app](examples/servlet_app).

#### `join`

This function is not recommended for normal use, but is provided for compatibility
with the `ring-jetty-adapter`.  `ring-jetty-adapter/run-jetty`, by default,
calls `join` on the underlying Jetty server instance.  This allows your thread
to block until Jetty shuts down.  This should not be necessary for normal
trapperkeeper usage, because trapperkeeper already blocks the main thread and
waits for a termination condition before allowing the process to exit.  However,
if you do need this functionality for some reason, you can simply call `(join)`
to cause your thread to wait for the Jetty server to shut down.

