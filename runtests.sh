#!/usr/bin/env bash

set -e
set -x

lein test puppetlabs.trapperkeeper.services.webrouting.webrouting-service-handlers-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webrouting.webrouting-service-override-settings-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webrouting.webrouting-service-proxy-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webrouting.webrouting-service-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webserver.jetty9-config-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
2016-02-12 14:54:30,880 WARN  [p.t.s.w.jetty9-config] The 'post-config-script' setting is for advanced use cases only, and may be subject to minor changes when the application is upgraded.
2016-02-12 14:54:30,894 WARN  [p.t.s.w.jetty9-config] The 'post-config-script' setting is for advanced use cases only, and may be subject to minor changes when the application is upgraded.

lein test puppetlabs.trapperkeeper.services.webserver.jetty9-core-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webserver.jetty9-default-config-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webserver.jetty9-service-handlers-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webserver.jetty9-service-override-settings-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.services.webserver.jetty9-service-proxy-test puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.testutils.webrouting.common puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.testutils.webserver puppetlabs.trapperkeeper.services.webserver.jetty9-service-test

lein test puppetlabs.trapperkeeper.testutils.webserver.common puppetlabs.trapperkeeper.services.webserver.jetty9-service-test
