(ns onekeepass.frontend.events.clone-entry-to-other-db
  "Events and effects for cloning an entry from one open database to another.
   The source entry is not removed. History is always cleared. No reference linking."
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             check-error]]
   ;; This fn is refered from other event ns. Need to watch out for any circular references
   [onekeepass.frontend.events.group-tree-content :as gt-events]
   
   [onekeepass.frontend.background :as bg]))

;; ---- Public dispatch/subscribe fns (called from UI) ----

(defn clone-entry-to-other-db-dialog-show [entry-uuid]
  (dispatch [:clone-entry-to-other-db/dialog-show entry-uuid]))

(defn clone-target-db-changed [target-db-key]
  (dispatch [:clone-entry-to-other-db/target-db-changed target-db-key]))

(defn other-unlocked-dbs
  "Returns a subscribed atom to the unlocked open databases excluding the
  currently active (source) database."
  []
  (subscribe [:clone-entry-to-other-db/other-unlocked-dbs]))

(defn multi-db-open?
  "Returns true when more than one database is open and unlocked."
  []
  (subscribe [:clone-entry-to-other-db/multi-db-open?]))

;; ---- Subscriptions ----

(reg-sub
 :clone-entry-to-other-db/other-unlocked-dbs
 ;; Reuse the move-events subscription as the underlying signal
 :<- [:move-group-entry/unlocked-opened-dbs]
 :<- [:current-db-file-name]
 (fn [[all-dbs source-db-key] _]
   (filterv (fn [{:keys [db-key]}] (not= db-key source-db-key)) all-dbs)))

(reg-sub
 :clone-entry-to-other-db/multi-db-open?
 :<- [:move-group-entry/unlocked-opened-dbs]
 (fn [all-dbs _]
   (> (count all-dbs) 1)))

;; ---- Events ----

(reg-event-fx
 :clone-entry-to-other-db/dialog-show
 (fn [{:keys [db]} [_event-id entry-uuid]]
   (let [source-db-key (active-db-key db)
         ;; Capture entry title from the currently loaded entry form so the dialog can
         ;; display it for the user's awareness
         entry-title (get-in db [source-db-key :entry-form-data :data :title])]
     {:fx [[:dispatch [:generic-dialog-show-with-state :clone-entry-to-other-db-dialog
                       {:entry-uuid entry-uuid
                        :entry-title entry-title
                        :source-db-key source-db-key
                        :target-db-key nil
                        :target-db-groups-listing nil
                        :target-db-loading? false
                        :group-selection-info nil}]]]})))

(reg-event-fx
 :clone-entry-to-other-db/target-db-changed
 (fn [{:keys [db]} [_event-id target-db-key]]
   (let [current (get-in db [:generic-dialogs :clone-entry-to-other-db-dialog])
         updated (-> current
                     (assoc :target-db-key target-db-key)
                     (assoc :group-selection-info nil)
                     (assoc :target-db-groups-listing nil)
                     (assoc :target-db-loading? true))]
     {:db (assoc-in db [:generic-dialogs :clone-entry-to-other-db-dialog] updated)
      :fx [[:bg-fetch-clone-target-db-groups target-db-key]]})))

(reg-fx
 :bg-fetch-clone-target-db-groups
 (fn [target-db-key]
   (bg/groups-summary-data
    target-db-key
    (fn [api-response]
      (when-let [result (check-error api-response)]
        (dispatch [:clone-entry-to-other-db/target-db-groups-loaded target-db-key result]))))))

(reg-event-db
 :clone-entry-to-other-db/target-db-groups-loaded
 (fn [db [_event-id target-db-key groups-tree-response]]
   (let [current (get-in db [:generic-dialogs :clone-entry-to-other-db-dialog])]
     (if (= target-db-key (:target-db-key current))
       (let [listing (gt-events/flatten-groups-summary-to-listing
                      (js->clj groups-tree-response))
             updated (-> current
                         (assoc :target-db-groups-listing listing)
                         (assoc :target-db-loading? false))]
         (assoc-in db [:generic-dialogs :clone-entry-to-other-db-dialog] updated))
       db))))

(reg-event-fx
 :clone-entry-to-other-db/ok-clicked
 (fn [{:keys [db]} [_event-id]]
   (let [dialog-state (get-in db [:generic-dialogs :clone-entry-to-other-db-dialog])
         {:keys [entry-uuid source-db-key target-db-key group-selection-info]} dialog-state]
     (if (nil? group-selection-info)
       {:db (assoc-in db [:generic-dialogs :clone-entry-to-other-db-dialog :field-error] true)}
       {:fx [[:bg-clone-entry-to-other-db
              [source-db-key entry-uuid target-db-key (:uuid group-selection-info)]]]}))))

(reg-fx
 :bg-clone-entry-to-other-db
 (fn [[source-db-key entry-uuid target-db-key target-parent-uuid]]
   (bg/clone-entry-to-other-db
    source-db-key entry-uuid target-db-key target-parent-uuid
    (fn [api-response]
      (when-let [summary (check-error api-response)]
        (dispatch [:clone-entry-to-other-db/completed source-db-key target-db-key summary]))))))

(reg-event-fx
 :clone-entry-to-other-db/completed
 (fn [{:keys [db]} [_event-id _source-db-key target-db-key summary]]
   ;; Only target is modified — source entry is unchanged
   (let [group-name (:target-parent-group-name summary)
         msg (str "Entry cloned to '" group-name "'")]
     {:db (assoc-in db [target-db-key :db-modification :save-pending] true)
      :fx [[:dispatch [:generic-dialog-close :clone-entry-to-other-db-dialog]]
           [:dispatch [:common/message-snackbar-open msg]]
           ;; Switch to the target DB so the user sees the cloned entry immediately
           [:dispatch [:common/change-active-db-complete target-db-key]]
           ;; Reload all target DB data from backend memory (groups, tags,
           ;; entry-type headers, entry list) — same sequence used after a merge
           [:dispatch [:common/reload-on-merge]]]})))
