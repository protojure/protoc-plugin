;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.data :as data]
            [clojure.pprint :refer :all]
            [clojure.tools.namespace :as namespace]
            [me.raynes.fs :as fs]
            [protojure.protobuf :refer [->pb]]
            [protojure.plugin.main :as main]
            [protojure.plugin.core :refer :all]
            [protojure.plugin.parse.core :refer :all]
            [clojure.spec.test.alpha :as stest]
            [clojure.string :as string])
  (:import [java.io ByteArrayOutputStream]))

(stest/instrument)

(defn- generate-sample [output-dir protoc-request-file]
  (let [{:keys [file]} (->> (io/resource protoc-request-file)
                            io/input-stream
                            main/decode-request
                            (generate))]

    ;; Write the file(s) to our temp-dir
    (doseq [{:keys [name content]} file]
      (let [h (io/file output-dir name)
            parent (.getParent h)]
        (fs/mkdirs parent)
        (spit h content)))))

(defn- generate-and-load-samples []
  (let [output-dir (io/file "target/test")]
    ;; Ensure we start fresh
    (fs/delete-dir output-dir)
    (generate-sample output-dir "testdata/protoc.request")
    (generate-sample output-dir "testdata/toaster.protoc.request"))

  ;; JIT-require our 'com.example' namespaces now that they are available
  (require '[com.example.addressbook :as addressbook] :reload
           '[com.example.kitchensink :as example] :reload
           '[com.example.kitchensink.Greeter.server :as example-server] :reload
           '[com.example.kitchen.toaster.Toaster.client :as toaster-client] :reload
           '[com.example.kitchen.toaster.Toaster.server :as toaster-server] :reload
           '[com.example.enum_proto :as enum-proto] :reload
           '[com.example.enum_proto2 :as enum-proto2] :reload
           '[com.example.enum_proto3 :as enum-proto3] :reload
           '[grpc.gateway.protoc_gen_openapiv2.options :as openapi] :reload))

(generate-and-load-samples)

(deftest cli-test
  (testing "Ensure our CLI parsing works as advertised"
    (is (= (main/-app "-h") 0))
    (is (= (main/-app "-v") 0))
    (is (= (main/-app "--foo") -1))))

(deftest io-test
  (testing "Drive a sample request through plugin IO and ensure we get a 0 exit value"
    (let [in (io/input-stream (io/resource "testdata/protoc.request"))
          out (ByteArrayOutputStream.)]
      (is (nil? (main/execute in out))))))

(defn- pbverify
  "End to end serdes testing for a specific message"
  [newf pb->f data]
  (let [msg-tx (newf data)
        msg-rx (-> msg-tx
                   ->pb
                   pb->f)
        [diff-tx diff-rx _] (data/diff msg-tx msg-rx)]
    (assert (and (nil? diff-tx) (nil? diff-rx)))))

(deftest proto3-enum-defaults
  (testing "round-trip"
    (pbverify enum-proto/new-M enum-proto/pb->M {})
    (pbverify enum-proto2/new-M enum-proto2/pb->M {})
    (pbverify enum-proto3/new-M enum-proto3/pb->M {}))
  (testing "defaults by language"
    (is (= :c (-> {} enum-proto/new-M :e)))
    (is (= :b (-> {} enum-proto2/new-M :e)))
    (is (= :a (-> {} enum-proto3/new-M :e)))))

(deftest oneof-test
  (testing "oneof support"
    (pbverify example/new-OneOf
              example/pb->OneOf
              {:firsts "hello"
               ;--oneof with a nested field with a string in it
               :One {:ss {:s "and ss"}}
               :num 42
               ;--oneof with an enum
               :AndAnother-One {:aae :e1}
               :seconds {:s "secondstr"}
               :eo {:s "eos"
                    ;-- oneof with a complex nested structure with another oneof
                    :EOneOf {:atm {:s "hello"
                                   :i 42
                                   :mSimple {"k1" 42 "k2" 43}
                                   :mComplex {"k1" {:s "v1"} "k2" {:s "v2"}}
                                   :sSimple {:s "simple"}
                             ;--note the oneof within oneof
                                   :oe :e2}}}
               :e :e1
               ;--oneof with bytes
               :FinalOne {:fb (byte-array (mapv byte "Clojure!"))}})))

;;-- this the above test with all oneof fields removed. The value of these fields is nil (appears to be similar
;;-- to the way messages are handled)
(deftest oneof-test-all-unset
  (testing "Remove One, AndAnotheOne, EOneOf and FinalOne. These will not be set to default values"
    (pbverify example/new-OneOf
              example/pb->OneOf
              {:firsts "hello"
               :num 42
               :seconds {:s "secondstr"}
               :eo {:s "eos"}
               :e :e1})))

(deftest e2e-test
  (testing "End-to-end testing for AllThings type"
    (pbverify example/new-AllThings
              example/pb->AllThings
              {:person {:name "Test User"
                        :id 42
                        :email "tester@acme.com"
                        :phones [{:type :home :number "(123) 456-7890"}]}
               :snake-case "Snake to Kebab!"})))

(deftest default-enum-test
  (testing "Check to ensure that default enums are set"
    (let [msg (-> (addressbook/new-Person {:name "Test User" :phones [{:number "(123) 456-7890"}]})
                  ->pb
                  addressbook/pb->Person)
          type (-> msg :phones first :type)]
      (is (= type :mobile)))))

(deftest java-package-test
  (testing "Check to ensure that code generated with java_package uses the correct service name"
    (is (= "kitchen.toaster.Toaster" toaster-client/Toaster-service-name))
    (is (= "kitchen.toaster.Toaster" toaster-server/Toaster-service-name))))

(deftest allthingsmap-test
  (testing "Test all things map"
    (pbverify example/new-AllThingsMap
              example/pb->AllThingsMap
              {:s "hello"
               :i 42
               :mSimple {"k1" 42 "k2" 43}
               :mComplex {"k1" {:s "v1"} "k2" {:s "v2"}}
               :sSimple {:s "simple"}
               :r [1 2 3 4]})))

(deftest repeated-enum-test
  (testing "Test repeated-enum map"
    (pbverify example/new-AllThingsMap
              example/pb->AllThingsMap
              {:roe [:e1 :e2]})))

(deftest nestedmaps-test
  (testing "repeated complex objects with maps"
    (pbverify example/new-NestedMap
              example/pb->NestedMap
              {:s {:s "somestring"}
               :i64 64
               :ams
               [{:s "hello"
                 :i 42
                 :mSimple {"k1" 42}
                 :mComplex {"k1" {:s "v1"}}}]})))

(deftest badmsg-test
  (testing "Create a bad message and verify that it is rejected"
    (is (thrown? clojure.lang.ExceptionInfo
                 (->pb
                  (addressbook/new-Person
                   {:name "Bad User" :id "Bad ID"}))))))

(deftest scalar-test
  (testing "Test the scalar decoding logic"
    (is (= (-> :test-uint32 decode-integer-field :type) "UInt32"))
    (is (= (-> :test-sfixed32 decode-integer-field :type) "SFixed32"))))

(def requires-msg
  [{:name "Foo" :fields
    [{:name "field1"
      :number 1
      :label :label-optional
      :type :type-message
      :json-name "field1"
      :ns "foo"}
     {:name "field2"
      :number 2
      :label :label-optional
      :type :type-message
      :json-name "field2"
      :ns "foo"}]}
   {:name "Bar" :fields
    [{:name "field1"
      :number 1
      :label :label-optional
      :type :type-message
      :json-name "field1"
      :ns "bar"}]}])

(def requires-rpc
  (list {:name "Bat", :package "com.bat", :methods
         [{:name   "rpc1"
           :param  {:fname "fname-in", :ns "bat"}
           :retval {:fname "fname-out", :ns "notbat"}
           :clientstreaming false
           :serverstreaming false}
          {:name   "rpc2"
           :param  {:fname "other-in"}
           :retval {:fname "other-out", :ns "com.baz"}
           :clientstreaming false
           :serverstreaming false}]}))

(defn- test-repeated [data]
  (let [result (-> (mapv byte data)
                   (byte-array)
                   (example/pb->SimpleRepeated)
                   (:data))]
    (is (= (count result) 3))))

;; Represent a 'repeated int32' wire representation of [1 2 3] in both
;; packed and unpacked format.  For more details, see:
;; https://developers.google.com/protocol-buffers/docs/encoding#packed
(deftest packed-repeated-test
  (testing "Testing repeated field decoding of packed structures"
    (test-repeated [0xA 3 1 2 3])))

(deftest unpacked-repeated-test
  (testing "Testing repeated field decoding of unpacked structures"
    (test-repeated [0x8 1 0x8 2 0x8 3])))

(deftest int-enum-test
  (testing "Ensure integers may be used in place of keywords for enum values"
    (let [msg (-> (addressbook/new-Person {:name "Test User" :phones [{:number "(123) 456-7890" :type 2}]})
                  ->pb
                  addressbook/pb->Person)]
      (is (-> msg :phones first :type (= :work))))))

(deftest int-passthrough-test
  (testing "A future evolution of a schema may include enumerations that we are not aware of.  Allow them to pass through"
    (let [msg (-> (addressbook/new-Person {:name "Test User" :phones [{:number "(123) 456-7890" :type 5}]})
                  ->pb
                  addressbook/pb->Person)]
      (is (-> msg :phones first :type (= 5))))))

(deftest bad-enum-test
  (testing "Check whether a bogus label is correctly rejected"
    (is (thrown? java.lang.AssertionError (->pb (addressbook/new-Person
                                                 {:name "Test User"
                                                  :phones [{:number "(123) 456-7890"
                                                            :type :ROTARY}]}))))))

(deftest grpc-service-fact-check
  (testing "Check that grpc service rpc-metadata appear accurate")
  (is (= (map :method example-server/rpc-metadata)
         ["Hello" "sayRepeatHello" "sayHelloToMany" "sayHelloToEveryone"]))
  ;; Note that the expected pkg is com.example.kitchensink, which in resources/interface.stg is generic_namespace *not*
  ;; namespace. See ->tablesyntax in protojure-lib.protojure.pedestal.routes for usage.
  (is (= (map :pkg example-server/rpc-metadata)
         (repeat 4 "com.example.kitchensink"))))

(deftest merged-packages
  (testing "Test to ensure our two addressbook packages are truly merged into one namespace")
  (let [primary (addressbook/new-Person {:name "Foo"})
        secondary (addressbook/new-GreeterReply {:message "Hello, Foo"})]
    (is (and (some? primary) (some? secondary)))))

(deftest snakecase-pkg-test
  (testing "Test that packages with snakecase are handled properly"
    (let [msg (-> (openapi/new-Foo {:bar "baz"})
                  ->pb
                  openapi/pb->Foo)]
      (is (-> msg :bar (= "baz"))))))
