## Configuring The Webserver Service

The `webserver` section in your Trapperkeeper configuration files configures an embedded
HTTP server inside trapperkeeper.

### `host`

This sets the hostname to listen on for _unencrypted_ HTTP traffic. If not
supplied, we bind to `localhost`, which will reject connections from anywhere
but the server process itself. To listen on all available interfaces,
use `0.0.0.0`.

### `port`

This sets what port to use for _unencrypted_ HTTP traffic. If not supplied, we
won't listen for unencrypted traffic at all.

### `max-threads`

This sets the maximum number of threads assigned to responding to HTTP and HTTPS
requests, effectively changing how many concurrent requests can be made at one
time. Defaults to 100.

### `ssl-host`

This sets the hostname to listen on for _encrypted_ HTTPS traffic. If not
supplied, we bind to `localhost`. To listen on all available interfaces,
use `0.0.0.0`.

### `ssl-port`

This sets the port to use for _encrypted_ HTTPS traffic. If not supplied, we
won't listen for encrypted traffic at all.

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

