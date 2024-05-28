(ns onekeepass.frontend.events.entry-category
  "Various entry category related events"
  (:require [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.constants
             :as const
             :refer [GROUPING_LABEL_TYPES GROUPING_LABEL_TAGS
                     GROUPING_LABEL_CATEGORIES GROUPING_LABEL_GROUPS]]
            [onekeepass.frontend.events.common
             :as cmn-events
             :refer [active-db-key 
                     assoc-in-key-db
                     check-error
                     get-in-key-db]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

(set! *warn-on-infer* true)

(defn initiate-new-blank-group-form [root-group-uuid]
  (dispatch [:group-form/create-blank-group root-group-uuid]))

(defn load-category-entry-items
  "Called to load all entries for a category that is clicked in entry category view.
  The the category info map of the selected category is passed and also what set of 
  categories this map belongs as categories-kind

  This is not called when 'Groups' tree is selected. 
  For 'Groups' tree, see 'group-tree-content/node-on-select'
  "
  [{:keys [group-uuid title entry-type-uuid] :as category-detail} categories-kind]
  (dispatch [:selected-category-info category-detail])
  (dispatch [:group-tree-content/clear-group-selection])
  (dispatch [:entry-form-ex/show-welcome])
  (dispatch [:entry-list/load-entry-items
             (condp = categories-kind
               :general-categories title
               :tag-categories {:tag title}
               :type-categories {:entry-type-uuid entry-type-uuid}   ;;{:entrytype title}
               :group-categories {:group group-uuid})]))

(defn show-as-group-tree
  "Called to show the groups as tree when user wants to show 
  Groups in the lower left panel"
  []
  (dispatch [:clear-and-show :group]))

(defn show-as-group-category
  "Called to show the groups as category 
   when user wants to show Categories in the lower left panel"
  []
  (dispatch [:clear-and-show :category]))

(defn show-as-type-category
  "Called to show entry type categories 
   when user wants to show Types in the lower left panel"
  []
  (dispatch [:clear-and-show :type]))

(defn show-as-tag-category
  "Called to show entry type categories 
   when user wants to show Types in the lower left panel"
  []
  (dispatch [:clear-and-show :tag]))

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

(defn tag-categories []
  (subscribe [:tag-categories]))

(defn deleted-entries-category []
  (subscribe [:deleted-entries-category]))

(defn is-selected-category [cat-detail-m]
  (subscribe [:is-selected-category cat-detail-m]))

(defn root-group-uuid []
  (subscribe [:group-tree-content/root-group-uuid]))

(defn to-entry-groupings-kind-kw
  "Converts the string value of grouping label to an appropriate keyword"
  [start-view-to-show]
  (cond
    (= start-view-to-show GROUPING_LABEL_TYPES) :type
    (= start-view-to-show GROUPING_LABEL_CATEGORIES) :category
    (= start-view-to-show GROUPING_LABEL_GROUPS) :group
    (= start-view-to-show GROUPING_LABEL_TAGS) :tag
    :else :type))

(defn is-current-detail-general?
  "Checks whether the passed arg 'category-detail' map is from :general-categories 
   or from :grouped-categories (for :type or :category or :tag)"
  [{:keys [entry-type-uuid group-uuid tag-id]}]
  (if (and (nil? entry-type-uuid) (nil? group-uuid) (nil? tag-id))
    true
    false))

(defn- general-category-detail-by-title
  "Gets the category detail map (struct CategoryDetail) from the general categories with given title"
  [db title]
  (let [general-categories (get-in-key-db db [:entry-category :data :general-categories])]
    (first (filter #(= (:title %) title) general-categories))))

;; valid value for kind is one of :category,:group,:type,:tag
(reg-event-fx
 :clear-and-show
 (fn [{:keys [_db]} [_event-id kind]]
   (let [cmn-actions [(if (= kind :group)
                        [:dispatch [:group-tree-content/load-groups-once]]
                        [:dispatch [:group-tree-content/clear-group-selection]])
                      [:dispatch [:selected-category-info nil]]
                      [:dispatch [:entry-list/clear-entry-items]]
                      [:dispatch [:entry-form-ex/show-welcome]]
                      [:dispatch [:load-combined-category-data kind]]]]
     ;; here cmn-actions is a vec of vec
     {:fx cmn-actions})))

;; kind may be :type, :tag, :category or :group and it represents the bottom view only
(reg-event-db
 :show-group-as
 (fn [db [_event-id kind]]
   (assoc-in-key-db db [:entry-category :showing-groups-as] kind)))

;; Called from group tree to clear previously selected category title
(reg-event-db
 :entry-category/clear-selected-category-info
 (fn [db [_]]
   (assoc-in-key-db db [:entry-category :selected-category-info] nil)))

(reg-event-db
 :entry-category/select-all-entries-category
 (fn [db [_event-id]]
   (let [v (general-category-detail-by-title db const/CATEGORY_ALL_ENTRIES)]
     (assoc-in-key-db db [:entry-category :selected-category-info] v))))

(reg-event-db
 :selected-category-info
 (fn [db [_ category-detail]]
   (assoc-in-key-db db [:entry-category :selected-category-info] category-detail)))

(reg-event-fx
 :entry-category/reload-category-data
 (fn [{:keys [db]} [_event-id]]
   ;;valid value is one of :category,:group,:type,:tag
   (let [view (get-in-key-db db [:entry-category :showing-groups-as])]
     {:fx [[:dispatch [:load-combined-category-data view]]]})))

;; This is called called after the db is opened and also when db is unlocked
(reg-event-fx
 :entry-category/category-data-load-start
 (fn [{:keys [_db]} [_event-id start-view-to-show]]
   ;; start-view-to-show is a string
   (let [start-view-to-show (to-entry-groupings-kind-kw start-view-to-show)]
     ;; valid value is one of :category or :group or :type or :tag
     {:fx [[:dispatch [:load-combined-category-data start-view-to-show]]]})))

(defn- show-as->grouping-kind
  "Converts the show-as kw to a string that is convertable to enum EntryCategoryGrouping"
  [kw-kind]
  (cond
    (= kw-kind :type)
    "AsTypes"

    (= kw-kind :tag)
    "AsTags"

    (= kw-kind :category)
    "AsGroupCategories"

    (= kw-kind :group)
    "AsGroupCategories"))

(reg-event-fx
 :load-combined-category-data
 (fn [{:keys [db]} [_event-id showing-groups-as]]
   ;; valid  value is one of :category or :group or :type or :tag
   {:db (assoc-in-key-db db [:entry-category :showing-groups-as] showing-groups-as)
    :fx [[:bg-combined-category-details [(active-db-key db) showing-groups-as]]]}))

(reg-fx
 :bg-combined-category-details
 (fn [[db-key showing-groups-as]]
   (bg/combined-category-details db-key (show-as->grouping-kind showing-groups-as)
                                 (fn [api-reponse]
                                   ;; EntryCategories
                                   (when-let [entry-categories (check-error api-reponse)]
                                     (dispatch [:update-category-data entry-categories]))))))

(defn sort-by-tag-name [grouped-categories]
  ;; First fn provides the comparision key
  ;; Second fn provides the comparator that uses the keys
  (sort-by (fn [m] (:title m)) (fn [v1 v2] (compare v1 v2)) grouped-categories))

;; Called when categories to show call returns with a list of map formed from struct EntryCategories
(reg-event-fx
 :update-category-data
 (fn [{:keys [db]} [_ entry-categories]]
   ;; The map 'entry-categories' is a map with keys [general-categories grouped-categories grouping-kind] 
   ;; The value of general-categories is a vec of map formed from struct CategoryDetail 
   ;; The value of grouped-categories is a vec of map formed from struct CategoryDetail for kind identified 
   ;; in grouping-kind. The 'grouping-kind' has the same value as in fn show-as->grouping-kind 

   (let [{:keys [grouping-kind grouped-categories]} entry-categories
         sorted-grouped-categories (if (= grouping-kind "AsTags")
                                     (sort-by-tag-name grouped-categories)
                                     grouped-categories)
         entry-categories (merge entry-categories {:grouped-categories sorted-grouped-categories})]
     {:db (assoc-in-key-db db [:entry-category :data] entry-categories)})))

(defn- is-in-group-categories
  "Returns the category name if the group is shown in category view or nil"
  [db category-group-uuid]
  (let [group-categories (get-in-key-db db [:entry-category :data :grouped-categories])
        found (filter (fn [{:keys [group-uuid]}]
                        (= group-uuid category-group-uuid)) group-categories)]
    (-> found first :title)))

(defn- selected-category-source
  "Gets the title, selected-category-detail and ecategory source based on  
   current showing kind - :type :category or :group
   Returns a map with keys [title category-source]
   The 'category-source' is deserializable to 'EntryCategory' enum and used in entry-list
  "
  [db group-uuid entry-type-uuid entry-type-name]
  (let [showing-as (get-in-key-db db [:entry-category :showing-groups-as])
        curr-cat-detail (get-in-key-db db [:entry-category :selected-category-info])
        grp-title (is-in-group-categories db group-uuid)
        category-source-title  (cond
                                 (and (= showing-as :type) (= (:title curr-cat-detail) entry-type-name))
                                 {:title entry-type-name
                                  :selected-category-detail curr-cat-detail
                                  :category-source {:entry-type-uuid entry-type-uuid}}

                                 (and (= showing-as :category) (not (nil? grp-title)))
                                 {:title grp-title
                                  :selected-category-detail curr-cat-detail
                                  :category-source {:group group-uuid}}

                                 (= showing-as :group)
                                 nil

                                 ;; for all other cases including showing-as = :tag
                                 :else
                                 {:title const/CATEGORY_ALL_ENTRIES
                                  :selected-category-detail (general-category-detail-by-title db const/CATEGORY_ALL_ENTRIES)
                                  :category-source const/CATEGORY_ALL_ENTRIES})]
    category-source-title))

;; Called from entry form after a new entry is inserted
(reg-event-fx
 :entry-category/entry-inserted
 (fn [{:keys [db]} [_event-id entry-uuid group-uuid entry-type-uuid entry-type-name]]
   (let [showing-as (get-in-key-db db [:entry-category :showing-groups-as])
         {:keys [title selected-category-detail category-source]} (selected-category-source
                                                                   db
                                                                   group-uuid
                                                                   entry-type-uuid
                                                                   entry-type-name)]
     {;; For now first we set selected-category-info and then loading of EntryCategories is called
      ;; Ideally, we should complete loading EntryCategories and then set the selected-category-info
      :db (if-not (nil? selected-category-detail)
            (-> db  (assoc-in-key-db [:entry-category :selected-category-info] selected-category-detail))
            db)
      :fx [;; Need to reload category data so that entries count are recent 
           [:bg-combined-category-details [(active-db-key db) showing-as]]

           ;; The title will not be nil, if :type or :category is selected or general-category is selected
           ;; In case of :group, the title will be nil and 
           ;; entry-inserted call is delegated to group-tree-content
           (if-not (nil? title)
             [:dispatch [:entry-list/entry-inserted entry-uuid category-source]]
             [:dispatch [:group-tree-content/entry-inserted entry-uuid group-uuid]])]})))

;; Called (in an entry_form event) after a custom entry type is successfully deleted 
(reg-event-fx
 :entry-category/entry-type-deleted
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:selected-category-info nil]]
         [:dispatch [:entry-list/clear-entry-items]]
         [:dispatch [:entry-form-ex/show-welcome]]
         [:bg-combined-category-details [(active-db-key db) :type]]
         [:dispatch [:show-group-as :type]]]}))

;;;;;;; 

;; Gets the group uuid if a group is selected in the category view
(reg-sub
 :entry-category/group-uuid-of-category
 ;; Uses syntax sugar which returns values from 3 elsewhere defined subscribes as vector of values 
 ;; instead of the follwing explicit signal-fn
 #_(fn [_query-vec _dynamic-vec] [(subscribe [..]) () ()])
 :<- [:showing-groups-as]
 :<- [:selected-category-info]
 (fn [[group-view-kind cat-detail-m] _query-vec]
   (when (= group-view-kind :category)
     (:group-uuid cat-detail-m))))

(reg-sub
 :showing-groups-as
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :showing-groups-as])))

