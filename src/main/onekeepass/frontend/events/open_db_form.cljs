(ns onekeepass.frontend.events.open-db-form
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             active-db-key
                                                             active-db-file-name]]
   [onekeepass.frontend.background :as bg]))


(defn open-file-explorer-on-click []
  (dispatch [:open-db-form/open-db]))

(defn open-key-file-explorer-on-click []
  (bg/open-file-dialog (fn [api-response]
                         (let [key-file-name (check-error api-response)]
                           (when key-file-name (dispatch [:open-db-update-key-file-name key-file-name]))))))

(defn file-name-on-change [^js/Event e]
  (dispatch [:open-db-update-file-name (->  e .-target .-value)]))

(defn db-password-on-change [^js/Event e]
  (dispatch [:open-db-update-password (->  e .-target .-value)]))

(defn key-file-name-on-change [^js/Event e]
  (dispatch [:open-db-update-key-file-name (->  e .-target .-value)]))

(defn recent-file-link-on-click [file-name]
  (dispatch [:open-db-dialog-show-on-file-selection file-name]))

(defn cancel-on-click []
  (dispatch [:open-db-dialog-hide])
  ;; Refresh start page. Need to do this only in case there was a load error instead of just cancel
  (dispatch [:common/load-app-preference]))

(defn ok-on-click [file-name pwd key-file-name db-list]
  (if (cmn-events/is-in-opend-db-list file-name db-list)
    (dispatch [:open-db-error "Database is already opened"])
    (dispatch [:open-db-login-credential-entered file-name pwd key-file-name])))

(defn unlock-ok-on-click [pwd key-file-name]
  (dispatch [:open-db-login-credential-entered nil pwd key-file-name]))

(defn password-visible-change [^boolean t]
  (dispatch [:open-db-password-visible t]))

(defn dialog-data []
  (subscribe [:open-db-dialog-data]))

(defn- init-open-db-vals
  "Initializes all open db related values in the incoming main app db 
  and returns the updated main app db
  "
  [db]
  (-> db
      (assoc-in [:open-db :dialog-show] false)
      (assoc-in [:open-db :unlock-request] false)
      (assoc-in [:open-db :password-visibility-on] false)
      (assoc-in [:open-db :status] nil)
      (assoc-in [:open-db :error-fields] {})
      (assoc-in [:open-db :error-text] nil)
      (assoc-in [:open-db :file-name] nil)
      (assoc-in [:open-db :password] nil)
      (assoc-in [:open-db :key-file-name] nil)))

(reg-event-db
 :open-db-password-visible
 (fn [db [_event-id visible?]]
   (assoc-in db [:open-db :password-visibility-on] visible?)))

(reg-event-db
 :open-db-update-file-name
 (fn [db [_event-id file-name]]
   (assoc-in db [:open-db :file-name] file-name)))

(reg-event-db
 :open-db-update-password
 (fn [db [_event-id password]]
   (assoc-in db [:open-db :password] password)))

(reg-event-db
 :open-db-update-key-file-name
 (fn [db [_event-id key-file-name]]
   (assoc-in db [:open-db :key-file-name] key-file-name)))

;; Called to open the system file explorer
(reg-event-fx
 :open-db-form/open-db
 (fn [{:keys [_db]}  [_event-id]]
   (bg/open-file-dialog (fn [api-response]
                          (let [file-name (check-error api-response)]
                            (dispatch [:open-db-dialog-show-on-file-selection file-name]))))
   {}))

;; Called when user selects a file from file system using system File Open Dialog
(reg-event-fx
 :open-db-dialog-show-on-file-selection
 (fn [{:keys [db]} [_event-id file-name]]
   (if file-name
     {:db (-> db (assoc-in [:open-db :dialog-show] true)
              (assoc-in [:open-db :file-name] file-name))}
     {:db (init-open-db-vals db)})))

(reg-event-fx
 :open-db-form/dialog-show-on-current-db-unlock-request
 (fn [{:keys [db]} [_event-id]]
   (let [file-name (active-db-file-name db)]
     {:db (-> db (assoc-in [:open-db :dialog-show] true)
              (assoc-in [:open-db :unlock-request] true)
              (assoc-in [:open-db :file-name] file-name))})))

;; After unlock, we detected databse change. But reloading of database fails  
;; because the credentials of database has been changed outside our app
;; Need to do relogin
(reg-event-fx
 :open-db-form/login-dialog-show-on-reload-error
 (fn [{:keys [db]} [_event-id {:keys [file-name error-text]}]]
   {:db (-> db (assoc-in [:open-db :dialog-show] true)
            (assoc-in [:open-db :unlock-request] false)
            (assoc-in [:open-db :file-name] file-name)
            #_(assoc-in [:open-db :key-file-name] key-file-name)
            (assoc-in [:open-db :error-text] error-text))}))

