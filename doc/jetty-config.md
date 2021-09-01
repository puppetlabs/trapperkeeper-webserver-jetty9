## Configuring The Webserver Service

The `webserver` section in your Trapperkeeper configuration files configures an
embedded HTTP server inside trapperkeeper.

### `host`

This sets the hostname to listen on for _unencrypted_ HTTP traffic. If not
supplied, we bind to `localhost`, which will reject connections from anywhere
but the server process itself. To listen on all available interfaces,
use `0.0.0.0`.

### `port`

This sets what port to use for _unencrypted_ HTTP traffic.  If not supplied, but
`host` is supplied, a value of 8080 will be used.  If neither host nor port is
supplied, we won't listen for unencrypted traffic at all.

### `acceptor-threads`

This sets the number of threads that the webserver will dedicate to accepting
socket connections for _unencrypted_ HTTP traffic.  Defaults to the number of
virtual cores on the host divided by 8, with a minimum of 1 and maximum of 4.

### `selector-threads`

This sets the number of selectors that the webserver will dedicate to
processing events on connected sockets for unencrypted HTTPS traffic. Defaults
to the minimum of: virtual cores on the host divided by 2 or `max-threads`
divided by 16, with a minimum of 1.

### `max-threads`

This sets the maximum number of threads assigned to responding to HTTP and/or
HTTPS requests for a single webserver, effectively changing how many concurrent
requests can be made at one time.  Defaults to 200.

Each webserver instance requires a minimum number of threads in order to boot
properly.  The minimum number is calculated as:

~~~~
(number of "acceptor-threads" for each port) +
(number of "selector-threads" for each port) +
(number of "reserved-threads" for each port) +
2 "worker" threads
~~~~

Reserved threads are unconfigurable and default to the minimum of the number
of virtual cores or `max-threads` divided by 10, with a minimum of one thread
allocated.

Because reserved threads (and if not explicitly configured, selector threads)
scale with max-threads, lowering the max-threads will cause fewer resources to
be allocated to handling reqeusts on each port down to a threshold
calculable by the forumalas above.

If the configured value for `max-threads` is less
than the minimum required value, server startup will fail with an
`IllegalStateException`, with a message containing the words
"Insufficient configured threads".

Note that each web request must be processed on a "worker" thread which is
separate from the acceptor and selector threads.  "1" is the minimum number of
worker threads required to process incoming web requests.  The `max-threads`
value should be large enough that the server can allocate all of the selector
and acceptor threads that it needs and yet still have a sufficient number of
worker threads left over for handling concurrent web requests.

### `queue-max-size`

This can be used to set an upper-bound on the size of the worker queue that the
web server uses to temporarily store incoming client connections before they
can be serviced.  This value defaults to the maximum value of a 32-bit signed
integer, 2147483647.  A request which is rejected by the web server because the
queue is full would be seen by the client as having initially connected to the
server socket at the TCP layer but having been closed almost immediately
afterward by the server with no HTTP layer response body.

### `request-body-max-size`

This sets the maximum size, in bytes, of the body for an HTTP request. The size
of the request body is determined from the value for the request's HTTP
Content-Length header. If the Content-Length exceeds the configured value, Jetty
will return an HTTP 413 Error response. If this setting is not configured and/or
the request does not provide a Content-Length header, Jetty will pass the
request through to underlying handlers (bypassing Content-Length evaluation).

### `request-header-max-size`

This sets the maximum size of an HTTP Request Header. If a header is sent
that exceeds this value, Jetty will return an HTTP 431 Error response. This
defaults to 8192 bytes, and only needs to be configured if an exceedingly large
header is being sent in an HTTP Request.

### `so-linger-seconds`

This setting has been removed. The option used in Jetty no longer attempts to
set the SO_LINGER for the socket as of v9.4.12.

The Jetty maintainers discovered this option has undefined behavior on
non-blocking connections (and all underlying connections became non-blocking
in Jetty 9.0).

The actual behavior, though undefined by the Java spec, also changes between
Java versions (so a user on Java 8 will see different behavior on Java 11) and
has been reported to the Jetty community as a source of bugs on some platforms.

### `idle-timeout-milliseconds`

This optional setting can be used to control how long Jetty will allow a
connection to be held open by a client without any activity on the socket.  If
a connection is idle for longer than this value, Jetty will forcefully close
it from the server side.  Jetty's default value for this setting is 30 seconds.
Note that Jetty will not automatically close the connection if the idle timeout
is reached while Jetty is still actively processing a client request.

### `ssl-host`

This sets the hostname to listen on for _encrypted_ HTTPS traffic. If not
supplied, we bind to `localhost`. To listen on all available interfaces,
use `0.0.0.0`.

### `ssl-port`

This sets the port to use for _encrypted_ HTTPS traffic. If not supplied, but
`ssl-host` is supplied, a value of 8081 will be used for the https port.  If
neither ssl-host nor ssl-port is supplied, we won't listen for encrypted traffic
at all.