(reg-sub
 :entry-category/showing-groups-as-category
 :<- [:showing-groups-as]
 (fn [group-view-kind _query-vec]
   (if (= group-view-kind :category) true false)))

;; The title of any current selected Category and this is mainly used to select that row 
;; in the category view panel
;; "Deleted" "AllEntries", "Favorites" or Group name (when Category is selected) or tag name or type name 
(reg-sub
 :selected-category-title
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :selected-category-title])))

(reg-sub
 :selected-category-info
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :selected-category-info])))

(reg-sub
 :is-selected-category
 (fn [db [_query-id cat-detail-m]]
   (let [{:keys [title entry-type-uuid group-uuid]} cat-detail-m
         curr-cat-detail (get-in-key-db db [:entry-category :selected-category-info])
         curr-cat-title (:title curr-cat-detail)
         curr-cat-entry-type-uuid (:entry-type-uuid curr-cat-detail)
         curr-cat-group-uuid (:group-uuid curr-cat-detail)]
     ;; group-view-kind may be anything as it represents the bottom view only
     (cond

       ;; This covers general categories(AllEntries, Favorite and Deleted) 
       ;; and also for :tag as :title value is tag name 
       (and (nil? entry-type-uuid) (nil? group-uuid))
       (=  title curr-cat-title)

       ;; for :type 
       (not (nil? entry-type-uuid))
       (= entry-type-uuid curr-cat-entry-type-uuid)

       ;; for :category
       (not (nil? group-uuid))
       (= group-uuid curr-cat-group-uuid)

       :else
       false))))


