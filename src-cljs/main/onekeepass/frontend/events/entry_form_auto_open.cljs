(ns onekeepass.frontend.events.entry-form-auto-open
  (:require
   [clojure.string :as str] 
   [onekeepass.frontend.constants :as const :refer [IFDEVICE PASSWORD URL
                                                    USERNAME]]
   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             check-error]] 
   [onekeepass.frontend.events.entry-form-common :refer [extract-form-field-names-values
                                                         form-data-kv-data
                                                         get-form-data
                                                         place-holder-resolved-value]]
   [onekeepass.frontend.background-auto-open :as bg-ao]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))


(defn entry-form-open-url 
  "Called when user clicks on the lauch icon in the entry form url field"
  [url-value]
  (dispatch [:entry-form-open-url url-value]))


(reg-event-fx
 :entry-form-open-url
 (fn [{:keys [_db]} [_event-id url-value]] 
   (cond
     ;; The url is expected to start with lowercase at this time
     (str/starts-with? url-value "kdbx:")
     {:fx [[:dispatch [:entry-form-auto-open-resolve-properties]]]}

     (or (str/starts-with? url-value "https://") (str/starts-with? url-value "http://"))
     {:fx [[:common/bg-open-url [url-value]]]}

     (str/starts-with? url-value "file://")
     {:fx [[:common/bg-open-file [url-value]]]}

     ;; Just add the prefix and open
     :else
     {:fx [[:common/bg-open-url [(str "https://" url-value)]]]})))

(def all-auto-open-kvd-keys [URL USERNAME IFDEVICE])

;; ao-keys is all-auto-open-kvd-keys and is not yet used
(defn auto-open-properties-dispatch-fn [ao-keys api-response]
  (when-let [auto-props-resolved
             (check-error api-response
                          #(dispatch [:entry-form-auto-open-properties-resolve-error %]))]
    (dispatch [:entry-form-auto-open-properties-resolved ao-keys auto-props-resolved])))

;; This event is called to resolve auto open properties before 
;; using that info to open a database 
(reg-event-fx
 :entry-form-auto-open-resolve-properties
 (fn [{:keys [db]} [_event-id]] 
   (let [{:keys [entry-type-uuid] :as form-data} (get-form-data db)]
     ;; Should entry-type-uuid check is required ?
     (if (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_AUTO_OPEN) 
       (let [field-m (extract-form-field-names-values form-data)
             parsed-fields (:parsed-fields form-data) 
             ;; Ensure that all place holders in the entry fields are parsed 
             url (place-holder-resolved-value parsed-fields URL (get field-m URL)) 
             key-file-path (place-holder-resolved-value parsed-fields USERNAME (get field-m USERNAME)) 
             auto-props {:source-db-key (active-db-key db)
                         :url-field-value url
                         :key-file-path key-file-path
                         :device_if_val (get field-m const/IFDEVICE)}]
         {:fx [[:bg-resolve-auto-open-properties [auto-props (partial auto-open-properties-dispatch-fn all-auto-open-kvd-keys)]]]})
       {}))))

;; Backend api is called to ensure that db file and key file paths
;; are valid
(reg-fx
 :bg-resolve-auto-open-properties
 (fn [[auto-props dispatch-fn]]
   (bg-ao/resolve-auto-open-properties auto-props dispatch-fn)))

;;  The db file and key file paths are resolved and valid
(reg-event-fx
 :entry-form-auto-open-properties-resolved
 (fn [{:keys [db]} [_event-id _ao-keys {:keys [url-field-value key-file-path can-open]}]]
   (if (not can-open)
     {:fx [[:dispatch [:common/error-info-box-show {:title "Database auto open" :error-text "The device is excluded in opening this database url"}]]]}
     (let [form-data (get-form-data db)
           parsed-fields (:parsed-fields form-data)
           {:keys [value]} (form-data-kv-data form-data PASSWORD)
           password (place-holder-resolved-value parsed-fields PASSWORD value)]
       {:fx [[:dispatch [:open-db/auto-open-with-credentials url-field-value password key-file-path]]]}))))

(reg-event-fx
 :entry-form-auto-open-properties-resolve-error
 (fn [{:keys [_db]} [_event-id error]]
   {:fx [[:dispatch [:common/error-info-box-show {:title  "Auto open error" :error-text error}]]]}))


(comment)


#_(reg-event-fx
   :entry-form-auto-open-properties-resolved
   (fn [{:keys [db]} [_event-id ao-keys {:keys [url-field-value key-file-path can-open]} :as _auto-props-resolved]]
     (let [{:keys [section-fields] :as _form-data} (get-form-data db)
           [section-name section-kvs] (section-containing-field section-fields URL)

           section-kvs (if (contains-val? ao-keys URL)
                         (section-kvs-updated-with-field-value section-kvs (assoc (field-key-value-data section-kvs URL) :url-field-value url-field-value))
                         section-kvs)

           section-kvs (if (contains-val? ao-keys USERNAME)
                         (section-kvs-updated-with-field-value section-kvs (assoc (field-key-value-data section-kvs USERNAME) :key-file-path key-file-path))
                         section-kvs)

           section-kvs (if (contains-val? ao-keys IFDEVICE)
                         (section-kvs-updated-with-field-value section-kvs (assoc (field-key-value-data section-kvs IFDEVICE) :can-open can-open))
                         section-kvs)


        ;;  kvd (field-key-value-data section-kvs URL)
        ;;  section-kvs (section-kvs-updated-with-field-value section-kvs (assoc kvd :url-field-value url-field-value))

        ;;  kvd (field-key-value-data section-kvs USERNAME)
        ;;  section-kvs (section-kvs-updated-with-field-value section-kvs (assoc kvd :key-file-path key-file-path))

        ;;  kvd (field-key-value-data section-kvs IFDEVICE)
        ;;  section-kvs (section-kvs-updated-with-field-value section-kvs (assoc kvd :can-open can-open))
           ]
       {:db (assoc-in-key-db db [entry-form-key :data :section-fields section-name] section-kvs)})))



