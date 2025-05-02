(ns onekeepass.frontend.events.merging
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             on-error
                                                             active-db-key
                                                             current-opened-db
                                                             active-db-file-name]]
   [onekeepass.frontend.background-merging :as bg-merging]
   [onekeepass.frontend.background :as bg]))

(defn open-dbs-start []
  (dispatch [:merging/open-dbs-start]))

(reg-event-fx
 :merging/open-dbs-start
 (fn [{:keys [db]}  [_event-id]]
   (if (nil? (active-db-key db))
     {:fx [[:dispatch [:common/message-snackbar-error-open "No database is opened"]]]}
     {:fx [[:dispatch [:open-db-form/open-db true]]]})))

(reg-event-fx
 :merging/credentials-entered
 (fn [{:keys [db]} [_event-id source-db-fkey pwd key-file-name]] 
   (let [target-db-fkey (active-db-key db)]
     (if-not (nil? target-db-fkey)
       {:fx [[:bg-merge-databases [(active-db-key db) source-db-fkey  pwd key-file-name]]]}
       {:fx [[:dispatch [:common/message-snackbar-error-open "No database is opened"]]]}))))

(reg-fx
 :bg-merge-databases
 (fn [[target-db-key source-db-key  password key-file-name]]
   (bg-merging/merge-databases target-db-key source-db-key  password key-file-name
                               (fn [api-response] 
                                 (when-some [merge-result (check-error api-response #(dispatch [:open-db/db-merge-error %]))]
                                   (println "merge-result is " merge-result)
                                   (dispatch [:merge-databases-completed]))
                                 #_(when-not (on-error api-response #(dispatch [:open-db/db-merge-error %]))
                                     (dispatch [:merge-databases-completed])
                                     #_(dispatch [:open-db/dialog-hide])
                                     #_(dispatch [:common/refresh-forms])
                                     #_(dispatch [:common/message-snackbar-open
                                                  "Database merging is completed"]))))))
;;[:bg-reload-kdbx [(active-db-key db)]]
(reg-event-fx
 :merge-databases-completed
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:open-db/dialog-hide]]
         [:dispatch [:common/message-snackbar-open "Database merging is completed"]]
         [:dispatch [:common/reload-on-merge]]
         #_[:dispatch [:common/refresh-forms]]
         #_[:bg-reload-kdbx [(active-db-key db)]]]}))

(comment
  (-> @re-frame.db/app-db keys)

  (def db-key (:current-db-file-name @re-frame.db/app-db))

  (-> @re-frame.db/app-db (get db-key) keys))