(reg-sub
 :entry-category/deleted-category-showing
 (fn [db _query-vec]
   (let [curr-cat-detail (get-in-key-db db [:entry-category :selected-category-info])]
     (if (is-current-detail-general? curr-cat-detail)
       (= (:title curr-cat-detail) const/CATEGORY_DELETED_ENTRIES)
       false))))

(reg-sub
 :group-categories
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :data :grouped-categories])))

(reg-sub
 :type-categories
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :data :grouped-categories])))

(reg-sub
 :tag-categories
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :data :grouped-categories])))

;; General categories are : AllEntries,Favorites,Deleted
(reg-sub
 :general-categories
 (fn [db _query-vec]
   (get-in-key-db db [:entry-category :data :general-categories])))

(reg-sub
 :all-entries-category
 (fn [db _query-vec]
   (general-category-detail-by-title db const/CATEGORY_ALL_ENTRIES)))

(reg-sub
 :deleted-entries-category
 (fn [db _query-vec]
   (general-category-detail-by-title db const/CATEGORY_DELETED_ENTRIES)))

(reg-sub
 :favorite-entries-category
 (fn [db _query-vec]
   (let [fc (general-category-detail-by-title db const/CATEGORY_FAV_ENTRIES)]
     (if (nil? fc)
       {:entries-count 0, :icon-id 0, :title const/CATEGORY_FAV_ENTRIES}
       fc))))

(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)
  (-> @re-frame.db/app-db (get db-key) :entry-category)
  (select-keys (-> @re-frame.db/app-db (get db-key) :entry-category) [:showing-groups-as :selected-category-title :selected-category-info]))
