# Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
#
# SPDX-License-Identifier: Apache-2.0

NAME=protoc-gen-clojure
LEIN = $(shell which lein || echo $(CURDIR)/lein)
BINDIR ?= /usr/local/bin
OUTPUT=target/$(NAME)

COVERAGE_THRESHOLD = 83
COVERAGE_EXCLUSION += "user"
COVERAGE_EXCLUSION += "com.example.*"
COVERAGE_EXCLUSION += "com.google.protobuf.*"

SRCS += $(shell find src -type f)
SRCS += $(shell find resources/generators -type f)

PROTOS += $(wildcard resources/testdata/*.proto)

all: scan test test-example bin

testdata: resources/testdata/protoc.request resources/testdata/toaster.protoc.request

scan:
	$(LEIN) cljfmt check

.PHONY: test

test-example:
	cd examples/hello && $(LEIN) test

test:
	$(LEIN) cloverage --lcov --fail-threshold $(COVERAGE_THRESHOLD) $(patsubst %,-e %, $(COVERAGE_EXCLUSION))

bin: $(OUTPUT)

$(OUTPUT): $(SRCS) Makefile project.clj
	@$(LEIN) bin

$(PREFIX)$(BINDIR):
	mkdir -p $@

install: $(OUTPUT) $(PREFIX)$(BINDIR)
	cp $(OUTPUT) $(PREFIX)$(BINDIR)

resources/testdata/protoc.request: $(PROTOS) Makefile
	REQCAPTURE_OUTPUT=$@ protoc \
        --reqcapture_out=grpc-server,grpc-client:. \
        --plugin=$(CURDIR)/tools/protoc-gen-reqcapture \
				--proto_path=$(CURDIR)/resources/testdata \
				kitchensink.proto addressbook.proto address-service.proto nested/foobar.proto complex-package.proto \
				enum-proto.proto enum-proto2.proto enum-proto3.proto

resources/testdata/toaster.protoc.request: $(PROTOS) Makefile
	REQCAPTURE_OUTPUT=$@ protoc \
        --reqcapture_out=grpc-server,grpc-client:. \
        --plugin=$(CURDIR)/tools/protoc-gen-reqcapture \
				--proto_path=$(CURDIR)/resources/testdata \
				toaster.proto

clean:
	@echo "Cleaning up.."
	@$(LEIN) clean
	-@rm -rf target
	-@rm -f *~
