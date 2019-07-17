;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protoc-gen-clojure.ast)

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
   (-> ast :options :java-package)
   (:package ast)))

(defn get-by-package [{:keys [by-package] :as ast} package]
  (get by-package package))

(defn get-packages [{:keys [by-package] :as ast}]
  (keys by-package))

(defn get-definition-by-src [{:keys [by-src]} src]
  (get by-src src))
