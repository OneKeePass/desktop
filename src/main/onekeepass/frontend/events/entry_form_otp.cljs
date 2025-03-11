(ns onekeepass.frontend.events.entry-form-otp
  (:require [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                                      assoc-in-key-db
                                                                      get-in-key-db 
                                                                      on-error]]
            [onekeepass.frontend.events.entry-form-common :refer [entry-form-key
                                                                  extract-form-otp-fields
                                                                  merge-section-key-value]]
            [re-frame.core :refer [reg-event-fx reg-fx reg-sub]]
            [onekeepass.frontend.constants :as const]))


;; Called to update with the current tokens 
(reg-event-fx
 :entry-form/update-otp-tokens
 (fn [{:keys [db]} [_event-id entry-uuid current-opt-tokens-by-field]]

   ;; First we need to ensure that the incoming entry id is the same one showing
   (if (= entry-uuid (get-in-key-db db [entry-form-key :data :uuid]))

     ;; otp-fields is a map with otp field name as key and its token info with time ttl 
     (let [db (reduce (fn [db [otp-field-name {:keys [token ttl]}]]
                        (let [otp-field-name (name otp-field-name) ;; make sure field name is string
                              otp-field-m (get-in-key-db db [entry-form-key :otp-fields otp-field-name])
                              otp-field-m (if (nil? token)
                                            (assoc otp-field-m :ttl ttl)
                                            (assoc otp-field-m :token token :ttl ttl))]
                          (assoc-in-key-db db [entry-form-key :otp-fields otp-field-name] otp-field-m)))
                      db current-opt-tokens-by-field)]

       {:db db})
     {})))

(defn remove-section-otp-field [otp-field-name {:keys [key] :as section-field-m}]
  (cond
    ;; current-opt-token is Option type in struct CurrentOtpTokenData and should be set to nil and not {}
    (= key const/OTP)
    (assoc section-field-m :value nil :current-opt-token nil)

    (= key otp-field-name)
    nil

    :else
    section-field-m))


;; :entry-form-delete-otp-field-complete renamed as :entry-form-delete-otp-field

;; Called after stopping the otp update polling
(reg-event-fx
 :entry-form-delete-otp-field
 (fn [{:keys [db]} [_event-id section otp-field-name]]
   (let [section-kvs (get-in-key-db db [entry-form-key :data :section-fields section])
         section-kvs (mapv (fn [m] (remove-section-otp-field otp-field-name m)) section-kvs)
         ;; Remove nil values
         section-kvs (filterv (fn [m] m) section-kvs)
         otp-fields (-> db (get-in-key-db [entry-form-key :otp-fields])
                        (dissoc otp-field-name))
         ;; Set the db before using in fx
         db (-> db
                (assoc-in-key-db [entry-form-key :data :section-fields section] section-kvs)
                (assoc-in-key-db [entry-form-key :otp-fields] otp-fields))]
     {:db db
      ;; Calling update will reload the entry form 
      :fx [[:bg-update-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]]]})))

(reg-event-fx
 :entry-form/otp-url-formed
 (fn [{:keys [db]} [_event-id section otp-field-name otp-url]]
   (let [form-status (get-in-key-db db [entry-form-key :showing])
         section-kvs (merge-section-key-value db section otp-field-name otp-url)
         ;; Set the db before using in fx
         db (-> db
                (assoc-in-key-db [entry-form-key :data :section-fields section] section-kvs))]
     ;;(println "entry-form/otp-url-formed form-status is  " form-status)
     {:db db
      :fx [(if (= form-status :new)
             [:bg-insert-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]]
             [:bg-update-entry [(active-db-key db) (get-in-key-db db [entry-form-key :data])]])]})))

;;; Events used in useEffect 

;; NOTE: 
;; Because of aync nature, the order of stopping polling for previous entry vs starting polling
;; for next entry may not be in sequence

;; TODO: 
;; Explore Whether we need to use 'db-key' in the backend fns 
;; 'stop_polling_entry_otp_fields', 'stop-polling-all-entries-otp-fields' 
;; For now, db-key is ignored for 'stop_polling_entry_otp_fields' and for 'stop-polling-all-entries-otp-fields'

(reg-event-fx
 :entry-form-otp-start-polling
 (fn [{:keys [db]} [_event-id]]
   (let [entry-uuid (get-in-key-db db [entry-form-key :data :uuid])
         otp-fields (extract-form-otp-fields (get-in-key-db db [entry-form-key :data]))]
     {:fx [[:bg-otp-start-polling-otp-fields [(active-db-key db) entry-uuid otp-fields]]]})))

(reg-fx
 :bg-otp-start-polling-otp-fields
 (fn [[db-key entry-uuid otp-field-m]]
   ;; otp-field-m is a map with otp field name as key and token data as its value
   ;; See 'start-polling-otp-fields' fn
   (let [fields-m (into {} (filter (fn [[_k v]] (not (nil? v))) otp-field-m))] 
     (when (boolean (seq fields-m))
       ;;(println "start-polling-entry-otp-fields called for " entry-uuid)
       (bg/start-polling-entry-otp-fields db-key entry-uuid fields-m #(on-error %))))))


;; Called from entry form a usEffect cleanup fn
;; If the database is closed when a form is shown, usEffect's cleanup fn is called after the database closed
;; We can not use (active-db-key db) to get db-key as it may be nil or pointing incorrect db-key for this event
(reg-event-fx
 :entry-form-otp-stop-polling
 (fn [{:keys [_db]} [_event-id entry-uuid]]
   {:fx [[:bg-stop-all-entry-form-polling ["db-key-not-used" entry-uuid]]]}))

#_(reg-event-fx
 :entry-form-otp-stop-polling
 (fn [{:keys [db]} [_event-id entry-uuid]]
   (let [db-key (last-active-db-key db)
         db-key (if-not (nil? db-key) db-key (active-db-key db))]
     {:fx [[:bg-stop-all-entry-form-polling [db-key entry-uuid]]]})))

;; Stops all otp fields polling of an entry form
;; db-key should not be nil 
;; Api call will fail if db-key is nil
(reg-fx
 :bg-stop-all-entry-form-polling
 (fn [[db-key entry-uuid]]
   ;;(println "stop-polling-entry-otp-fields called for " entry-uuid)
   (bg/stop-polling-entry-otp-fields db-key entry-uuid #(on-error %))))


(reg-sub
 :otp-currrent-token
 (fn [db [_query-id otp-field-name]]
   (get-in-key-db db [entry-form-key :otp-fields otp-field-name])))

(comment
  (keys @re-frame.db/app-db)

  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)

  (-> (get @re-frame.db/app-db db-key) :entry-form-data) ;; for now

  ;; All entry map keys
  (-> (get @re-frame.db/app-db db-key) :entry-form-data keys)
  ;; => (:showing :group-selection-info :edit :expiry-duration-selection :welcome-text-to-show 
  ;;     :error-fields :entry-history-form :api-error-text :undo-data :data)

  ;; All entry form data keys
  (-> (get @re-frame.db/app-db db-key) :entry-form-data :data keys)
  ;; => :tags :icon-id :binary-key-values :section-fields :title :expiry-time :history-count
  ;;    :expires :standard-section-names :last-modification-time :entry-type-name :auto-type
  ;;    :notes :section-names :entry-type-icon-name :last-access-time :uuid :entry-type-uuid :group-uuid :creation-time
  )