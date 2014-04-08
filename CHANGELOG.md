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
