;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns user
  (:require [protoc-gen-clojure.main :as main]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]))

;; user is a namespace that the Clojure runtime looks for and loads if
;; its available

;; You can place helper functions in here. This is great for starting
;; and stopping your webserver and other development services

;; The definitions in here will be available if you run "lein repl" or launch a
;; Clojure repl some other way

;; You have to ensure that the libraries you :require are listed in the :dependencies
;; in the project.clj

;; Once you start down this path
;; you will probably want to look at
;; tools.namespace https://github.com/clojure/tools.namespace
;; and Component https://github.com/stuartsierra/component

;; or the exciting newcomer https://github.com/weavejester/integrant

;; DEVELOPMENT SERVER HELPERS: starting and stopping a development server in the REPL

(defn file-stream [path]
  (io/input-stream (io/file path)))

(defn sample-stream []
  (io/input-stream (io/resource "testdata/protoc.request")))

(defn sample-request []
  (let [is (sample-stream)]
    (main/decode-request is)))
