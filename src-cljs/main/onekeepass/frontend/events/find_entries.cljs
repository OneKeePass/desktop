(ns onekeepass.frontend.events.find-entries
  "Events for the on demand Find Entries page that lists entries with attachments, history,
   passkeys, TOTP or all entries by size"
  (:require
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :refer [active-db-key
                                              assoc-in-key-db
                                              check-error
                                              get-in-key-db]]
   [re-frame.core :refer [dispatch reg-event-fx reg-sub subscribe]]))

;; The keywords match the enum EntryFindKind of onekeepass-core in camelCase
(def find-kinds [:with-attachments :with-history :passkeys :totp :by-size])

(def ^:private default-kind :with-attachments)

(defn find-entries-start []
  (dispatch [:find-entries/start]))

(defn find-entries-close []
  (dispatch [:find-entries-close]))

(defn kind-selected [kind]
  (dispatch [:find-entries-kind-selected kind]))

(defn entry-selected [entry-uuid]
  (dispatch [:find-entries-entry-selected entry-uuid]))

(defn find-kind []
  (subscribe [:find-entries-kind]))

(defn found-items []
  (subscribe [:find-entries-items]))

(defn total-data-size []
  (subscribe [:find-entries-total-data-size]))

(defn selected-entry-id []
  (subscribe [:find-entries-selected-entry-id]))

(defn- find-entries-shown? [db]
  (= :find-entries (get-in-key-db db [:show-content])))

(reg-event-fx
 :find-entries/start
 (fn [{:keys [db]} [_event-id]]
   ;; The last kind used stays in this db's state, so reopening the page shows the same list
   (let [kind (or (get-in-key-db db [:find-entries :kind]) default-kind)]
     {:db (assoc-in-key-db db [:find-entries] {:kind kind :items [] :selected-entry-id nil})
      :fx [[:dispatch [:entry-form-ex/show-welcome]]
           [:dispatch [:common/show-content :find-entries]]
           [:dispatch [:find-entries-load]]]})))

(reg-event-fx
 :find-entries-kind-selected
 (fn [{:keys [db]} [_event-id kind]]
   {:db (-> db
            (assoc-in-key-db [:find-entries :kind] kind)
            (assoc-in-key-db [:find-entries :items] [])
            (assoc-in-key-db [:find-entries :selected-entry-id] nil))
    :fx [[:dispatch [:entry-form-ex/show-welcome]]
         [:dispatch [:find-entries-load]]]}))

(reg-event-fx
 :find-entries-load
 (fn [{:keys [db]} [_event-id]]
   (let [kind (get-in-key-db db [:find-entries :kind])]
     (bg/find-entries (active-db-key db) kind
                      (fn [api-response]
                        (when-let [result (check-error api-response)]
                          (dispatch [:find-entries-load-complete result]))))
     {})))

(reg-event-fx
 :find-entries-load-complete
 (fn [{:keys [db]} [_event-id {:keys [items total-data-size]}]]
   (let [selected-id (get-in-key-db db [:find-entries :selected-entry-id])
         still-listed? (some #(= selected-id (-> % :summary :uuid)) items)]
     {:db (-> db
              (assoc-in-key-db [:find-entries :items] items)
              (assoc-in-key-db [:find-entries :total-data-size] total-data-size)
              (assoc-in-key-db [:find-entries :selected-entry-id] (when still-listed? selected-id)))
      ;; After an edit the entry form is reloaded so that it goes back to read only mode with
      ;; the saved values. An entry no longer matching the kind leaves the form empty
      :fx [(if still-listed?
             [:dispatch [:entry-form-ex/find-entry-by-id selected-id]]
             [:dispatch [:entry-form-ex/show-welcome]])]})))

(reg-event-fx
 :find-entries-entry-selected
 (fn [{:keys [db]} [_event-id entry-uuid]]
   {:db (assoc-in-key-db db [:find-entries :selected-entry-id] entry-uuid)
    :fx [[:dispatch [:entry-form-ex/find-entry-by-id entry-uuid]]]}))

;; Called after an entry is saved or the forms are refreshed (delete, merge etc). An edit can
;; move an entry out of the list - for example removing its last attachment
(reg-event-fx
 :find-entries/reload-if-shown
 (fn [{:keys [db]} [_event-id]]
   (if (find-entries-shown? db)
     {:fx [[:dispatch [:find-entries-load]]]}
     {})))

(reg-event-fx
 :find-entries-close
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in-key-db db [:find-entries :items] [])
    ;; Reloading the entry list brings back the entry form for the entry list's selected
    ;; entry, or the welcome form when that entry is no longer in the list
    :fx [[:dispatch [:common/show-content :group-entry]]
         [:dispatch [:entry-list/entry-updated]]]}))

(reg-sub
 :find-entries-kind
 (fn [db _query-vec]
   (or (get-in-key-db db [:find-entries :kind]) default-kind)))

(reg-sub
 :find-entries-items
 (fn [db _query-vec]
   (get-in-key-db db [:find-entries :items])))

(reg-sub
 :find-entries-total-data-size
 (fn [db _query-vec]
   (get-in-key-db db [:find-entries :total-data-size])))

(reg-sub
 :find-entries-selected-entry-id
 (fn [db _query-vec]
   (get-in-key-db db [:find-entries :selected-entry-id])))
