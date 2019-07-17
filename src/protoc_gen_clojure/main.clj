;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protoc-gen-clojure.main
  (:import [com.google.protobuf.compiler
            PluginProtos$CodeGeneratorRequest
            PluginProtos$CodeGeneratorResponse])
  (:require [flatland.protobuf.core :as fl]
            [clojure.java.io :as io]
            [protoc-gen-clojure.core :as core])
  (:gen-class))

(def CodeGeneratorRequest (fl/protodef PluginProtos$CodeGeneratorRequest))
(def CodeGeneratorResponse (fl/protodef PluginProtos$CodeGeneratorResponse))

(defn decode-request [in]
  (fl/protobuf-load-stream CodeGeneratorRequest in))

(defn encode-response [out params]
  (let [response (fl/protobuf CodeGeneratorResponse params)]
    (io/copy (fl/protobuf-dump response) out)))

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

(defn -main
  "plugin entrypoint: request/response protocol runs over stdin/out.

  protoc will send us a protobuf encoded CodeGeneratorRequest over stdin
  and expect a CodeGeneratorResponse on stdout.  We make use of our
  (java-interop based) flatland.protobuf library to manage the serdes
  function for us to bootstrap native clojure protobuf support via this
  tool."
  [& args]
  (when-let [ret (execute (.. System in) (.. System out))]
    (.println System/err (second ret))
    (System/exit (first ret))))
