;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.ast)

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
  (->> (group-by :package proto-files)
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
