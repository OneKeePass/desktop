(ns onekeepass.frontend.events.entry-form-common
  (:require
   ;;[clojure.string :as str] 
   [onekeepass.frontend.events.common :as cmn-events :refer [get-in-key-db]]
   [onekeepass.frontend.constants :refer [ONE_TIME_PASSWORD]]
   [onekeepass.frontend.utils :as u :refer [contains-val?]]))

(def entry-form-key :entry-form-data)

(def ^:private standard-kv-fields ["Title" "Notes"])

(def Favorites "Favorites")

(defn is-field-exist
  "Checks that a given field name exists in the entry form or not "
  [app-db field-name]
  ;(println "field-name is " field-name)
  (let [all-section-fields (-> (get-in-key-db
                                app-db
                                [entry-form-key :data :section-fields])
                               vals flatten) ;;all-section-fields is a list of maps for all sections
       ;;_ (println "all-section-fields are " all-section-fields)
        ;;found  (filter (fn [m] (= field-name (:key m))) all-section-fields)
        ]
    (or (contains-val? standard-kv-fields field-name)
        (-> (filter (fn [m] (= field-name (:key m))) all-section-fields) seq boolean))))

(defn merge-section-key-value [db section key value]
  (let [section-kvs (get-in-key-db db [entry-form-key :data :section-fields section])
        section-kvs (mapv (fn [m] (if (= (:key m) key) (assoc m :value value) m)) section-kvs)]
    section-kvs))

(defn extract-form-otp-fields
  "Returns a map with a otp field name as key and current-opt-token value as value"
  [form-data]
  ;; :section-fields returns a map with section name as keys
  ;; vals fn return 'values' ( a vec of field info map) for all sections. Once vec for each section. 
  ;; And need to use flatten to combine all section values
  ;; For example if two sections, vals call will return a two member ( 2 vec)
  ;; sequence. Flatten combines both vecs and returns a single sequence of field info maps
  (let [fields (-> form-data :section-fields vals flatten)
        otp-fields (filter (fn [m] (=  ONE_TIME_PASSWORD (:data-type m))) fields)
        names-values (into {} (for [{:keys [key current-opt-token]} otp-fields] [key current-opt-token]))]
    names-values))