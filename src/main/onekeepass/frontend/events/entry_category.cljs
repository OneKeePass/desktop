(ns onekeepass.frontend.events.entry-category
  "Various entry category related events"
  (:require
   [onekeepass.frontend.events.common
    :as cmn-events
    :refer [active-db-key
            get-in-key-db
            assoc-in-key-db]]
   [onekeepass.frontend.constants :as const]
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub
                          dispatch
                          subscribe]]
   [onekeepass.frontend.background :as bg]))

(set! *warn-on-infer* true)

;; TODO
;; Currently we are using two set of keywords 
;; :general-categories, :type-categories, group-categories to denote which field
;; of [:entry-category :data] is used
;; Also :type, :category, :group in :showing-groups-as
;; Though the purpose appears slightly differ, need to exlore whether one set will do

(defn initiate-new-blank-group-form [root-group-uuid]
  (dispatch [:group-form/create-blank-group root-group-uuid]))

(defn load-category-entry-items
  "Called to load all entries for a category that is clicked in entry category view.
  The the category info map of the selected category is passed and also what set of 
  categories this map belongs as categories-kind

  This is not called when 'Groups' tree is selected. 
  For 'Groups' tree, see 'group-tree-content/node-on-select'
  "
  [{:keys [uuid title entry-type-uuid]} categories-kind] 
  (dispatch [:entry-category/selected-category-title title])
  ;; selected-category-info is used only for :type for now
  (dispatch [:selected-category-info (if (= categories-kind :type-categories) :type nil) {:entry-type-uuid entry-type-uuid}])
  (dispatch [:group-tree-content/clear-group-selection])
  (dispatch [:entry-form-ex/show-welcome])
  (dispatch [:entry-list/load-entry-items
             (condp = categories-kind
               :general-categories title
               :type-categories {:entry-type-uuid entry-type-uuid}   ;;{:entrytype title}
               :group-categories {:group uuid})]))

(defn show-as-group-tree
  "Called to show the groups as tree when user wants to show 
  Groups in the lower left half"
  []
  (dispatch [:group-tree-content/load-groups-once])
  ;; clears selection
  (dispatch [:entry-category/selected-category-title nil])
  (dispatch [:selected-category-info nil nil])

  (dispatch [:entry-list/clear-entry-items])
  (dispatch [:entry-form-ex/show-welcome])
  (dispatch [:show-group-as :group]))

(defn show-as-group-category
  "Called to show the groups as category 
   when user wants to show Categories in the lower left half"
  []
  (dispatch [:group-tree-content/clear-group-selection])
  (dispatch [:entry-category/selected-category-title nil])
  (dispatch [:selected-category-info nil nil])

  (dispatch [:entry-list/clear-entry-items])
  (dispatch [:entry-form-ex/show-welcome])
  (dispatch [:show-group-as :category]))

(defn show-as-type-category
  "Called to show entry type categories 
   when user wants to show Types in the lower left half"
  []
  (dispatch [:group-tree-content/clear-group-selection])
  (dispatch [:entry-category/selected-category-title nil])
  (dispatch [:selected-category-info nil nil])
  (dispatch [:entry-list/clear-entry-items])
  (dispatch [:entry-form-ex/show-welcome])
  (dispatch [:show-group-as :type]))

(defn delete-custom-entry-type [entry-type-uuid]
  (dispatch [:entry-form-ex/delete-custom-entry-type entry-type-uuid]))

(defn showing-groups-as
  "Returns an atom for the category selection values"
  []
  (subscribe [:showing-groups-as]))

(defn all-entries-category []
  (subscribe [:all-entries-category]))

(defn favorite-entries-category []
  (subscribe [:favorite-entries-category]))

(defn group-categories []
  (subscribe [:group-categories]))

(defn type-categories []
  (subscribe [:type-categories]))

(defn deleted-entries-category []
  (subscribe [:deleted-entries-category]))

(defn selected-category-title []
  (subscribe [:selected-category-title]))

(defn is-entry-type-selected [entry-type-uuid]
  (subscribe [:is-entry-type-selected entry-type-uuid]))

(defn root-group-uuid []
  (subscribe [:group-tree-content/root-group-uuid]))

(reg-event-db
 :show-group-as
 (fn [db [_event-id kind]]
   (assoc-in-key-db db [:entry-category :showing-groups-as] kind)))

;;Called from group tree to clear previously selected category title
(reg-event-db
 :entry-category/clear-category-title
 (fn [db [_]]
   (assoc-in-key-db db [:entry-category :selected-category-title] nil)))

