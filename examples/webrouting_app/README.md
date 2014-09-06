Sample Trapperkeeper Multiserver Web App
-----------------------------------------

To run the app, use this command:

```sh
lein trampoline run --config examples/webrouting_app/webrouting-example.conf \
                    --bootstrap-config examples/webrouting_app/bootstrap.cfg

```

Open any of

```
http://localhost:8080/foo
http://localhost:8080/bar
http://localhost:8080/baz
http://localhost:8080/goodbye
http://localhost:9000/quux
http://localhost:9000/bert

```

in your browser to see the famous Hello World message.

Open

```
http://localhost:8080/hello/[string]

```
where [string] is any string of your choosing to see a Hello message specific for
that string.
