(defproject hello "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :year 2019
            :key "apache-2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [io.pedestal/pedestal.service "0.5.5"]

                 ;; -- PROTOC-GEN-CLOJURE --
                 [protojure "1.0.0"]
                 ;; Include Undertow for supporting HTTP/2 for GRPCs
                 [io.undertow/undertow-core "2.0.17.Final"]
                 [io.undertow/undertow-servlet "2.0.17.Final"]
                 ;; And of course, protobufs
                 [com.google.protobuf/protobuf-java "3.6.1"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "hello.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.5"]]}
             :uberjar {:aot [hello.server]}}
  :main ^{:skip-aot true} hello.server)
