;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.specs.msg
  "clojure.spec definitions for our AST msg structure"
  (:require [clojure.spec.alpha :as s]))

;; Common definitions
(s/def ::name string?)

;; Msg.Fields
(s/def ::number int?)
(s/def ::label keyword?)
(s/def ::type keyword?)
(s/def ::json-name string?)
(s/def ::ns string?)
(s/def ::fname string?)

;; Field
(s/def ::field (s/keys :req-un [::name ::number ::label ::type ::json-name]
                       :opt-un [::ns ::fname]))
(s/def ::fields (s/coll-of ::field))

;; Msg
(s/def ::msg (s/keys :req-un [::name ::fields]))
(s/def ::msgs (s/coll-of ::msg))
