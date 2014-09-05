## Configuring The Webserver Service

The `webserver` section in your Trapperkeeper configuration files configures an embedded
HTTP server inside trapperkeeper.

### `host`

This sets the hostname to listen on for _unencrypted_ HTTP traffic. If not
supplied, we bind to `localhost`, which will reject connections from anywhere
but the server process itself. To listen on all available interfaces,
use `0.0.0.0`.

### `port`

This sets what port to use for _unencrypted_ HTTP traffic.  If not supplied, but
`host` is supplied, a value of 8080 will be used.  If neither host nor port is
supplied, we won't listen for unencrypted traffic at all.

### `max-threads`

This sets the maximum number of threads assigned to responding to HTTP and HTTPS
requests, effectively changing how many concurrent requests can be made at one
time. Defaults to 100.

### `request-header-max-size`

This sets the maximum size of an HTTP Request Header. If a header is sent
that exceeds this value, Jetty will return an HTTP 413 Error response. This
defaults to 8192 bytes, and only needs to be configured if an exceedingly large
header is being sent in an HTTP Request.

### `ssl-host`

This sets the hostname to listen on for _encrypted_ HTTPS traffic. If not
supplied, we bind to `localhost`. To listen on all available interfaces,
use `0.0.0.0`.

### `ssl-port`

This sets the port to use for _encrypted_ HTTPS traffic. If not supplied, but
`ssl-host` is supplied, a value of 8081 will be used for the https port.  If
neither ssl-host nor ssl-port is supplied, we won't listen for encrypted traffic
at all.

### `ssl-cert`

This sets the path to the server certificate PEM file used by the web
service for HTTPS.  During the SSL handshake for a connection, certificates
extracted from this file are presented to the client for the client's use in
validating the server.  This file may contain a single certificate or a chain
of certificates ordered from the end certificate first to the most-root
certificate last.  For example, a certificate chain could contain:

* An end certificate
* An intermediate CA certificate with which the end certificate was issued
* A root CA certificate with which the intermediate CA certificate was issued

In the PEM file, the end certificate should appear first, the intermediate CA
certificate should appear second, and the root CA certificate should appear
last.

If a chain is present, it is not required to be complete.  If a
path has been specified for the `ssl-cert-chain` setting, the server will
construct the cert chain starting with the first certificate found in the
`ssl-cert` PEM and followed by any certificates in the `ssl-cert-chain` PEM.  In
the latter case, any certificates in the `ssl-cert` PEM beyond the first one
would be ignored.

> **Note:** This setting overrides the alternate configuration settings
`keystore` and `key-password`.

### `ssl-cert-chain`

This sets the path to a PEM with CA certificates for use in presenting a
client with the server's chain of trust.  Certs found in this PEM file are
appended after the first certificate from the `ssl-cert` PEM in the
construction of the certificate chain.  This is an optional setting.  The
certificates in the `ssl-cert-chain` PEM file should be ordered from the
least-root CA certificate first to the most-root CA certificate last.  For
example, a certificate chain could contain:

* An end certificate
* An intermediate CA certificate with which the end certificate was issued
* A root CA certificate with which the intermediate CA certificate was issued

The end certificate should appear in the `ssl-cert` PEM file.  In the
`ssl-cert-chain` PEM file, the intermediate CA certificate should appear
first and the root CA certificate should appear last.

The chain is not required to be complete.

> **Note:** This setting overrides the alternate configuration settings
`keystore` and `key-password`.

### `ssl-key`

This sets the path to the private key PEM file that corresponds with the
`ssl-cert`, it used by the web service for HTTPS.

> **Note:** This setting overrides the alternate configuration settings
`keystore` and `key-password`.

### `ssl-ca-cert`

This sets the path to the CA certificate PEM file used for client
authentication.  The PEM file may contain one or more CA certificates.
Authorized clients must have been signed - either directly or via an
intermediate CA - using one of the CA certificates in the PEM file.

> **Note:** This setting overrides the alternate configuration settings
`truststore` and `trust-password`.

### `keystore`

This sets the path to a Java keystore file containing the key and certificate
to be used for HTTPS.

### `key-password`

This sets the passphrase to use for unlocking the keystore file.

### `truststore`

This describes the path to a Java keystore file containing the CA certificate(s)
for your infrastructure.

### `trust-password`

This sets the passphrase to use for unlocking the truststore file.

### `cipher-suites`

