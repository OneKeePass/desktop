(ns onekeepass.frontend.events.import-file.csv
  (:require-macros [onekeepass.frontend.okp-macros
                    :refer  [as-map]])
  (:require
   [clojure.set :as cset]
   [onekeepass.frontend.background.csv :as bg-csv]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             on-error
                                                             current-opened-db
                                                             locked?]]
   [onekeepass.frontend.translation :refer [lstr-sm]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub subscribe]]))

;; Called from csv-imoprt-start-dialog
(defn open-file-explorer-on-click
  "Opens the os specific file picking window dialog so that user can pick a csv file"
  []
  (cmn-events/open-file-explorer-on-click :import-csv-file-picked))

;; (defn import-cvs-file []
;;   (dispatch [:import/import-csv-file-start]))

(defn import-csv-mapped [mapping-options]
  (dispatch [:import-csv-mapped mapping-options]))

(defn import-csv-map-custom-field
  "Called whether we want to consider the unmapped header fields as custom fields or not"
  [checked?]
  (dispatch [:import-csv-map-custom-field checked?]))

(defn import-csv-new-database
  "Called when new database creation is selected after the mapping"
  []
  (dispatch [:import-csv-new-database]))

(defn import-csv-into-current-db []
  (dispatch [:import-csv-into-current-db]))

(defn import-csv-unmapped-header-field-delete [header-field]
  (dispatch [:import-csv-unmapped-header-field-delete header-field]))

(defn import-csv-mapping-result []
  (subscribe [:import-csv-mapping-result]))

;; Called from system nenu
(reg-event-fx
 :import/import-csv-file-start
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:generic-dialog-show :csv-imoprt-start-dialog]]]}))

(reg-event-fx
 :import-csv-file-picked
 (fn [{:keys [_db]} [_event-id picked-file-full-name]]
   (if-not (nil? picked-file-full-name)
     {:fx [[:bg-import-cvs-file [picked-file-full-name]]]}
     {})))

(reg-fx
 :bg-import-cvs-file
 (fn [[file-full-path]]
   (bg-csv/import-cvs-file file-full-path
                           (fn [api-response]
                             ;; CvsHeaderInfo
                             (when-some [header-info (check-error api-response)]
                               (dispatch [:import-csv-loaded header-info]))))))

(def NOT_PRESENT "Not Available")