(reg-event-db
 :open-db-dialog-hide
 (fn [db [_event-id]]
   (init-open-db-vals db)))

(reg-event-fx
 :open-db-login-credential-entered
 (fn [{:keys [db]} [_event-id file-name pwd key-file-name]]
   (let [unlock-request (get-in db [:open-db :unlock-request])]
     (if (str/blank? (get-in db [:open-db :password]))
       {:db (-> db
                (assoc-in [:open-db :error-fields]
                          {:password "Valid password is required"}))}
       {:db (-> db
                (assoc-in [:open-db :error-fields] {})
                (assoc-in [:open-db :status] :in-progress))
        :fx [(if unlock-request
               [:unlock-kdbx-file [(active-db-key db) pwd key-file-name]]
               [:load-kdbx-file [file-name pwd key-file-name]])]}))))

(reg-event-db
 :open-db-error
 (fn [db [_event-id error]]
   (-> db (assoc-in [:open-db :error-text] error)
       (assoc-in [:open-db :status] :error))))

(reg-event-fx
 :open-db-file-loading-done
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db init-open-db-vals) ;; will hide dialog
    :fx [[:dispatch [:common/kdbx-database-opened kdbx-loaded]]]}))


(defn- on-file-loading [api-response]
  (let [error-fn (fn [err] (dispatch [:open-db-error err]))]
    (when-let [kdbx-loaded (check-error api-response error-fn)]
      (dispatch [:open-db-file-loading-done kdbx-loaded]))))

;;IMPORTANT reg-fx handler fn takes single argument. So we need to use vec [file-name pwd]
(reg-fx
 :load-kdbx-file
 (fn [[file-name pwd key-file-name]]
   (bg/load-kdbx file-name pwd key-file-name on-file-loading)))

;;common/kdbx-database-unlocked
(reg-event-fx
 :unlock-db-file-loading-done
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db init-open-db-vals) ;; will hide dialog
    :fx [[:dispatch [:common/kdbx-database-unlocked kdbx-loaded]]]}))

(reg-fx
 :unlock-kdbx-file
 (fn [[db-key pwd key-file-name]]
   (bg/unlock-kdbx db-key pwd key-file-name
                   (fn [api-response]
                     (when-let [kdbx-loaded (check-error
                                             api-response
                                             #(dispatch [:open-db-error %]))]
                       (dispatch [:unlock-db-file-loading-done kdbx-loaded]))))))

(reg-event-fx
 :open-db-form/authenticate-with-biometric
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-authenticate-with-biometric [(active-db-key db)]]]}))

(reg-fx
 :bg-authenticate-with-biometric
 (fn [[db-key]]
   (bg/authenticate-with-biometric db-key
                                   (fn [api-response] 
                                     ;; IMPORTANT we can not use when-let as we get {:result true} or {:result false}
                                     ;; When we use when-let and the api-reponse is {:result false}, the whole clause 
                                     ;; will be skipped as the when-let evaluates to false 
                                     (let [autheticated? (check-error
                                                          api-response
                                                          (fn [error]
                                                            (println "error is " error)
                                                            (dispatch [:open-db-biometric-login-fail])))] 
                                       (when-not (nil? autheticated?)
                                         (if autheticated?
                                           (dispatch [:open-db-biometric-login-success])
                                           (dispatch [:open-db-biometric-login-fail]))))))))


(reg-event-fx
 :open-db-biometric-login-success
 (fn [{:keys [db]} [_event-id]]
   (println ":open-db-biometric-login-success called")
   {:fx [[:bg-unlock-kdbx-on-biometric-authentication [(active-db-key db)]]]}))

(reg-fx
 :bg-unlock-kdbx-on-biometric-authentication
 (fn [[db-key]]
   (bg/unlock-kdbx-on-biometric-authentication db-key
                                               (fn [api-response]
                                                 (when-let [kdbx-loaded (check-error
                                                                         api-response
                                                                         #(dispatch [:open-db-error %]))]
                                                   (dispatch [:unlock-db-file-loading-done kdbx-loaded]))))))


(reg-event-fx
 :open-db-biometric-login-fail
 (fn [{:keys [db]} [_event-id]]
   (println "open-db-biometric-login-fail is called ")
   {:fx [[:dispatch [:common/message-snackbar-error-open "Biometric authentication is not successful"]]
         [:dispatch [:open-db-form/dialog-show-on-current-db-unlock-request]]]}))

(reg-sub
 :open-db-dialog-data
 (fn [db _query-vec]
   (-> db :open-db)))
