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
                     on-error
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

(defn- grouping-kind->pref-entry-category-groupings
  "Converts the show-as kw to a string that is used in app preference settings"
  [kw-kind]
  (cond
    (= kw-kind :type)
    GROUPING_LABEL_TYPES

    (= kw-kind :tag)
    GROUPING_LABEL_TAGS

    (= kw-kind :category)
    GROUPING_LABEL_CATEGORIES

    (= kw-kind :group)
    GROUPING_LABEL_GROUPS))

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
 (fn [{:keys [db]} [_event-id kind]]
   (let [cmn-actions [(if (= kind :group)
                        [:dispatch [:group-tree-content/load-groups-once]]
                        [:dispatch [:group-tree-content/clear-group-selection]])
                      [:dispatch [:selected-category-info nil]]
                      [:dispatch [:entry-list/clear-entry-items]]
                      [:dispatch [:entry-form-ex/show-welcome]]
                      [:dispatch [:load-combined-category-data kind]]
                      [:bg-entry-category-groupings-preference [kind]]
                      ]]
     ;; here cmn-actions is a vec of vec
     ;; :first-non-empty => once the new grouping's category data is loaded,
     ;; auto-select the first item that has entries (falls back to All Entries)
     ;; so the list/form panels are not left blank after switching grouping.
     {:db (assoc-in-key-db db [:entry-category :auto-select-on-load] :first-non-empty)
      :fx cmn-actions})))

(reg-fx
   :bg-entry-category-groupings-preference
   (fn [[showin-kind-kw]]
     (bg/update-preference {:default-entry-category-groupings (grouping-kind->pref-entry-category-groupings showin-kind-kw)}
                           (fn [api-reponse]
                             (when-not (on-error api-reponse)
                               ;; Reloads the whole app preference 
                               ;; This ensures the pass phrase option in preference data is the updated one 
                               (dispatch [:common/load-app-preference]))))))

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
;; select-all-on-load? (optional) - when true, "All Entries" is auto-selected and
;; its entry list is loaded once the category data finishes loading (see
;; :update-category-data). Used on db open so the panes are not blank.
(reg-event-fx
 :entry-category/category-data-load-start
 (fn [{:keys [db]} [_event-id start-view-to-show select-all-on-load?]]
   ;; start-view-to-show is a string
   (let [start-view-to-show (to-entry-groupings-kind-kw start-view-to-show)]
     ;; valid value is one of :category or :group or :type or :tag
     {:db (assoc-in-key-db db [:entry-category :auto-select-on-load]
                           (when select-all-on-load? :all-entries))
      :fx [[:dispatch [:load-combined-category-data start-view-to-show]]]})))

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
         entry-categories (merge entry-categories {:grouped-categories sorted-grouped-categories})
         ;; One-shot mode set on db open (:all-entries) or on grouping switch
         ;; (:first-non-empty). The entries-count needed to pick a non-empty item
         ;; is only known now that the category data has loaded, so the
         ;; auto-selection is done here and the mode is then cleared.
         auto-mode (get-in-key-db db [:entry-category :auto-select-on-load])
         new-db (-> db
                    (assoc-in-key-db [:entry-category :data] entry-categories)
                    (assoc-in-key-db [:entry-category :auto-select-on-load] nil))]
     (cond-> {:db new-db}
       (= auto-mode :all-entries)
       (assoc :fx [[:dispatch [:entry-category/select-all-entries-category]]
                   [:dispatch [:entry-list/load-entry-items const/CATEGORY_ALL_ENTRIES]]])

       (= auto-mode :first-non-empty)
       (assoc :fx [[:dispatch [:entry-category/auto-select-first-non-empty]]])))))

;; fx that selects "All Entries" and loads its list - the universal fallback
;; when nothing better can be auto-selected after a grouping switch.
(def ^:private all-entries-fallback-fx
  [[:dispatch [:entry-category/select-all-entries-category]]
   [:dispatch [:entry-list/load-entry-items const/CATEGORY_ALL_ENTRIES]]])

(defn- first-group-with-entry
  "Pre-order DFS from the root group over the groups-tree data (string-keyed map
   from bg/groups-summary-data). Returns the uuid of the first group - starting
   from root - that has at least one entry, skipping the recycle bin and any
   deleted groups. Returns nil if no such group exists (or data is nil)."
  [tree-data]
  (when tree-data
    (let [groups (get tree-data "groups")
          recycle-bin (get tree-data "recycle_bin_uuid")
          deleted (set (get tree-data "deleted_group_uuids"))
          excluded? (fn [uuid] (or (= uuid recycle-bin) (contains? deleted uuid)))]
      (letfn [(walk [uuid]
                (when (and uuid (not (excluded? uuid)))
                  (let [g (get groups uuid)]
                    (if (seq (get g "entry_uuids"))
                      uuid
                      (some walk (get g "group_uuids"))))))]
        (walk (get tree-data "root_uuid"))))))