Optional. A comma-separated list of cryptographic ciphers to allow for incoming
SSL connections. Valid names are listed in the
[official JDK cryptographic providers documentation](http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html#SupportedCipherSuites);
you'll need to use the all-caps cipher suite name.

If not supplied, trapperkeeper uses the default cipher suites for your local
system on JDK versions older than 1.7.0u6. On newer JDK versions, trapperkeeper
will use only non-DHE cipher suites.

### `ssl-protocols`

Optional. A comma-separated list of protocols to allow for incoming SSL
connections. Valid names are listed in the
[official JDK cryptographic protocol documentation](http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider);
you'll need to use the names with verbatim capitalization.
For example: `SSLv3, TLSv1, TLSv1.1, TLSv1.2`.

If not supplied, trapperkeeper uses the default SSL protocols for your local
system.

### `client-auth`

Optional. This determines the mode that the server uses to validate the
client's certificate for incoming SSL connections. One of the following
values may be specified:

* `need` - The server will request the client's certificate and the certificate
           must be provided and be valid. The certificate must have been issued
           by a Certificate Authority whose certificate resides in the
           `truststore`.

* `want` - The server will request the client's certificate. A certificate, if
           provided by the client, must have been issued by a Certificate
           Authority whose certificate resides in the `truststore`. If the
           client does not provide a certificate, the server will still consider
           the client valid.

* `none` - The server will not request a certificate from the client and will
           consider the client valid.

If a value is not provided for this setting, `need` will be used as the default
value.

### `ssl-crl-path`

Optional. This describes a path to a Certificate Revocation List file. Incoming
SSL connections will be rejected if the client certificate matches a
revocation entry in the file.

### `static-content`

Optional. This is a list of static content to be added to the server as context handlers
during initialization. Each item in this list should be a map containing two keys. The first,
`resource`, is the path to the resource you want added as a context handler (the equivalent of
the `base-path` argument of the `add-context-handler` service function). The second, `path`,
is the URL endpoint at which you want to mount the context handler (the equivalent of the
`context-path` argument of the `add-context-handler` service function).

For example, say you have a `web-assets` directory containing a file called `image.jpg`.
If your configuration were like so:

```
webserver: {
  port: 8080
  static-content: [{resource: "./web-assets"
                    path:     "/assets"}]
}
```

Then the static content in the `web-assets` directory would be mounted at the URL endpoint
`"/assets"` on your server during initialization, and you could access the contents of
`image.jpg` by visiting `"http://localhost:8080/assets/image.jpg"`.

By default, symbolic links will not be served by the Jetty9 Webservice. However, if you have
a symbolic link that you want to serve as static content, you can add an extra option,
`follow-links`, to the specification for a piece of static content. The value of this should
be a boolean, and if set to true, symbolic links will be served.

For example, say that you have a symbolic link in your `web-assets` directory, `image-link`,
that links to the `image.jpg` file. If you want this to be served, you would configure
your static content like so:

```
webserver: {
  port: 8080
  static-content: [{resource: "./web-assets"
                    path:     "/assets"
                    follow-links: true}]
}
```
Since `follow-links` is set to true, `image-link` will now be served, and can
be accessed by visiting `"http://localhost:8080/assets/image-link"`.

### `gzip-enable`

Optional. This controls whether or not the webserver could compress the
response body for any request using Gzip encoding. A value of `false` would
prevent the server from using Gzip encoding the response body for all requests.
If this option is not specified or is specified with a value of `true`, the
webserver "could" Gzip encode the response body.

Note that in order for Gzip encoding to be used, a client would also need to
include in the request an "Accept-Encoding" HTTP header containing the value
"gzip". The webserver also may use other heuristics to avoid Gzip encoding the
response body independent of the configuration of this setting.  For example,
the webserver may skip compression for a sufficiently small response body.

## Configuring multiple webservers on isolated ports

It is possible to configure multiple webservers on isolated ports within a single Jetty9
webservice. In order to configure multiple webservers, change the `webserver` section of your
Trapperkeeper configuration files to be a nested map. Each key in this map is the id of a server, and
its value is the configuration for that server.

For example, say you wanted to configure two servers on localhost, one on port 9000 and one on port
10000. The webserver section of your configuration file would look something like this:

```
webserver: {
    bar: {
        host: localhost
        port: 9000
    }

    foo: {
        host: localhost
        port: 10000
    }
}
```

This configuration would cause the Jetty9 service to create two different Jetty servers on isolated
ports. You can then specify which server you would like to add handlers to when calling the Jetty9
service functions, and they will be added to the server you specify.

Please note that, with the above configuration, you MUST specify a server-id when calling a service
function, or else the operation will fail. If you would like to have a multi-server configuration
and NOT specify a server-id when calling some service functions, you can optionally specify a
default server in your configuration file. Then, if no server-id is specified when performing
an operation, the operation will automatically be performed on the default server.

To specify a default server, add a `:default-server` key with a value of `true` to the configuration
information for one of your servers in your trapperkeeper configuration. For example:

```
webserver: {
    bar: {
        host:           localhost
        port:           9000
        default-server: true
    }

    foo: {
        host: localhost
        port: 10000
    }
}
```

The above configuration would set up two servers as in the previous example, except
the server with id `:bar` would be set as the default server. Calling a service function
without specifying a server-id will cause the operation to be performed on the server with
id `:bar`.

Please note that only one server can be specified as the default server. Please also note that
setting a default server is optional. It is only required if you are planning to call a service
function without passing in a server-id in a multi-server set-up.

Note that you are NOT limited to two servers and can configure more according to your needs.

Also note that you can still set the `webserver` section of your configuration to be an un-nested map
containing a single webserver configuration, like so

```
webserver: {
    host: localhost
    port: 9000
}
```

In this case, the Jetty9 Service will simply create a single webserver and give it id `:default`,
and will automatically make this server the default server.

### `jmx-enabled`

Optional. When enabled this setting will register the Jetty 9 MBeans so they are visible via
JMX. Useful for monitoring the state of your Jetty 9 instance while it is running; for monitoring and
debugging purposes. Defaults to `true`.
