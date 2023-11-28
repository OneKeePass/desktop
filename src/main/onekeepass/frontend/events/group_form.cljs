(ns onekeepass.frontend.events.group-form
  "All group form edit/info related events"
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common :refer [active-db-key
                                              assoc-in-key-db
                                              get-in-key-db
                                              fix-tags-selection-prefix
                                              check-error
                                              on-error]]
   [onekeepass.frontend.utils :refer [tags->vec vec->tags]]
   [onekeepass.frontend.background :as bg]))

(set! *warn-on-infer* true)

(defn form-on-change-factory
  "Called to update whenever form text field data is changed"
  [field-name-kw]
  (fn [^js/Event e]
    (dispatch [:update-form-data [field-name-kw (-> e .-target .-value)]])))

(defn update-form-data [field-name-kw value] 
  (dispatch [:update-form-data [field-name-kw value]]))

(defn on-tags-selection
  "Called on selecting one or more tags on the entry form"
  [_e tags]
  (dispatch [:tags-selected (fix-tags-selection-prefix tags)]))

(defn find-group-by-id
  "Finds the details of a group for the given group uuid
  The mode (:edit or :info) indentifies where we need to show form as readonly or not
  "
  [uuid mode]
  ;; mode is either :edit or :info
  (dispatch [:group-form/find-group-by-id uuid mode]))

(defn edit-form
  "Puts the form to the edit mode"
  []
  (dispatch [:set-group-form-mode :edit]))

(defn cancel-edit-on-click
  "Cancel the changes"
  [_e]
  (dispatch [:update-dialog-open false]))

(defn ok-edit-on-click
  "Called to save the changes"
  [_e]
  (dispatch [:ok-group-edit])
  
  ;; Following dipatch call is to close edit dialog on successful saving
  ;; TODO:
  ;; Needs to be moved to the dispatch handler in bg/update-group and also needs to refresh 
  ;; group tree data to reflect changes in group data
  (dispatch [:update-dialog-open false]))

(defn ok-new-group-on-click [_e]
  (dispatch [:ok-new-group])
  (dispatch [:update-dialog-open false]))

(defn close-dialog []
  (dispatch [:update-dialog-open false]))

(defn marked-as-category-on-check [^js/Event e]
  (dispatch [:update-marked-as-category (->  e .-target .-checked)]))

(defn dialog-form-data
  "Returns an atom of form data"
  []
  (subscribe [:dialog-form-data]))

(defn form-modified
  "Checks whether any data changed or added"
  []
  (subscribe [:form-modified]))

(defn marked-as-category []
  (subscribe [:marked-as-category]))

(defn showing-groups-as-category []
  (subscribe [:entry-category/showing-groups-as-category]))

(defn- find-group-callback
  "Called by backend api when a group data is loaded"
  [mode api-response]
  (when-let [group (check-error api-response)]
    (dispatch [:update-find-group-by-id group])
    (dispatch [:set-group-form-mode mode])
    (dispatch [:set-new-group-flag false])))

(defn- update-group-callback
  "Called after the succssful update of a group data to the backend store.
  See also mark-group-callback
  "
  [api-response]
  (when-not (on-error api-response)
    (dispatch [:group-tree-content/load-groups])
    ;; entry-category needs to refresh after any group update
    (dispatch [:entry-category/reload-category-data])))

(defn- new-blank-group-callback [parent-group-uuid api-response]
  (when-let [group (check-error api-response)]
    (let [g (assoc group :parent-group-uuid parent-group-uuid)]
      (dispatch [:new-blank-group-created g])
      (dispatch [:set-group-form-mode :edit])
      (dispatch [:set-new-group-flag true]))))

;;;;
(reg-event-fx
 :group-form/find-group-by-id
 (fn [{:keys [db]} [_event-id group-id mode]]
   (bg/get-group-by-id (active-db-key db) group-id (partial find-group-callback mode))
   {}))

