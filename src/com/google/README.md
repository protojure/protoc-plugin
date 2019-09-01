# Protocol Buffer Specifications

This directory contains protobuf definitions for the messages sent between protoc and a plugin (such as this one).  

## Provenance
These specifications originate from the official [protobuf repository](https://github.com/protocolbuffers/protobuf/tree/7f520092d9050d96fb4b707ad11a51701af4ce49/src/google/protobuf):

Specifically:
- [plugin.proto](https://github.com/protocolbuffers/protobuf/blob/7f520092d9050d96fb4b707ad11a51701af4ce49/src/google/protobuf/compiler/plugin.proto)
- [descriptor.proto](https://github.com/protocolbuffers/protobuf/blob/7f520092d9050d96fb4b707ad11a51701af4ce49/src/google/protobuf/descriptor.proto)

## Consumption
These files are compiled (by protojure) into native Clojure and the output is placed in the ./src/com/google/protobuf directory.