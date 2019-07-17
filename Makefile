# Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
#
# SPDX-License-Identifier: Apache-2.0

NAME=protoc-gen-clojure
LEIN = $(shell which lein || echo ./lein)
BINDIR ?= /usr/local/bin
OUTPUT=target/$(NAME)

COVERAGE_THRESHOLD = 86 
COVERAGE_EXCLUSION += "user"
COVERAGE_EXCLUSION += "com.example.*"

SRCS += $(shell find src -type f)
SRCS += $(shell find resources/generators -type f)

PROTOS += $(wildcard resources/testdata/*.proto)

all: scan test bin

testdata: resources/testdata/protoc.request

scan:
	$(LEIN) cljfmt check
	$(LEIN) eastwood '{:debug [:none] :config-files [".eastwood-overrides"]}'

.PHONY: test
test:
	$(LEIN) cloverage --lcov --fail-threshold $(COVERAGE_THRESHOLD) $(patsubst %,-e %, $(COVERAGE_EXCLUSION))

bin: $(OUTPUT)

$(OUTPUT): $(SRCS) Makefile project.clj
	@$(LEIN) bin

$(PREFIX)$(BINDIR):
	mkdir -p $@

install: $(OUTPUT) $(PREFIX)$(BINDIR)
	cp $(OUTPUT) $(PREFIX)$(BINDIR)

%.request: $(PROTOS) Makefile
	REQCAPTURE_OUTPUT=$@ protoc \
        --reqcapture_out=grpc-server,grpc-client:. \
        --plugin=$(CURDIR)/tools/protoc-gen-reqcapture \
		--proto_path=$(CURDIR)/resources/testdata \
		kitchensink.proto addressbook.proto nested/foobar.proto

clean:
	@echo "Cleaning up.."
	@$(LEIN) clean
	-@rm -rf target
	-@rm -f *~