;; Selects the first item that has at least one entry and loads its entry list,
;; so the list/form panels are not left blank after a grouping switch:
;; - flat views (:type/:tag/:category): first non-empty grouped category
;; - :group tree view: first group with an entry, walked from the root
;; Falls back to "All Entries" when nothing non-empty is found.
(reg-event-fx
 :entry-category/auto-select-first-non-empty
 (fn [{:keys [db]} [_event-id]]
   (let [showing-as (get-in-key-db db [:entry-category :showing-groups-as])]
     (if (= showing-as :group)
       ;; Group tree view - pick the first group (from root) that has an entry
       (if-let [g-uuid (first-group-with-entry (get-in-key-db db [:groups-tree :data]))]
         {:fx [[:dispatch [:entry-category/clear-selected-category-info]]
               [:dispatch [:group-selected g-uuid]]
               ;; Expand the chain down to g-uuid so the selected group is visible
               ;; even when it is nested under collapsed parents
               [:dispatch [:group-tree-content/expand-ancestors g-uuid]]
               [:dispatch [:entry-form-ex/show-welcome]]
               [:dispatch [:entry-list/load-entry-items {:group g-uuid}]]]}
         {:fx all-entries-fallback-fx})
       ;; Flat views (:type/:tag/:category) - first non-empty grouped category
       (let [grouped (get-in-key-db db [:entry-category :data :grouped-categories])
             first-non-empty (->> grouped
                                  (filter (fn [{:keys [group-uuid entry-type-uuid tag-id entries-count]}]
                                            (and (or group-uuid entry-type-uuid tag-id)
                                                 (pos? (or entries-count 0)))))
                                  first)]
         (if first-non-empty
           (let [{:keys [group-uuid entry-type-uuid title]} first-non-empty
                 category-source (case showing-as
                                   :type {:entry-type-uuid entry-type-uuid}
                                   :tag  {:tag title}
                                   ;; :category (and any other flat view)
                                   {:group group-uuid})]
             {:db (assoc-in-key-db db [:entry-category :selected-category-info] first-non-empty)
              :fx [[:dispatch [:group-tree-content/clear-group-selection]]
                   [:dispatch [:entry-form-ex/show-welcome]]
                   [:dispatch [:entry-list/load-entry-items category-source]]]})
           {:fx all-entries-fallback-fx}))))))

(defn- grouped-category-detail
  "Finds a category detail map (struct CategoryDetail) from the grouped categories
   whose value for key 'k' equals 'v'. Returns nil when there is no match."
  [db k v]
  (let [grouped-categories (get-in-key-db db [:entry-category :data :grouped-categories])]
    (first (filter #(= (get % k) v) grouped-categories))))

(defn- selected-category-source
  "Determines which category should be selected/highlighted and which entry list
   should be shown after a new entry is inserted into 'group-uuid' with 'tags'.

   The selection follows the entry's destination so the category panel, the entry
   list and the entry form stay consistent even when the user picks a group (or
   edits tags) in the new entry form that differs from the currently selected
   category.

   Returns a map with keys [title selected-category-detail category-source], or nil
   for the :group grouping where selection is delegated to the group tree.
   The 'category-source' is deserializable to the 'EntryCategory' enum used by entry-list."
  [db group-uuid entry-type-uuid entry-type-name tags]
  (let [showing-as (get-in-key-db db [:entry-category :showing-groups-as])
        all-entries-source (fn []
                             {:title const/CATEGORY_ALL_ENTRIES
                              :selected-category-detail (general-category-detail-by-title db const/CATEGORY_ALL_ENTRIES)
                              :category-source const/CATEGORY_ALL_ENTRIES})]
    (cond
      ;; Showing entry types: follow the new entry's own type category
      (= showing-as :type)
      (if-let [detail (grouped-category-detail db :entry-type-uuid entry-type-uuid)]
        {:title entry-type-name
         :selected-category-detail detail
         :category-source {:entry-type-uuid entry-type-uuid}}
        (all-entries-source))

      ;; Showing group categories: follow the entry's destination group category
      (= showing-as :category)
      (if-let [detail (grouped-category-detail db :group-uuid group-uuid)]
        {:title (:title detail)
         :selected-category-detail detail
         :category-source {:group group-uuid}}
        (all-entries-source))

      ;; Showing tag categories: keep the previously selected tag highlighted only
      ;; when the saved entry still carries that tag (the user may have edited tags
      ;; in the form); otherwise fall back to All Entries
      (= showing-as :tag)
      (let [selected-tag (get-in-key-db db [:entry-category :selected-category-info :tag-id])]
        (if-let [detail (and (seq selected-tag)
                             (some #(= % selected-tag) tags)
                             (grouped-category-detail db :tag-id selected-tag))]
          {:title selected-tag
           :selected-category-detail detail
           :category-source {:tag selected-tag}}
          (all-entries-source)))

      ;; Showing the group tree: selection is handled by group-tree-content
      (= showing-as :group)
      nil

      :else
      (all-entries-source))))

;; Called from entry form after a new entry is inserted
(reg-event-fx
 :entry-category/entry-inserted
 (fn [{:keys [db]} [_event-id entry-uuid group-uuid entry-type-uuid entry-type-name tags]]
   (let [showing-as (get-in-key-db db [:entry-category :showing-groups-as])
         {:keys [title selected-category-detail category-source]} (selected-category-source
                                                                   db
                                                                   group-uuid
                                                                   entry-type-uuid
                                                                   entry-type-name
                                                                   tags)]
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
