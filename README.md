# Protojure protoc compiler plugin [![CircleCI](https://circleci.com/gh/protojure/protoc-plugin/tree/master.svg?style=svg)](https://circleci.com/gh/protojure/protoc-plugin/tree/master)

[Protoc compiler plugin](https://developers.google.com/protocol-buffers/docs/reference/other) to generate native [Clojure](https://clojure.org/) support for [Google Protocol Buffers](https://developers.google.com/protocol-buffers/) and [GRPC](https://grpc.io/).

## Installation

Ultimately, you need to have the binary 'protoc-gen-clojure' available on your $PATH so that _protoc_ may find it during execution.

One way to do that is to use:
```make install```

## Usage

```protoc --clojure_out=src ...```

## License

This project is licensed under the Apache License 2.0.

## Contributing

Pull requests welcome.  Please be sure to include a [DCO](https://en.wikipedia.org/wiki/Developer_Certificate_of_Origin) in any commit messages.