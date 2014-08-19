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