;; These are some standard keepass login entry type fields that we support in csv import
(def okp-filed-to-csv-header-mapping [{:field-name const/GROUP :mapped-name NOT_PRESENT}
                                      {:field-name const/TITLE :mapped-name NOT_PRESENT}
                                      {:field-name const/USERNAME :mapped-name NOT_PRESENT}
                                      {:field-name const/PASSWORD :mapped-name NOT_PRESENT}
                                      {:field-name const/URL :mapped-name NOT_PRESENT}

                                      {:field-name const/NOTES :mapped-name NOT_PRESENT}
                                      {:field-name const/OTP :mapped-name NOT_PRESENT}
                                      {:field-name const/TAGS :mapped-name NOT_PRESENT}
                                      #_{:field-name const/MODIFIED_TIME :mapped-name NOT_PRESENT}
                                      #_{:field-name const/CREATED_TIME :mapped-name NOT_PRESENT}])

;; Chosen in the "exported from" dropdown to ignore any detected profile and map the
;; columns by hand. Not a real profile id - the backend never sees it
(def GENERIC_PROFILE_ID "generic")

(defn- with-suggested-mapping
  "Returns the standard mapping options with any column the profile suggested filled in.
   Fields the profile does not cover stay NOT_PRESENT, so the user can still see at a
   glance what is unmapped and correct anything the profile got wrong."
  [suggested]
  (let [by-field (into {} (map (juxt :field-name :mapped-name) suggested))]
    (mapv (fn [{:keys [field-name] :as m}]
            (assoc m :mapped-name (get by-field field-name NOT_PRESENT)))
          okp-filed-to-csv-header-mapping)))

(reg-event-fx
 :import-csv-loaded
 (fn [{:keys [db]} [_event-id {:keys [headers detected-profile]}]]
   (let [{:keys [id display-name mapping consumed-columns]} detected-profile]
     {:db (-> db
              (assoc-in [:import-csv :headers] headers)
              (assoc-in [:import-csv :profile-id] id)
              (assoc-in [:import-csv :consumed-columns] consumed-columns))
      :fx [;; Populates the "exported from" dropdown
           [:bg-csv-import-profiles]
           ;; Show the dialog csv-columns-mapping-dialog 
           [:dispatch [:generic-dialog-show-with-state :csv-columns-mapping-dialog
                       ;; Add NOT_PRESENT as first element to vec headers
                       {:data {:csv-headers (-> NOT_PRESENT (cons headers) vec)
                               :profile-id (if (nil? id) GENERIC_PROFILE_ID id)
                               :profile-display-name display-name
                               :mapping-options (with-suggested-mapping mapping)}}]]]})))

;; Public API fns for the mapping dialog

(defn import-csv-profiles []
  (subscribe [:import-csv-profiles]))

(defn import-csv-profile-changed [profile-id]
  (dispatch [:import-csv-profile-changed profile-id]))

(reg-sub
 :import-csv-profiles
 (fn [db _query-vec]
   (get-in db [:import-csv :profiles])))

(reg-fx
 :bg-csv-import-profiles
 (fn [_none]
   (bg-csv/csv-import-profiles
    (fn [api-response]
      (when-let [profiles (check-error api-response)]
        (dispatch [:import-csv-profiles-loaded profiles]))))))

(reg-event-db
 :import-csv-profiles-loaded
 (fn [db [_event-id profiles]]
   (assoc-in db [:import-csv :profiles] profiles)))

;; The user picked a different product, or picked the generic option to start over
(reg-event-fx
 :import-csv-profile-changed
 (fn [{:keys [db]} [_event-id profile-id]]
   (if (= profile-id GENERIC_PROFILE_ID)
     {:db (-> db
              (assoc-in [:import-csv :profile-id] nil)
              (assoc-in [:import-csv :consumed-columns] nil))
      :fx [[:dispatch [:import-csv-mapping-suggested GENERIC_PROFILE_ID nil]]]}
     {:db (assoc-in db [:import-csv :profile-id] profile-id)
      :fx [[:bg-csv-import-profile-mapping [profile-id (get-in db [:import-csv :headers])]]]})))

(reg-fx
 :bg-csv-import-profile-mapping
 (fn [[profile-id headers]]
   (bg-csv/csv-import-profile-mapping
    profile-id headers
    (fn [api-response]
      (when-let [detected-profile (check-error api-response)]
        (dispatch [:import-csv-mapping-suggested profile-id detected-profile]))))))

(reg-event-fx
 :import-csv-mapping-suggested
 (fn [{:keys [db]} [_event-id profile-id {:keys [mapping consumed-columns]}]]
   (let [display-name (->> (get-in db [:import-csv :profiles])
                           (filter (fn [{:keys [id]}] (= id profile-id)))
                           first
                           :display-name)]
     ;; assoc-in rather than a deep merge - the mapping options are a vector and have to
     ;; be replaced wholesale, not merged element by element
     {:db (assoc-in db [:import-csv :consumed-columns] consumed-columns)
      :fx [[:dispatch [:generic-dialog-update :csv-columns-mapping-dialog
                       [[:data :mapping-options] (with-suggested-mapping mapping)]]]
           [:dispatch [:generic-dialog-update :csv-columns-mapping-dialog
                       [[:data :profile-id] profile-id]]]
           [:dispatch [:generic-dialog-update :csv-columns-mapping-dialog
                       [[:data :profile-display-name] display-name]]]
           [:dispatch [:generic-dialog-update :csv-columns-mapping-dialog
                       [[:api-error-text] nil]]]]})))

(reg-event-fx
 :import-csv-mapped
 (fn [{:keys [db]} [_event-id mapping-options]]
   (let [headers (get-in db [:import-csv :headers])

         ;; The standard fields mapped to headers
         mapped-fields (filter (fn [{:keys [mapped-name] :as m}] (when (not= mapped-name NOT_PRESENT) m)) mapping-options)
         matched-count (count mapped-fields)

         ;; Unique headers considered for mapping
         mapped-headers (->> mapped-fields (map (fn [m] (:mapped-name m))) set)

         ;; Columns the detected profile reads itself. They never appear in the mapping
         ;; grid, so without this they would look unmapped and could be imported as a
         ;; custom field on every entry - the item type column in particular, which has
         ;; already been used to choose the entry type
         consumed-columns (set (get-in db [:import-csv :consumed-columns]))

         ;; Unique headers not yet considered for mapping
         not-mapped-headers (cset/difference (set headers) mapped-headers consumed-columns)

         ;; Title should be mapped
         title-matched (filter (fn [{:keys [field-name mapped-name] :as m}] (when (and (= field-name const/TITLE) (not= mapped-name NOT_PRESENT)) m)) mapping-options)

         {:keys [db-key file-name]} (current-opened-db db)
         db-locked? (locked? db-key)]
     (cond

       (= matched-count 0)
       {:fx [[:dispatch [:generic-dialog-set-api-error :csv-columns-mapping-dialog "No mapping is done"]]]}

       (empty? title-matched)
       {:fx [[:dispatch [:generic-dialog-set-api-error :csv-columns-mapping-dialog "Title mapping is not done"]]]}

       :else
       {:db (-> db (assoc-in [:import-csv :mapping-result] {:mapped-fields mapped-fields

                                                            :mapped-headers (vec mapped-headers)
                                                            :not-mapped-headers (vec not-mapped-headers)
                                                            :db-key db-key
                                                            :unmapped-custom-field false
                                                            :current-db-file-name file-name
                                                            :db-locked db-locked?}))
        :fx [#_[:dispatch [:generic-dialog-close :csv-columns-mapping-dialog]]
             [:dispatch [:generic-dialog-show :csv-mapping-completed-dialog]]]}))))

(reg-event-fx
 :import-csv-map-custom-field
 (fn [{:keys [db]} [_event-id checked?]]
   {:db (-> db (assoc-in [:import-csv :mapping-result :unmapped-custom-field] checked?))}))

;; Removes a header field from considering it as a custom field
(reg-event-fx
 :import-csv-unmapped-header-field-delete
 (fn [{:keys [db]} [_event-id header-field]]
   (let [unmapped-headers (get-in db [:import-csv :mapping-result :not-mapped-headers])
         unmapped-headers (filterv (fn [h] (not= h header-field)) unmapped-headers)]
     {:db (-> db (assoc-in [:import-csv :mapping-result :not-mapped-headers] unmapped-headers))})))

;; Called when user wants to create a new database with imported csv data. 
;; This launches the standard "New Database" dialog 
(reg-event-fx
 :import-csv-new-database
 (fn [{:keys [_db]} [_event-id]]
   {:fx [;; Need to close dialogs
         [:dispatch [:generic-dialog-close :csv-mapping-completed-dialog]]
         [:dispatch [:generic-dialog-close :csv-columns-mapping-dialog]]
         ;; Launches the usual new database creation dialog
         [:dispatch [:new-database/dialog-show true]]]}))

(defn- to-mapping [db]
  (let [headers (get-in db [:import-csv :headers])
        ;; nil for a generic csv. It tells the backend the group path separator and the
        ;; item type column to use, neither of which the column mapping can express
        profile-id (get-in db [:import-csv :profile-id])
        {:keys [mapped-fields not-mapped-headers unmapped-custom-field]} (get-in db [:import-csv :mapping-result])]
    (as-map [headers mapped-fields not-mapped-headers unmapped-custom-field profile-id])))

;; Called after user enter details for new databse creation in the "New Database" dialog and clicks done button
(reg-event-fx
 :import-file/new-database
 (fn [{:keys [db]} [_event-id new-db on-database-creation-completed]]
   (let [;;  headers (get-in db [:import-csv :headers])
         ;;  {:keys [mapped-fields not-mapped-headers unmapped-custom-field]} (get-in db [:import-csv :mapping-result])
         ;;  mapping (as-map [headers mapped-fields not-mapped-headers unmapped-custom-field])
         mapping (to-mapping db)]

     {:fx [[:bg-create-new-db-with-imported-csv [new-db mapping on-database-creation-completed]]]})))

(reg-fx
 :bg-create-new-db-with-imported-csv
 (fn [[new-db mapping on-database-creation-completed]]
   (bg-csv/create-new-db-with-imported-csv new-db mapping on-database-creation-completed)))

(reg-event-fx
 :import-csv-into-current-db
 (fn [{:keys [db]} [_event-id]]
   (let [db-key (get-in db [:import-csv :mapping-result :db-key])
         mapping (to-mapping db)]
     {:fx [[:bg-update-db-with-imported_csv [db-key mapping]]]})))

(reg-fx
 :bg-update-db-with-imported_csv
 (fn [[db-key mapping]]
   (bg-csv/update-db-with-imported-csv db-key mapping
                                       (fn [api-response]
                                         (when-not (on-error api-response)
                                           (dispatch [:generic-dialog-close :csv-mapping-completed-dialog])
                                           (dispatch [:generic-dialog-close :csv-columns-mapping-dialog])
                                           (dispatch [:common/reload-on-merge])
                                           (dispatch [:import-csv/clear])
                                           (dispatch [:common/message-snackbar-open
                                                      (lstr-sm 'importCompleted)]))))))

(defn import-csv-clear []
  (dispatch [:import-csv/clear]))

(reg-event-db
 :import-csv/clear
 (fn [db [_event-id]]
   (let [import-csv (get-in db [:import-csv])]
     (if-not (nil? import-csv)
       (do
         ;; Side effect call
         (bg-csv/clear-csv-data-cache (fn [api-response] (on-error api-response)))
         (-> db (assoc-in [:import-csv] nil)))
       db))))

(reg-sub
 :import-csv-mapping-result
 (fn [db _query-vec]
   (get-in db [:import-csv :mapping-result])))

(comment
  (-> @re-frame.db/app-db keys))
