## 0.3.3-SNAPSHOT
 * Fix bug where even if no http "port" was specified in the webserver config,
   the Jetty webserver was still opening an http binding on port 8080.  An
   http port binding will now be opened only if a "port" is specified in the
   config file.


