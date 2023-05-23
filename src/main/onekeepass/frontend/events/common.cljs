(ns onekeepass.frontend.events.common
  "All common events that are used across many pages"
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-sub
                          dispatch
                          dispatch-sync
                          subscribe]]
   [clojure.string :as str]
   [cljs.core.async :refer [go-loop timeout <!]]
   [onekeepass.frontend.constants :refer [ADD_TAG_PREFIX]]
   [onekeepass.frontend.utils :refer [contains-val? str->int utc-to-local-datetime-str]]
   [onekeepass.frontend.background :as bg]))

(declare check-error)
(declare assoc-in-key-db)


(defn sync-initialize
  "Called just before rendering to set all requied values in re-frame db"
  []
  ;;(re-frame.core/dispatch-sync [:initialise-db]) 
  (dispatch-sync [:custom-icons/load-custom-icons])
  (dispatch-sync [:load-system-info-with-preference]))

(defn open-file-explorer-on-click [kw-dispatch-name]
  (bg/open-file-dialog (fn [api-response]
                         (let [file-name (check-error api-response)]
                           (dispatch [kw-dispatch-name file-name])))))

;;;;;;;;;;;;;;;;;;;;;;;; One time System Info loading ;;;;;;;;;;;;;;;;;;;;

(declare set-session-timeout)

(defn new-db-full-file-name [app-db db-name]
  (let [document-dir (-> app-db :standard-dirs :document-dir)
        path-sep (:path-sep app-db)]
    (str document-dir path-sep "OneKeePass" path-sep db-name ".kdbx")))

(defn recent-files []
  (subscribe [:recent-files]))

(reg-event-db
 :load-system-info-with-preference
 (fn [db [_event-id]]
   (bg/system-info-with-preference
    (fn [api-response]
      (when-let [sys-info (check-error api-response)]
        (dispatch [:load-system-info-with-preference-complete sys-info]))))
   db))

(reg-event-fx
 :load-system-info-with-preference-complete
 (fn [{:keys [db]} [_event-id {:keys [standard-dirs os-name path-sep preference]}]]
   (set-session-timeout (:session-timeout preference))
   ;;(println "document-dir os-name path-sep preference " document-dir os-name path-sep preference)
   {:db (-> db (assoc :app-preference preference)
            (assoc :standard-dirs standard-dirs)
            (assoc :path-sep path-sep)
            (assoc :os-name os-name))}))

(reg-event-db
 :common/load-app-preference
 (fn [db [_event-id]]
   (bg/read-app-preference
    (fn [api-response]
      (when-let [preference (check-error api-response)]
        (dispatch [:load-app-preference-complete preference]))))
   db))

(reg-event-fx
 :load-app-preference-complete
 (fn [{:keys [db]} [_event-id preference]]
   {:db (assoc db :app-preference preference)}))

(reg-sub
 :recent-files
 :<- [:app-preference]
 (fn [pref _query-vec]
   (:recent-files pref)))

(reg-sub
 :app-preference
 (fn [db _query-vec]
   (:app-preference db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn db-opened
  "Updates the db list and current active db key when a new kdbx database is loaded
  The args are the re-frame 'app-db' and KdbxLoaded struct returned by backend API.
  Returns the updated app-db
  "
  [app-db {:keys [db-key database-name]}] ;;kdbx-loaded
  (let [app-db  (if (nil? (:opened-db-list app-db)) (assoc app-db :opened-db-list []) app-db)]
    (-> app-db
        (assoc :current-db-file-name db-key)
         ;; TODO: Need to avoid duplicate entries for the same db-key. 
         ;;       This should be done in open db dialog validation itself (?)
        (update-in [:opened-db-list] conj {:db-key db-key
                                           :database-name database-name
                                           :user-action-time (js/Date.now)
                                           ;;:database-name (:database-name meta)
                                           }))))

;; Called after creating a new database or after opening an existing database
(reg-event-fx
 :common/kdbx-database-opened
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (db-opened db kdbx-loaded)
    :fx [[:dispatch [:load-all-tags]]
         [:dispatch [:group-tree-content/load-groups]]
         [:dispatch [:entry-category/category-data-load-start (-> db :app-preference :default-entry-category-groupings)]]
         [:dispatch [:common/load-entry-type-headers]]
         [:dispatch [:common/message-snackbar-open (str "Opened database " (:db-key kdbx-loaded))]]]}))

