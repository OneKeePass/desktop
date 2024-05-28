(ns onekeepass.frontend.events.entry-list
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub dispatch subscribe]]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.common :as common :refer [active-db-key
                                                         get-in-key-db
                                                         assoc-in-key-db
                                                         check-error]]
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
       title

       (= key-name const/MODIFIED_TIME)
       modified-time

       (= key-name const/CREATED_TIME)
       created-time

       :else
       title))
   
   ;; This is comparater for the keys
   (fn [v1 v2] (if (= direction const/ASCENDING)
                 (compare v1 v2)
                 (compare v2 v1)))
   entries))

(defn sort-entries-with-creteria
  "Sorts the entry list based on the currrent sort creteria"
  [db entries]
  (let [sort-creteria (list-sort-creteria db)]
    (sort-entries sort-creteria entries)))

(reg-event-fx
 :sort-entry-items
 (fn [{:keys [db]} [_event-id]]
   (let [entries (get-in-key-db db [:entry-list :selected-entry-items])]
     {:db (assoc-in-key-db db [:entry-list :selected-entry-items] (sort-entries-with-creteria db entries))})))

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
           [:dispatch [:entry-list/update-selected-entry-id entry-uuid]]
           [:dispatch [:entry-form-ex/find-entry-by-id entry-uuid]]
           [:load-bg-entry-summary-data [(active-db-key db) category]]]})))

;; Called after any update in entry form
(reg-event-fx
 :entry-list/entry-updated
 (fn [{:keys [db]} [_event-id]]
   (let [category (get-in-key-db db [:entry-list :category-source])]
     {:fx [[:load-bg-entry-summary-data [(active-db-key db) category]]]})))

(reg-fx
 :load-bg-entry-summary-data
 ;; reg-fx accepts only single argument. So the calleer needs to use map or vector to pass multiple values
 (fn [[db-key category]]
   (bg/entry-summary-data db-key category
                          (fn [api-response]
                            (when-let [result (check-error api-response)]
                              (dispatch [:entry-list-load-complete result category])))
                          #_(partial summary-entry-items-loaded category))))

;; When a list of all entry summary data is loaded successfully, this is called 
(reg-event-fx
 :entry-list-load-complete
 (fn [{:keys [db]} [_event-id result category]]
   (let [current-selected-entry-id (get-in-key-db db [:entry-list :selected-entry-id])
         found (filter (fn [m] (= current-selected-entry-id (:uuid m))) result)]
     
     {:db db
      :fx [;; Need to sort the loaded entry list as per the current sort creteria
           [:dispatch [:update-selected-entry-items (sort-entries-with-creteria db result)]]
           
           [:dispatch [:update-category-source category]]
           (if-not (boolean (seq found))
             [:dispatch [:entry-form-ex/show-welcome]]
             (dispatch [:entry-form-ex/find-entry-by-id current-selected-entry-id]))]})))

;; list of entry items returned by backend api when a category selected
;; or entry items returned in a search result - Work is yet to be done
(reg-event-db
 :update-selected-entry-items
 (fn [db [_event-id  v]]
   (assoc-in-key-db db [:entry-list :selected-entry-items] v)))

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