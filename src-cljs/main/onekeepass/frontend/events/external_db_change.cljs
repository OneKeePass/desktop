(ns onekeepass.frontend.events.external-db-change
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.background.merging :as bg-merging]
   [onekeepass.frontend.constants :as const :refer [MERGE_FAILED_CREDENTIALS_CHANGED]]
   [onekeepass.frontend.events.common :refer [active-db-key
                                              check-error
                                              get-in-key-db
                                              locked?]]
   [onekeepass.frontend.translation :refer-macros [tr-dlg-title tr-dlg-text] :refer [lstr-sm]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))

;;;;;;;;;;;; External DB Change - file watcher events ;;;;;;;;;;;;


(defn external-change-merge-start [db-key]
  (dispatch [:external-change-merge-start db-key]))

(defn external-change-reload-start [db-key]
  (dispatch [:external-change-reload-start db-key]))

(defn external-change-ignore [db-key]
  (dispatch [:external-change-ignore db-key]))

;; Triggered by the Tauri DB_FILE_CHANGED_EVENT.
;; Routes based on whether the changed DB is the active one and whether it is locked.
;;   - Active + unlocked  -> show dialog immediately
;;   - Active + locked    -> store :external-change-pending flag; picked up after unlock
;;   - Non-active tab     -> store flag; picked up when user switches to that tab
(reg-event-fx
 :external-db-change/db-file-changed-externally
 (fn [{:keys [db]} [_event-id db-key]]
   (cond
     (and (= db-key (active-db-key db)) (not (locked? db)))
     {:fx [[:dispatch [:show-external-db-change-dialog db-key]]]}

     :else
     {:db (assoc-in db [db-key :external-change-pending] true)
      :fx [[:dispatch [:common/message-snackbar-open
                       (lstr-sm 'externalChangePending
                                {:file-name (-> db-key (str/split #"/") last)})]]]})))

;; Called after unlock or tab switch to check if a watcher-flagged change is waiting
(reg-event-fx
 :external-db-change/check-external-change-pending
 (fn [{:keys [db]} [_event-id db-key]]
   (if (get-in db [db-key :external-change-pending])
     {:db (assoc-in db [db-key :external-change-pending] false)
      :fx [[:dispatch [:show-external-db-change-dialog db-key]]]}
     {})))

(reg-event-fx
 :show-external-db-change-dialog
 (fn [{:keys [db]} [_event-id db-key]]
   {:fx [[:dispatch [:generic-dialog-show-with-state
                     :external-db-change-dialog
                     {:data {:db-key db-key
                             :save-pending (get-in-key-db db [:db-modification :save-pending])}}]]]}))

;; User chose "Merge"
(reg-event-fx
 :external-change-merge-start
 (fn [{:keys [_db]} [_event-id db-key]]
   {:fx [[:dispatch [:generic-dialog-close :external-db-change-dialog]]
         [:dispatch [:common/progress-message-box-show
                     (tr-dlg-title "mergingExternalChanges")
                     (tr-dlg-text "mergingExternalChangesTxt")]]
         [:bg-merge-kdbx-with-disk-version [db-key]]]}))

(reg-fx
 :bg-merge-kdbx-with-disk-version
 (fn [[db-key]]
   (bg-merging/merge-kdbx-with-disk-version
    db-key
    (fn [api-response]
      (dispatch [:common/progress-message-box-hide])
      (when-some [merge-result (check-error api-response
                                            #(dispatch [:external-change-merge-error %]))]
        (dispatch [:external-change-merge-completed merge-result]))))))

(reg-event-fx
 :external-change-merge-completed
 ;; After merge we always show AllEntries so the user can see all entries (both
 ;; unchanged and merged ones) regardless of which category/group was active before.
 (fn [{:keys [db]} [_event-id merge-result]]
   {:fx [[:dispatch [:load-all-tags]]
         [:dispatch [:entry-form-ex/show-welcome]]
         [:dispatch [:group-tree-content/load-groups]]
         [:dispatch [:entry-category/category-data-load-start
                     (-> db :app-preference :default-entry-category-groupings)]]
         [:dispatch [:common/load-entry-type-headers]]
         [:dispatch [:entry-list/load-entry-items const/CATEGORY_ALL_ENTRIES]]
         ;; Highlight "All Entries" in the category panel and deselect any group node
         [:dispatch [:entry-category/select-all-entries-category]]
         [:dispatch [:group-tree-content/clear-group-selection]]
         [:dispatch [:generic-dialog-show-with-state :merge-result-dialog {:data merge-result}]]]}))

(reg-event-fx
 :external-change-merge-error
 (fn [{:keys [_db]} [_event-id error]]
   (if (= error MERGE_FAILED_CREDENTIALS_CHANGED)
     {:fx [[:dispatch [:common/error-info-box-show
                       {:title (tr-dlg-title "mergeNotPossible")
                        :message (tr-dlg-text "mergeNotPossibleCredentialsChangedTxt")}]]]}
     {:fx [[:dispatch [:common/error-info-box-show {:title (tr-dlg-title "mergeFailed") :message error}]]]})))

;; User chose "Reload"
(reg-event-fx
 :external-change-reload-start
 (fn [{:keys [db]} [_event-id db-key]]
   {:db (assoc-in db [db-key] nil)
    :fx [[:dispatch [:generic-dialog-close :external-db-change-dialog]]
         [:dispatch [:common/progress-message-box-show
                     (tr-dlg-title "reloadingDatabase")
                     (tr-dlg-text "reloadingFromDiskTxt")]]
         [:bg-acknowledge-and-reload [db-key]]]}))

(reg-fx
 :bg-acknowledge-and-reload
 (fn [[db-key]]
   ;; Acknowledge clears notification_pending in the backend, then reload
   (bg/acknowledge-db-file-change
    db-key
    (fn [_]
      (bg/reload-kdbx
       db-key
       (fn [api-response]
         (dispatch [:common/progress-message-box-hide])
         (when-let [kdbx-loaded (check-error api-response
                                             #(dispatch [:reload-database-error %]))]
           (dispatch [:common/kdbx-database-loading-complete kdbx-loaded]))))))))

;; User chose "Ignore"
(reg-event-fx
 :external-change-ignore
 (fn [{:keys [_db]} [_event-id db-key]]
   {:fx [[:dispatch [:generic-dialog-close :external-db-change-dialog]]
         [:bg-acknowledge-db-change [db-key]]
         [:dispatch [:common/message-box-show
                     (tr-dlg-title "externalDbChangedIgnored")
                     (tr-dlg-text "externalDbChangedIgnoredTxt")]]]}))

(reg-fx
 :bg-acknowledge-db-change
 (fn [[db-key]]
   (bg/acknowledge-db-file-change db-key #())))