(ns onekeepass.frontend.events.entry-form-auto-open
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.constants :as const :refer [IFDEVICE PASSWORD URL
                                                    USERNAME]]
   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             check-error
                                                             on-error]]
   [onekeepass.frontend.events.entry-form-common :refer [extract-form-field-names-values
                                                         form-data-kv-data
                                                         get-form-data]] 
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))


(defn entry-form-open-url [url-value]
  (dispatch [:entry-form-open-url url-value]))

(reg-fx
 :bg-open-url
 (fn [[path]]
   (bg/open-url path
                (fn [api-response]
                  (on-error api-response)))))

(reg-event-fx
 :entry-form-open-url
 (fn [{:keys [_db]} [_event-id url-value]]
   (cond
     (str/starts-with? url-value "kdbx:")
     {:fx [[:dispatch [:entry-form-auto-open-resolve-properties]]]}

     (or (str/starts-with? url-value "https://") (str/starts-with? url-value "http://"))
     {:fx [[:bg-open-url [url-value]]]}

     :else
     {})))

(def all-auto-open-kvd-keys [URL USERNAME IFDEVICE])

;; ao-keys is all-auto-open-kvd-keys and is not yet used
(defn auto-open-properties-dispatch-fn [ao-keys api-response]
  (when-let [auto-props-resolved
             (check-error api-response
                          #(dispatch [:entry-form-auto-open-properties-resolve-error %]))]
    (dispatch [:entry-form-auto-open-properties-resolved ao-keys auto-props-resolved])))
;; 
(reg-event-fx
 :entry-form-auto-open-resolve-properties
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [entry-type-uuid] :as form-data} (get-form-data db)]
     ;; Should entry-type-uuid check is required ?
     (if (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_AUTO_OPEN)
       (let [field-m (extract-form-field-names-values form-data)
             auto-props {:source-db-key (active-db-key db)
                         :url-field-value (get field-m URL)
                         :key-file-path (get field-m USERNAME)
                         :device_if_val (get field-m const/IFDEVICE)}]
         {:fx [[:bg-resolve-auto-open-properties [auto-props (partial auto-open-properties-dispatch-fn all-auto-open-kvd-keys)]]]})
       {}))))

(reg-fx
 :bg-resolve-auto-open-properties
 (fn [[auto-props dispatch-fn]]
   (bg/resolve-auto-open-properties auto-props dispatch-fn)))

(reg-event-fx
 :entry-form-auto-open-properties-resolved
 (fn [{:keys [db]} [_event-id _ao-keys {:keys [url-field-value key-file-path can-open]}]]
   (if (not can-open)
     {:fx [[:dispatch [:common/error-info-box-show {:title "Database auto open" :error-text "The device is excluded in opening this database url"}]]]}
     (let [{:keys [value]} (form-data-kv-data (get-form-data db) PASSWORD)]
       {:fx [[:dispatch [:open-db/auto-open-with-credentials url-field-value value key-file-path]]]}))))

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



