;; Copyright © 2019 State Street Bank and Trust Company.  All rights reserved
;;
;; SPDX-License-Identifier: Apache-2.0

(ns protojure.plugin.parse.core
  "Functions that transform the protoc CodeGeneratorRequest to STG template format"
  (:require [clojure.pprint :refer :all]
            [clojure.string :as string]
            [protojure.plugin.ast :as ast]
            [protojure.plugin.parse.oneof :as oneof]
            [protojure.plugin.util :as util]
            [camel-snake-kebab.core :refer :all])
  (:import (java.util ArrayList)
           (org.stringtemplate.v4 STGroupFile)))

;;-------------------------------------------------------------------
;; The following deftypes at the top of this ns represent the data
;; shape expected by the STG when rendering the protobuff and/or
;; grpc clojure source file.
;;
;; After the deftypes, various helpers follow. These functions parse
;; and process the request sent by protoc mapping the data in the
;; [CodeGeneratorRequest](https://github.com/protocolbuffers/protobuf/blob/master/src/google/protobuf/compiler/plugin.proto#L68). The types are
;; marshalled by calls within the generate-impl-content function,
;; and the data is then passed to the render-template function.
;;
;; Additions or modifications of content to the final rendered
;; template should be made by altering the types defined below, and
;; their handling by the helpers contained here and the templates in
;; resources/generators/interface.stg
;;
;; N.B. The structure of the protoc CodeGeneratorRequest above can
;; be interogatted in its raw form by capturing the output of
;; `(protojure.plugin.main/decode-request
;;    (io/input-stream <CodeGeneratorRequest bytes>))`
;;
;;-------------------------------------------------------------------


;;-------------------------------------------------------------------
;; Define an EnumDescriptor type, which is passed to the STG in order
;; to render .proto [Enums](https://developers.google.com/protocol-buffers/docs/proto3#enum)
;;-------------------------------------------------------------------
;; Definition of a tag & label combination for a given enum member.
;;
;; Illustration (deftype does not create a clojure map, but rather a java class):
;;    `{:tag "0", :label ":E1"}`
;;
;;-------------------------------------------------------------------
(deftype EnumDescriptor [^String tag ^String label])

;;-------------------------------------------------------------------
;; Define a ValueType type, which is passed to the STG in order
;; to properly render .proto primitive or Message type [fields](https://developers.google.com/protocol-buffers/docs/proto3#simple)
;; in clj source
;;-------------------------------------------------------------------
;; Definition of metadata needed for interpretation and handling of a given .proto message field, e.g.:
;;
;;  `{:embedded nil,
;;   :repeated false,
;;   :builtin true,
;;   :packable true,
;;   :ns nil,
;;   :type "Double",
;;   :default "0.0",
;;   :spec "float?"}`
;;
;;-------------------------------------------------------------------
(deftype ValueType [^Boolean embedded ^Boolean repeated ^Boolean builtin ^Boolean packable ^String ns ^String type ^String default ^String spec])

;;-------------------------------------------------------------------
;; Define a Field type, which is passed to the STG to render .proto
;; [primitive or Message type fields](https://developers.google.com/protocol-buffers/docs/proto3#simple)
;; in clj source
;;-------------------------------------------------------------------
;; Definition of metadata fields used by this protoc plugin during STG
;; template population
;;
;;  `{:tag "1",
;;   :name "one",
;;   :type
;;   #object[protoc_gen_clojure.core.ValueType 0x1c61eda5 "protoc_gen_clojure.core.ValueType@1c61eda5"],
;;   :isnested false,
;;   :ismap nil,
;;   :oindex nil,
;;   :ofields {}}`
;;
;;-------------------------------------------------------------------
(deftype Field [^String tag ^String name ^ValueType type ^Boolean isnested ^Boolean ismap ^Integer oindex ^ArrayList ofields])

;;-------------------------------------------------------------------
;; Define a wrapper type for Field above, providing the top-level name
;; and metadata that determines typesetting in the STG template.
;;-------------------------------------------------------------------
;; E.g.:
;;
;;  {:name "AddressBook",
;;   :items
;;   {"people"
;;    #object[protoc_gen_clojure.core.FieldInfo ...]},
;;   :nested
;;   {"people"
;;    #object[protoc_gen_clojure.core.FieldInfo ...]},
;;   :ismap nil,
;;   :ofields {}}`
;;
;;-------------------------------------------------------------------
(deftype Descriptor [^String name ^ArrayList items ^ArrayList nested ^Boolean ismap ^ArrayList ofields])

;;-------------------------------------------------------------------
;; Define a Service type, which is passed to the STG to render .proto
;; [Services](https://developers.google.com/protocol-buffers/docs/proto3#services)
;; in clj source
;;-------------------------------------------------------------------
;;  {:name "Greeter",
;;   :package "com.example.kitchensink",
;;   :methods
;;   {"Hello"
;;    #object[protoc_gen_clojure.core.Method ...],
;;    "sayRepeatHello"
;;    #object[protoc_gen_clojure.core.Method ...],
;;    "sayHelloToMany"
;;    #object[protoc_gen_clojure.core.Method ...],
;;    "sayHelloToEveryone"
;;    #object[protoc_gen_clojure.core.Method ...]}}
(deftype Service [^String name ^String package ^ArrayList methods])

;;-------------------------------------------------------------------
;; Define a Method type, which is passed to the STG to render .proto
;; [methods](https://grpc.io/docs/guides/concepts/#service-definition)
;; in clj source. Also see the Service comment link above.
;;-------------------------------------------------------------------
;;
;;  {:name "Hello",
;;   :param
;;       #object[protoc_gen_clojure.core.ValueType ...],
;;   :retval
;;         #object[protoc_gen_clojure.core.ValueType ...],
;;   :clientstreaming false,
;;   :serverstreaming false}

(deftype Method [^String name ^ValueType param ^ValueType retval ^Boolean clientstreaming ^Boolean serverstreaming])

;;-------------------------------------------------------------------
;; Generate our namespace based on the type and file string
;;-------------------------------------------------------------------
(defn- generate-impl-ns [protos pkg impl-str]
  (-> (ast/get-package protos pkg)
      ast/get-namespace
      (str (when impl-str ".") impl-str)))

;;-------------------------------------------------------------------
;; convert something like "foo.bar.baz" -> "foo/bar/baz.clj"
;;-------------------------------------------------------------------
(defn- package-to-filename [pkg]
  (-> (string/replace pkg #"\." "/")
      (str ".cljc")))

;;-------------------------------------------------------------------
;; Generate an output filename for a given input .proto file
;;-------------------------------------------------------------------
(defn- generate-impl-name [protos pkg template impl-str]
  (let [base (generate-impl-ns protos pkg impl-str)]
    (package-to-filename (case template
                           "grpc-client" (str base ".client")
                           "grpc-server" (str base ".server")
                           base))))

;;-------------------------------------------------------------------
;; Initialize a new Descriptor structure
;;-------------------------------------------------------------------
(defn- new-descriptor [name statements nested ismap ofields]
  (vector name (->Descriptor name statements nested ismap ofields)))

;;-------------------------------------------------------------------
;; generic builder pattern shared with all types
;;-------------------------------------------------------------------
(defn- builder [f coll]
  (->> coll
       (map f)
       (into {})))

;;-------------------------------------------------------------------
;; A version of concat form that removes nil/empty slop
;;-------------------------------------------------------------------
(defn- concat-clean [& colls]
  (->> (apply concat colls)
       (remove nil?)
       (remove empty?)))

;;-------------------------------------------------------------------
;; generate a hierarchically accurate context name
;;-------------------------------------------------------------------
(defn- context-name [parent local]
  (str parent local "-"))

;;-------------------------------------------------------------------
;; Create a new instance of ->EnumDescriptor
;;-------------------------------------------------------------------
(defn- new-enum-descriptor [{:keys [tag label]}]
  (vector tag (->EnumDescriptor tag label)))

;;-------------------------------------------------------------------
;; for the input "TYPE_FOO" returns ":type-foo", suitable for
;;  enum label fields
;;-------------------------------------------------------------------
(defn- enum2keyword [name]
  (->> name
       (util/clojurify-name)
       (string/lower-case)
       (str ":")))

;;-------------------------------------------------------------------
;; Build an enumeration structure
;;-------------------------------------------------------------------
;;
;; For a given enum structure as input, create a tag/value descriptor
;;
;; Input shape:
;;        {:name "Person-PhoneType",
;;         :value
;;         [{:name "MOBILE", :number 0}
;;          {:name "HOME", :number 1}
;;          {:name "WORK", :number 2}]}
;;
;;-------------------------------------------------------------------
(defn- build-enum [{:keys [name value] :as enum}]
  (let [statements (->> value
                        (map (fn [{:keys [name number]}]
                               (new-enum-descriptor {:tag (str number) :label (enum2keyword name)})))
                        (into {}))]
    (new-descriptor name statements nil false nil)))

;;-------------------------------------------------------------------
;; build-enums
;;-------------------------------------------------------------------
(defn- build-enums [enums]
  (builder build-enum enums))

;;-------------------------------------------------------------------
;; Traverse the AST and collect all enum entries, correcting for context
;;-------------------------------------------------------------------
(defn- traverse-nested-enums
  ([msg]
   (traverse-nested-enums "" msg))
  ([parent-context msg]
   (let [name (:name msg)
         context (context-name parent-context name)
         local (->> (:enum-type msg)
                    (mapv (fn [x] (update x :name #(str context %)))))
         nested (->> msg
                     :nested-type
                     (mapv #(traverse-nested-enums context %))
                     (reduce concat))]
     (concat-clean local nested))))

;;-------------------------------------------------------------------
;; Generate all of our enums
;;-------------------------------------------------------------------
(defn- generate-enums [desc]
  (->> (:message-type desc)
       (map traverse-nested-enums)
       (apply concat-clean (:enum-type desc))
       build-enums))

;;-------------------------------------------------------------------
;; converts a keyword like :type-string -> "String" by stripping off
;; the ":type-" and converting to pascalcase.  We need to special case
;; things like SInt32 because PascalCase would normally output
;; "Sint32"
;;-------------------------------------------------------------------
(defn decode-integer-field [type]
  (let [t (-> type
              name
              (subs 5)
              ->PascalCase
              (string/replace #"int" "Int")
              (string/replace #"fixed" "Fixed"))]
    {:type t :builtin true :packable true :default "0" :spec "int?"}))

;;-------------------------------------------------------------------
;; Generates a function that evaluates to the default (i.e. tag=0)
;; enum value
;;-------------------------------------------------------------------
(defn- enum-default [ns name]
  (str "(" (when (some? ns) (str ns "/")) name "-val2label 0)"))

;;-------------------------------------------------------------------
;; Decode a type like 'type-string' to a map for initializing
;; a Descriptor
;;-------------------------------------------------------------------
(defn- decode-field-type [protos {:keys [type ns label fname] :as field}]
  (-> (case type
        :type-message {:ns ns :type fname :embedded true}
        :type-enum {:ns ns :type fname :packable true
                    :default (enum-default ns fname) :spec "(s/or :keyword keyword? :int int?)"}
        :type-string {:type "String" :builtin true :default "\"\"" :spec "string?"}
        :type-bytes {:type "Bytes" :builtin true :default "(byte-array 0)" :spec "bytes?"}
        :type-bool {:type "Bool" :builtin true :packable true :default "false" :spec "boolean?"}
        :type-float {:type "Float" :builtin true :packable true :default "0.0" :spec "float?"}
        :type-double {:type "Double" :builtin true :packable true :default "0.0" :spec "float?"}
        :type-oneof {:ns ns :type fname}
        (decode-integer-field type))
      (assoc :repeated (= label :label-repeated))))

;;-------------------------------------------------------------------
;; Is the field a nested field ?
;;-------------------------------------------------------------------
(defn- nested? [f]
  (and (= (:type f) :type-message) (not (:ismap f))))

;;-------------------------------------------------------------------
;; Create a new instance of ->Field
;;-------------------------------------------------------------------
(defn- new-field [{:keys [tag name isnested ismap oindex ofields] {:keys [embedded repeated builtin packable type ns default spec]} :type}]
  (let [cljname (util/clojurify-name name)
        t (->ValueType embedded repeated builtin packable ns type default spec)]
    (vector cljname (->Field tag cljname t isnested ismap oindex ofields))))

;;-------------------------------------------------------------------
;; Builds a statement for a msg::field
;;-------------------------------------------------------------------
;; 10 [:name (.readStringRequireUtf8 is)]
;; 16 [:id (.readInt32 is)]
;; 26 [:email (.readStringRequireUtf8 is)]
;; 34 [:phones (partial cons (parse-embedded -parse-Person-PhoneNumber is))]
;;-------------------------------------------------------------------
(defn- build-msg-field [protos oneofdecl {:keys [name number type type-name ismap ofields] :as field}]
  (let [isnested (nested? field)
        ofs (->> ofields
                 (map (fn [f] (new-field {:tag (:number f)
                                          :name (:name f)
                                          :isnested (nested? f)
                                          :ismap (:ismap f)
                                          :oindex (oneof/get-index oneofdecl f)
                                          :ofields nil
                                          :type (decode-field-type protos f)})))
                 (into {}))
        type (decode-field-type protos field)]
    (new-field {:tag (str number)
                :isnested isnested
                :name name
                :ismap ismap
                :oindex (oneof/get-index oneofdecl field)
                :ofields ofs
                :type type})))

;;-------------------------------------------------------------------
;; Build a message structure
;;-------------------------------------------------------------------
;;
;; Input shape:
;;        {:name "Person-PhoneNumber",
;;         :fields
;;         [{:name "number",
;;           :number 1,
;;           :label :label-optional,
;;           :type :type-string,
;;           :json-name "number"}
;;          {:name "type",
;;           :number 2,
;;           :label :label-optional,
;;           :type :type-enum,
;;           :type-name ".tutorial.Person.PhoneType",
;;           :json-name "type"}]}
;;-------------------------------------------------------------------
(defn- build-msg [protos {:keys [name fields oneofdecl] :as msg}]
  (let [builder (partial build-msg-field protos oneofdecl)
        statements (->> fields
                        ;;-- don't include the original oneof fields which are now in a container as well
                        (filter (fn [f] (or (not (oneof/valid? oneofdecl f)) (some? (:ofields f)))))
                        (map builder)
                        (into {}))
        ofields (->> fields
                     ;;-- collect only parent oneof container fields
                     (filter (fn [f] (some? (:ofields f))))
                     (map builder)
                     (into {}))
        ismap (:ismap msg)
        nested? (fn [x] (and (= (:type x) :type-message) (not (:ismap x))))
        nested (->> fields
                    (filter nested?)
                    (map builder)
                    (into {}))]
    (new-descriptor name statements nested ismap ofields)))

;;-------------------------------------------------------------------
;; build-msgs
;; ------------------------------------------------------------------
;; Note that we compose build-msg, followed by a second pass of
;; oneof/adjust-msg due to the layered nature of one-ofs. See the
;; comments in the protojure.plugin.parse.oneof namespace for more details
;;-------------------------------------------------------------------
(defn- build-msgs [protos msgs]
  (builder (comp (partial build-msg protos) oneof/adjust-msg) msgs))

;;-------------------------------------------------------------------
;; getmaptypes collects all map types
;;-------------------------------------------------------------------
(defn- getmaptypes [types]
  (filter :ismap types))

;;-------------------------------------------------------------------
;; ismaptype? returns map type given type name
;;-------------------------------------------------------------------
(defn- ismaptype? [msgs name]
  (some #(= (:name %) name) (getmaptypes msgs)))

;;-------------------------------------------------------------------
;; update-msg-with-map-attr adds :ismap key to fields in a msg
;;-------------------------------------------------------------------
(defn- update-msg-with-map-attr [msgs msg]
  (map #(assoc % :ismap (ismaptype? msgs (:fname %))) (:fields msg)))

;;-------------------------------------------------------------------
;; update-fields-with-map-attr adds :ismap key to fields
;;-------------------------------------------------------------------
(defn- update-fields-with-map-attr [msgs]
  (mapv #(assoc % :fields (update-msg-with-map-attr msgs %)) msgs))

;;-------------------------------------------------------------------
;; Traverse the AST and collect all message entries, correcting for context
;;-------------------------------------------------------------------
(defn- traverse-nested-msgs
  ([msg]
   (traverse-nested-msgs "" msg))
  ([parent-context msg]
   (let [name (:name msg)
         context (context-name parent-context name)
         nested (->> msg
                     :nested-type
                     (mapv #(traverse-nested-msgs context %))
                     (reduce concat))
         ismap (get-in msg [:options :map-entry])]
     (conj nested
           {:name (str parent-context name)
            :fields (:field msg)
            :oneofdecl (:oneof-decl msg)
            :ismap ismap}))))

;;-------------------------------------------------------------------
;; Generate a flat sequence of all messages - This is helpful for
;; two main reasons
;;
;; 1) we are going to generate a flat namespace of clojure functions
;;    anyway, since "nested classes" doesnt make sense in clojure
;;
;; 2) It gives us a convenient structure to update in one place,
;;    e.g. type resolution
;;-------------------------------------------------------------------
(defn- flatten-msgs [desc]
  (->> (:message-type desc)
       (mapv traverse-nested-msgs)
       (reduce concat)))

;;-------------------------------------------------------------------
;; Find our dependency
;;
;; 'type-name' will look something like ".tutorial.Person.PhoneType"
;; and we cant be sure which part is the package and which is the
;; name.  Therefore, we exhaustively search all packages that have
;; a substring match, and then select the longest matching candidate
;; e.g. "foo.bar.baz" has higher priority than "foo.bar".
;;-------------------------------------------------------------------
(defn- find-dependency [protos type-name]
  (->> (ast/list-packages protos)
       (filter (partial string/includes? type-name))
       (apply max-key count)
       (ast/get-package protos)))

;;-------------------------------------------------------------------
;; Given a type-name such as ".tutorial.Person.PhoneType", we want
;; to extract the function name.  This means stripping off the
;; '.$package.' and converting '.' to '-' to give us
;; 'Person-PhoneType'
;;-------------------------------------------------------------------
(defn- decode-function-name [package type-name]
  (let [pkglen (-> (count package)
                   (+ 2))]
    (-> (subs type-name pkglen)
        (string/replace #"\." "-"))))

;;-------------------------------------------------------------------
;; Return a fully-qualified type tuple [:dep :fname] from a type
;; such as ".tutorial.Person.PhoneType"
;;-------------------------------------------------------------------
(defn- fqtype [protos type]
  (let [{:keys [package] :as dep} (find-dependency protos type)
        name (decode-function-name package type)]
    {:dep dep :fname name}))

;;-------------------------------------------------------------------
;; augments a msg-type field with :package and :fname (function-name)
;;-------------------------------------------------------------------
(defn- update-msg-field [protos {:keys [package] :as desc} {:keys [type-name] :as field}]
  (let [{:keys [dep fname]} (fqtype protos type-name)]
    (-> field
        (assoc :fname fname)
        (cond-> (not= (:package dep) package)
          (assoc :ns (ast/get-namespace dep))))))

;;-------------------------------------------------------------------
;; Update any fields of :type-message or :type-enum
;;-------------------------------------------------------------------
(defn- update-msg-fields [protos desc fields]
  (for [{:keys [type] :as field} fields]
    (if (contains? #{:type-message :type-enum} type)
      (update-msg-field protos desc field)
      field)))

;;-------------------------------------------------------------------
;; Iterate through all fields, and update the message/enum types
;;-------------------------------------------------------------------
(defn- update-msg-type [protos desc msg]
  (update msg :fields (partial update-msg-fields protos desc)))

;;-------------------------------------------------------------------
;; Generate all of our messages
;;-------------------------------------------------------------------
(defn- generate-msgs [protos desc]
  (mapv
   (partial update-msg-type protos desc)
   (flatten-msgs desc)))

;;-------------------------------------------------------------------
;; Render STG template by name and optional attributes
;;-------------------------------------------------------------------
(defn- render-template
  [name attrs]
  (let [stg  (STGroupFile. "generators/interface.stg")
        template (.getInstanceOf stg name)]

    (doseq [[k v] attrs]
      (.add template k v))

    (.render template)))

;;-------------------------------------------------------------------
;; Format requires such that imported files are aliased as themselves
;; ex: "portfolio" --> "portfolio :as portfolio"
;;-------------------------------------------------------------------
(defn- format-requires [reqs]
  (if (> (count reqs) 0)
    (loop [aggr (list)
           [first & remainder] reqs]
      (let [result (conj aggr (str first " :as " first))]
        (if (zero? (count remainder))
          result
          (recur result remainder))))
    reqs))

;;-------------------------------------------------------------------
;; convert a canonical pkg to its namespace, factoring
;; in things like 'java_package' options
;;-------------------------------------------------------------------
(defn get-namespace [protos pkg]
  (ast/get-namespace (ast/get-package protos pkg)))

;;-------------------------------------------------------------------
;; generate a deduplicated list of our dependencies by aggregating
;; all msg.field.ns and rpc.method.param/retval.ns attributes
;;-------------------------------------------------------------------
(defn generate-requires [protos desc]
  (->> (:dependency desc)
       (map (partial get-namespace protos))
       (distinct)
       (format-requires)))

;;-------------------------------------------------------------------
;; method functions - build Service.Method objects for ST4
;;-------------------------------------------------------------------
(defn- new-method-type [{:keys [fname ns]}]
  (->ValueType nil nil nil nil ns fname nil nil))

(defn- new-method [{:keys [name param retval clientstreaming serverstreaming]}]
  (vector name (->Method name (new-method-type param) (new-method-type retval) clientstreaming serverstreaming)))

(defn- build-methods [methods]
  (builder new-method methods))

;;-------------------------------------------------------------------
;; generate a method-type tuple {:fname :ns}
;;
;; note that we elide the namespace if the function is within our
;; own package
;;-------------------------------------------------------------------
(defn- generate-method-type [protos mypackage type]
  (let [{:keys [dep fname]} (fqtype protos type)]
    (-> {:fname fname}
        (cond-> (not= (:package dep) mypackage)
          (assoc :ns (ast/get-namespace dep))))))

;;-------------------------------------------------------------------
;; generate a list of [:name :param :retval] tuples based on the
;; service AST.
;;-------------------------------------------------------------------
(defn- generate-methods [protos {:keys [package] :as src} methods]
  (for [{:keys [name input-type output-type server-streaming client-streaming]} methods]
    {:name name
     :param (generate-method-type protos package input-type)
     :retval (generate-method-type protos package output-type)
     :serverstreaming server-streaming
     :clientstreaming client-streaming}))

;;-------------------------------------------------------------------
;; service functions - build Service objects (See deftype Service above) for ST4
;;-------------------------------------------------------------------
(defn- new-service [{:keys [name package methods]}]
  (vector name (->Service name package (build-methods methods))))

;;-------------------------------------------------------------------
;; generate RPC entries to implement GRPCs as things like
;; pedestal interceptors or client stubs
;;-------------------------------------------------------------------
(defn generate-rpcs [protos desc]
  (let [package (ast/get-namespace desc)]
    (for [{:keys [name method] :as svc} (:service desc)]
      {:name name :package package :methods (generate-methods protos desc method)})))

(defn- build-services [protos desc]
  (->> (generate-rpcs protos desc)
       (builder new-service)))

;;-------------------------------------------------------------------
;; Refer to the deftypes at the top of this ns for details on the contents
;; of each
;;-------------------------------------------------------------------
(defn- generate-impl-content [protos pkg template modded-rpcs]
  (let [gns (generate-impl-ns protos pkg nil)
        ns (generate-impl-ns protos pkg (when modded-rpcs (first (keys modded-rpcs)))) ;; first key of modded-rpcs names a Service
        desc (ast/get-package protos pkg)
        enums (generate-enums desc)
        pre-map-msgs (generate-msgs protos desc)
        msgs (update-fields-with-map-attr pre-map-msgs)
        services (or modded-rpcs (build-services protos desc)) ;; built services
        requires (generate-requires protos desc)]
    (render-template template
                     [["generic_namespace" gns]
                      ["namespace" ns]
                      ["enums" enums]
                      ["messages" (build-msgs protos msgs)]
                      ["requires" (into-array String requires)]
                      ["rpcs" services]])))

;;-------------------------------------------------------------------
;; Generate the map of name and content
;;
;; Params:
;;   impl-name: name of the file to be created
;;   impl-str:  function in stg
;;   m:         map of RPCs from a single service
;;-------------------------------------------------------------------
(defn- gen-impl-map [protos pkg impl-name template m]
  {:name    (generate-impl-name protos pkg template impl-name)
   :content (generate-impl-content protos pkg template (when m {impl-name (get m impl-name)}))})

;;-------------------------------------------------------------------
;; Top-level impl generation function
;;
;; Note that if there are not any RPCs, there is no need to create
;; the stub or clients. Therefore there is a check on the impl-str
;; and the built collection of RPCs
;;-------------------------------------------------------------------
(defn generate-impl [protos pkg template]
  (let [desc (ast/get-package protos pkg)
        services (build-services protos desc)]
    (cond
      (= template "messages")
      [(gen-impl-map protos pkg nil template nil)]

      (not-empty services)
      (mapv (fn [service-name] (gen-impl-map protos pkg service-name template {service-name (get services service-name)})) (keys services))

      :default nil)))

;;-------------------------------------------------------------------
;; checks before processing, abort if error
;;-------------------------------------------------------------------
(defn validity-checks [protos]
  (when-let [nilpkg (ast/get-package protos nil)]
    (util/abort -1 (str (:name nilpkg) " does not have a package name"))))