(reg-event-fx
 :common/kdbx-database-unlocked
 (fn [{:keys [db]} [_event-id _kdbx-loaded]]
   ;;(println "kdbx-loaded in kdbx-database-opened" kdbx-loaded)
   {:db (assoc-in-key-db db [:locked] false)
    :fx [[:dispatch [:load-all-tags]]
         [:dispatch [:group-tree-content/load-groups]]
         [:dispatch [:entry-category/category-data-load-start (-> db :app-preference :default-entry-category-groupings)]]
         [:dispatch [:common/load-entry-type-headers]]
         [:dispatch [:common/show-content :group-entry]]]}))

;; A common refresh all forms after an entry form changes - delete, put back , delete permanent
(reg-event-fx
 :common/refresh-forms
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:entry-form-ex/show-welcome]]
         [:dispatch [:group-tree-content/load-groups]]
         [:dispatch [:entry-category/show-groups-as-tree-or-category]]
         [:dispatch [:entry-list/entry-updated]]]}))

;; A common refresh all forms after an entry form changes - delete, put back , delete permanent
(reg-event-fx
 :common/refresh-forms-2
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:entry-form-ex/show-welcome]]
         [:dispatch [:group-tree-content/load-groups]]
         [:dispatch [:entry-category/show-groups-as-tree-or-category]]
         [:dispatch [:entry-list/clear-entry-items]]]}))

(defn active-db-key
  ;; To be called only in react components as it used 'subscribe' (i.e in React context)
  ([]
   (subscribe [:current-db-file-name]))
  ;; Used in reg-event-db , reg-event-fx by passing the main re-frame global 'app-db' 
  ([app-db]
   (:current-db-file-name app-db)))

;; db-file-name is the same as db-key
(def active-db-file-name active-db-key)

(defn set-active-db-key
  "Sets the new current active db"
  [db-key]
  (dispatch [:set-active-db-key db-key]))

(defn opened-db-list
  "Gets an atom to get all opened db summary list if app-db is not passed"
  ([]
   (subscribe [:opened-db-list]))
  ([app-db]
   (:opened-db-list app-db)))

(defn opened-db-keys
  "Gets the list of db keys of all opened dbs in UI"
  [app-db]
  (mapv (fn [m] (:db-key m)) (:opened-db-list app-db)))

(defn is-in-opend-db-list [db-file-name db-list]
  ;; Using (boolean (seq a)) instead of (not (empty? a))
  (-> (filter (fn [m] (= (:db-key m) db-file-name)) db-list) seq boolean))

(defn assoc-in-key-db
  "Called to associate the value in 'v' to the keys 'ks' location 
  in the db for the currently selected db key and then updates the main db 
  with this new key db
  Returns the main db 
  "
  [app-db ks v]
  ;;Get current db key and update that map with v in ks
  ;;Then update the main db
  (let [kdbx-db-key (active-db-key app-db)
        kdb (get app-db kdbx-db-key)]
    (assoc app-db kdbx-db-key (assoc-in kdb ks v))))

(defn get-in-key-db
  "Gets the value for the key lists from an active kdbx content"
  [app-db ks]
  ;; First we get the kdbx content map and then supplied keys 'ks' used to get the actual value
  (get-in app-db (into [(active-db-key app-db)] ks)))

(defn on-error
  "A common error handler for the background API call.
  logs the error and returns true in case of error or false
  "
  ([{:keys [error]} error-fn]
   (if-not (nil? error)
     (do
       (if  (nil? error-fn)
         #_(println "API returned error: " error)
         ;; Should we create a gedneric error dialog instead of snackbar ?
         (dispatch [:common/message-snackbar-error-open error])
         (error-fn error))
       true)
     false))
  ([api-response]
   (on-error api-response nil)))

(defn check-error
  "Receives a map with keys result and error or either one.
  Returns the value of result in case there is no error
  "
  ([{:keys [result error]} error-fn]
   (if-not (nil? error)
     (do
       (if (nil? error-fn)
         #_(println "API returned error: " error)
         (dispatch [:common/message-snackbar-error-open error])
         (error-fn error))
       nil)
     result))
  ([api-response]
   (check-error api-response nil)))

(defn save-as
  "Called when user wants to save a modified db to another name"
  []
  (dispatch [:common/save-db-file-as]))

(defn- on-save-as [m]
  (when-let [new-db-key (check-error m)]
    (dispatch [:save-as-completed new-db-key])))

