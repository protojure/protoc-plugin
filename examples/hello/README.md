# hello

This is a sample application, written to demonstrate how to use protoc-gen-clojure's
GRPC support for pedestal-based services.

The overall structure of the code/doc is what you get when you run:

```lein new pedestal-service hello```

However, it has been augmented in the following ways:

1. Added resources/addressbook.proto
2. Ran `protoc --clojure_out=grpc-server:src --proto_path=resources addressbook.proto`.
3. Checked in the protoc generated code under ./src/com
4. Modified the src/hello/service.clj to implement the GRPC service
   (see `-- PROTOC-GEN-CLOJURE --` annotations in the code)
5. Added dependencies to project.clj for [undertow](http://undertow.io/)
   and [protobuf](https://developers.google.com/protocol-buffers/).  For example:

```
[io.undertow/undertow-core "2.0.17.Final"]
[io.undertow/undertow-servlet "2.0.17.Final"]
[com.google.protobuf/protobuf-java "3.6.1"]
```

## Getting Started

1. Start the application: `lein run`
2. Go to [localhost:8080](http://localhost:8080/) to see: `Hello from Clojure!`

Once you have the basic service running, you can invoke GRPC requests using any standard GRPC client.

For example, [grpcc](https://github.com/njpatel/grpcc):

```
$ grpcc -p resources/addressbook.proto -i -a localhost:8080

Connecting to com.example.addressbook.Greeter on localhost:8080. Available globals:

  client - the client connection to Greeter
    hello (Person, callback) returns HelloResponse

  printReply - function to easily print a unary call reply (alias: pr)
  streamReply - function to easily print stream call replies (alias: sr)
  createMetadata - convert JS objects into grpc metadata instances (alias: cm)
  printMetadata - function to easily print a unary call's metadata (alias: pm)

Greeter@localhost:8080> client.hello({name: "Greg"}, printReply);
EventEmitter {}
Greeter@localhost:8080> (node:46302) [DEP0079] DeprecationWarning: Custom inspection function on Objects via .inspect() is deprecated

{
  "message": "Hello, Greg"
}
```

N.B. TLS support is not enabled yet, thus the use of "-i"

## Other interesting attractions:

1. HTTP/2 support (try `curl -v --http2 http://localhost:8080`)
2. Standard pedestal route integration such that GRPCs may live along side
   other pedestal routes (e.g. "hello" from /, Prometheus, etc)

## License

This project is licensed under the Apache License 2.0.