;;Called when group data is loaded in a background process
(reg-event-db
 :update-find-group-by-id
 (fn [db [_ {:keys [notes tags] :as v}]]
   ;;Assuming we get a valid value 'v' from  'find-group-by-id' result in bg
   ;;Need to replace notes' line feed charater if any so that form change can be deteted properly
   ;;The 'KeePass' db uses \r\n in notes
   (-> db
       #_(assoc-in-key-db [:group-form :data] (assoc v :notes (str/replace (:notes v) #"\r\n" "\n")))
       (assoc-in-key-db [:group-form :data]
                        (-> v
                            (assoc :notes (str/replace notes #"\r\n" "\n"))
                            (assoc :tags (tags->vec tags))))
       (assoc-in-key-db [:group-form :dialog-open] true)
       (assoc-in-key-db [:group-form :undo-data] {}) ;;used for edit cancel action
       )))

(reg-event-db
 :new-blank-group-created
 (fn [db [_ blank-group]]
   (-> db (assoc-in-key-db [:group-form :data] (assoc blank-group :tags (tags->vec (:tags blank-group))))
       (assoc-in-key-db [:group-form :dialog-open] true)
       ;;(assoc-in-key-db [:group-form :new-group] true) -  see :set-new-group-flag use
       ;;(assoc-in-key-db  [:group-form :mode] true) - see :set-group-form-mode use
       (assoc-in-key-db [:group-form :undo-data] blank-group))))

;;Need to be dispatched explicitly so that sub call in 'group-content-dialog-main'
;;gets the latest values  
(reg-event-db
 :set-new-group-flag
 (fn [db [_ flag]]
   (assoc-in-key-db db [:group-form :new-group] flag)))

;;Called when data is entered or updated for all fields except tags
(reg-event-db
 :update-form-data
 (fn [db [_event-id [field-name-kw value]]]
   (assoc-in-key-db db [:group-form :data field-name-kw] value)))

(reg-event-db
 :tags-selected
 (fn [db [_event-id tags]]
   (assoc-in-key-db db [:group-form :data :tags] tags)))

(reg-event-fx
 :ok-group-edit
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [uuid icon-id parent-group-uuid name tags notes marked-category]} (get-in-key-db db [:group-form :data])]
     (bg/update-group (active-db-key db)
                      {:uuid uuid
                       :icon-id icon-id
                       :parent-group-uuid parent-group-uuid
                       :name name
                       :tags (vec->tags tags)
                       :notes notes
                       :marked-category marked-category} update-group-callback))
   {}))

(reg-event-fx
 :ok-new-group
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [uuid icon-id parent-group-uuid name tags notes marked-category]} (get-in-key-db db [:group-form :data])]
     (bg/insert-group (active-db-key db)
                      {:uuid uuid
                       :icon-id icon-id
                       :parent-group-uuid parent-group-uuid
                       :name name
                       :tags (vec->tags tags)
                       :notes notes
                       :marked-category marked-category} update-group-callback))
   {}))

(reg-event-db
 :update-dialog-open
 (fn [db [_ open?]]
   (assoc-in-key-db db [:group-form :dialog-open] open?)))

;;Form is shown in :edit or :info mode
;;Need to be dispatched explicitly so that sub call in 'group-content-dialog-main'
;;gets the latest values  
(reg-event-db
 :set-group-form-mode
 (fn [db [_ mode]]
   (if (= mode :edit)
     (-> db
         (assoc-in-key-db [:group-form :undo-data] (get-in-key-db db [:group-form :data]))
         (assoc-in-key-db  [:group-form :mode] mode))
     (assoc-in-key-db db [:group-form :mode] mode))))

(reg-event-db
 :update-marked-as-category
 (fn [db [_event-id checked?]]
   (assoc-in-key-db db [:group-form :data :marked-category] checked?)))

(reg-event-fx
 :group-form/create-blank-group
 (fn [_cofx [_event-id parent-group-uuid]]
   {:fx [[:bg-new-blank-group parent-group-uuid]]}))

(reg-fx
 :bg-new-blank-group
 (fn [parent-group-uuid]
   (bg/new-blank-group true (partial new-blank-group-callback parent-group-uuid))))

;; Provides the data for the dialog form
(reg-sub
 :dialog-form-data
 (fn [db _query-vec]
   (let [{:keys [data dialog-open mode new-group]} (get-in-key-db db [:group-form])]
     {:data data
      :dialog-open dialog-open
      :mode mode
      :new-group new-group})))

;; Determines whether any data is changed
(reg-sub
 :form-modified
 (fn [db _query-vec]
   (let [undo-data (get-in-key-db db [:group-form :undo-data])
         data (get-in-key-db db [:group-form :data])]
     (if (and (seq undo-data) (not= undo-data data))
       true
       false))))

(reg-sub
 :marked-as-category
 (fn [db _query-vec]
   (get-in-key-db db [:group-form :data :marked-category])))

(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> (get @re-frame.db/app-db db-key) keys)
  (-> (get @re-frame.db/app-db db-key) :group-form))

