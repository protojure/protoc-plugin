;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.ast)

(defn- key-by [pred proto-file]
  (->> proto-file
       (map #(vector (pred %) %))
       (into {})))

(defn new [proto-files]
  (let [by-package (key-by :package proto-files)
        by-src (key-by :name proto-files)]
    {:by-package by-package
     :by-src by-src
     :inc-fmt proto-files}))

(defn get-fqpackage [ast]
  (or
    ;; TODO .proto options are controlled fields that must be declared in descriptor.proto
    ;; See https://developers.google.com/protocol-buffers/docs/proto#customoptions
    ;; The below will not work, and the appropriate approach here is probably to PR to upstream to include the
    ;; top level clojure-namespace option in descriptor.proto
    ;; (-> ast :options :clojure-namespace)
   (-> ast :options :java-package)
   (:package ast)))

(defn get-by-package [{:keys [by-package] :as ast} package]
  (get by-package package))

(defn get-packages [{:keys [by-package] :as ast}]
  (keys by-package))

(defn get-definition-by-src [{:keys [by-src]} src]
  (get by-src src))
