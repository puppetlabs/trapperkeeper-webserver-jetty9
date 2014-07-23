Sample Trapperkeeper Multiserver Web App
-----------------------------------------

To run the app, use this command:

```sh
lein trampoline run --config examples/multiserver_app/multiserver-example.conf \
                    --bootstrap-config examples/multiserver_app/bootstrap.cfg

```

Open

```
http://localhost:8080/hello

```

in your browser to see the famous Hello World message.

Open

```
http://localhost:9000/hello

```

in your browser to see the same message. Note that this is on a separate server on a different port.

Open

```
http://localhost:9000/goodbye

```

in your browser to see a "Goodbye world" message. Note that this response is NOT displayed at address

```
http://localhost:8080/goodbye

```

due to the fact that it was added specifically to the server on port 9000 and NOT the server on port
8080.