### `ssl-acceptor-threads`

This sets the number of threads that the webserver will dedicate to accepting
socket connections for _encrypted_ HTTPS traffic.  Defaults to the number of
virtual cores on the host divided by 8, with a minimum of 1 and maximum of 4.

### `ssl-selector-threads`

This sets the number of selectors that the webserver will dedicate to
processing events on connected sockets for encrypted HTTPS traffic. Defaults
to the number of virtual cores on the host divided by 2, with a minimum of 1
and maximum of 4. The number of selector threads actually used by Jetty is
twice the number of selectors requested. For example, if a value of 3 is
specified for the `ssl-selector-threads` setting, Jetty will actually use 6
selector threads.

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

Optional. The cryptographic ciphers to allow for incoming SSL
connections. This may be formatted either as a list (in a HOCON
configuration file) or a comma-separated string. Valid names are
listed in the
[official JDK cryptographic providers documentation](http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html#SupportedCipherSuites);
you'll need to use the all-caps cipher suite name.

If not supplied, trapperkeeper uses this list of cipher suites:

 - `TLS_AES_128_GCM_SHA256`
 - `TLS_AES_256_GCM_SHA384`
 - `TLS_CHACHA20_POLY1305_SHA256`
 - `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256`
 - `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`
 - `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384`
 - `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384`
 - `TLS_DHE_RSA_WITH_AES_128_GCM_SHA256`
 - `TLS_DHE_RSA_WITH_AES_256_GCM_SHA384`


### `ssl-protocols`

Optional. The protocols to allow for incoming SSL connections. This
may be formatted either as a list (in a HOCON configuration file) or a
comma-separated string. Valid names are listed in the
[official JDK cryptographic protocol documentation](http://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider);
you'll need to use the names with verbatim capitalization.  For
example: `TLSv1, TLSv1.1, TLSv1.2`.

If not supplied, trapperkeeper uses this list of SSL protocols: 

- `TLSv1.3`
- `TLSv1.2`


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

### `allow-renegotiation`

Optional. This controls whether the web server will allow client initiated SSL/TLS
renegotiations. By default this will be disabled since allowing client to renegotiate
is a vulnerability causing denial of service and information disclosure in certain
cases. It can be over-ridden by setting this parameter to true.

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

### `access-log-config`

Optional. This is a path to an XML file containing configuration information
for the `Logback-access` module. If present, a logger will be set up to log
information about any HTTP requests Jetty receives according to the logging configuration,
as long as the XML file pointed to exists and is valid. Information on configuring the
`Logback-access` module is available [here](http://logback.qos.ch/access.html#configuration).

An example configuration file can be found [here](request-logging-example-config.xml). This
example configures a `FileAppender` that outputs to a file, `access.log`, in the `dev-resources`
directory. The `pattern` element configures the output format to match the [Apache Combined Log Format](https://httpd.apache.org/docs/2.4/logs.html#combined).
See the [Logback access layout documentation](https://logback.qos.ch/manual/layouts.html#logback-access)
for a list of other items that can be added to the `pattern` element.

TrapperKeeper configures the `Logback-access` library with additional support
for the SLF4J [Mapped Diagnostic Context](https://logback.qos.ch/manual/mdc.html) (MDC).
This support allows the `%X` and `%mdc` conversion words to be used in the `Logback-access`
`pattern` which behave as described in the [docs for Logback-classic](https://logback.qos.ch/manual/layouts.html#mdc).
Jetty is configured to clear any items added to the MDC at the end of each request
so that incorrect data won't show up in subsequent requests that are handled by
the same worker thread.


### `shutdown-timeout-seconds`

Optional. This is an integer representing the desired graceful stop timeout in seconds.
Defaults to 30 seconds.

### `post-config-script`

Optional.  This setting is for advanced use cases only, and is intended for
debugging purposes.  You can use it to modify low-level Jetty settings that
are not directly exposed in our normal configuration options.  In most cases,
if you find yourself using this, it is an indicator that we need to expose
additional settings directly in our main configuration (so please let us know!).
Also, the implementation details of this setting may change between releases.

If you do need to use this, you can set the value to a String containing some
Java code that should be executed against the Jetty `Server` object.  This object
will be injected into the scope of your code in a variable named `server`.

Here is a pathological example that shows how you could change the port that
your server listens on (which you could achieve in a much simpler fashion by
using the existing `port` setting; this example is only for the purposes of
illustration):

    post-config-script: "import org.eclipse.jetty.server.ServerConnector;
                         ServerConnector c = (ServerConnector)(server.getConnectors()[0]);
                         c.setPort(10000);"

For more info on the Jetty `Server` object model, see the
[Jetty Javadocs](http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/server/Server.html).

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

### `jmx-enable`

Optional. When enabled this setting will register the Jetty 9 MBeans so they are visible via
JMX. Useful for monitoring the state of your Jetty 9 instance while it is running; for monitoring and
debugging purposes. Defaults to `true`.
