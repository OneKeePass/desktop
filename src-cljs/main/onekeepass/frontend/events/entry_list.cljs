(ns onekeepass.frontend.events.entry-list
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub dispatch subscribe]]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.common :as common :refer [active-db-key
                                                         get-in-key-db
                                                         assoc-in-key-db
                                                         check-error
                                                         on-error]]
   [onekeepass.frontend.translation :refer [lstr-sm]]
   [onekeepass.frontend.background :as bg]))

#_(set! *warn-on-infer* true)

(defn deleted-category-showing []
  (subscribe [:entry-category/deleted-category-showing]))

(defn update-selected-entry-id
  "Called from entry list view with the selected entry uuid"
  [entry-uuid]
  (dispatch [:entry-list/update-selected-entry-id entry-uuid])
  ;;Show the selected entry details in form 
  (dispatch [:entry-form-ex/find-entry-by-id entry-uuid])
  #_(dispatch [:entry-form/find-entry-by-id entry-uuid]))

(defn add-new-entry [group-info entry-type-uuid]
  (dispatch [:entry-form-ex/add-new-entry group-info entry-type-uuid]))

(defn entry-list-sort-key-changed [key-name]
  (dispatch [:entry-list-sort-key-changed key-name]))

(defn entry-list-sort-direction-toggle []
  (dispatch [:entry-list-sort-direction-toggle]))

(defn get-selected-entry-items []
  (subscribe [:selected-entry-items]))

(defn get-selected-entry-id []
  (subscribe [:selected-entry-id]))

(defn initial-group-selection-info
  "Returns an atom of the any selected group summary info in group-tree-content"
  []
  (subscribe [:group-tree-content/group-summary-info]))

(defn selected-entry-type
  "Returns an atom for the entry type name if the entry type category is selected 
  in the category view."
  []
  (subscribe [:selected-entry-type]))

(defn entry-list-sort-creteria []
  (subscribe [:entry-list-sort-creteria]))

(defn selected-entry-item-index []
  (subscribe [:selected-entry-item-index]))

;;;;;;;;;;;;;;;;;;;;;;; Entries sorting ;;;;;;;;;;;;;;;;;;;;;;;;;;

(def sort-default-key-name const/TITLE)

(def sort-default-direction const/ASCENDING)

(defn list-sort-creteria
  ([db]
   (let [{:keys [key-name direction] :as el-sort} (get-in-key-db db [:entry-list :sort])]
     (if (nil? key-name)
       {:key-name sort-default-key-name
        :direction (if (nil? direction) sort-default-direction direction)}
       el-sort))))

(defn sort-entries [{:keys [key-name direction]} entries]
  (sort-by

   ;; This is the key fn that provides keys for the comparion
   (fn [{:keys [title modified-time created-time]}]
     (cond
       (= key-name const/TITLE)
       ;; Case-insensitive title sort so e.g. "apple" is not pushed below "Zebra"
       (some-> title str/lower-case)

       (= key-name const/MODIFIED_TIME)
       modified-time

       (= key-name const/CREATED_TIME)
       created-time

       :else
       (some-> title str/lower-case)))

   ;; This is comparater for the keys
   (fn [v1 v2] (if (= direction const/ASCENDING)
                 (compare v1 v2)
                 (compare v2 v1)))
   entries))

(defn sort-entries-with-creteria
  "Sorts the entry list based on the currrent sort creteria and returns a vec"
  [db entries]
  (let [sort-creteria (list-sort-creteria db)]
    (vec (sort-entries sort-creteria entries))))

(defn- entry-item-index
  "Finds the position of the entry with the uuid 'entry-uuid' in the entry summary items 'entries'
  Returns the index or nil when the uuid is nil or is not found in that list"
  [entries entry-uuid]
  (when-not (nil? entry-uuid)
    (first (keep-indexed (fn [idx {:keys [uuid]}]
                           (when (= uuid entry-uuid) idx))
                         entries))))

(reg-event-fx
 :sort-entry-items
 (fn [{:keys [db]} [_event-id]]
   (let [entries (get-in-key-db db [:entry-list :selected-entry-items])
         sorted-entries (sort-entries-with-creteria db entries)
         selected-entry-id (get-in-key-db db [:entry-list :selected-entry-id])]
     ;; Re-sorting moves the selected entry to a new position and the index used to scroll
     ;; the virtualized list needs to follow it. Otherwise the list scrolls to the row that
     ;; happens to be at the old index
     {:db (-> db
              (assoc-in-key-db [:entry-list :selected-entry-items] sorted-entries)
              (assoc-in-key-db [:entry-list :selected-entry-item-index]
                               (entry-item-index sorted-entries selected-entry-id)))})))

;; entry-list-sort is a map with keys [key-name direction]
(reg-event-fx
 :entry-list-sort-key-changed
 (fn [{:keys [db]} [_event-id key-name]]
   {:db (assoc-in-key-db db [:entry-list :sort :key-name] key-name)
    :fx [[:dispatch [:sort-entry-items]]]}))

(reg-event-fx
 :entry-list-sort-direction-toggle
 (fn [{:keys [db]} [_event-id]]
   (let [curr-direction (get-in-key-db db [:entry-list :sort :direction])]
     {:db (assoc-in-key-db db [:entry-list :sort :direction]
                           (if (or (nil? curr-direction) (= curr-direction const/ASCENDING))
                             const/DESCENDING
                             const/ASCENDING))
      :fx [[:dispatch [:sort-entry-items]]]})))


(reg-sub
 :entry-list-sort-creteria
 (fn [db _query-vec]
   (list-sort-creteria db)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Called to clear previously loaded entry summary items (list) data
;; Also clears out the category source
(reg-event-fx
 :entry-list/clear-entry-items
 (fn [_cofx [_event-id]]
   {:fx [[:dispatch [:update-selected-entry-items nil]]
         [:dispatch [:update-category-source nil]]]}))

;; Called to get all entry summary items for a selected category in entry category view.
;; This also sets the category source. The valid values 
;; are AllEntries or Deleted or Favorites or a map for group or type category.
;; These match the EntryCategory enum in backend service
(reg-event-fx
 :entry-list/load-entry-items
 (fn [{:keys [db]} [_event-id category]]
   {:fx [[:dispatch [:entry-list/update-selected-entry-id nil]]
         [:load-bg-entry-summary-data [(active-db-key db) category]]]}))

;; Called after new entry is inserted in entry form
(reg-event-fx
 :entry-list/entry-inserted
 (fn [{:keys [db]} [_event-id entry-uuid category-source]]
   {:fx [[:dispatch [:entry-list/update-selected-entry-id entry-uuid]]
         [:load-bg-entry-summary-data [(active-db-key db) category-source]]]}))

;; When an entry is selected in the search results, the AllEntries cat
;; is set as selected and entry is loaded from backend and the entry details are shown in form
(reg-event-fx
 :entry-list/entry-selected-in-search-result
 (fn [{:keys [db]} [_event-id entry-uuid]]
   (let [db (assoc-in-key-db db [:entry-list :category-source] const/CATEGORY_ALL_ENTRIES)
         category (get-in-key-db db [:entry-list :category-source]) ;;category-source is const/CATEGORY_ALL_ENTRIES
         ]
     {:db db
      :fx [#_[:dispatch [:entry-category/selected-category-title category]]
           [:dispatch [:entry-category/select-all-entries-category]]
           [:dispatch [:group-tree-content/clear-group-selection]]
           [:dispatch [:entry-list/update-selected-entry-id entry-uuid]]
           [:dispatch [:entry-form-ex/find-entry-by-id entry-uuid]]
           [:load-bg-entry-summary-data [(active-db-key db) category]]]})))

;; Called after any update in entry form
(reg-event-fx
 :entry-list/entry-updated
 (fn [{:keys [db]} [_event-id]]
   (let [category (get-in-key-db db [:entry-list :category-source])
         ;; category in [:entry-list :category-source] may be nil when no 'category' is 
         ;; selected on the left panel in the begining
         ;; When this event is called, we expect a valid category selected
         ;; However when the refreshing of the UI done after db merge, there is a possibility
         ;; category is nil as the user might have not selected any category on the left panel
         ;; before calling db merge. If the nil category is passed to the backend api 'bg/entry-summary-data'
         ;; The cameCase conversion will fail. So we need to ensure some default 'category' to use in such a case
         category (if-not (nil? category) category const/CATEGORY_ALL_ENTRIES)]
     {:fx [[:load-bg-entry-summary-data [(active-db-key db) category]]]})))

(reg-fx
 :load-bg-entry-summary-data
 ;; reg-fx accepts only single argument. So the calleer needs to use map or vector to pass multiple values
 (fn [[db-key category]]
   (bg/entry-summary-data db-key category
                          (fn [api-response]
                            (when-let [entry-summaries-v (check-error api-response)]
                              (dispatch [:entry-list-load-complete entry-summaries-v category])))
                          #_(partial summary-entry-items-loaded category))))

;; When a list of all entry summary data is loaded successfully, this is called 
(reg-event-fx
 :entry-list-load-complete
 (fn [{:keys [db]} [_event-id entry-summaries-v category]]
   (let [current-selected-entry-id (get-in-key-db db [:entry-list :selected-entry-id])
         ;; The loaded entry list is sorted as per the current sort creteria before it is shown
         ;; The index used to scroll the virtualized list must be found in this sorted vec and
         ;; not in the vec as returned by the backend. The backend sorts the entries by title
         ;; only and that order does not match when the user sorts by any other key or direction
         sorted-entries (sort-entries-with-creteria db entry-summaries-v)

         ;; When user selects an entry in the search list, current-selected-entry-id is set and
         ;; entry summary vec is loaded to show in 'entry-list'.
         ;; We need to find the entry's index in that vec so that we can use that info to scroll to that item.
         ;; In all other cases, the index is nil and the list is shown from the top
         item-index (entry-item-index sorted-entries current-selected-entry-id)]

     {:db (-> db (assoc-in-key-db [:entry-list :selected-entry-item-index] item-index))
      :fx [[:dispatch [:update-selected-entry-items sorted-entries]]
           [:dispatch [:update-category-source category]]
           (if (nil? item-index)
             [:dispatch [:entry-form-ex/show-welcome]]
             ;; Following event is dipatched only when the selected entry is in the list ( search time )
             [:dispatch [:entry-form-ex/find-entry-by-id current-selected-entry-id]]
             ;; Following will not work as  we see warning on console "re-frame: in ":fx" effect found"
             #_[[:dispatch [:entry-form-ex/find-entry-by-id current-selected-entry-id]]])]})))

;; list of entry items returned by backend api when a category selected
;; or entry items returned in a search result - Work is yet to be done

#_(reg-event-db
   :update-selected-entry-items
   (fn [db [_event-id  entry-summaries-v]]
     (assoc-in-key-db db [:entry-list :selected-entry-items] entry-summaries-v)))

(reg-event-fx
 :update-selected-entry-items
 (fn [{:keys [db]} [_event-id entry-summaries-v]]
   {:db (assoc-in-key-db db [:entry-list :selected-entry-items] entry-summaries-v)
    ;; Need to clear any previous selection done in dnd
    :fx [[:dispatch [:entry-list/clear-entry-selection]]]}))

;; Sets the category-source that is selected in the category view
(reg-event-db
 :update-category-source
 (fn [db [_event-id  source]]
   (assoc-in-key-db db [:entry-list :category-source] source)))

(reg-event-db
 :entry-list/update-selected-entry-id
 (fn [db [_event-id  entry-id]]
   (assoc-in-key-db db [:entry-list :selected-entry-id] entry-id)))

(reg-sub
 :selected-entry-items
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :selected-entry-items])))

(reg-sub
 :selected-entry-id
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :selected-entry-id])))

;; Gets the source based on the category view selection
(reg-sub
 :category-source
 (fn [db _query-vec]
   (get-in-key-db db [:entry-list :category-source])))

(reg-sub
 :selected-entry-type
 :<- [:category-source]
 (fn [{:keys [entry-type-uuid]} _query-vec]
   entry-type-uuid))

(reg-sub
 :selected-entry-item-index
 (fn [db [_query-id]]
   (let [index (get-in-key-db db [:entry-list :selected-entry-item-index])]
     (if-not (nil? index) index 0))))


;;; Multi-select support for drag-and-drop

(defn toggle-entry-selection [uuid]
  (dispatch [:entry-list/toggle-entry-selection uuid]))

(defn clear-entry-selection []
  (dispatch [:entry-list/clear-entry-selection]))

(defn get-selected-entry-ids []
  (subscribe [:entry-list/selected-entry-ids]))

(defn delete-selected-entries-start [selected-ids]
  (dispatch [:entry-list/delete-selected-entries-start selected-ids]))

(reg-event-db
 :entry-list/toggle-entry-selection
 (fn [db [_event-id uuid]]
   (let [current (or (get-in-key-db db [:entry-list :selected-entry-ids]) #{})]
     #_(println "In :entry-list/toggle-entry-selection current selection" current)
     (assoc-in-key-db db [:entry-list :selected-entry-ids]
                      (if (contains? current uuid)
                        (disj current uuid)
                        (conj current uuid))))))

(reg-event-db
 :entry-list/clear-entry-selection
 (fn [db [_event-id]]
   (assoc-in-key-db db [:entry-list :selected-entry-ids] #{})))

(reg-sub
 :entry-list/selected-entry-ids
 (fn [db _query-vec]
   (or (get-in-key-db db [:entry-list :selected-entry-ids]) #{})))

(reg-event-fx
 :entry-list/delete-selected-entries-start
 (fn [{:keys [db]} [_event-id selected-ids]]
   (let [selected-entry-ids (vec selected-ids #_(get-in-key-db db [:entry-list :selected-entry-ids]))]
     (if (seq selected-entry-ids)
       {:fx [[:bg-delete-selected-entries [(active-db-key db) selected-entry-ids]]]}
       {}))))

(reg-fx
 :bg-delete-selected-entries
 (fn [[db-key entry-ids]]
   (letfn [(delete-next [remaining]
             (if-let [entry-id (first remaining)]
               (bg/move-entry-to-recycle_bin
                db-key
                entry-id
                (fn [api-response]
                  (when-not (on-error api-response #(dispatch [:entry-list/delete-selected-entries-error %]))
                    (delete-next (rest remaining)))))
               (dispatch [:entry-list/delete-selected-entries-completed (count entry-ids)])))]
     (delete-next entry-ids))))

(reg-event-fx
 :entry-list/delete-selected-entries-completed
 (fn [{:keys [db]} [_event-id deleted-count]]
   {:db (assoc-in-key-db db [:entry-list :selected-entry-ids] #{})
    :fx [[:dispatch [:common/refresh-forms]]
         [:dispatch [:common/message-snackbar-open
                     (lstr-sm 'entriesDeletedCount {:count deleted-count})]]]}))

(reg-event-fx
 :entry-list/delete-selected-entries-error
 (fn [{:keys [_db]} [_event-id error-text]]
   {:fx [[:dispatch [:common/message-snackbar-error-open error-text]]]}))

;;; Drag-active state — tracks the uuid of the entry currently being dragged.
;;; Set from core.cljs onDragStart/onDragEnd/onDragCancel so all selected
;;; row items can hide together when any drag is in progress.

(defn set-drag-active [uuid]
  (dispatch [:entry-list/set-drag-active uuid]))

(defn get-drag-active-uuid []
  (subscribe [:entry-list/drag-active-uuid]))

(reg-event-db
 :entry-list/set-drag-active
 (fn [db [_ uuid]]
   (assoc-in-key-db db [:entry-list :drag-active-uuid] uuid)))

(reg-sub
 :entry-list/drag-active-uuid
 (fn [db _]
   (get-in-key-db db [:entry-list :drag-active-uuid])))

(comment
  @re-frame.db/app-db
  (def db-key (:current-db-file-name @re-frame.db/app-db))

  (-> (get @re-frame.db/app-db db-key) keys)
  (-> (get @re-frame.db/app-db db-key) :entry-list))


;; (defn initiate-new-blank-entry-form
;;   "Called when Add Entry is clicked"
;;   []
;;   (dispatch [:entry-list/update-selected-entry-id nil])
;;   (dispatch [:entry-form/create-blank-entry]))

;; (defn- summary-entry-items-loaded
;;   "This is called with the result of the backend API call for a list of entry items for a 
;;   a category selected on the entry category view.

;;   The arg is a map that has the :result or :error as key
;;   "
;;   [category {:keys [result _error]}]
;;   (dispatch [:update-selected-entry-items result])
;;   (dispatch [:update-category-source category]))
