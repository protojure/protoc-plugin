;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.util
  (:require [slingshot.slingshot :as slingshot])
  (:gen-class))

;; throws an exception that should unwind us all the way to the main
;; function and exit cleanly with an error message rather than a stacktrace, etc
;; NOTE: ":dummy :dummyval" just to make linter happy
(defn abort [retval msg]
  (slingshot/throw+ {:type :protoc-clj-abort :dummy :dummyval :retval retval :msg msg}))
