(ns onekeepass.frontend.events.tool-bar
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub
                          dispatch
                          subscribe]]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             opened-db-keys
                                                             db-save-pending?
                                                             check-error]]
   [onekeepass.frontend.background :as bg]))

(defn save-current-db []
  (dispatch [:save-current-db false]))

(defn overwrite-external-changes []
  (dispatch [:save-current-db-overwrite]))

(defn save-current-db-msg-dialog-hide []
  ;; Calling save-current-db-completed resets the status to :completed
  (dispatch [:save-current-db-completed nil]))

(defn confirm-overwrite-external-db []
  (dispatch [:confirm-overwrite-external-db]))

(defn confirm-discard-current-db []
  (dispatch [:confirm-discard-current-db]))

(defn conflict-action-confirm-dialog-hide []
  (dispatch [:conflict-action-confirm-dialog-hide]))

(defn conflict-action-save-as []
  (dispatch [:conflict-action-save-as]))

(defn conflict-action-discard []
  (dispatch [:conflict-action-discard]))

(defn conflict-action-merge [db-key]
  (dispatch [:conflict-action-merge db-key]))

(defn save-current-db-data []
  (subscribe [:save-current-db-data]))

(defn conflict-action-confirm-dialog-data []
  (subscribe [:conflict-action-confirm-dialog-data]))

;; TODO:
;; combine save-current-db and :save-and-close-current-db; also bg-save-kdbx and bg-save-kdbx-before-close

;; 'target-db-key' defaults to the active db. It is passed explicitly by auto-save,
;; which may save a db the user is not looking at (a cross-db entry clone/move
;; dirties the target db's tab).
;;
;; 'quiet?' suppresses only the "Saving database is in progress" modal. The error
;; path is untouched on purpose: an auto-save can hit DbFileContentChangeDetected
;; just like a manual one, and the user must still get the conflict dialog.
(reg-event-fx
 :save-current-db
 (fn [{:keys [db]} [_event-id overwrite? target-db-key quiet?]]
   (let [db-key (if (nil? target-db-key) (active-db-key db) target-db-key)]
     {:db (-> db
              (assoc-in [:save-current-db :status] :in-progress)
              (assoc-in [:save-current-db :api-error-text] nil)
              ;; Remembered so the conflict actions and the overwrite retry act on
              ;; the db that was actually being saved, not on whatever is active now
              (assoc-in [:save-current-db :db-key] db-key)
              (assoc-in [:save-current-db :quiet?] (boolean quiet?))
              (assoc-in [:save-current-db :confirm-dialog-data] {}))
      :fx [[:bg-save-kdbx [db-key (if (nil? overwrite?) false overwrite?)]]]})))

;; Retry of the save that just reported a conflict, this time overwriting the
;; external changes. Reuses the db-key recorded by :save-current-db.
(reg-event-fx
 :save-current-db-overwrite
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [db-key quiet?]} (:save-current-db db)]
     {:fx [[:dispatch [:save-current-db true (or db-key (active-db-key db)) quiet?]]]})))

(reg-fx
 :bg-save-kdbx
 (fn [[db-key overwrite?]]
   (bg/save-kdbx db-key
                 overwrite?
                 (fn [api-response]
                   (when-let [kdbx-saved
                              (check-error
                               api-response
                               #(dispatch [:save-current-db-error %]))]
                     (dispatch [:save-current-db-completed kdbx-saved])
                     (dispatch [:common/db-modification-saved kdbx-saved]))))))
;; TODO: 
;; combine save-current-db and :save-and-close-current-db; also bg-save-kdbx and bg-save-kdbx-before-close
(reg-event-fx
 :save-and-close-current-db
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in [:save-current-db :status] :in-progress)
            (assoc-in [:save-current-db :api-error-text] nil)
            (assoc-in [:save-current-db :confirm-dialog-data] {}))
    :fx [[:bg-save-kdbx-before-close [(active-db-key db)]]]}))

