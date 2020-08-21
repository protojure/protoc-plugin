(defproject protoc-gen-clojure "0.9.1-SNAPSHOT"
  :description "Protobuf protoc compiler plugin to generate native Clojure support for Google Protocol Buffers and GRPC"
  :url "http://github.com/protojure/protoc-plugin"
  :license {:name "Apache License 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"
            :year 2019
            :key "apache-2.0"}
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :jvm-opts ["-server"]
  :java-source-paths ["src"]
  :plugins [[lein-bin "0.3.5"]
            [lein-cloverage "1.0.11" :exclusions [org.clojure/clojure]]
            [lein-cljfmt "0.5.7" :exclusions [org.clojure/clojure]]]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [protojure "1.2.0"]
                 [protojure/google.protobuf "0.9.1"]
                 [com.google.protobuf/protobuf-java "3.11.1"]
                 [org.antlr/ST4 "4.2"]
                 [slingshot "0.12.2"]
                 [camel-snake-kebab "0.4.1"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [org.clojure/tools.cli "0.4.2"]]
  :main ^:skip-aot protojure.plugin.main
  :bin {:name "protoc-gen-clojure"
        :bin-path "target"
        :bootclasspath false}
  :target-path "target/%s"
  :pedantic? :warn

  ;; nREPL by default starts in the :main namespace, we want to start in `user`
  ;; because that's where our development helper functions like (refresh) live.
  :repl-options {:init-ns user}

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.3.1"]
                                  [io.pedestal/pedestal.service "0.5.7" :exclusions  [org.clojure/tools.reader]]
                                  [me.raynes/fs "1.4.6" :exclusions [org.apache.commons/commons-compress]]]
                   :source-paths ["target/test"]}
             :uberjar {:aot :all}})
