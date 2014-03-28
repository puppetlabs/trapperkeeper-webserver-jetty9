# TrapperKeeper Webserver Service Test Utils

The trapperkeeper webserver service library provides some
[utility code](../test/clj/puppetlabs/trapperkeeper/testutils)
for use in tests. The code is available in a separate "test" jar that you may depend
on by using a classifier in your project dependencies.

```clojure
  (defproject yourproject "1.0.0"
    ...
    :profiles {:dev {:dependencies [[puppetlabs/trapperkeeper-webserver-jetty9 "x.y.z" :classifier "test"]]}})
```

The test jar contains a macro to assist in testing the functionality of a ring application.
You can find the macro in [webserver.clj](../test/puppetlabs/trapperkeeper/testutils/webserver.clj).

### with-test-webserver

The `with-test-webserver` macro starts up a new web server which is bound to a random unused port, and attaches a
provided Ring handler function. When the test is completed `with-test-webserver` also handles shutting down the web server.

The first parameter provided to the `with-test-webserver` macro is a ring handler function (see
[ring concepts](https://github.com/ring-clojure/ring/wiki/Concepts)) which will generally by a handler that exists in
your _trapperkeeper_ application somewhere. The second parameter is an identifier which will contain the port number
that the web server was started on.

Generally, inside the body of the `with-test-webserver` macro a number of web requests are made and their responses are
examined for correctness. For example:

```clojure
(with-test-webserver app port
  (testing "a gzipped response when requests"
    ;; The client/get function asks for compression by default
    (let [resp (http-client/get (format "http://localhost:%d/" port))]
      (is (= (resp :body) body))
      (is (= (get-in resp [:headers "content-encoding"]) "gzip")
          (format "Expected gzipped response, got this response: %s" resp))))
```
