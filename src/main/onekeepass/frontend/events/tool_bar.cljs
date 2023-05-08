(ns onekeepass.frontend.events.tool-bar
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub
                          dispatch
                          subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             opened-db-keys
                                                             db-save-pending? 
                                                             check-error]]
   [onekeepass.frontend.background :as bg]))

(defn save-current-db []
  (dispatch [:save-current-db]))

(defn save-current-db-msg-dialog-hide []
  ;; Calling save-current-db-completed resets the status to :completed
  (dispatch [:save-current-db-completed nil]))

(defn save-current-db-data []
  (subscribe [:save-current-db-data]))

(reg-event-db
 :save-current-db
 (fn [db [_event-id]]
   (bg/save-kdbx (active-db-key db) (fn [api-response]
                                      (when-let [kdbx-saved (check-error api-response #(dispatch [:save-current-db-error %]))]
                                        (dispatch [:save-current-db-completed kdbx-saved])
                                        ;;Move this to  save-current-db-completed
                                        (dispatch [:common/db-modification-saved kdbx-saved]))))

   (-> db (assoc-in [:save-current-db :status] :in-progress)
       (assoc-in [:save-current-db :api-error-text] nil))))

(reg-event-db
 :save-current-db-completed
 (fn [db [_event-id _result]]
   (-> db (assoc-in [:save-current-db :status] :completed)
       (assoc-in [:save-current-db :api-error-text] nil))))

(reg-event-db
 :save-current-db-error
 (fn [db [_event-id error]]
   (assoc-in db [:save-current-db :api-error-text] error)))

(reg-sub
 :save-current-db-data
 (fn [db _query-vec]
   (:save-current-db db)))

;;;;;;;;;;;;;;;;;;;;;; Lock/Unlock db ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn lock-current-db []
  (dispatch [:common/lock-current-db]))

(defn unlock-current-db []
  (dispatch [:open-db-form/dialog-show-on-current-db-unlock-request]))

;;;;;;;;;;;;;;;;;;;;;; Save on the current db closing ;;;;;;;;;

(defn close-current-db-on-click []
  (dispatch [:tool-bar/close-current-db-start]))

(defn close-current-db-on-save-click []
  (dispatch [:close-current-db-confirmed true]))

(defn close-current-db-on-cancel-click []
  (dispatch [:close-current-db-confirmed false]))

(defn close-current-db-no-save
  "Closes the current db without saving"
  []
  (dispatch [:close-current-db-no-save]))

(defn close-current-db-dialog-data []
  (subscribe [:close-current-db]))

(reg-event-fx
 :tool-bar/close-current-db-start
 (fn [{:keys [db]} [_query-id]]
   (if (db-save-pending? db)
     {:db (-> db (assoc-in [:close-current-db :dialog-show] true))}
     {:fx [[:dispatch [:common/close-kdbx-db (active-db-key db)]]]})))

;; Save or Cancel
(reg-event-fx
 :close-current-db-confirmed
 (fn [{:keys [db]} [_query-id save?]]
   (if save?
     {:db (-> db (assoc-in [:close-current-db :dialog-show] false))
      :fx [[:dispatch [:save-current-db]]
           [:dispatch [:common/close-kdbx-db (active-db-key db)]]]}
     {:db (-> db (assoc-in [:close-current-db :dialog-show] false))})))

;; Close the db without saving any changes
(reg-event-fx
 :close-current-db-no-save
 (fn [{:keys [db]} [_query-id]]
   {:db (-> db (assoc-in [:close-current-db :dialog-show] false))
    :fx [[:dispatch [:common/close-kdbx-db (active-db-key db)]]]}))

(reg-sub
 :close-current-db
 (fn [db _query-vec]
   (:close-current-db db)))

;;;;;;;;;;;;;;; Save all on close ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Called when user quits the application

(defn ask-save-dialog-show [show?]
  (dispatch [:ask-save-dialog-show show?]))

(defn ask-save-dialog-data []
  (subscribe [:ask-save]))

(defn on-save-click []
  (dispatch [:ask-save-dialog-save]))

(defn on-do-not-save-click []
  (dispatch [:ask-save-dialog-do-not-save]))

;; This is called when user Quits the appication either by menu or by closing the window
(reg-event-fx
 :tool-bar/app-quit-called
 (fn [{:keys [db]} [_event-id]]
   (let [pending-dbs (filterv
                      (fn [k] (-> db (get k) :db-modification :save-pending))
                      (opened-db-keys db))] 
     (if (empty? pending-dbs)
       {:fx [[:bg-quit-app-menu-action-requested]]} ;; quit application
       {:fx [[:dispatch [:ask-save-dialog-show true]]]} ;; show ask save or not dialog
       ))))

(reg-event-db
 :ask-save-dialog-show
 (fn [db [_query-id show?]]
   (if show?
     (-> db (assoc-in [:ask-save :dialog-show] show?))
     (assoc-in db [:ask-save :dialog-show] show?))))

(reg-event-fx
 :ask-save-dialog-save
 (fn [{:keys [db]} [_query-id]]
   (println "Save called and background api to save db will be called")
   {:db (-> db (assoc-in [:ask-save :status] :in-progress))
    :fx [[:bg-save-all-modified-dbs (opened-db-keys db)]]}))

(reg-fx
 :bg-save-all-modified-dbs
 (fn [db-keys]
   (bg/save-all-modified-dbs db-keys
                             (fn [api-response]
                               (when-let [result (check-error 
                                                  api-response
                                                  #(dispatch [:ask-save-dialog-save-error %]))]
                                 (dispatch [:ask-save-dialog-save-completed result]))))))

(reg-event-fx
 :ask-save-dialog-do-not-save
 (fn [{:keys [_db]} [_query-id]]
   {:fx [[:bg-quit-app-menu-action-requested]]}))

(reg-event-fx
 :ask-save-dialog-save-completed
 (fn [{:keys [db]} [_query-id _result]] 
   {:db (-> db (assoc-in [:ask-save :status] :completed))
    :fx [[:bg-quit-app-menu-action-requested]]}))

(reg-fx
 :bg-quit-app-menu-action-requested
 (fn []
   ;; See menu-id and other info in 'tauri_events.cljs'
   (bg/menu-action-requested "Quit" "Close" #(println %))))

(reg-event-db
 :ask-save-dialog-save-error
 (fn [db [_query-id error]] 
   (-> db (assoc-in [:ask-save :api-error-text] error)
       (assoc-in [:ask-save :status] :completed))))

(reg-sub
 :ask-save
 (fn [db _query-vec]
   (get-in db [:ask-save])))