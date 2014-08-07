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
service for HTTPS.

> **Note:** This setting overrides the alternate configuration settings
`keystore` and `key-password`.

### `ssl-key`

This sets the path to the private key PEM file that corresponds with the
`ssl-cert`, it used by the web service for HTTPS.

> **Note:** This setting overrides the alternate configuration settings
`keystore` and `key-password`.

### `ssl-ca-cert`

This sets the path to the CA certificate PEM file used for client
authentication. Authorized clients must be signed by the CA that that
corresponds to this certificate.

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

## Configuring multiple webservers on isolated ports

It is possible to configure multiple webservers on isolated ports within a single Jetty9
webservice. In order to configure multiple webservers, change the `webserver` section of your
Trapperkeeper configuration files to be a nested map. Each key in this map is the id of a server, and
its value is the configuration for that server. At least one server must have an id of default.

For example, say you wanted to configure two servers on localhost, one on port 9000 and one on port
10000. The webserver section of your configuration file would look something like this:

```
webserver: {
    default: {
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
service functions, and they will be added to the server you specify. If no server-id is specified
when adding handlers, they will be added to the `:default` server.

Note that you are NOT limited to two servers and can configure more according to your needs.

Also note that you can still set the `webserver` section of your configuration to be an un-nested map
containing a single webserver configuration, like so

```
webserver: {
    host: localhost
    port: 9000
}
```

In this case, the Jetty9 Service will simply create a single webserver and give it id `:default`.

### `jmx-enabled`

Optional. When enabled this setting will register the Jetty 9 MBeans so they are visible via
JMX. Useful for monitoring the state of your Jetty 9 instance while it is running; for monitoring and
debugging purposes. Defaults to `true`.
