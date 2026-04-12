(ns onekeepass.frontend.events.move-group-entry
  "All group and entry related move events. When a group/entry is deleted it is moved to
  the recycle bin group. When a group/entry is putback, again it is move event
   "
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common  :as cmn-events :refer [active-db-key
                                                              get-in-key-db
                                                              assoc-in-key-db
                                                              check-error
                                                              on-error]]
   [onekeepass.frontend.events.group-tree-content :as gt-events]
   [onekeepass.frontend.background :as bg]))


(defn move-group-entry-dialog-show [kind-kw show?] ;; show or hide
  (dispatch [:move-group-entry-dialog-show kind-kw show?]))

(defn move-group-entry-ok [kind-kw id]
  ;;(println "entry-put-back-ok called " kind-kw id)
  (dispatch [:move-group-entry-start kind-kw id]))

(defn move-group-entry-group-selected-factory
  "A factory fn that returns a fn with two args"
  [kind-kw]
  ;; Called on selecting a group in auto complete box
  ;; The option selected in autocomplete component is passed as 'group-info' - a javacript object 
  (fn [_e group-info]
    (dispatch [:move-group-entry-group-selected kind-kw (js->clj group-info :keywordize-keys true)])))

(defn move-group-entry-dialog-data [kind-kw]
  (subscribe [:move-group-entry kind-kw]))

;; kind-kw :entry or :group
(reg-event-fx
 :move-group-entry-start
 (fn [{:keys [db]} [_query-id kind-kw id]]
   (let [g (get-in-key-db db [:move-group-entry kind-kw :group-selection-info])]
     (if (empty? g)
       {:db (-> db (assoc-in-key-db [:move-group-entry kind-kw :field-error] true))}
       {:db (-> db (assoc-in-key-db [:move-group-entry kind-kw :field-error] false)
                (assoc-in-key-db [:move-group-entry kind-kw :status] :in-progress))
        :fx [[:bg-move [kind-kw (active-db-key db) id (:uuid g)]]]}))))

