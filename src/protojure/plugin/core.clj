;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.core
  (:require [protojure.plugin.parse.core :refer [validity-checks generate-impl]]
            [clojure.math.combinatorics :refer [cartesian-product]]
            [protojure.plugin.ast :as ast]))

(declare update-dependency-names)
;;-------------------------------------------------------------------
;; Entry point
;;
;; This will cycle through the coll of templates in order to create
;; all the requested files. There are two loops because it is
;; possible to pass more than one proto file to the generator and
;; create more than one file per proto.
;; 
;; The generation of the grpc mappings, and thus the grpc file
;; in addition to the protobuf file, is controlled by the presence
;; of the strings grpc-server,grpc-client in the `protoc --plugin`
;; string.
;;-------------------------------------------------------------------
(defn generate [{:keys [file-to-generate proto-file parameter options] :as config}]
  (let [protos (-> (update-dependency-names proto-file) (ast/new))]
    (validity-checks protos)
    (let [parameters (when parameter (-> parameter (clojure.string/split #",") set))
          server (contains? parameters "grpc-server")
          client (contains? parameters "grpc-client")
          templates (-> ["messages"]
                        (cond-> (true? server) (conj "grpc-server"))
                        (cond-> (true? client) (conj "grpc-client")))
          impls (->> (cartesian-product file-to-generate templates)
                     (mapcat (partial apply generate-impl protos)))]
      {:file impls})))

(defn update-when
  "Updates a value keyed by 'k' in an associative structure 'm' with 'f' when 'k' is not nil"
  [m k f]
  (let [v (get m k)]
    (cond-> m
      (some? v) (assoc k (f v)))))

(defn xform-deps-names-to-deps-packages
  "Find the proto-files map object with name `dep-name`, and return the package key value"
  [proto-files dependencies]
  (map (fn [dep-name]
         (:package (some (fn [p-file]
                           (when (= dep-name (:name p-file))
                             p-file)) proto-files))) dependencies))

(defn update-dependency-names
  "Walks each proto map replacing the dependency entries with their package key value.

 In more detail, walks each proto map in the parsed CodeGeneratorRequest :proto-file key, and if the :dependency
 key is present, transforms the string equivalent to the name :key, to the corresponding :package
 value. E.g. for `[{:name foo.proto :package org.one.foo}, {:name bar.proto :package org.one.bar
 :dependency [foo.proto]]', the output second element becomes `{:name bar.proto, :package
 org.one.bar :dependency [org.one.foo]}`

 Note that in the fn passed to some, proto-file is used to indicate a given element in proto-files,
 whereas in the CodeGeneratorRequest, proto-file is the key given to the list of all parsed protos"
  [proto-files]
  (map #(update-when % :dependency (partial xform-deps-names-to-deps-packages proto-files)) proto-files))
