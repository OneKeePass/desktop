(ns onekeepass.frontend.events.merging
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             active-db-key]]
   [onekeepass.frontend.events.move-group-entry :as move-events]
   [onekeepass.frontend.background.merging :as bg-merging]))

;;;; Existing "Merge Database..." flow (opens a file from disk) ;;;;

(reg-event-fx
 :merging/open-dbs-start
 (fn [{:keys [db]}  [_event-id]]
   (if (nil? (active-db-key db))
     {:fx [[:dispatch [:common/message-snackbar-error-open "No database is opened"]]]}
     {:fx [[:dispatch [:open-db-form/open-db true]]]})))

(reg-event-fx
 :merging/credentials-entered
 (fn [{:keys [db]} [_event-id source-db-fkey pwd key-file-name]]
   (let [target-db-key (active-db-key db)]
     (if-not (nil? target-db-key)
       {:fx [[:bg-merge-databases [(active-db-key db) source-db-fkey  pwd key-file-name]]]}
       {:fx [[:dispatch [:common/message-snackbar-error-open "No database is opened"]]]}))))

(reg-fx
 :bg-merge-databases
 (fn [[target-db-key source-db-key password key-file-name]]
   (bg-merging/merge-databases target-db-key
                               source-db-key
                               ;; source-db's credentials
                               password
                               key-file-name
                               (fn [api-response]
                                 (when-some [merge-result (check-error api-response #(dispatch [:open-db/db-merge-error %]))]
                                   (dispatch [:merge-databases-completed merge-result]))))))

(reg-event-fx
 :merge-databases-completed
 (fn [{:keys [_db]} [_event-id merge-result]]
   {:fx [[:dispatch [:open-db/dialog-hide]]
         [:dispatch [:common/reload-on-merge]]
         ;; As we show this dialog with merge result, we do not call the usual ':common/message-snackbar-open'
         [:dispatch [:generic-dialog-show-with-state :merge-result-dialog {:data merge-result}]]]}))


;;;; "Merge Opened Databases" flow (both databases already open) ;;;;

;; Public API fns for UI components

(defn multiple-unlocked-dbs?
  "Returns a subscription to the list of all unlocked open databases."
  []
  (move-events/unlocked-opened-dbs))

(defn merge-opened-dbs-dialog-data []
  (subscribe [:generic-dialog-data :merge-opened-dbs-dialog]))

(defn merge-opened-dbs-source-changed [db-key]
  (dispatch [:merging-merge-opened-dbs-source-changed db-key]))

(defn merge-opened-dbs-target-changed [db-key]
  (dispatch [:merging-merge-opened-dbs-target-changed db-key]))

(defn merge-opened-dbs-confirm []
  (dispatch [:merging-merge-opened-dbs-confirm]))

;; Events

(reg-event-fx
 :merging/merge-opened-dbs-start
 (fn [{:keys [db]} [_event-id]]
   (let [source-db-key (active-db-key db)]
     (if (nil? source-db-key)
       {:fx [[:dispatch [:common/message-snackbar-error-open "No database is opened"]]]}
       {:fx [[:dispatch [:generic-dialog-show-with-state :merge-opened-dbs-dialog
                         {:source-db-key source-db-key
                          :target-db-key nil}]]]}))))

(reg-event-db
 :merging-merge-opened-dbs-source-changed
 (fn [db [_event-id source-db-key]]
   (assoc-in db [:generic-dialogs :merge-opened-dbs-dialog :source-db-key] source-db-key)))

(reg-event-db
 :merging-merge-opened-dbs-target-changed
 (fn [db [_event-id target-db-key]]
   (assoc-in db [:generic-dialogs :merge-opened-dbs-dialog :target-db-key] target-db-key)))

(reg-event-fx
 :merging-merge-opened-dbs-confirm
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [source-db-key target-db-key]}
         (get-in db [:generic-dialogs :merge-opened-dbs-dialog])]
     (if (or (nil? source-db-key) (nil? target-db-key))
       {}
       {:fx [[:bg-merge-opened-dbs [source-db-key target-db-key]]]}))))

(reg-fx
 :bg-merge-opened-dbs
 ;; Both DBs already open — password/key-file not needed.
 ;; db_service.rs checks if source is already in the store and skips the file load.
 (fn [[source-db-key target-db-key]]
   (bg-merging/merge-databases target-db-key
                               source-db-key
                               nil
                               nil
                               (fn [api-response]
                                 (when-some [merge-result (check-error api-response)]
                                   (dispatch [:merging-merge-opened-dbs-completed
                                              merge-result target-db-key]))))))

(reg-event-fx
 :merging-merge-opened-dbs-completed
 ;; Switches the active tab to target so that subsequent effects (reload-on-merge,
 ;; and the queued db-api-call-completed that sets save-pending) all operate on
 ;; the correct database.
 (fn [{:keys [db]} [_event-id merge-result target-db-key]]
   {:db (assoc db :current-db-file-name target-db-key)
    :fx [[:dispatch [:generic-dialog-close :merge-opened-dbs-dialog]]
         [:dispatch [:common/reload-on-merge]]
         [:dispatch [:generic-dialog-show-with-state :merge-result-dialog {:data merge-result}]]]}))


(comment
  (-> @re-frame.db/app-db keys)

  (def db-key (:current-db-file-name @re-frame.db/app-db))

  (-> @re-frame.db/app-db (get db-key) keys))
