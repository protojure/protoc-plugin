;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.main
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            [protojure.protobuf :refer [->pb]]
            [com.google.protobuf :as protobuf]
            [com.google.protobuf.compiler :as compiler]
            [protojure.plugin.core :as core])
  (:gen-class))

;; We have to manually remove the default ':oneof-index 0" from the generated code.
;; This function helps ensure we do not forget
(defn- validate-protobuf []
  (when (contains? protobuf/FieldDescriptorProto-defaults :oneof-index)
    (throw (ex-info "oneof-index must not have a default specified" {}))))

(validate-protobuf)

(defn decode-request [in]
  (compiler/pb->CodeGeneratorRequest in))

(defn encode-response [out params]
  (-> (compiler/new-CodeGeneratorResponse params)
      (->pb out)))

(defn execute
  "Execute our code generator wrapped in generic request/response serdes"
  [in out]
  (slingshot.slingshot/try+
   (->>
    (decode-request in)
    (core/generate)
    (encode-response out))
   (catch [:type :protoc-clj-abort :dummy :dummyval] {:keys [msg retval]}
     [retval msg])))

(def options
  [["-h" "--help"]
   ["-v" "--version" "Print the version and exit"]])

(defn exit [status msg & rest]
  (do
    (apply println msg rest)
    status))

(defn version [] (str "protoc-gen-clojure version: v" (System/getProperty "protoc-gen-clojure.version")))

(defn prep-usage [msg] (->> msg flatten (string/join \newline)))

(defn usage [options-summary]
  (prep-usage [(version)
               ""
               "Usage: protoc-gen-clojure [options]"
               ""
               "Options:"
               options-summary]))

(defn -app
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args options)]
    (cond

      (:help options)
      (exit 0 (usage summary))

      (not= errors nil)
      (exit -1 "Error: " (string/join errors))

      (:version options)
      (exit 0 (version))

      :else
      (when-let [ret (execute (.. System in) (.. System out))]
        (.println System/err (second ret))
        (System/exit (first ret))))))

(defn -main
  "plugin entrypoint: request/response protocol runs over stdin/out.

  protoc will send us a protobuf encoded CodeGeneratorRequest over stdin
  and expect a CodeGeneratorResponse on stdout."
  [& args]
  (System/exit (apply -app args)))
