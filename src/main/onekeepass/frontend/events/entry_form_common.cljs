(ns onekeepass.frontend.events.entry-form-common
  (:require [clojure.string :as str]
            [onekeepass.frontend.constants :refer [ONE_TIME_PASSWORD_TYPE]]
            [onekeepass.frontend.events.common :as cmn-events :refer [assoc-in-key-db
                                                                      get-in-key-db]]
            [onekeepass.frontend.utils :as u :refer [contains-val?]]))

(def entry-form-key :entry-form-data)

(def ^:private standard-kv-fields ["Title" "Notes"])

(def Favorites "Favorites")

(defn get-form-data 
  "Gets the current form data of a currently opened db from the app-db"
  [app-db]
  (get-in-key-db app-db [entry-form-key :data]))

(defn is-field-exist
  "Checks that a given field name exists in the entry form or not "
  [app-db field-name]
  ;(println "field-name is " field-name)
  (let [field-name (str/trim field-name)
        all-section-fields (-> (get-in-key-db
                                app-db
                                [entry-form-key :data :section-fields])
                               vals flatten) ;;all-section-fields is a list of maps for all sections
       ;;_ (println "all-section-fields are " all-section-fields)
        ;;found  (filter (fn [m] (= field-name (:key m))) all-section-fields)
        ]
    (or (contains-val? standard-kv-fields field-name)
        (-> (filter (fn [m] (= field-name (:key m))) all-section-fields) seq boolean))))

(defn add-section-field
  "Creates a new KV for the added section field and updates the 'section-name' section
  Returns the updated app-db
  "
  [app-db {:keys [section-name
                  field-name
                  protected
                  required
                  data-type]}] 
  (let [section-fields-m (get-in-key-db
                          app-db
                          [entry-form-key :data :section-fields])
        ;;_ (println "section-fields-m " section-fields-m)
        ;; fields is a vec of KVs for a given section
        fields (-> section-fields-m (get section-name []))
        fields (conj fields {:key field-name
                             :value nil
                             :protected protected
                             :required required
                             :data-type data-type
                             :standard-field false})]
    (assoc-in-key-db app-db [entry-form-key :data :section-fields]
                     (assoc section-fields-m section-name fields))))

(defn merge-section-key-value [db section key value]
  (let [section-kvs (get-in-key-db db [entry-form-key :data :section-fields section])
        section-kvs (mapv (fn [m] (if (= (:key m) key) (assoc m :value value) m)) section-kvs)]
    section-kvs))

(defn form-data-kvd-fields 
  "Gets the vec of all Kvd map found in an entry"
  [form-data]
  (-> form-data :section-fields vals flatten))

(defn extract-form-field-names-values
  "Returns a map with field name as key (a string type) and field value as value"
  [form-data]
  ;; :section-fields returns a map with section name as keys
  ;; vals fn return 'values' ( a vec of field info map) for all sections. Once vec for each section. 
  ;; And need to use flatten to combine all section values
  ;; For example if two sections, vals call will return a two member ( 2 vec)
  ;; sequence. Flatten combines both vecs and returns a single sequence of field info maps
  (let [fields  (form-data-kvd-fields form-data) #_(-> form-data :section-fields vals flatten)
        names-values (into {} (for [{:keys [key value]} fields] [key value]))]
    names-values))

(defn extract-form-otp-fields
  "Returns a map with a otp field name as key and current-opt-token value as value
   This is formed by going through all otp fields in KVs (section-fields)
  "
  [form-data]
  ;; :section-fields returns a map with section name as keys
  ;; vals fn return 'values' ( a vec of field info map) for all sections. One vec for each section. 
  ;; And need to use flatten to combine all section values
  ;; For example if we have two sections, vals call will return a two member ( 2 vec)
  ;; sequence. Flatten combines both vecs and returns a single sequence of field info maps
  (let [;; fields is a vec of KV maps 
        fields (form-data-kvd-fields form-data) 
        ;; otp-fields is a vec of KV maps only for otp fields
        otp-fields (filter (fn [m] (=  ONE_TIME_PASSWORD_TYPE (:data-type m))) fields)
        names-values (into {} (for [{:keys [key current-opt-token]} otp-fields] [key current-opt-token]))]
    names-values))

(defn field-key-value-data
  "Checks whether the arg 'field-maps-vec' (a vec of kvd maps) has a map 
   with kvd field map  (struct KeyValueData) for the key 'field-name'

  The arg 'field-name' is a string value like 'URL' 'Password' or 'UserName'
  Reurns the kvd map if the passed vec of maps has a map with :key = field-name 
  "
  ([field-maps-vec field-name]
   (->> field-maps-vec (filter
                        (fn [kvd] (= (:key kvd) field-name))) first)))

(defn form-data-kv-data 
  "Gets the kvd map for this 'field-name' from all kvds found for an entry in form-data map"
  [form-data field-name]
  (field-key-value-data (form-data-kvd-fields form-data) field-name ) )
  

;; Few other fns that may be useful 
;; Not used for now
#_(defn- has-field
  " Checks whether the arg 'field-maps-vec' has a map with entry form entry field map  
    (struct KeyValueData) that has a 'key' with value of the arg 'field-name' ( a string value)
    Reurns true if the passed vec of maps has a map with :key = field-name 
    "
  [field-maps-vec field-name]
  (->> field-maps-vec (filter
                       (fn [m] (= (:key m) field-name))) empty? boolean not))

#_(defn- section-containing-field
  "Called to get the section of a field
   Returns a vec (2 elements) of section name and a vec of field maps (kvd maps) 
   "
  [section-fields field-name]
  (let [section-name-with-kvs (->>
                               section-fields
                               (filterv (fn [[_section-name field-maps-vec]]
                                          (has-field field-maps-vec field-name))) first)]
    ;; [section-name section-kvs]
    section-name-with-kvs))

#_(defn- section-kvs-updated-with-field-value
  "The arg 'section-kvs' is a vec of kvd (struct KeyValueData) maps of a section 
   Returns the updated kvd map 
  "
  [section-kvs {:keys [key] :as kvd-m}]
  (let [section-kvs (mapv (fn [m] (if (= (:key m) key) kvd-m m)) section-kvs)]
    section-kvs))

#_(defn- section-updated-with-field-value
  "Updates the kvd found in a section with the kvd found in arg 'kvd-m
   Returns a vector (2 elements) with section name and a vec of kvd maps
   "
  [section-fields {:keys [key] :as kvd-m}]
  (let [[section-name section-kvs] (section-containing-field section-fields key)
        section-kvs (section-kvs-updated-with-field-value section-kvs kvd-m)]
    [section-name section-kvs]))
