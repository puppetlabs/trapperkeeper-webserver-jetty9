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
    puppetlabs.foo/foo-service: "/foo"
}
```

It is also possible to configure multiple web endpoints. In this case,
the value will be a map instead of a string. Each key in this map
will contain a URL endpoint stored in a string. They key is the route-id
for that endpoint. This can be done like so:

```
web-router-service: {
    puppetlabs.foo/foo-service: {
        default: "/foo"
        bar:     "/bar"
    }
}
```

In this case, two endpoints will be configured for the `foo-service`.
`"/foo"` will have route-id `:default`, and `"/bar"` will have route-id
`:bar`. Handlers can be added to the `"/bar"` endpoint by explicitly
specifying `:bar` as the `route-id` when adding a handler. Please see
[Trapperkeeper Webrouting Service](doc/webrouting-service.md) for
more information.

Please note that, when configuring endpoints in this way, there must be
an endpoint with route-id `:default`.