(reg-event-db
 :common/save-db-file-as
 (fn [db [_event-id]]
   (let [{:keys [current-db-file-name path-sep]} db
         ;; Extracts the file name from  the database full file path 
         ;; "/Users/jjklsdf/Documents/OneKeePass/Mypasswords.kdbx" to Mypasswords
         save-as-file-name (-> current-db-file-name
                               (str/split (re-pattern
                                           (str path-sep)))
                               last (str/split #"\.") first)
         ;; Appends timestamp and add back the extension 
         ;; Mypasswords to  Mypasswords-2023-05-12 16 36 30.kdbx
         save-as-file-name (if (= save-as-file-name current-db-file-name)
                             "Mypassword.kdbx"
                             (str save-as-file-name
                                  "-"
                                  (utc-to-local-datetime-str (js/Date.) "yyyy-MM-dd HH mm ss")
                                  ".kdbx"))
         f (fn [file-name]
            ;;(println "file-name is " file-name)
             (when-not (nil? file-name)
               (bg/save-as-kdbx (active-db-key db) file-name on-save-as)))]
    ;; Pass something similar {:default-path (str database-name ".kdbx")} to save as done in New database dialog
    ;; Now the user is presented with the default "Untitled"
     (bg/save-file-dialog {:default-path save-as-file-name} f)

     db)))

(reg-event-fx
 :save-as-completed
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   (let [old-key (:current-db-file-name db)]
     {:fx [[:dispatch [:close-kdbx-completed old-key]]
           [:dispatch [:common/kdbx-database-opened kdbx-loaded]]]})))

(reg-event-fx
 :common/close-kdbx-db
 (fn [{:keys [_db]}  [_event-id db-key]]
   (bg/close-kdbx db-key
                  (fn [api-response]
                    (when-not (on-error api-response)
                      (dispatch [:close-kdbx-completed db-key]))))
   {}))

;; This event will clean up the local db list and set the next current active db
;; The backend db is closed in :common/close-kdbx-db event
(reg-event-fx
 :close-kdbx-completed
 (fn [{:keys [db]} [_event-id db-key]]
   (let [;; Remove the closed db-key summary map from list
         dbs (filterv (fn [m] (not= (:db-key m) db-key)) (:opened-db-list db))
         ;; For now make the last db if any as the active one  
         next-active-db-key (if (empty? dbs) nil (:db-key (last dbs)))]
     {:db (-> db
              (assoc :opened-db-list dbs)
              (assoc :current-db-file-name next-active-db-key)
              (dissoc db-key))
      :fx [[:dispatch [:common/load-app-preference]]]})))



;;;;;;;;;;;;;;;;;;;;;;;;;;;; DB Lock/Unlock ;;;;;;;;;;;;;;;;;;;;;

(defn locked? []
  (subscribe [:common/current-db-locked]))

(defn unlock-current-db []
  (dispatch [:open-db-form/dialog-show-on-current-db-unlock-request]))

(reg-event-fx
 :common/lock-current-db
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in-key-db db [:locked] true)
    :fx [[:dispatch [:common/show-content :locked-content]]]}))

(reg-sub
 :common/current-db-locked
 (fn [db _query-vec]
   (boolean (get-in-key-db db [:locked]))))

;;;;;; Tags Related Begin ;;;;;;;;;;

