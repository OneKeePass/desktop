(ns onekeepass.frontend.events.group-form-ex
  "All group form edit/info related events using the generic dialog pattern"
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common :refer [active-db-key
                                              fix-tags-selection-prefix
                                              check-error
                                              on-error]]
   [onekeepass.frontend.utils :refer [tags->vec vec->tags]]
   [onekeepass.frontend.background :as bg]))

;;; dialog-identifier-kw :group-form-ex-dialog
;;; State stored at db[:generic-dialogs][:group-form-ex-dialog]
;;; Extra top-level keys (alongside :dialog-show, :data):
;;;   :mode      - :edit or :info
;;;   :new-group - boolean
;;;   :undo-data - snapshot of :data before editing (for modified detection)

;;; Public dispatch wrappers — mirror the old events.group-form API so callers use the same names

(defn find-group-by-id [uuid mode]
  (dispatch [:group-form-ex/find-group-by-id uuid mode]))

(defn form-on-change-factory [field-name-kw]
  (fn [^js/Event e]
    (dispatch [:group-form-ex/update-field [field-name-kw (-> e .-target .-value)]])))

(defn update-form-data [field-name-kw value]
  (dispatch [:group-form-ex/update-field [field-name-kw value]]))

(defn on-tags-selection [_e tags]
  (dispatch [:group-form-ex/tags-selected (fix-tags-selection-prefix tags)]))

(defn edit-form []
  (dispatch [:group-form-ex/set-mode :edit]))

(defn cancel-edit-on-click [_e]
  (dispatch [:generic-dialog-close :group-form-ex-dialog]))

(defn ok-edit-on-click [_e]
  (dispatch [:group-form-ex/ok-edit]))

(defn ok-new-group-on-click [_e]
  (dispatch [:group-form-ex/ok-new-group]))

(defn close-dialog []
  (dispatch [:generic-dialog-close :group-form-ex-dialog]))

(defn marked-as-category-on-check [^js/Event e]
  (dispatch [:group-form-ex/update-field [:marked-category (-> e .-target .-checked)]]))

;;; Public subscribe wrappers

(defn dialog-form-data []
  (subscribe [:generic-dialog-data :group-form-ex-dialog]))

(defn form-modified []
  (subscribe [:group-form-ex/form-modified]))

(defn marked-as-category []
  (subscribe [:group-form-ex/marked-as-category]))

(defn showing-groups-as-category []
  (subscribe [:entry-category/showing-groups-as-category]))

;;; Private callbacks

(defn- find-group-callback [mode api-response]
  (when-let [group (check-error api-response)]
    (let [normalized (-> group
                         (assoc :notes (str/replace (:notes group) #"\r\n" "\n"))
                         (assoc :tags (tags->vec (:tags group))))]
      (dispatch [:generic-dialog-show-with-state :group-form-ex-dialog
                 {:data normalized
                  :mode mode
                  :new-group false
                  :undo-data (if (= mode :edit) normalized {})}]))))

(defn- update-group-callback [api-response]
  (when-not (on-error api-response)
    (dispatch [:group-tree-content/load-groups])
    (dispatch [:entry-category/reload-category-data])))

(defn- new-blank-group-callback [parent-group-uuid api-response]
  (when-let [group (check-error api-response)]
    (let [g (-> group
                (assoc :parent-group-uuid parent-group-uuid)
                (assoc :tags (tags->vec (:tags group))))]
      (dispatch [:generic-dialog-show-with-state :group-form-ex-dialog
                 {:data g
                  :mode :edit
                  :new-group true
                  :undo-data g}]))))

;;; Re-frame events

(reg-event-fx
 :group-form-ex/find-group-by-id
 (fn [{:keys [db]} [_eid group-id mode]]
   (bg/get-group-by-id (active-db-key db) group-id (partial find-group-callback mode))
   {}))

(reg-event-fx
 :group-form-ex/update-field
 (fn [_cofx [_eid [field-name-kw value]]]
   {:fx [[:dispatch [:generic-dialog-update :group-form-ex-dialog
                     [[:data field-name-kw] value]]]]}))

(reg-event-fx
 :group-form-ex/tags-selected
 (fn [_cofx [_eid tags]]
   {:fx [[:dispatch [:generic-dialog-update :group-form-ex-dialog
                     [[:data :tags] tags]]]]}))

(reg-event-fx
 :group-form-ex/set-mode
 (fn [{:keys [db]} [_eid mode]]
   (let [current-data (get-in db [:generic-dialogs :group-form-ex-dialog :data])]
     {:fx [(if (= mode :edit)
             [:dispatch [:generic-dialog-update-with-map :group-form-ex-dialog
                         {:mode :edit :undo-data current-data}]]
             [:dispatch [:generic-dialog-update-with-map :group-form-ex-dialog
                         {:mode mode}]])]})))

(reg-event-fx
 :group-form-ex/ok-edit
 (fn [{:keys [db]} [_eid]]
   (let [{:keys [uuid icon-id parent-group-uuid name tags notes marked-category]}
         (get-in db [:generic-dialogs :group-form-ex-dialog :data])]
     (bg/update-group (active-db-key db)
                      {:uuid uuid
                       :icon-id icon-id
                       :parent-group-uuid parent-group-uuid
                       :name name
                       :tags (vec->tags tags)
                       :notes notes
                       :marked-category marked-category}
                      update-group-callback)
     {:fx [[:dispatch [:generic-dialog-close :group-form-ex-dialog]]]})))

(reg-event-fx
 :group-form-ex/ok-new-group
 (fn [{:keys [db]} [_eid]]
   (let [{:keys [uuid icon-id parent-group-uuid name tags notes marked-category]}
         (get-in db [:generic-dialogs :group-form-ex-dialog :data])]
     (bg/insert-group (active-db-key db)
                      {:uuid uuid
                       :icon-id icon-id
                       :parent-group-uuid parent-group-uuid
                       :name name
                       :tags (vec->tags tags)
                       :notes notes
                       :marked-category marked-category}
                      update-group-callback)
     {:fx [[:dispatch [:generic-dialog-close :group-form-ex-dialog]]]})))

(reg-event-fx
 :group-form-ex/create-blank-group
 (fn [_cofx [_eid parent-group-uuid]]
   {:fx [[:bg-new-blank-group-ex parent-group-uuid]]}))

(reg-fx
 :bg-new-blank-group-ex
 (fn [parent-group-uuid]
   (bg/new-blank-group true (partial new-blank-group-callback parent-group-uuid))))

;;; Subscriptions

(reg-sub
 :group-form-ex/form-modified
 (fn [db _]
   (let [dlg (get-in db [:generic-dialogs :group-form-ex-dialog])
         undo-data (:undo-data dlg)
         data (:data dlg)]
     (boolean (and (seq undo-data) (not= undo-data data))))))

(reg-sub
 :group-form-ex/marked-as-category
 (fn [db _]
   (get-in db [:generic-dialogs :group-form-ex-dialog :data :marked-category])))