(reg-fx
 :bg-save-kdbx-before-close
 (fn [[db-key]]
   (bg/save-kdbx db-key
                 false
                 (fn [api-response]
                   (when-let [kdbx-saved
                              (check-error
                               api-response
                               #(dispatch [:save-current-db-error %]))]
                     (dispatch [:save-current-db-completed kdbx-saved])
                     (dispatch [:common/close-kdbx-db db-key]))))))

(reg-event-db
 :save-current-db-completed
 (fn [db [_event-id _result]]
   (-> db (assoc-in [:save-current-db :status] :completed)
       (assoc-in [:save-current-db :api-error-text] nil)
       (assoc-in [:save-current-db :quiet?] false))))

;; If the error is due external database changes detection, then
;; :api-error-text will be "DbFileContentChangeDetected" and this is used 
;; to trigger the "content-change-action-dialog" dialog popup and user is presented 
;; with action options and aid the user to take an appropriate action to resolve the conflicts
(reg-event-db
 :save-current-db-error
 (fn [db [_event-id error]]
   (-> db
       (assoc-in  [:save-current-db :api-error-text] error)
       ;; A failed auto-save stops being quiet - the user has to resolve it, and
       ;; any retry from the conflict dialog should show its progress normally
       (assoc-in [:save-current-db :quiet?] false)
       (assoc-in [:save-current-db :status] :error))))

(reg-event-db
 :confirm-overwrite-external-db
 (fn [db [_event-id]]
   (-> db (assoc-in  [:save-current-db :confirm-dialog-data]
                     {:dialog-show true
                      :confirm :overwrite}))))

(reg-event-db
 :confirm-discard-current-db
 (fn [db [_event-id]]
   (-> db (assoc-in  [:save-current-db :confirm-dialog-data]
                     {:dialog-show true
                      :confirm :discard}))))

(reg-event-db
 :conflict-action-confirm-dialog-hide
 (fn [db [_event-id]]
   (-> db (assoc-in  [:save-current-db :confirm-dialog-data]
                     {:dialog-show false
                      :confirm nil}))))

(reg-event-fx
 :conflict-action-save-as
 (fn [{:keys [_db]} [_event-id]]
   ;; :save-current-db-completed nil will close the save dialog
   {:fx [[:dispatch [:save-current-db-completed nil]]
         [:dispatch [:common/save-db-file-as]]]}))

;; Save-time conflict: user picks Merge. Reuses the external-change merge flow,
;; which itself routes by db type (remote -> rs_merge_with_remote, local ->
;; disk-version merge), so behavior matches the focus-poll / watcher paths.
(reg-event-fx
 :conflict-action-merge
 (fn [{:keys [_db]} [_event-id db-key]]
   {:fx [[:dispatch [:save-current-db-completed nil]]
         [:dispatch [:external-change-merge-start db-key]]]}))

;;:common/close-kdbx-db
(reg-event-fx
 :conflict-action-discard
 (fn [{:keys [db]} [_event-id]]
   ;; Discards the db that hit the conflict, which is not necessarily the active
   ;; one when the save came from auto-save
   (let [db-key (or (get-in db [:save-current-db :db-key]) (active-db-key db))]
     {:fx [[:dispatch [:save-current-db-completed nil]]
           [:dispatch [:conflict-action-confirm-dialog-hide]]
           [:dispatch [:common/close-kdbx-db db-key]]]})))

(reg-sub
 :conflict-action-confirm-dialog-data
 (fn [db _query-vec]
   (-> db (get-in [:save-current-db :confirm-dialog-data]))))

;; The db-key the in-flight/failed save belongs to. The conflict dialog uses it
;; instead of the active db-key so a conflict raised by an auto-save of a
;; background db is resolved against that db.
(defn saving-db-key []
  (subscribe [:tool-bar/saving-db-key]))

(reg-sub
 :tool-bar/saving-db-key
 (fn [db _query-vec]
   (get-in db [:save-current-db :db-key])))

(reg-sub
 :save-current-db-data
 (fn [db _query-vec]
   (:save-current-db db)))

;;;;;;;;;;;;;;;;;;;;;; Auto save after an edit action ;;;;;;;;;
;; Issue #90. Saves the db automatically after the user completes an edit action
;; and moves on. The qualifying actions are listed in 'auto-save-api-calls' in
;; events/common.cljs; it is a deliberate subset - deletions, moves and history
;; housekeeping are excluded so that closing a db without saving stays the undo
;; for an accidental delete.
;;
;; Off by default; enabled in App Settings -> File Management.

(def ^:private auto-save-debounce-ms 1000)

;; A remote save is a full-file upload, so requests are coalesced over a longer
;; window than for a local file
(def ^:private auto-save-remote-debounce-ms 5000)

;; db-key -> pending js timer id
(defonce ^:private auto-save-timers (atom {}))

(reg-fx
 :tool-bar-auto-save-schedule
 (fn [db-key]
   ;; Restarting the timer coalesces a burst of qualifying calls (a CSV import or
   ;; several attachment uploads) into a single save
   (when-let [existing (get @auto-save-timers db-key)]
     (js/clearTimeout existing))
   (let [delay-ms (if (cmn-events/remote-db-key? db-key)
                    auto-save-remote-debounce-ms
                    auto-save-debounce-ms)
         timer-id (js/setTimeout
                   (fn []
                     (swap! auto-save-timers dissoc db-key)
                     (dispatch [:tool-bar/auto-save-fire db-key]))
                   delay-ms)]
     (swap! auto-save-timers assoc db-key timer-id))))

;; Called from events/common.cljs whenever a qualifying edit action completes
(reg-event-fx
 :tool-bar/auto-save-requested
 (fn [{:keys [db]} [_event-id db-key]]
   (if (and (some? db-key)
            (get-in db [:app-preference :auto-save :enabled]))
     {:fx [[:tool-bar-auto-save-schedule db-key]]}
     {})))

;; All guards are re-checked here rather than when the save was requested - the
;; debounce window is long enough for the db to have been closed or locked in
;; the meantime
(reg-event-fx
 :tool-bar/auto-save-fire
 (fn [{:keys [db]} [_event-id db-key]]
   (let [open? (boolean (some #{db-key} (opened-db-keys db)))
         pending? (boolean (get-in db [db-key :db-modification :save-pending]))
         ;; A locked db's content is encrypted in memory and cannot be written
         locked? (boolean (get-in db [db-key :locked]))
         save-status (get-in db [:save-current-db :status])]
     (cond
       (or (not open?) (not pending?) locked?)
       {}

       ;; A previous save failed and its dialog (a save error, or the
       ;; conflict-on-save choices) is still on screen waiting for the user.
       ;; Starting another save here would reset the status and pull that dialog
       ;; out from under them. Dropping is safe - save-pending stays set, and the
       ;; user's chosen resolution ends in a save anyway.
       (= :error save-status)
       {}

       ;; Never stack a second write on top of a running one; wait it out instead
       (= :in-progress save-status)
       {:fx [[:tool-bar-auto-save-schedule db-key]]}

       :else
       {:fx [[:dispatch [:save-current-db false db-key true]]]}))))

;;;;;;;;;;;;;;;;;;;;;; Lock/Unlock db ;;;;;;;;;;;;;;;;;;;;;;;;;

(defn lock-current-db []
  (dispatch [:tool-bar/lock-current-db])
  #_(dispatch [:common/lock-current-db]))

(defn on-lock-ask-save-dialog-hide []
  (dispatch [:on-lock-ask-save-dialog-show false]))

(defn on-lock-ask-save-dialog-data []
  (subscribe [:on-lock-ask-save-dialog-data]))

(defn unlock-current-db [biometric-type]
  ;; (println "biometric-type passed " biometric-type)
  (if (= biometric-type const/NO_BIOMETRIC)
    (dispatch [:open-db-form/dialog-show-on-current-db-unlock-request])
    (dispatch [:open-db-form/authenticate-with-biometric])))

;; From the "close a locked db" dialog: dismiss the close prompt and start the
;; unlock flow so the user can unlock, save, then close normally. A locked db's
;; content is encrypted in memory and cannot be saved until it is unlocked.
(defn close-current-db-unlock [biometric-type]
  (dispatch [:close-current-db-confirmed false])
  (unlock-current-db biometric-type))

(reg-event-fx
 :tool-bar/lock-current-db
 (fn [{:keys [db]} [_query-id]]
   ;; Unsaved changes are preserved in memory across lock/unlock (Phase 2
   ;; encrypt-in-place restores them on unlock), so locking no longer blocks to
   ;; ask the user to save first. The save-before-lock guard below is kept,
   ;; commented out, so it can be re-enabled later if needed. The dialog/events
   ;; it uses (ask-save-on-lock, :on-lock-ask-save-dialog-show/-data) are also
   ;; left in place for the same reason.
   #_(let [save-pending (db-save-pending? db)]
       (if save-pending
         {:fx [[:dispatch [:on-lock-ask-save-dialog-show true]]]}
         {:fx [[:dispatch [:common/lock-current-db]]]}))
   {:fx [[:dispatch [:common/lock-current-db]]]}))

(reg-event-fx
 :on-lock-ask-save-dialog-show
 (fn [{:keys [db]} [_query-id show?]]
   {:db (assoc-in db [:ask-save-on-lock :data] {:dialog-show show?})}))

(reg-sub
 :on-lock-ask-save-dialog-data
 (fn [db _query-vec]
   (get-in db [:ask-save-on-lock :data])))

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
      :fx [[:dispatch [:save-and-close-current-db]]]}
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
                      (opened-db-keys db))
         ;; Names of the pending dbs that are locked - these cannot be saved by
         ;; the quit "Save" (their content is encrypted in memory). The ask-save
         ;; dialog lists them so the user is not surprised by silently lost edits.
         locked-dirty-names (->> (:opened-db-list db)
                                 (filter (fn [{:keys [db-key]}]
                                           (and (some #{db-key} pending-dbs)
                                                (get-in db [db-key :locked]))))
                                 (mapv (fn [{:keys [database-name file-name]}]
                                         (or database-name file-name)))
                                 (into []))
         ;; True when every dirty db is locked - then nothing can be saved and
         ;; the quit dialog drops the misleading "Save" button.
         all-locked? (and (seq locked-dirty-names)
                          (= (count locked-dirty-names) (count pending-dbs)))]
     (if (empty? pending-dbs)
       {:fx [[:bg-quit-app-menu-action-requested]]} ;; quit application
       {:db (-> db
                (assoc-in [:ask-save :locked-dbs] locked-dirty-names)
                (assoc-in [:ask-save :all-locked?] all-locked?))
        :fx [[:dispatch [:ask-save-dialog-show true]]]} ;; show ask save or not dialog
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
   ;; Save only the unlocked dbs. A locked db (its content is encrypted in
   ;; memory) cannot be saved, and passing a locked remote db to the backend
   ;; save would still touch the remote file's mtime for no benefit. Locked
   ;; dirty dbs were already listed to the user in the ask-save dialog.
   (let [unlocked-keys (into []
                             (remove (fn [db-key] (get-in db [db-key :locked]))
                                     (opened-db-keys db)))]
     {:db (-> db (assoc-in [:ask-save :status] :in-progress))
      :fx [[:bg-save-all-modified-dbs unlocked-keys]]})))

(defn check-failures
  "We receive a vec of maps indicating the status of each database 
   saving before closing databases on quit
   Returns only the elements with failed info
  "
  [save-statuses]
  (filter (fn [{{:keys [failed]} :save-status}]
            (not (nil? failed))) save-statuses))

(reg-fx
 :bg-save-all-modified-dbs
 (fn [db-keys]
   (bg/save-all-modified-dbs
    db-keys
    (fn [api-response]
     ;; Sample api-response is something like this
     ;; {:result [{:db-key /Users/asdfghr/Documents/OneKeePass/Test1.kdbx, 
     ;;            :save-status {:failed Writing failed with error DbFileContentChangeDetected}} 
     ;;           {:db-key /Users/asdfghr/Documents/OneKeePass/Test2.kdbx, 
     ;;            :save-status {:message The db file is not in modified status. No saving was done}}]}

      (when-let [result (check-error
                         api-response
                         #(dispatch [:ask-save-dialog-save-error %]))]
        ;; Need to enure that there are no error in the returned statuses
        (let [failures? (-> result check-failures seq boolean)]
          (if failures?
            (dispatch [:save-on-quit-error])
            (dispatch [:ask-save-dialog-save-completed result]))))))))

(reg-event-fx
 :save-on-quit-error
 (fn [{:keys [db]} [_event-id]]
   {;; ask-save-dialog needs to be closed
    :db (-> db
            (assoc-in [:ask-save :dialog-show] false)
            (assoc-in [:ask-save :status] :completed))
    ;; Open the error info
    :fx [[:dispatch [:common/error-info-box-show
                     {:title "Quit error"
                      :error-text "Could not save databases and close the application. Please close all opened databases and then quit the application"}]]]}))

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

;; Names of the locked pending dbs shown in the quit ask-save dialog (see
;; :tool-bar/app-quit-called). Empty/nil when none are locked.
(defn quit-locked-dirty-dbs []
  (subscribe [:tool-bar/quit-locked-dirty-dbs]))

(reg-sub
 :tool-bar/quit-locked-dirty-dbs
 (fn [db _query-vec]
   (get-in db [:ask-save :locked-dbs])))

;; True when every modified db on quit is locked (so nothing can be saved).
(defn quit-all-dirty-locked? []
  (subscribe [:tool-bar/quit-all-dirty-locked?]))

(reg-sub
 :tool-bar/quit-all-dirty-locked?
 (fn [db _query-vec]
   (get-in db [:ask-save :all-locked?])))

(comment 
  (-> @re-frame.db/app-db keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  )