(defn- on-move-complete [kind-kw api-response]
  (when (not (on-error api-response #(dispatch [:move-group-entry-error kind-kw %])))
    (dispatch [:move-group-entry-completed kind-kw])))

(reg-fx
 :bg-move
 (fn [[kind-kw db-key id parent-group-uuid]]
   (if (= kind-kw :group)
     (bg/move-group db-key id parent-group-uuid #(on-move-complete kind-kw %))
     (bg/move-entry db-key id parent-group-uuid #(on-move-complete kind-kw %)))))

(reg-event-db
 :move-group-entry-dialog-show
 (fn [db [_query-id kind-kw show?]]
   (if show?
     (-> db (assoc-in-key-db [:move-group-entry kind-kw :dialog-show] true)
         (assoc-in-key-db [:move-group-entry kind-kw :api-error-text] nil)
         (assoc-in-key-db [:move-group-entry kind-kw :group-selection-info] nil)
         (assoc-in-key-db [:move-group-entry kind-kw :field-error] false))
     (-> db (assoc-in-key-db [:move-group-entry kind-kw :dialog-show] false)))))

(reg-event-db
 :move-group-entry-error
 (fn [db [_query-id kind-kw error]]
   (-> db (assoc-in-key-db [:move-group-entry kind-kw :api-error-text] error)
       (assoc-in-key-db [:move-group-entry kind-kw :status] :completed))))

(reg-event-fx
 :move-group-entry-completed
 (fn [{:keys [db]} [_event-id kind-kw]]
   {:db (-> db
            (assoc-in-key-db [:move-group-entry kind-kw :dialog-show] false)
            (assoc-in-key-db [:move-group-entry kind-kw :status] :completed)
            (assoc-in-key-db [:move-group-entry kind-kw :api-error-text] nil)
            (assoc-in-key-db [:move-group-entry kind-kw :field-error] false))

    :fx [[:dispatch [:common/message-snackbar-open
                     (str (if (= kind-kw :group) "Group" "Entry") " is moved")]]
         [:dispatch [:common/refresh-forms]]]}))


(reg-event-db
 :move-group-entry-group-selected
 (fn [db [_event-id kind-kw group-info]]
   ;;(println "Calling with group in event " group-info " kind-kw " kind-kw)
   (-> db (assoc-in-key-db [:move-group-entry kind-kw :group-selection-info] group-info))))

(reg-sub
 :move-group-entry
 (fn [db [_event-id kind-kw]]
   (get-in-key-db db [:move-group-entry kind-kw])))


;;;;;;;;;;;;;; Permanent delete ;;;;;;;;;;;;;;

(defn delete-permanent-group-entry-dialog-data [kind-kw]
  (subscribe [:delete-permanent-group-entry kind-kw]))

(defn delete-permanent-group-entry-dialog-show
  "The arg kind-kw is either :entry or :group"
  [kind-kw show?] ;; show or hide
  (dispatch [:delete-permanent-group-entry-dialog-show kind-kw show?]))

(defn delete-permanent-group-entry-ok [kind-kw id]
  (dispatch [:delete-permanent-group-entry-start kind-kw id]))

(reg-event-db
 :delete-permanent-group-entry-dialog-show
 (fn [db [_query-id kind-kw show?]]
   (if show?
     (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :dialog-show] true)
         (assoc-in-key-db [:delete-permanent-group-entry kind-kw :api-error-text] nil))
     (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :dialog-show] false)))))

(reg-event-fx
 :delete-permanent-group-entry-start
 (fn [{:keys [db]} [_query-id kind-kw id]]
   {:db (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :status] :in-progress))
    :fx [[:bg-permanent-delete [(active-db-key db) kind-kw id]]]}))


(defn- on-delete-permanent-complete [kind-kw api-response]
  (when (not (on-error api-response #(dispatch [:delete-permanent-group-entry-error kind-kw %])))
    (dispatch [:delete-permanent-group-entry-completed kind-kw])))

(reg-fx
 :bg-permanent-delete
 (fn [[db-key kind-kw id]]
   (if (= kind-kw :group)
     (bg/remove-group-permanently db-key id #(on-delete-permanent-complete kind-kw %))
     (bg/remove-entry-permanently db-key id #(on-delete-permanent-complete kind-kw %)))))


(reg-event-fx
 :delete-permanent-group-entry-completed
 (fn [{:keys [db]} [_event-id kind-kw]]
   {:db (-> db
            (assoc-in-key-db [:delete-permanent-group-entry kind-kw :dialog-show] false)
            (assoc-in-key-db [:delete-permanent-group-entry kind-kw :status] :completed)
            (assoc-in-key-db [:delete-permanent-group-entry kind-kw :api-error-text] nil))
    :fx [[:dispatch [:common/message-snackbar-open
                     (str (if (= kind-kw :group) "Group" "Entry") " is permanently deleted")]]
         [:dispatch [:common/refresh-forms]]]}))

(reg-event-db
 :delete-permanent-group-entry-error
 (fn [db [_query-id kind-kw error]]
   (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :api-error-text] error)
       (assoc-in-key-db [:delete-permanent-group-entry kind-kw :status] :completed))))

(reg-sub
 :delete-permanent-group-entry
 (fn [db [_event-id kind-kw]]
   (get-in-key-db db [:delete-permanent-group-entry kind-kw])))

;;;;;;;;;;;;;;;;;;;;;

(defn empty-trash []
  (dispatch [:empty-trash]))

(reg-event-fx
 :empty-trash
 (fn [{:keys [db]} [_event-id]]
   ;; Calls the backend directly
   (bg/empty-trash (active-db-key db)
                   (fn [api-response]
                     (when-not (on-error api-response)
                       (dispatch [:common/refresh-forms])
                       (dispatch [:common/message-snackbar-open "Recycle bin is emptied"]))))
   {}))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Uses generic dialogs based features. 

(defn move-entry-or-group
  "The arg id is either entry uuid or group uuid"
  [kind-kw id group-selection-info]
  (dispatch [:move-entry-or-group kind-kw id group-selection-info]))

(reg-event-fx
 :move-entry-or-group
 (fn [{:keys [db]} [_event-id kind-kw id group-selection-info]]
   (let [source-db-key (active-db-key db)
         dialog-state (get-in db [:generic-dialogs :move-group-or-entry-dialog])
         target-db-key (or (:target-db-key dialog-state) source-db-key)
         target-parent-uuid (:uuid group-selection-info)]
     (if (= source-db-key target-db-key)
       {:fx [[:bg-move-entry-or-group [source-db-key kind-kw id target-parent-uuid]]]}
       {:fx [[:bg-move-entry-or-group-cross-db
              [source-db-key target-db-key kind-kw id target-parent-uuid]]]}))))

(defn- call-on-move-complete [kind-kw api-response]
  (when-not (on-error api-response)
    ;; Ensure that the dialog is closed
    (dispatch [:generic-dialog-close :move-group-or-entry-dialog])
    (dispatch [:common/message-snackbar-open
               (str (if (= kind-kw :group) "Group" "Entry") " is moved")])
    (dispatch [:common/refresh-forms])))

;; Called to move a group or an entry from one parent group to another parent group
(reg-fx
 :bg-move-entry-or-group
 (fn [[db-key kind-kw id parent-group-uuid]]
   (if (= kind-kw :group)
     (bg/move-group db-key id parent-group-uuid #(call-on-move-complete kind-kw %))
     (bg/move-entry db-key id parent-group-uuid #(call-on-move-complete kind-kw %)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Cross database move support for the move-group-or-entry-dialog

(defn unlocked-opened-dbs
  "Returns a subscribed atom to the list of currently unlocked opened databases.
  Each item is a map shaped for a selection-autocomplete: {:name :db-key :file-name}"
  []
  (subscribe [:move-group-entry/unlocked-opened-dbs]))

(reg-sub
 :move-group-entry/unlocked-opened-dbs
 (fn [db _query-vec]
   (let [all (:opened-db-list db)]
     (->> all
          (remove (fn [{:keys [db-key]}] (boolean (get-in db [db-key :locked]))))
          (mapv (fn [{:keys [db-key database-name file-name]}]
                  {:name database-name
                   :db-key db-key
                   :file-name file-name}))))))

(defn target-db-changed
  "Called when the user picks a different destination database in the move dialog"
  [target-db-key]
  (dispatch [:move-group-entry/target-db-changed target-db-key]))

(reg-event-fx
 :move-group-entry/target-db-changed
 (fn [{:keys [db]} [_event-id target-db-key]]
   (let [source-db-key (active-db-key db)
         current (get-in db [:generic-dialogs :move-group-or-entry-dialog])
         updated (-> current
                     (assoc :target-db-key target-db-key)
                     ;; Clear any previous group selection since it belonged
                     ;; to the previously selected database
                     (assoc :group-selection-info nil)
                     (assoc :target-db-groups-listing nil)
                     (assoc :target-db-loading? (not= target-db-key source-db-key)))]
     (merge {:db (assoc-in db [:generic-dialogs :move-group-or-entry-dialog] updated)}
            (when (not= target-db-key source-db-key)
              {:fx [[:bg-fetch-target-db-groups target-db-key]]})))))

(reg-fx
 :bg-fetch-target-db-groups
 (fn [target-db-key]
   (bg/groups-summary-data
    target-db-key
    (fn [api-response]
      (when-let [result (check-error api-response)]
        (dispatch [:move-group-entry/target-db-groups-loaded target-db-key result]))))))

(reg-event-db
 :move-group-entry/target-db-groups-loaded
 (fn [db [_event-id target-db-key groups-tree-response]]
   (let [current (get-in db [:generic-dialogs :move-group-or-entry-dialog])]
     ;; Only apply the response if the user has not switched to yet another
     ;; target in the meantime
     (if (= target-db-key (:target-db-key current))
       (let [listing (gt-events/flatten-groups-summary-to-listing
                      (js->clj groups-tree-response))
             updated (-> current
                         (assoc :target-db-groups-listing listing)
                         (assoc :target-db-loading? false))]
         (assoc-in db [:generic-dialogs :move-group-or-entry-dialog] updated))
       db))))

(defn- db-name-for [db-list db-key]
  (some (fn [m] (when (= db-key (:db-key m)) (:database-name m))) db-list))

(defn- call-on-cross-db-move-complete [kind-kw source-db-key target-db-key api-response]
  (when-let [summary (check-error api-response)]
    (let [db-list @(cmn-events/opened-db-list)
          src-name (or (db-name-for db-list source-db-key) source-db-key)
          tgt-name (or (db-name-for db-list target-db-key) target-db-key)
          group-name (:target-parent-group-name summary)
          label (if (= kind-kw :group) "Group" "Entry")
          msg (str label " moved from '" src-name "' to '" tgt-name
                   "' \u2192 '" group-name "'. Both databases are modified.")]
      (dispatch [:generic-dialog-close :move-group-or-entry-dialog])
      (dispatch [:common/message-snackbar-open msg])
      (dispatch [:common/refresh-forms])
      (dispatch [:cross-db-move/mark-both-save-pending source-db-key target-db-key])
      (dispatch [:cross-db-move/save-prompt-show source-db-key target-db-key]))))

(reg-event-db
 :cross-db-move/mark-both-save-pending
 (fn [db [_event-id source-db-key target-db-key]]
   (-> db
       (assoc-in [source-db-key :db-modification :save-pending] true)
       (assoc-in [target-db-key :db-modification :save-pending] true))))

(reg-fx
 :bg-move-entry-or-group-cross-db
 (fn [[source-db-key target-db-key kind-kw id target-parent-uuid]]
   (if (= kind-kw :group)
     (bg/move-group-to-other-db
      source-db-key id target-db-key target-parent-uuid
      #(call-on-cross-db-move-complete kind-kw source-db-key target-db-key %))
     (bg/move-entry-to-other-db
      source-db-key id target-db-key target-parent-uuid
      #(call-on-cross-db-move-complete kind-kw source-db-key target-db-key %)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Save-both-databases confirmation prompt shown after a cross-db move

(defn save-prompt-data []
  (subscribe [:cross-db-move/save-prompt]))

(defn save-prompt-close []
  (dispatch [:cross-db-move/save-prompt-close]))

(defn save-prompt-save-both []
  (dispatch [:cross-db-move/save-prompt-save-both]))

(reg-event-db
 :cross-db-move/save-prompt-show
 (fn [db [_event-id source-db-key target-db-key]]
   (assoc db :cross-db-move-save-prompt
          {:dialog-show true
           :source-db-key source-db-key
           :target-db-key target-db-key})))

(reg-event-fx
 :cross-db-move/save-prompt-close
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc db :cross-db-move-save-prompt {:dialog-show false})
    :fx [[:dispatch [:cross-db-move/pending-save-dialog-show]]]}))

(reg-event-fx
 :cross-db-move/save-prompt-save-both
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [source-db-key target-db-key]} (:cross-db-move-save-prompt db)]
     {:db (assoc db :cross-db-move-save-prompt {:dialog-show false})
      :fx [[:bg-cross-db-move-save-both [source-db-key target-db-key]]]})))

(defn- any-save-failure? [result]
  (some (fn [{{:keys [failed]} :save-status}] (some? failed)) result))

(reg-fx
 :bg-cross-db-move-save-both
 (fn [[source-db-key target-db-key]]
   (bg/save-all-modified-dbs
    [source-db-key target-db-key]
    (fn [api-response]
      (when-let [result (check-error api-response)]
        (if (any-save-failure? result)
          (dispatch [:common/error-info-box-show
                     {:title "Save error"
                      :error-text "One or more databases could not be saved. Please check and save manually."}])
          (dispatch [:cross-db-move/save-both-completed
                     source-db-key target-db-key])))))))

(reg-event-db
 :cross-db-move/save-both-completed
 (fn [db [_event-id source-db-key target-db-key]]
   ;; Clear save-pending flag for both dbs directly (bypassing active-db-key)
   (-> db
       (assoc-in [source-db-key :db-modification :save-pending] false)
       (assoc-in [target-db-key :db-modification :save-pending] false))))

(reg-sub
 :cross-db-move/save-prompt
 (fn [db _query-vec]
   (get db :cross-db-move-save-prompt {:dialog-show false})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pending-save info dialog — shown after the user declines to save
;; the two databases immediately following a cross-db move

(defn pending-save-dialog-data []
  (subscribe [:cross-db-move/pending-save-dialog]))

(defn pending-save-dialog-close []
  (dispatch [:cross-db-move/pending-save-dialog-close]))

(reg-event-db
 :cross-db-move/pending-save-dialog-show
 (fn [db [_event-id]]
   (assoc db :cross-db-move-pending-save-dialog {:dialog-show true})))

(reg-event-db
 :cross-db-move/pending-save-dialog-close
 (fn [db [_event-id]]
   (assoc db :cross-db-move-pending-save-dialog {:dialog-show false})))

(reg-sub
 :cross-db-move/pending-save-dialog
 (fn [db _query-vec]
   (get db :cross-db-move-pending-save-dialog {:dialog-show false})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Drag-and-drop entry move

(defn drag-move-entries
  "Called when one or more entries are dropped onto a target group.
   entry-uuids is a vector of entry UUIDs to move."
  [entry-uuids target-group-uuid]
  (dispatch [:drag-move-entries entry-uuids target-group-uuid]))

(reg-event-fx
 :drag-move-entries
 (fn [{:keys [db]} [_event-id entry-uuids target-group-uuid]]
   {:fx [[:bg-drag-move-entries [(active-db-key db) entry-uuids target-group-uuid]]]}))

(reg-fx
 :bg-drag-move-entries
 (fn [[db-key entry-uuids target-group-uuid]]
   (let [total (count entry-uuids)
         completed (atom 0)]
     (doseq [uuid entry-uuids]
       (bg/move-entry db-key uuid target-group-uuid
                      (fn [api-response]
                        (swap! completed inc)
                        (when-not (on-error api-response)
                          (when (= @completed total)
                            (dispatch [:entry-list/clear-entry-selection])
                            (dispatch [:common/message-snackbar-open
                                       (if (= total 1) "Entry is moved"
                                           (str total " entries moved"))])
                            (dispatch [:common/refresh-forms])))))))))