;;;Need to decide whether this event holder needs to be some other events/*.cljs file

(defn add-tag [tag]
  (dispatch [:add-tag tag]))

(defn all-tags []
  (subscribe [:all-tags]))

(def pat1 (re-pattern (str ADD_TAG_PREFIX ".*")))

;; will give something like  #"Add\s"
(def pat2 (re-pattern (str ADD_TAG_PREFIX "\\s")))

;; Also see onekeepass.frontend.common-components/tags-filter-options where the prefix
;; ADD_TAG_PREFIX is added and here we remove
;; Note: 
;; If the user adds a tag with exact name as ADD_TAG_PREFIX, this will break
;; Needs fixing to take care of such situations
(defn fix-tags-selection-prefix
  "Called on selecting one or more tags on the entry form"
  [tags]
  (let [ts (js->clj tags)
        ;;We need to remove prefix ADD_TAG_PREFIX for any new tag added 
        ts (mapv (fn [v]
                   (let [t (re-matches pat1 v)]
                     ;; t starts with ADD_TAG_PREFIX or not
                     (if (nil? t)
                       v
                       (let [nv (str/replace v pat2 "")]
                         ;; Add this new tag to all-tags
                         (add-tag nv) ;;side effect?
                         nv)))) ts)]
    ts))

(reg-event-db
 :add-tag
 (fn [db [_event-id tag]]
   (let [tags (get-in-key-db db [:tags :all])]
     (assoc-in-key-db db [:tags :all] (conj tags tag)))))

(reg-event-db
 :load-all-tags
 (fn [db [_event-id]]
   (bg/collect-entry-group-tags
    (active-db-key db)
    (fn [api-response]
      (when-let [result (check-error api-response)]
        (dispatch [:load-all-tags-completed result]))))
   db))

(reg-event-db
 :load-all-tags-completed
 (fn [db [_event-id result]]
   (assoc-in-key-db db
                    [:tags :all]
                    (into []
                          (concat (:entry-tags result)
                                  (:group-tags result))))))

;; Should we exclude "Favourites" tag from showing in all-tags ?
(reg-sub
 :all-tags
 (fn [db _query-vec]
   (get-in-key-db db [:tags :all])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Entry types ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn all-entry-type-headers
  ;; Returns an atom that holds list of all entry names and meant to be used in UI 
  ([]
   (subscribe [:all-entry-type-headers]))
  ([app-db]
   (let [{:keys [standard custom]} (get-in-key-db app-db [:entry-type-headers])]
     (vec (concat standard custom)))))

(defn is-custom-entry-type [entry-type-uuiid]
  (subscribe [:is-custom-entry-type-uuid entry-type-uuiid]))

;; Needs to be called during initial loading and also whenever new custom type
;; is added.
(reg-event-db
 :common/load-entry-type-headers
 (fn [db [_event-id]]
   ;; entry-type-headers call gets a map formed by struct EntryTypeHeaders
   (bg/entry-type-headers
    (active-db-key db)
    (fn [api-response]
      (when-let [et-headers-m (check-error api-response)]
        (dispatch [:load-entry-type-headers-completed et-headers-m]))))
   db))

(reg-event-db
 :load-entry-type-headers-completed
 (fn [db [_event-id et-headers-m]]
   (assoc-in-key-db db [:entry-type-headers] et-headers-m)))

;; Gets a map formed by struct EntryTypeHeaders
(reg-sub
 :entry-type-headers
 (fn [db _query-vec]
   (get-in-key-db db [:entry-type-headers])))

(reg-sub
 :all-entry-type-headers
 :<- [:entry-type-headers]
 (fn [{:keys [standard custom]} _query-vec]
   (vec (concat standard custom))))

(reg-sub
 :is-custom-entry-type-uuid
 :<- [:entry-type-headers]
 (fn [{:keys [custom]} [_query-id entry-type-uuid]]
   ;; custom field is a vector with a list of  maps (EntryTypeHeader struct)
   ;; One matching member in custom vector need to be found to return true
   ;; We can use (boolean seq coll) to determine true or false or alternative we can use 
   ;; (->> custom (filter (fn [m] (= entry-type-uuid (:uuid m)))) count (= 1))
   (->> custom (filter (fn [m] (= entry-type-uuid (:uuid m)))) seq boolean)))

;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Start Page related ;;;;;;;;;;;;;;;

(defn show-start-page []
  (subscribe [:start-page]))

(reg-sub
 :start-page
 (fn [db _query-vec]
   (if (:current-db-file-name db) false true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Start Page End ;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; ;; Put an initial value into app-db.
;; ;; Using the sync version of dispatch means that value is in
;; ;; place before we go onto the next step.
;; ;;Need to add the following dispatch-sync in 'core.cljs' top 
;; ;;During development we can call this in the REPL to initialize the app-db
;; ;;(re-frame.core/dispatch-sync [:initialise-db]) 

(reg-event-db
 :initialise-db
 (fn [_db _event]              ;; Ignore both params (db and event)
   {:opened-db-list []}))

(reg-event-db
 :set-active-db-key
 (fn [db [_event-id db-key]]
   (assoc db :current-db-file-name db-key)))

(reg-sub
 :current-db-file-name
 (fn [db _query-vec]
   (:current-db-file-name db)))

(reg-sub
 :opened-db-list
 (fn [db _query-vec]
   (-> db :opened-db-list)))

;;;;;;;;;;;;;;; Message dialog ;;;;;;;;;;;;;

;; TODO:  
;; Need to see how effectively the message-box is used and use more or remove

(defn show-message
  [title message]
  (dispatch [:common/message-box-show title message]))

(defn close-message-dialog []
  (dispatch [:message-box-hide]))

(defn message-dialog-data []
  (subscribe [:message-box]))

(reg-event-db
 :common/message-box-show
 (fn [db [_event-id title message]]
   (-> db
       (assoc-in [:message-box :dialog-show] true)
       (assoc-in [:message-box :title] title)
       (assoc-in [:message-box :message] message))))

(reg-event-db
 :message-box-hide
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:message-box :dialog-show] false))))

(reg-sub
 :message-box
 (fn [db _query-vec]
   (-> db :message-box)))

;;;;;;;;;;;;;;;; error-info-dialog ;;;;;;;;;;;;;;;;;;;

(defn close-error-info-dialog []
  (dispatch [:error-info-box-hide]))

(defn error-info-dialog-data []
  (subscribe [:error-info-box]))

(reg-event-db
 :common/error-info-box-show
 (fn [db [_event-id title error-text message]]
   (-> db
       (assoc-in [:error-info-box :dialog-show] true)
       (assoc-in [:error-info-box :title] title)
       (assoc-in [:error-info-box :error-text] error-text)
       (assoc-in [:error-info-box :message] message))))

(reg-event-db
 :error-info-box-hide
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:error-info-box :dialog-show] false))))

(reg-sub
 :error-info-box
 (fn [db _query-vec]
   (-> db :error-info-box)))

;;;;;;;;;;;;;;;;;;;;; Common snackbar ;;;;;;;;;;;;;;;;

;; See message-sanckbar and message-sanckbar-alert in onekeepass.frontend.common-components
;; TODO: 
;; Need to combine message-sanckbar and message-sanckbar-alert


(defn close-message-snackbar []
  (dispatch [:message-snackbar-close]))

(defn message-snackbar-data []
  (subscribe [:message-snackbar-data]))

(reg-event-db
 :common/message-snackbar-open
 (fn [db [_event-id message]]
   (-> db
       (assoc-in [:message-snackbar-data :open] true)
       (assoc-in [:message-snackbar-data :message] message))))

(reg-event-db
 :message-snackbar-close
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:message-snackbar-data :open] false))))

(reg-sub
 :message-snackbar-data
 (fn [db _query-vec]
   (-> db :message-snackbar-data)))


(defn close-message-snackbar-alert []
  (dispatch [:message-snackbar-alert-close]))

(defn message-snackbar-alert-data []
  (subscribe [:message-snackbar-alert-data]))

#_(reg-event-db
   :common/message-snackbar-alert-open
   (fn [db [_event-id message severity]]
     (-> db
         (assoc-in [:message-snackbar-alert-data :open] true)
         (assoc-in [:message-snackbar-alert-data :severity] (if (nil? severity) "info" severity))
         (assoc-in [:message-snackbar-alert-data :message] message))))

(reg-event-db
 :common/message-snackbar-error-open
 (fn [db [_event-id message]]
   (-> db
       (assoc-in [:message-snackbar-alert-data :open] true)
       (assoc-in [:message-snackbar-alert-data :severity] "error")
       (assoc-in [:message-snackbar-alert-data :message] message))))

(reg-event-db
 :message-snackbar-alert-close
 (fn [db [_event-id]]
   (-> db
       (assoc-in [:message-snackbar-alert-data :open] false))))

(reg-sub
 :message-snackbar-alert-data
 (fn [db _query-vec]
   (-> db :message-snackbar-alert-data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;; Db Modification tracking ;;;;;;

(def ^:private all-modiying-api-calls
  ["update_entry"
   "insert_entry"
   ;; Need to be removed after back end api 'update_entry' is refactored to use EntryFormData struct
   "update_entry_from_form_data"
   "insert_entry_from_form_data"
   "move_entry"
   "move_entry_to_recycle_bin"
   "remove_entry_permanently"
   "upload_entry_attachment"

   "update_group"
   "insert_group"
   "move_group"
   "mark_group_as_category"
   "move_group_to_recycle_bin"
   "remove_group_permanently"

   "insert-or-update-custom-entry-type"
   "delete-custom-entry-type"
   "set_db_settings"])

(defn db-save-pending?
  "Checks whether there is any unsaved changes for the current db
  If the app-db is passed, then checking is done and returns boolean
  If no argument is passed, then a subscription is returned to use in UI
  "
  ([app-db]
   ;;(println "db-save-pending? called with app-db " (get-in-key-db app-db [:db-modification :save-pending]))
   (get-in-key-db app-db [:db-modification :save-pending]))
  ([]
   (subscribe [:db-save-pending])))

(reg-event-fx
 :common/db-api-call-completed
 (fn [{:keys [db]} [_event-id api-name]]
   ;;(println "api-name is " api-name)
   (if (contains-val? all-modiying-api-calls api-name)
     {:db (assoc-in-key-db db [:db-modification :save-pending] true)}
     {})))

;; Receives the struct KdbxSaved
(reg-event-db
 :common/db-modification-saved
 (fn [db [_event-id {:keys [db-key database-name]}]] ;; event arg is kdbx-saved   
   (let [dbs (mapv (fn [m]
                     (if (= db-key (:db-key m))
                       (assoc m :database-name database-name)
                       m)) (:opened-db-list db))]
     (-> db (assoc-in [:opened-db-list] dbs)
         (assoc-in-key-db [:db-modification :save-pending] false)))))

(reg-sub
 :db-modification
 (fn [db _query-vec]
   (get-in-key-db db [:db-modification])))

(reg-sub
 :db-save-pending
 :<- [:db-modification]
 (fn [db-modification _query-vec]
   (:save-pending db-modification)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn content-to-show []
  (subscribe [:common/content-to-show]))

;; content-name-kw :group-entry or :entry-history or :locked-content
(reg-event-db
 :common/show-content
 (fn [db [_event-id content-name-kw]]
   ;;(println "common/show-content called with " content-name-kw)
   (assoc-in-key-db db [:show-content] content-name-kw)))

(reg-sub
 :common/content-to-show
 (fn [db _query-vec]
   (get-in-key-db db [:show-content])))


;;;;;;;;;;;;;;;;;;;;;  Session timeout ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def session-timeout (atom 600000))  ;; time in milli second

(defn set-session-timeout [time-in-minute]
  (let [in-timeout (str->int time-in-minute)
        in-timeout (if (nil? in-timeout) 10 in-timeout)
        in-timeout (* in-timeout 60 1000)]
    (reset! session-timeout in-timeout)))

(defn user-action-detected []
  (dispatch [:user-action-detected]))

;; An example of using window event. As this fires when user move mouse anywhere
;; we will be using  on-click event inside "main-content"
;; (defn init-user-action-listeners []
;;   (.addEventListener js/window "mousedown" user-action-detected))

;; This is used during dev time to stop the tick loop
;; Call (reset! continue-tick false) in repl
(def continue-tick (atom true))

;;The static Date.now() method returns the number of milliseconds elapsed since January 1, 1970 00:00:00 UTC

(defn init-session-timeout-tick
  "Needs to be called once to start the periodic ticking which is required for session timeout
  Called in the main init function
  "
  []
  (go-loop []
    ;; Every 30 sec, we send the tick
    (<! (timeout 30000))
    (dispatch [:check-db-list-to-lock (js/Date.now)])
    (when @continue-tick
      (recur))))

;; Called whenver user clicks on any part of the content of the current db
(reg-event-fx
 :user-action-detected
 (fn [{:keys [db]} []]
   (let [db-key (active-db-key db)
         dbs (mapv (fn [m]
                     (if (= db-key (:db-key m))
                       (assoc m :user-action-time (js/Date.now))
                       m)) (:opened-db-list db))]
     {:db (-> db (assoc-in [:opened-db-list] dbs))})))

;; Locks all openned dbs that are timeout
;; Called periodically in the go loop
(reg-event-fx
 :check-db-list-to-lock
 (fn [{:keys [db]} [_event-id tick]]
   (let [db (reduce (fn [db {:keys [db-key user-action-time]}]
                      ;; If the user is not active for more that 15 minutes, the screen is locked
                      (if  (> (- tick user-action-time) @session-timeout) ;; 2 min = 120000 , 5 min = 300000
                        ;; Need to update :locked :show-content of all dbs that are timed out
                        (-> db (assoc-in [db-key :locked] true)
                            (assoc-in [db-key :show-content] :locked-content))
                        db)) db (:opened-db-list db))]
     {:db db})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (-> @re-frame.db/app-db keys)
  (reset! continue-tick false)

  (def db-key (:current-db-file-name @re-frame.db/app-db))

  (-> @re-frame.db/app-db (get db-key) keys))