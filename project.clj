(defproject protoc-gen-clojure "2.1.1-SNAPSHOT"
  :description "Protobuf protoc compiler plugin to generate native Clojure support for Google Protocol Buffers and GRPC"
  :url "http://github.com/protojure/protoc-plugin"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :year 2022
            :key "apache-2.0"}
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :jvm-opts ["-server"]
  :java-source-paths ["src"]
  :plugins [[lein-bin "0.3.5"]
            [lein-cloverage "1.2.2" :exclusions [org.clojure/clojure]]
            [lein-cljfmt "0.8.0" :exclusions [org.clojure/clojure]]]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [io.github.protojure/core "2.0.9"]
                 [io.github.protojure/grpc-client "2.0.9"]
                 [io.github.protojure/google.protobuf "2.0.0"]
                 [org.antlr/ST4 "4.3.1"]
                 [slingshot "0.12.2"]
                 [camel-snake-kebab "0.4.2"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [org.clojure/tools.cli "1.0.206"]]
  :main ^:skip-aot protojure.plugin.main
  :bin {:name "protoc-gen-clojure"
        :bin-path "target"
        :bootclasspath false}
  :target-path "target/%s"
  :pedantic? :warn

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (refresh) live.
  :repl-options {:init-ns user}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "1.2.0"]
                                  [io.pedestal/pedestal.service "0.5.10" :exclusions  [org.clojure/tools.reader]]
                                  [me.raynes/fs "1.4.6" :exclusions [org.apache.commons/commons-compress]]]
                   :source-paths ["target/test"]}
             :uberjar {:aot :all}})
