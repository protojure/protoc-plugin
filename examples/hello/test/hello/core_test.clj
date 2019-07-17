(ns hello.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as bootstrap]
            [hello.service :as service]))

(def service
  (::bootstrap/service-fn (bootstrap/create-servlet service/service)))

(deftest page-test
  (is (.contains
       (:body (response-for service :get "/about"))
       "Clojure 1.9")))

(deftest grpc-test
  (is (.contains
        (:body (response-for service :post "/com.example.addressbook.Greeter/Hello"))
        "Hello, ")))
