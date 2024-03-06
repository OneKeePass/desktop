(ns onekeepass.frontend.events.entry-form-common
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-fx reg-event-db reg-event-fx reg-sub  dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             active-db-key
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             fix-tags-selection-prefix]]
   [onekeepass.frontend.constants :refer [PASSWORD]]
   [onekeepass.frontend.utils :as u :refer [contains-val?]]
   [onekeepass.frontend.background :as bg])
  )

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