## Configuring The Webrouting Service

The `web-router-service` section in your Trapperkeeper configuration files
configures the endpoints for your webrouter service.

Each key in the `web-router-service` section is the namespaced symbol of
a service. The value stored in this key can be one of two things.

If only specifying one endpoint for a particular service, this value can
be a string containing a URL endpoint. This will be the only endpoint
available for the service it is configured for, and it will automatically
be assigned route-id `:default`. This can be done like so:

```
web-router-service: {
    "puppetlabs.foo/foo-service": "/foo"
}
```

It is also possible to configure multiple web endpoints. In this case,
the value will be a map instead of a string. Each key in this map
will contain a URL endpoint stored in a string. They key is the route-id
for that endpoint. This can be done like so:

```
web-router-service: {
    "puppetlabs.foo/foo-service": {
        foo: "/foo"
        bar: "/bar"
    }
}
```

In this case, two endpoints will be configured for the `foo-service`.
`"/foo"` will have route-id `:foo`, and `"/bar"` will have route-id
`:bar`. Handlers can be added to the `"/bar"` endpoint by explicitly
specifying `:bar` as the `route-id` when adding a handler. Please see
[Trapperkeeper Webrouting Service](webrouting-service.md) for
more information.

In the case where you have configured multiple servers, you can configure
the webrouting service to add specific endpoints to specific servers. For
example, say you have two servers, one with id `:foo` and one with id `:bar`.
Say you want the endpoint for a service to be added to the server with id
`:foo`. You could do this like so:

```
web-router-service: {
    "puppetlabs.foo/foo-service": {
        route: "/foo"
        server: "foo"
    }
}
```

You can do the same thing when you have multiple routes configured for a
service:

```
web-router-service: {
    "puppetlabs.foo/foo-service": {
        foo: {
            route: "/foo"
            server: "foo"
        }
        bar: {
            route: "/bar"
            server: "bar"
        }
    }
}
```

In this case, adding a handler to endpoint `:foo` would add it to the
server with id `:foo` at path "/foo". Adding a handler to endpoint
`bar` would add it to the server with id `:bar` at path "/bar".

Note that, if no server is specified for an endpoint and there are
multiple servers, the endpoint will be added to the default server.
If no default server is set, a server MUST be provided for every
endpoint.

Also note that, because the webrouting service is built on top of the
webserver service, the webserver service will need to be included in your
`bootstrap.cfg` file, and the webserver service will need to be configured in
your trapperkeeper configuration files. Please see
[Configuring the Webserver](jetty-config.md) for more details.

