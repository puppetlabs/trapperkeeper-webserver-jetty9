Sample Trapperkeeper Multiserver Web App
-----------------------------------------

To run the app, use this command:

```sh
lein trampoline run --config examples/webrouting_app/webrouting-example.conf \
                    --bootstrap-config examples/webrouting_app/bootstrap.cfg

```

Open any of

```
http://localhost:8080/hello
http://localhost:8080/bar
http://localhost:8080/goodbye

```

in your browser to see the famous Hello World message.