(reg-event-db
 :entry-category/selected-category-title
 (fn [db [_ v]]
   (assoc-in-key-db db [:entry-category :selected-category-title] v)))

;; For now this is used only for entry type category (kind :type)
;; We can explore to use for other categories (:categories, :groups) also
(reg-event-db
 :selected-category-info
 (fn [db [_ kw-kind info-m]]
   (if (nil? kw-kind)
     (assoc-in-key-db db [:entry-category :selected-category-info] nil)
     (assoc-in-key-db db [:entry-category :selected-category-info kw-kind] info-m))))

;; Called after successful login with a lsit of map formed from struct EntryCategoryInfo
(reg-event-db
 :update-category-data
 (fn [db [_ data]]
   ;;(println "on-category-data-load called with data " data)
   (assoc-in-key-db db [:entry-category :data] data)))

;; Called from the group tree  view 
(reg-event-fx
 :entry-category/show-groups-as-tree
 (fn [{:keys [_db]} [_event-id]]
   ;;valid value is one of :category or :group
   {:fx [[:dispatch [:load-category-data :group]]]}))

(reg-event-fx
 :entry-category/show-groups-as-tree-or-category
 (fn [{:keys [db]} [_event-id]]
   ;;valid value is one of :category or :group
   (let [view (get-in-key-db db [:entry-category :showing-groups-as])]
     {:fx [[:dispatch [:load-category-data view]]]})))

;; This is called called after the db is opened and also when db is unlocked
(reg-event-fx
 :entry-category/category-data-load-start
 (fn [{:keys [_db]} [_event-id start-view-to-show]]
   (let [start-view-to-show (cond
                              (= start-view-to-show "Types") :type
                              (= start-view-to-show "Categories") :category
                              (= start-view-to-show "Groups") :group
                              :else :type)]
     ;;valid value is one of :category or :group or :type
     {:fx [[:dispatch [:load-category-data start-view-to-show]]]})))

(reg-event-fx
 :load-category-data
 (fn [{:keys [db]} [_event-id showing-groups-as]]
   ;; valid  value is one of :category or :group or :type
   {:db (assoc-in-key-db db [:entry-category :showing-groups-as] showing-groups-as)
    :fx [[:load-bg-category-data (active-db-key db)]]}))

(defn on-category-data-load [{:keys [result error]}]
  (if (nil? error)
    (dispatch [:update-category-data result])
    ;; Need to send to an alert
    (dispatch [:common/message-snackbar-error-open error])
    #_(println "Error in on-category-data-load: " error)))

(reg-fx
 :load-bg-category-data
 (fn [db-key]
   ;; Get all categories - list of a map as per struct EntryCategoryInfo - to show on the left most panel
   (bg/get-categories-to-show db-key on-category-data-load)))

(defn- is-in-group-categories
  "Returns the category name if the group is shown in category view or nil"
  [db group-uuid]
  (let [group-categories (get-in-key-db db [:entry-category :data :group-categories])
        found (filter (fn [{:keys [uuid]}] (= uuid group-uuid)) group-categories)]
    (-> found first :category-detail :title)))

(defn- category-source-title-to-select
  "Gets the title and category source based on current showing kind - :type :category or :group"
  [db group-uuid entry-type-uuid entry-type-name]
  (let [showing-as (get-in-key-db db [:entry-category :showing-groups-as])
        grp-title (is-in-group-categories db group-uuid)
        category-source-title  (cond
                                 (= showing-as :type)
                                 {:title entry-type-name
                                  :category-source {:entry-type-uuid entry-type-uuid}}

                                 (and (= showing-as :category) (not (nil? grp-title)))
                                 {:title grp-title
                                  :category-source {:group group-uuid}}

                                 (= showing-as :group)
                                 nil

                                 :else
                                 {:title const/CATEGORY_ALL_ENTRIES
                                  :category-source const/CATEGORY_ALL_ENTRIES})]
    category-source-title))

;; Called from entry form after a new entry is inserted
(reg-event-fx
 :entry-category/entry-inserted
 (fn [{:keys [db]} [_event-id entry-uuid group-uuid entry-type-uuid entry-type-name]]
   (let [{:keys [title category-source]}
         (category-source-title-to-select db group-uuid entry-type-uuid entry-type-name)]
     {:db (-> db  (assoc-in-key-db [:entry-category :selected-category-title] title))
      :fx [;; Need to reload category data so that entries count are recent
           [:load-bg-category-data (active-db-key db)]
           ;; The title will not be nil, if :type or :category is selected or  
           ;; general-category is selected
           ;; In case of :group, the title will be nil and 
           ;; entry-inserted call is delegated to group-tree-content
           (if-not (nil? title)
             [:dispatch [:entry-list/entry-inserted entry-uuid category-source]]
             [:dispatch [:group-tree-content/entry-inserted entry-uuid group-uuid]])]})))

;; Called (in an entry_form event) after a custom entry type is successfully deleted 
(reg-event-fx
 :entry-category/entry-type-deleted
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:entry-category/selected-category-title nil]]
         [:dispatch [:entry-list/clear-entry-items]]
         [:dispatch [:entry-form-ex/show-welcome]]
         [:load-bg-category-data (active-db-key db)]
         [:dispatch [:show-group-as :type]]]}))

