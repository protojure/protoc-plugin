(ns hello.core-test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as bootstrap]
            [hello.service :as service]
            [hello.test.utils :as test.utils]
            [protojure.pedestal.core :as protojure.pedestal]
            [protojure.internal.grpc.client.providers.http2.jetty :as jetty-client]
            [protojure.grpc.client.providers.http2 :as grpc.http2]
            [protojure.pedestal.routes :as pedestal.routes]
            [clojure.core.async :as async :refer [go >! <!!]]
            [com.example.addressbook :as example]
            [com.example.addressbook.Greeter.client :as greeter]
            [io.pedestal.http :as pedestal]
            [io.pedestal.http.body-params :as body-params]
            [protojure.grpc.client.api :as grpc]
            [promesa.core :as p]))

;;-----------------------------------------------------------------------------
;; Fixtures
;;-----------------------------------------------------------------------------
(defonce test-env (atom {}))

(defn create-service []
      (let [port (test.utils/get-free-port)
            interceptors [(body-params/body-params)
                          pedestal/html-body]
            server-params {:env                      :prod
                           ::pedestal/routes         (into #{} service/grpc-routes)
                           ::pedestal/port           port
                           ::pedestal/type           protojure.pedestal/config
                           ::pedestal/chain-provider protojure.pedestal/provider}]

           (let [server (test.utils/start-pedestal-server server-params)]
                (swap! test-env assoc :port port :server server))))

(defn destroy-service []
      (swap! test-env update :server pedestal/stop))

(defn wrap-service [test-fn]
      (create-service)
      (test-fn)
      (destroy-service))

(use-fixtures :once wrap-service)

(deftest basic-grpc-check
  (testing "Check that a round-trip GRPC request works"
    (let [resp @(greeter/Hello @(grpc.http2/connect {:uri (str "http://localhost:" (:port @test-env))}) {:name "Jane Doe"})]
      (is (= "Hello, Jane Doe" (:message resp))))))
