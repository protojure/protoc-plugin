(defproject hello "0.0.1-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :year 2019
            :key "apache-2.0"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.pedestal/pedestal.service "0.5.10"]

                 ;; -- PROTOC-GEN-CLOJURE --
                 [io.github.protojure/grpc-server "2.3.1"]

                 [ch.qos.logback/logback-classic "1.2.11" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.36"]
                 [org.slf4j/jcl-over-slf4j "1.7.36"]
                 [org.slf4j/log4j-over-slf4j "1.7.36"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "hello.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.10"]
                                  [io.github.protojure/grpc-client "2.3.1"]]}
             :uberjar {:aot [hello.server]}}
  :main ^{:skip-aot true} hello.server)
