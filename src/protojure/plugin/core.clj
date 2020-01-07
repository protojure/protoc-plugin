;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.core
  (:require [protojure.plugin.parse.core :refer [validity-checks generate-impl]]
            [clojure.math.combinatorics :refer [cartesian-product]]
            [protojure.plugin.ast :as ast]))

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
(defn generate [{:keys [file-to-generate proto-file parameter] :as config}]
  (let [protos (ast/new proto-file)
        pkgs (ast/deps-2-pkg proto-file file-to-generate)]
    (validity-checks protos)
    (let [parameters (when parameter (-> parameter (clojure.string/split #",") set))
          server (contains? parameters "grpc-server")
          client (contains? parameters "grpc-client")
          templates (-> ["messages"]
                        (cond-> (true? server) (conj "grpc-server"))
                        (cond-> (true? client) (conj "grpc-client")))
          impls (->> (cartesian-product pkgs templates)
                     (mapcat (partial apply generate-impl protos)))]
      {:file impls})))
