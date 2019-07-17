;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protoc-gen-clojure.specs.rpc
  "clojure.spec definitions for our AST rpc structure"
  (:require [clojure.spec.alpha :as s]))

;; Common definitions
(s/def ::name string?)
(s/def ::package string?)

;; Rpc.Param/Retval
(s/def ::ns string?)
(s/def ::fname string?)

;; Rpc.Methods.Param
(s/def ::param (s/keys :req-un [::fname]
                       :opt-un [::ns]))

;; Rpc.Methods.Retval
(s/def ::retval (s/keys :req-un [::fname]
                        :opt-un [::ns]))

;; Rpc.Methods.Server-streaming
(s/def ::serverstreaming boolean?)

;; Rpc.Methods.Client-streaming
(s/def ::clientstreaming boolean?)

;; Method
(s/def ::method (s/keys :req-un [::name ::param ::retval ::serverstreaming ::clientstreaming]))
(s/def ::methods (s/coll-of ::method))

;; Rpc
(s/def ::rpc (s/keys :req-un [::name ::package ::methods]))
(s/def ::rpcs (s/coll-of ::rpc))