## 4.4.1
* update clj-parent to 5.2.9, which includes the stylefruits/gniazdo dependency.

## 4.4.0
* update clj-parent which moves to the `18on` series of bouncy castle from the `15on` series.

## 4.3.1
* update to Jetty 9.4.48 for additional bug fixes

## 4.3.0

(maint) Update to Jetty 9.4.44 for small bug fixes and dependency bumps
(PE-32764) Update default ciphers:
  - Remove the TLS_CHACHA20_POLY1305_SHA256 cipher
  - Add TLS_DHE_RSA* ciphers
  - Add the RSA ECDHE AES 256 cipher to the FIPS list
  - Rearrange the cipher list to be in the preferred order

## 4.2.1

(maint) Enable TLS 1.3 by default for FIPS [#232](https://github.com/puppetlabs/trapperkeeper-webserver-jetty9/pull/232)

## 4.2.0

[TK-494](https://tickets.puppetlabs.com/browse/TK-494) Enable TLS 1.3 by default.

## 4.1.8

Update Jetty to 9.4.43 to resolve CVE-2021-34429

## 4.1.7

Update Jetty to 9.4.42 to resolve CVE-2021-28169

## 4.1.6

Update jetty to 9.4.40 to attempt to avoid a connection reset bug.

## 4.1.5

Update jetty to 9.4.39.v20210325 to resolve CVEs:

- CVE-2021-28165 - #6072 - jetty server high CPU when client send data length > 17408
- CVE-2021-28164 - #6101 - Normalize ambiguous URIs
- CVE-2021-28163 - #6102 - Exclude webapps directory from deployment scan

## 4.1.4

Update clj-kitchensink to 3.1.3 via an update of clj-parent to 4.6.18

## 4.1.3

Update flatland/ordered to 1.5.9, to avoid conflicting with other libs.

## 4.1.2

Ship artifacts with Java 8 builds.

## 4.1.1

Update jetty version to 9.4.36. This is a maintenance update.

## 4.1.0

Update jetty version to 9.4.28. This is a maintenance update.

## 4.0.3

Fix ambiguous type inference when running under Java 11

## 4.0.2

Further FIPS updates and cleanup

## 4.0.1

Add a FIPS profile and remove SSLv3 support.

## 4.0.0

Further restrict cipher-suites and protocols to current best practices.
See documentation for configuring Jetty for additional details.

## 3.0.3

This was released but had no code changes

## 3.0.2

Suppress warning about empty contextPath

## 3.0.1

This is a bug fix release

With this release we no longer fail to start if so-linger-seconds is set,
only warn the user that the seeting will be ignored. This should allow
easier upgrade paths.


## 3.0.0

This is a feature release with backwards breaking changes.

* Java 11 support. This library should now be runnable and compilable on Java 11.
* Jetty version bump from v9.4.11 to v9.4.18. This version bump resolves many issues with Jetty and enables the above Java 11 support. It also breaks several existing configurations.
    * `so-linger-seconds` option removed. Jetty maintainers realized this option had undefined behavior and removed the underlying option.
    * `IOException` thrown when failure to bind to port. Previously, this library tested that `BindException` was thrown and treated it as an API. This was invalid from the Jetty maintainers standpoint (they only test that the more general `IOException` is thrown).
    * Default Cipher Suite refresh. Only three of the existing default ciphers were still considered by Jetty to be secure enough not to cause warnings on startup. These three cipher suites remain and many additional modern cipher suites have been added.


## 2.4.1

* [PCP-862](https://tickets.puppetlabs.com/browse/PCP-862) Only disconnect if
  the session has not already been closed.

## 2.4.0

* [PCP-862](https://tickets.puppetlabs.com/browse/PCP-862) Add disconnect
  function to allow disconnecting from websocket connections rather than
  closing them
* (maint) Renew expired SSL certificates
  This renews expired SSL certificates `certs/ca.pem`, `certs/localhost.pem`,
  and `certs/localhost.p12`, which were causing tests to fail unexpectedly.

## 2.3.1

* [TK-473](https://tickets.puppetlabs.com/browse/TK-473) Stop reporting jetty
  version in responses

## 2.3.0

This is a feature release.

* [TK-470](https://tickets.puppetlabs.com/browse/TK-470) Add MDC support
  This release adds the ability to use the MDC (Mapped Diagnostic
  Context) to save per thread diagnostic information about requests. That
  information may then be output in request logs as desired. For more
  information see https://logback.qos.ch/manual/mdc.html.
* (maint) Update version dependencies
  This moves us to using clj-parent 2.x (adds improved support for Java 11
  and Clojure 1.9)

## 2.2.0

This is a dependency update release that should be transparent to users
but may have upgrade risks.

* [SERVER-2213](https://tickets.puppetlabs.com/browse/SERVER-2118) Upgrade
  Jetty to latest
  This upgrades from Jetty 9.4.4.v20170414 to Jetty 9.4.11.v20180605. The
  only thing of note noticed in testing is that minimum threads allocated
  per connection has changed, now scaling up and down with the size
  of the server's threadpool. This change has not been noticed to cause
  issue with any default setup, though with a version bump of this size
  there may be yet uncaught regressions.

## 2.1.2

This is a bug fix release.

* [SERVER-2118](https://tickets.puppetlabs.com/browse/SERVER-2118) Enable
  gzipping post request responses.
  A jetty update requiring HTTP methods to be whitelisted before their
  responses could be gzipped broke gzipping of POST request responses.
  This patch updates our jetty configuration to also allow gzipping of
  POST requests, instead of just GET requests.

## 2.1.1

This is a bug fix release, but contains important upgrade information.

* [SERVER-1597](https://tickets.puppetlabs.com/browse/SERVER-1597) Enforce
  disallowed content re-negotiation.
  Previously, Jetty would allow content negotiation despite being documented
  as defaulting to disallow. This patch explicitly disallows as a default and
  provides users the means to allow renegotiation by using the
  `webserver.allow-renegotiation` key.

Also, a special thanks to @EdwardBetts for the documentation fix!

## 2.1.0

This is a feature release.

* [TK-451](https://tickets.puppetlabs.com/browse/TK-451) Support runtime refresh
  of Jetty CRL via puppetlabs/trapperkeeper-filesystem-watcher. Changes to CRL
  will result in Jetty reloading the CRL without a server reload or restart.

## 2.0.1

The 2.0.0 release was mistakenly for a Java 7 runtime even though the
underlying Jetty libraries were built for Java 8 runtime.  The code
for this release is built for a Java 8 runtime instead.

## 2.0.0

This is a major release

* [TK-369](https://tickets.puppetlabs.com/browse/TK-369) Move Jetty dependency to 9.4.4.
  Notably, this change drops Java 7 support as Jetty 9.3 and higher only
  support Java 8 and higher. No public APIs have changed in this release.

* The behavior of normalize-uri-path has changed slightly due to changes in
  Jetty's URIUtil decodePath method. Specifically, overlong encodings (which
  are considered invalid UTF-8) change their decoded value (but are still not
  traversable), and semicolons no longer terminate URI processing, but only
  when ; is followed at some point by another path segment beginning with /.
  For example, "/foo/bar;bar=chocolate/baz;baz=bim" now decodes to
  "/foo/bar/baz" while before it decoded to "/foo/bar". If the request has
  invalid % encoded UTF-8 characters, the path will be decoded as an ISO-8859-1
  encoded string.
  (https://github.com/eclipse/jetty.project/commit/7f62f2600b943b9aed0e4771891939bf61372c5a)

* [TK-373](https://tickets.puppetlabs.com/browse/TK-373) Enable Diffie Hellman
  cipher suites.  DHE cipher suites were previously disabled due to bugs with
  DH ciphers on Oracle JDK 7.

* [TK-374](https://tickets.puppetlabs.com/browse/TK-374) Remove obsolete SSL ciphers.

## 1.8.0

This is a feature release.

* [TK-149](https://tickets.puppet.com/browse/TK-149) Support runtime refresh of
  Jetty CRL via puppetlabs/trapperkeeper-filesystem-watcher - changes to CRL
  will be result in Jetty reloading the CRL without a service reload or
  restart.

## 1.7.0

This is a feature and bugfix release.

* [SERVER-1695](https://tickets.puppetlabs.com/browse/SERVER-1695) Add an
optional `request-body-max-size` setting for restricting the maximum
`Content-Length` allowed for requests.
* [TK-429](https://tickets.puppetlabs.com/browse/TK-429) Fix for the ability
to gzip-encode response bodies when an access log is configured.

## 1.6.0

This is a "feature" release.

* Added a new function `request-path` to the WebsocketProtocol
* [TK-410](https://tickets.puppetlabs.com/browse/TK-410) Add the i18n library
as a dependency and use it to externalize strings.

## 1.5.10

This is a maintenance release.

* Remove unneeded logback-access dependency

## 1.5.9

This is a maintenance release.

* Upgrade ring-servlet and related dependencies to 1.4.0

## 1.5.8

This is a maintenance release.

* Upgrade java.jmx dependency to 0.3.1

## 1.5.7

This is a bugfix release.

* [TK-372](https://tickets.puppetlabs.com/browse/TK-372) Fix a memory leak that
  occurred when a SIGHUP was used to restart services and at least one webserver
  has Jetty's JMX metrics enabled.

## 1.5.6

This is a security release.

* [TK-343](https://tickets.puppetlabs.com/browse/TK-343) Support a new
  option for handler registrations, `normalize-request-uri`, which can be
  used to request that the URI path component is sanitized before the
  handler is invoked for a request and that `.getRequestURI` calls made by
  the handler return a path that has been percent-decoded.

## 1.5.5

This is a bugfix and maintenance release.

* [TK-333](https://tickets.puppetlabs.com/browse/TK-333) Tolerate multiple
  calls to `stop` by ensuring that the server shuts down and cleans up mbeans in
  an idempotent way.
* Upgrade Trapperkeeper dependency to 1.3.1
* Upgrade Clojure dependency to 1.7.0

## 1.5.4

This is a bugfix release.

* [TK-338](https://tickets.puppetlabs.com/browse/TK-338) Handle the
  `TimeoutException` that Jetty throws if its `stopTimeout` is reached
  before it can gracefully complete all of the open requests.  Ensures
  that the server will be restarted during a HUP even if the timeout
  occurs.

## 1.5.3

This version number was burned due to an error during the release/deploy
process.

## 1.5.2

This is a maintenance release.

* Make `org.clojure/java.jmx` a top-level dependency so that it can be
  pulled in automatically via a transitive dependency by consumers of the
  testutils jar.

## 1.5.1

This is a bugfix release.

* [TK-301](https://tickets.puppetlabs.com/TK-301) Fix a memory leak related
  to Jetty's JMX metrics; this leak is only relevant if using the recent
  HUP support released in Trapperkeeper 1.3.0.

## 1.5.0

This is a "feature" release.

* Added new function `get-server` to web routing service.

## 1.4.1

This is a bugfix release.

* [TK-270](https://tickets.puppetlabs.com/TK-270) Fix a bug that prevented
  the use of 1-arity WebsocketProtocol/close!.

## 1.4.0

This is a feature and maintenance release.

* [TK-247](https://tickets.puppetlabs.com/TK-247) Added tests for Path
  Traversal Attacks.
* Add experimental support for websockets via the `add-websockets-handler`
  function in the WebserverService and WebroutingService and corresponding
  client protocol WebsocketProtocol.
* Updated ssl-utils dependency to 0.8.1

## 1.3.1

This is a maintenance release.

* [TK-195](https://tickets.puppetlabs.com/browse/TK-195) Update prismatic
  dependencies to the latest versions

## 1.3.0

This is a "feature" and security release.

* [TK-178](https://tickets.puppetlabs.com/browse/TK-178) Upgraded Jetty version
  dependency to v9.2.10.  Jetty v9.2.10 includes changes made in the Jetty
  v9.2.9 release to address a critical security vulnerability with data
  potentially being leaked across requests.  See https://dev.eclipse.org/mhonarc/lists/jetty-announce/msg00074.html
  for more information.  For a rollup of changes included in the Jetty v9.2.10
  release, see https://github.com/eclipse/jetty.project/blob/jetty-9.2.10.v20150310/VERSION.txt.

* [TK-168](https://tickets.puppetlabs.com/browse/TK-168) Default values for
  several settings will now derive from the underlying defaults that Jetty would
  use.  This effectively changes the defaults for the following settings:

  - `shutdown-timeout-seconds` in `webserver` section - 60 seconds -> 30 seconds

  - `:idle-timeout` for `add-proxy-route` - 60 seconds -> 30 seconds

* [TK-148](https://tickets.puppetlabs.com/browse/TK-148) Several related
  changes:

  - Default for `max-threads` in `webserver` section changed from 100 to
    200.

  - Exposed new settings for configuring the number of `acceptor-threads`
    and `selector-threads` that a Jetty webserver will use.

  - Removed work which would automatically bump the server's `max-threads` up
    to the minimum needed for the server to boot for the case that `max-threads`
    had not been configured but the server's minimum needed threads had
    exceeded the default `max-threads`.  The original work which enabled the
    automatic bump had been done in [TK-130](https://tickets.puppetlabs.com/browse/TK-130).

## 1.2.0

This is a feature release.

* Upgrade to version 9.2.8 of upstream Jetty.  We were previously at
  v9.1.0, which was over a year old.  The newer version contains some
  performance improvements and bug fixes for potential networking
  issues.
* [TK-140](https://tickets.puppetlabs.com/browse/TK-140)
  Expose new `so-linger-seconds` setting, which can be used to adjust the TCP
  SO_LINGER time.
* [TK-144](https://tickets.puppetlabs.com/browse/TK-144)
  Expose new `post-config-script` setting; this is for advanced / edge-case
  configuration needs.  If you need to modify a Jetty setting that we don't
  expose in our own config, you can provide a snippet of Java code to access
  the Jetty Server object directly and modify additional settings.
* [TK-133](https://tickets.puppetlabs.com/browse/TK-133)
  Support comma-delimited strings for the config value for `ssl-protocols`
  and `cipher-suites`.  This allows these settings to be used with older
  config file formats, such as ini.
* [TK-151](https://tickets.puppetlabs.com/browse/TK-151)
  Expose new `idle-timeout-milliseconds` setting, which can be used to tell
  Jetty to forcefully close a client connection if it is idle for a specified
  amount of time.

## 1.1.1

* [TK-82](https://tickets.puppetlabs.com/browse/TK-82)
  Add configuration option to control maximum number of
  open HTTP connections that Jetty will maintain.
* Upgrade trapperkeeper dependency to 1.0.1.
* Upgrade jvm-ssl-utils (previously known as jvm-certificate-authority)
  dependency to 0.7.0.

## 1.1.0

* [TK-130](https://tickets.puppetlabs.com/browse/TK-130)
  The default value for Jetty's maximum threadpool size is now
  calculated to ensure it can start up on a box with a large
  number of cores.

## 1.0.1

* This release adds an additional configuration option to
  `add-proxy-route` ([TK-110](https://tickets.puppetlabs.com/browse/TK-110)).

## 1.0.0

* Promoting previous version to 1.0.0 so that we can begin to
  be more deliberate about adhering to semver in the future.

## 0.9.0

This is a security release.

* [TK-96](https://tickets.puppetlabs.com/browse/TK-96): Define
  a default set of SSL protocols that the server should allow
  (TLSv1, TLSv1.1, TLSv1.2) and use them if the user doesn't
  explicitly set the `ssl-protocols` setting.

## 0.8.1

This is a minor bugfix release.

* Fix an issue wherein the default graceful shutdown
  timeout was not being set to 60 seconds.

## 0.8.0

* Adds a new option, `:redirect-if-no-trailing-slash`,
  that determines whether or not a 302 response will be
  returned when making requests to endpoints with registered
  handlers without a trailing slash on the end.
* By default, requests will now route through to a handler
  when no trailing slash is present on the request URL rather
  than returning a 302 response (which was the behavior in
  previous versions).
* Adds graceful shutdown support and a new option to the
  webserver config, `shutdown-timeout-seconds`, that allows
  users to set the stop timeout of the Jetty server.

## 0.7.7

This is a minor feature and bugfix release.

* Improves various error messages thrown by the
  Webrouting and Webserver services.
* Changes the data structure output by the
  `get-registered-endpoints` and `log-registered-endpoints`
  functions. Now, a map will be output where each key is
  an endpoint, with its value being an array containing
  information on every handler registered at that endpoint.
* Adds a new option to the webserver configuration,
  `access-log-config`, that allows configuration of request
  logging.
* [TK-84](https://tickets.puppetlabs.com/browse/TK-84)
  Query parameters were not being decoded when the URI was
  being rewritten in the reified ProxyServlet class, meaning
  they would get double encoded.
* Adds a new option to `add-proxy-route`,
  `failure-callback-fn`, which allows customization of
  HTTP Error Responses.

## 0.7.6

This is a dead release.

## 0.7.5

This is a minor feature release.

* [TK-75](https://tickets.puppetlabs.com/browse/TK-75)
  Adds a new option `gzip-enable` that can be used to
  enable/disable support for gzipping responses to
  requests that include an appropriate `Accept-Encoding`
  header.

## 0.7.4

This is a minor feature release.

* Adds a new option to both the `static-content` configuration
  setting in the webserver config and to the add-context-handler
  service function that allows symlinks to be followed when serving
  static content.

## 0.7.3

This is a minor feature release.

* Adds a new, optional `static-content` configuration setting to the
  webserver config.  This setting allows you to serve files on disk
  or resources in a jar as static assets at a given URL prefix,
  all via configuration.

## 0.7.2

This is a minor, backward-compatible feature and bugfix release.

* [TK-58](https://tickets.puppetlabs.com/browse/TK-58):
  `default-server` support did not work for some functions, such as
  `get-registered-endpoints`.
* Add support for SSL certificate chains, and new setting `ssl-cert-chain`
* Upgrade to Trapperkeeper 0.5.1

## 0.7.1

* [TK-53](https://tickets.puppetlabs.com/browse/TK-53):
  Add a `get-route` function to web routing service.
* [TK-33](https://tickets.puppetlabs.com/browse/TK-33):
  Add support for configuring proxy routes to automatically
  follow redirects from the remote server.
* In proxy configuration, add support for a callback function that
  can rewrite the URI before the request is proxied.
* [TK-45](https://tickets.puppetlabs.com/browse/TK-45):
  Add support for strings in addition to keywords when specifying the
  URI scheme for proxy requests.

## 0.7.0

* [TK-50](https://tickets.puppetlabs.com/browse/TK-50):
  Changes to "default" server handling in a multi-server configuration:
  * It is no longer required to specify a default server.  If a service function
    is called without specifying a `server-id` when there are multiple servers
    configured, an error will be thrown.
  * It is no longer required that the default server be named `default`;
    instead it is configured by specifying `default-server: true`
    in the configuration for the given server.
* [TK-51](https://tickets.puppetlabs.com/browse/TK-51):
  Added the ability to the specify `server-id` in the `WebroutingService`
  configuration, instead of forcing it to be done in code.
* Minor bug fixes and improvements:
    * [TK-48](https://tickets.puppetlabs.com/browse/TK-48),
      [TK-44](https://tickets.puppetlabs.com/browse/TK-44)


## 0.6.1
* Add configuration option `request-header-max-size`
* Increase default buffer sizes for request and response
* Update test dependencies to latest version of puppetlabs/http-client (0.2.1)

## 0.6.0
* The `WebserverService` can now run multiple Jetty servers, on different ports.
* Added a new `WebroutingService` to provide a centralized, configuration-based
  way to configure all of the URL paths at which services will register web applications
  (Ring handlers, Servlets, etc.)
* Added JMX reporting to the `jetty9-service`
* Added `get-registered-endpoints` and `log-registered-endpoints` functions
  to the `WebserverService`
* Minor bug fixes and improvements:
  * [TK-21](https://tickets.puppetlabs.com/browse/TK-21),
    [TK-22](https://tickets.puppetlabs.com/browse/TK-22),
    [TK-31](https://tickets.puppetlabs.com/browse/TK-31),
    [TK-43](https://tickets.puppetlabs.com/browse/TK-43)
* Upgraded trapperkeeper dependency to version 0.4.3
* Upgraded kitchensink dependency to version 0.7.2

## 0.5.2
 * Update trapperkeeper dependency to version 0.4.2.
 * Update kitchensink dependency to version 0.7.1.
 * Update certificate-authority dependency to 0.1.5.
 * Update http-client dev dependency to 0.1.7.
 * Stop is now called on the Jetty Server instance if an error occurs in Jetty
   code while the server is starting up.  This allows the process running
   Trapperkeeper to shut down properly after such an error has occurred.
 * Validation of the webserver configuration is now done via the use of
   Prismatic Schema.
 * A new webserver option, `ssl-crl-path`, can be used to configure a
   Certificate Revocation List that Jetty would use to validate client
   certificates for incoming SSL connections.

## 0.5.1
 * Upgrade trapperkeeper dependency to version 0.3.12
 * Upgrade kitchensink dependency to version 0.7.0
 * Replace clj-http dependency with [puppetlabs/http-client](https://github.com/puppetlabs/clj-http-client)
 * Update test/example configuration files to use HOCON instead of .ini files

## 0.5.0
 * Added new function `override-webserver-settings!`, which allows another
   service to provide overridden values for the webserver configuration.
 * Update to latest version of puppetlabs/kitchensink
 * Use puppetlabs/certificate-authority for all SSL-related tasks

## 0.4.0
 * Added new function `add-proxy-route`, which supports configuring the server to
   work as a reverse proxy for certain routes

## 0.3.5
 * Added a new service function, `add-context-handler`, which supports registering
   a context handler for static content, with optional support for context listeners
   via the `javax.servlet.ServletContextListener` interface.

## 0.3.4
 * Added support for registering WAR files via the `add-war-handler` service function.
 * Moved server creation from the `init` life cycle to the `start` life cycles.

## 0.3.3
 * Fix bug where even if no http `port` was specified in the webserver config,
   the Jetty webserver was still opening an http binding on port 8080.  An
   http port binding will now be opened only if a `port` is specified in the
   config file.
 * A config file can now optionally include a `client-auth` webserver setting.
   The setting specifies how the server validates the client certificate
   during the setup of an SSL connection.  The default behavior if the setting
   is not specified is the same as with prior releases; the server will
   require that the SSL client provide a certificate and that the certificate
   be valid.  For more information, refer to the [jetty-config.md]
   (doc/jetty-config.md) document.
