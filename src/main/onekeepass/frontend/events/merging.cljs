(ns onekeepass.frontend.events.merging
  (:require
   [re-frame.core :refer [reg-event-fx reg-fx dispatch]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             active-db-key]]
   [onekeepass.frontend.background.merging :as bg-merging]))
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


(comment
  (-> @re-frame.db/app-db keys)

  (def db-key (:current-db-file-name @re-frame.db/app-db))

  (-> @re-frame.db/app-db (get db-key) keys))