;;;;;;; 

;;Gets the group uuid if a group is selected in the category view
(reg-sub
 :entry-category/group-uuid-of-category
 ;; Uses syntax sugar which returns values from 3 elsewhere defined subscribes as vector of values 
 ;; instead of the follwing explicit signal-fn
 #_(fn [_query-vec _dynamic-vec] [(subscribe [..]) () ()])
 :<- [:showing-groups-as]
 :<- [:selected-category-title]
 :<- [:group-categories]

 (fn [[group-view-kind cat-name group-cats] _query-vec]
   (when (= group-view-kind :category)
     (->> group-cats (filter (fn [m] (= cat-name (-> m :title)))) first :uuid))))

(reg-sub
 :showing-groups-as
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :showing-groups-as])))

(reg-sub
 :entry-category/showing-groups-as-category
 :<- [:showing-groups-as]
 (fn [group-view-kind _query-vec]
   (if (= group-view-kind :category) true false)))

;; The title of any current selected Category ( "Deleted" "AllEntries" etc or Group name in category view)
(reg-sub
 :selected-category-title
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :selected-category-title])))

(reg-sub
 :selected-category-info
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :selected-category-info])))

(reg-sub
 :is-entry-type-selected
 :<- [:selected-category-info]
 (fn [info [_query-id type-uuid]]
   (let [id (get-in info [:type :entry-type-uuid])]
     (and (not (nil? type-uuid)) (= type-uuid id)))))


;; Is the category selected is deleted one?
(reg-sub
 :entry-category/deleted-category-showing
 :<- [:selected-category-title]
 (fn [cat-title _query-vec]
   (= cat-title "Deleted")))

;;Returns a group info map and it is formed from the original backend group summary info 
;;{:entries-count 1, :icon-id 59, :title "MyGroup1", :uuid "45121394-2a38-4cc2-9761-43d0d3dc80bf"}
(reg-sub
 :group-categories
 (fn [db _query-vec]
   (let [gc (get-in-key-db db [:entry-category :data :group-categories])]
     ;; gc is a vector of a map  (where keys are :category-detail :uuid) - say m1 - formed from the struct GroupCategory 
     ;; an example m1  is 
     ;; {:category-detail {:entries-count 1, :icon-id 59, :title "MyGroup1"} 
     ;;   :uuid "45121394-2a38-4cc2-9761-43d0d3dc80bf"
     ;; }
     ;; Adds the group uuid from the m1 to each category-detail map found in 'group-categories' list
     (map (fn [g] (merge (:category-detail g) {:uuid (:uuid g)})) gc))))

(reg-sub
 :type-categories
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :data :type-categories])))

;; General categories are : AllEntries,Favorites,Deleted
(reg-sub
 :general-categories
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :data :general-categories])))

;;TODO: Need to refactor these individual subscriptions
(reg-sub
 :all-entries-category
 (fn [_query-vec _dynamic-vec]
   (subscribe [:general-categories]))
 (fn [general-categories _query-vec]
   (first (filter #(= (:title %) "AllEntries") general-categories))))

(reg-sub
 :deleted-entries-category
 (fn [_query-vec _dynamic-vec]
   (subscribe [:general-categories]))
 (fn [general-categories _query-vec]
   (first (filter #(= (:title %) "Deleted") general-categories))))

(reg-sub
 :favorite-entries-category
 (fn [_query-vec _dynamic-vec]
   (subscribe [:general-categories]))
 (fn [general-categories _query-vec]
   (let [r (first (filter #(= (:title %) "Favorites") general-categories))]
     (if (nil? r)
       {:entries-count 0, :icon-id 0, :title "Favorites"}
       r))))


(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)
  (-> @re-frame.db/app-db (get db-key) :entry-category)
  (select-keys (-> @re-frame.db/app-db (get db-key) :entry-category) [:showing-groups-as :selected-category-title :selected-category-info]))