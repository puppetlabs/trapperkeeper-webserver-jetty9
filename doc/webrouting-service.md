## Trapperkeeper Webrouting Service

This project additionally provides a webrouting service, which acts as a
wrapper around the Trapperkeeper Webserver Service, also contained in this
project. This service is for use with the
[trapperkeeper service framework.](https://github.com/puppetlabs/trapperkeeper)

The Webrouting Service is an optional service that allows you to manage the
configuration of your web service URLs in a different manner. It is a thin
wrapper around the Webserver Service, and it allows you to consolidate all
of your URL endpoints in a single section of your trapperkeeper configuration.

When using the Webserver Service to directly register web endpoints, the endpoints
get scattered throughout the code base, and it can be difficult to determine what
endpoints are running in your server and which services registered them. With the
webrouting service, all this information is stored in your configuration file. It
is easy to determine which endpoints are running on your server and which services
registered those endpoints.

For example:

```
web-router-service: {
    "puppetlabs.foo/foo-service": "/foo"
    "puppetlabs.bar/bar-service": {
        bar: "/bar"
        baz: "/baz"
    }
}
```

The services specified in the above configuration would use the Webrouting Service
instead of the Webserver Service to register web endpoints. A
developer/user/administrator can simply look at the trapperkeeper configuration and
determine there are web endpoints registered at '/foo', '/bar/, and '/baz', and that
these are registered in the clojure namespaces 'puppetlabs.foo' and 'puppetlabs.bar'.

To use this service in your trapperkeeper application, simply add this project
as a dependency in your leiningen project file, and then add the webrouting
service to your [`bootstrap.cfg`](https://github.com/puppetlabs/trapperkeeper#bootstrapping)
file, via:

    puppetlabs.trapperkeeper.services.webrouting.webrouting-service/webrouting-service

The webrouting service is configured via the
[trapperkeeper configuration service](https://github.com/puppetlabs/trapperkeeper#configuration-service).
Please see [Configuring the Webrouting Service](webrouting-config.md) for information on
how to configure the webrouting service.

### Service Protocol

This is the protocol for the current implementation of the `:WebroutingService`:

```clj
(defprotocol WebroutingService
  (get-route [this svc] [this svc route-id])
  (add-context-handler [this svc context-path] [this svc context-path options])
  (add-ring-handler [this svc handler] [this svc handler options])
  (add-servlet-handler [this svc servlet] [this svc servlet options])
  (add-war-handler [this svc war] [this svc war options])
  (add-websocket-handler [this svc handlers] [this svc handlers options])
  (add-proxy-route [this svc target] [this svc target options])
  (override-webserver-settings! [this overrides] [this server-id overrides])
  (get-registered-endpoints [this] [this server-id])
  (log-registered-endpoints [this] [this server-id])
  (join [this] [this server-id]))
```

#### `get-route`

This function allows you to get the web-route for a particular service
as configured in your configuration file. The one-argument version will
return the web route configured for the current service in a single-route
configuration. The two
argument version will return the web route configured for the current
service with the id you specify.

Note that the one argument version cannot be used with a service that
has multiple webroutes configured.

#### Other functions

The functions `override-webserver-settings!`, `get-registered-endpoints`,
`log-registered-endpoints`, and `join` all work in the exact same way as
their corresponding functions in the webserver service, and are there so that
you don't need to specify a dependency on the Webserver Service.

The other functions do the same thing as their Webserver Service counterparts. However,
instead of taking an explicit path as an argument, these functions take a service,
`svc`. `svc` should be the service calling the function. Instead of having an explicit
endpoint passed in as an argument, these functions will use the service given to them to
find the endpoint configured for that service in the configuration file. So, for example,
with the Webserver service, you would call

```clj
(add-ring-handler my-app "/my-app")
```

which would add the ring handler `my-app` to the endpoint `"/my-app"`. With the webrouting
service, however, you would call

```clj
(add-ring-handler this my-app)
```

which would find the endpoint configured for the current service in the configuration file,
then register the ring handler `my-app` at that endpoint.

The options map for each of these functions is identical to those in the corresponding
webserver service functions, with two exceptions.

First, they can take an additional, optional
key, `:route-id`. This is used when multiple endpoints are configured for a specific
service, with its value being the id of the specific endpoint you want to add the handler to.
In a multiroute configuration, a route-id MUST be specified or the operation will fail.

Second, `:server-id` is a disallowed key in this options map. Specifying a specific server
to which to add an endpoint is handled in the configuration of the webrouting service.

As an example, say you decide to add two endpoints using a specific service, and you have
two endpoints configured for that service.
One is endpoint `"/foo"` and is kept at key `:foo`. The other is
endpoint `"/bar"` and is kept at key `:bar`. If you were to call

```clj
(add-ring-handler this my-app {:route-id :foo)
```

the ring handler `my-app` would be registered at endpoint `"/foo"`. However, if you were to call

```clj
(add-ring-handler this my-app {:route-id :bar})
```

the ring handler `my-app` would be registered at endpoint `"/bar"`.

For information on how to configure multiple endpoints, please see
[Configuring the Webrouting Service](webrouting-config.md).
