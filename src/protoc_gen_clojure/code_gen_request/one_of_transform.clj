(ns protoc-gen-clojure.code-gen-request.one-of-transform)

(declare adjust-fields)

;;-------------------------------------------------------------------
;; Move oneof fields to a parent container named with the oneof name
;;-------------------------------------------------------------------
;;
;; Understanding the flow of this parsing code benefits from a
;; familiarity with the protoc CodeGeneratorRequest oneof descriptor
;; format. The only indication that an element in the :field array
;; (here using the flatland key conversion conventions) is a oneof
;; "possible field" is the presence of the key
;; :oneof-index. e.g.
;;
;; ```
;; :proto-file [
;;              {:name "Foo"
;;                ...
;;                :message-type [{:name "Bar"
;;                                :field [{:name "s",
;;                                        :type :type-string,
;;                                        :oneof-index 0,
;;                                        ...}
;; ```
;;
;; Given this structure, the task is to convert multiple declared
;; elements in the :fields array of the CodeGeneratorRequest to a
;; single (protobuf) tag handler capable of deserializing any one
;; of the multiple declared fields with identical :oneof-index values.
;;
;; In order to do this in the .clj bindings, we group fields with
;; the same :oneof-index value into a form similar to our handling
;; of the protobuf message type, with the additional constraint
;; that only a single type will appear in the single one-of tag.
;;
;; A further example:
;;
;; One-of fields "s" and "ss" in "MyMessage" below will be moved into a container
;; field under the "ofields" key. The name of the container field will be
;; the entry of the "oneof-index" in the ":oneofdecl" key (in this example
;; "TheStrings"). The new container will be used as a regular field with tag "TheStrings"
;; and will serdes any of the child fields.
;;
;; Input message:
;;   {:name "MyMessage",
;;    :fields
;;    (
;;     {:name "not-a-oneof-field",
;;      :number 1,
;;      :one-index 1,
;;      ...}
;;     {:name "s",
;;      :number 2,
;;      :one-index 1,
;;       ...}
;;     {:name "ss",
;;      :number 3,
;;      :one-index 1,
;;         ...}
;;     )
;;    :oneofdecl ["FirstOneof", "TheStrings"]}
;;
;; Output message:
;;   {:name "MyMessage",
;;    :fields
;;    (
;;     {:name "not-a-oneof-field",
;;      :number 1,
;;      :one-index 1,
;;      ...}
;;     {:name "TheStrings"
;;      :one-index 1,
;;      :ofields [{:name "s",
;;                 :number 2,
;;                 :one-index 1,
;;                  ...}
;;                {:name "ss",
;;                 :number 3,
;;                 :one-index 1,
;;                    ...}}
;;     )
;;    :oneofdecl ["FirstOneof", "TheStrings"]}
;;-------------------------------------------------------------------
(defn adjust-msg [{:keys [oneofdecl] :as msg}]
  (cond-> msg (not (empty? oneofdecl))
          (update-in [:fields] (partial adjust-fields oneofdecl))))

(defn valid? [oneofdecl {:keys [oneof-index] :as field}]
  (contains? oneofdecl oneof-index))

(defn get-index [oneofdecl {:keys [oneof-index] :as field}]
  (when (valid? oneofdecl field)
    oneof-index))

;;-------------------------------------------------------------------
;; Add oneof fields to the appropriate parent field
;;-------------------------------------------------------------------
(defn- adjust-field [oneofdecl coll f]
  (let [oi (get-index oneofdecl f)
        newf (first (filter #(when-let [oiother (get-index oneofdecl %)] (when (= oi oiother) %)) coll))
        inewf (.indexOf coll newf)]
    (cond
      ;;-- not a oneof field, just add it
      (nil? oi) (conj coll f)
      ;;--parent not created ?
      (nil? newf) (let [name (get-in oneofdecl [oi :name])]
                    (conj coll {:name name
                                :fname name
                                :oneof-index oi
                                :type :type-oneof
                                :label :label-optional
                                :ofields [f]}))
      ;;--update the parent with the passed oneof
      :default (update-in coll [inewf :ofields] (fn [of] (conj of f))))))

(defn- adjust-fields [oneofdecl fields]
  (reduce
   (fn [coll f]
     (adjust-field oneofdecl coll f))
   [] fields))
