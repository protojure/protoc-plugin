;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.ast)

(defn update-when
  "Updates a value keyed by 'k' in an associative structure 'm' with 'f' when 'k' is not nil"
  [m k f]
  (let [v (get m k)]
    (cond-> m
      (some? v) (assoc k (f v)))))

(defn find-src
  [proto-files src]
  (some (fn [desc]
          (when (= src (:name desc))
            desc)) proto-files))

(defn src-2-pkg
  [proto-files src]
  (:package (find-src proto-files src)))

(defn deps-2-pkg
  "Find the proto-files map object with name `dep-name`, and return the package key value"
  [proto-files dependencies]
  (map (partial src-2-pkg proto-files) dependencies))

(defn update-deps
  "Walks each proto map replacing the dependency entries with their package key value.

 In more detail, walks each proto map in the parsed CodeGeneratorRequest :proto-file key, and if the :dependency
 key is present, transforms the string equivalent to the name :key, to the corresponding :package
 value. E.g. for `[{:name foo.proto :package org.one.foo}, {:name bar.proto :package org.one.bar
 :dependency [foo.proto]]', the output second element becomes `{:name bar.proto, :package
 org.one.bar :dependency [org.one.foo]}`

 Note that in the fn passed to some, proto-file is used to indicate a given element in proto-files,
 whereas in the CodeGeneratorRequest, proto-file is the key given to the list of all parsed protos"
  [proto-files]
  (map #(update-when % :dependency (partial deps-2-pkg proto-files)) proto-files))

(defn- concat-desc
  "partially concatenates two descriptors together"
  [acc {:keys [message-type enum-type dependency service]}]
  (-> acc
      (update :message-type concat message-type)
      (update :enum-type concat enum-type)
      (update :dependency concat dependency)
      (update :service concat service)))

(defn- merge-desc
  "(group-by) creates lists under each key, and we want to merge our packages back
  together again.  We take the first descriptor as authoritative w.r.t. fields such
  as 'options', and then partially merge select fields from subordinate descriptors
  to form one namespace"
  [desc]
  (reduce concat-desc (first desc) (rest desc)))

(defn new [proto-files]
  (->> (update-deps proto-files)
       (group-by :package)
       (reduce (fn [acc [pkg desc]] (assoc acc pkg (merge-desc desc))) {})))

(defn get-namespace [ast]
  (or
    ;; TODO .proto options are controlled fields that must be declared in descriptor.proto
    ;; See https://developers.google.com/protocol-buffers/docs/proto#customoptions
    ;; The below will not work, and the appropriate approach here is probably to PR to upstream to include the
    ;; top level clojure-namespace option in descriptor.proto
    ;; (-> ast :options :clojure-namespace)
   (-> ast :options :java-package)
   (:package ast)))

(defn get-package [ast package]
  (get ast package))

(defn list-packages [ast]
  (keys ast))

