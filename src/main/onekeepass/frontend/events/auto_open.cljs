(ns onekeepass.frontend.events.auto-open
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]

   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             update-db-opened
                                                             assoc-in-with-db-key
                                                             get-in-with-db-key
                                                             check-error]]
   [onekeepass.frontend.constants
    :as const
    :refer [GROUPING_LABEL_TYPES GROUPING_LABEL_TAGS
            GROUPING_LABEL_CATEGORIES GROUPING_LABEL_GROUPS]]
   [onekeepass.frontend.background-auto-open :as bg-ao]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))

(reg-event-fx
 :auto-open/verify-and-load
 (fn [{:keys [_db]} [_event-id main-db-key]]
   (bg-ao/auto-open-group-uuid main-db-key (fn [api-response]
                                             (when-some [ao-group-uuid (check-error api-response)]
                                               (dispatch [:load-child-databases main-db-key]))))
   {}))

(reg-event-fx
 :load-child-databases
 (fn [{:keys [_db]} [_event-id main-db-key]]
   {:fx [[:dispatch [:common/progress-message-box-show "Loading" "Please wait..."]]
         [:bg-load-open-all-auto-open-dbs [main-db-key]]]}))

(reg-fx
 :bg-load-open-all-auto-open-dbs
 (fn [[main-db-key]]
   (bg-ao/open-all-auto-open-dbs main-db-key
                                 (fn [api-response]
                                   ;; auto-open-dbs-info is map from AutoOpenDbsInfo
                                   (when-some [auto-open-dbs-info (check-error api-response)]
                                     (dispatch [:common/progress-message-box-hide])
                                     (dispatch [:open-all-auto-open-dbs-done auto-open-dbs-info]))))))

;; (dispatch [:load-all-tags-completed result])
#_(assoc-in-key-db db
                   [:tags :all]
                   (into []
                         (concat (:entry-tags result)
                                 (:group-tags result))))
#_(reg-event-db
   :groups-tree-data-update
   (fn [db [_event-id v]]
     (assoc-in-key-db db [:groups-tree :data] v)))

;; (reg-event-fx :update-category-data

#_(let [{:keys [grouping-kind grouped-categories]} entry-categories
        sorted-grouped-categories (if (= grouping-kind "AsTags")
                                    (sort-by-tag-name grouped-categories)
                                    grouped-categories)
        entry-categories (merge entry-categories {:grouped-categories sorted-grouped-categories})]
    {:db (assoc-in-key-db db [:entry-category :data] entry-categories)})

;; (assoc-in-key-db db [:entry-type-headers] et-headers-m)

;;  kdbx_loaded: KdbxLoaded,
;;   all_tags: AllTags,
;;   groups_tree: GroupTree,
;;   entry_categories: EntryCategories,
;;   entry_type_headers: EntryTypeHeaders,

;;   opened_dbs: Vec<AutoOpenedDbInitData>,

;;   // failed db keys with error message
;;   opening_failed_dbs: HashMap<String, String>,

;;   error_messages: Vec<String>,

;; (-> db :app-preference :default-entry-category-groupings)

(defn to-entry-groupings-kind-kw
  "Converts the string value of grouping label to an appropriate keyword"
  [start-view-to-show]
  (cond
    (= start-view-to-show GROUPING_LABEL_TYPES) :type
    (= start-view-to-show GROUPING_LABEL_CATEGORIES) :category
    (= start-view-to-show GROUPING_LABEL_GROUPS) :group
    (= start-view-to-show GROUPING_LABEL_TAGS) :tag
    :else :type))

(defn sort-by-tag-name [grouped-categories]
  ;; First fn provides the comparision key
  ;; Second fn provides the comparator that uses the keys
  (sort-by (fn [m] (:title m)) (fn [v1 v2] (compare v1 v2)) grouped-categories))

(defn open-child-database [app-db {:keys [kdbx-loaded all-tags groups-tree entry-categories entry-type-headers]}]
  (let [{:keys [db-key]} kdbx-loaded
        showing-as (to-entry-groupings-kind-kw (-> app-db :app-preference :default-entry-category-groupings))
        {:keys [grouping-kind grouped-categories]} entry-categories
        sorted-grouped-categories (if (= grouping-kind "AsTags")
                                    (sort-by-tag-name grouped-categories)
                                    grouped-categories)
        entry-categories (merge entry-categories {:grouped-categories sorted-grouped-categories})]
    (-> app-db 
        (update-db-opened kdbx-loaded)
        (assoc-in-with-db-key db-key
                              [:tags :all]
                              (into []
                                    (concat (:entry-tags all-tags)
                                            (:group-tags all-tags))))
        (assoc-in-with-db-key db-key [:groups-tree :data] groups-tree)
        (assoc-in-with-db-key db-key [:entry-category :showing-groups-as] showing-as) 
        (assoc-in-with-db-key db-key [:entry-category :data] entry-categories) 
        (assoc-in-with-db-key db-key [:entry-type-headers] entry-type-headers))))

(defn open-all-child-databases 
  "Returns the updated app-db"
  [app-db opened-dbs]
  (reduce (fn [db db-init-data] (open-child-database db db-init-data)) app-db opened-dbs))

(reg-event-fx
 :open-all-auto-open-dbs-done
 (fn [{:keys [db]} [_event-id {:keys [opened-dbs opening-failed-dbs error-messages] :as auto-open-dbs-info}]]
   (if-not (empty? opened-dbs)
     {:db (-> db (open-all-child-databases opened-dbs))
      
      }
     {})
   #_{:db (-> db (assoc-in [:auto-open-dbs-info] auto-open-dbs-info))}))

(comment
  (-> @re-frame.db/app-db keys)
  (require '[clojure.pprint :refer [pprint]])
  (->  @re-frame.db/app-db (get db-key) keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db)))



#_(reg-event-fx
   :auto-open-load-databases
   (fn [{:keys [_db]} [_event-id main-db-key]]
     (bg-ao/open-all-auto-open-dbs main-db-key (fn [api-response]
                                               ;; auto-open-dbs-info is map from AutoOpenDbsInfo
                                                 (when-some [auto-open-dbs-info (check-error api-response)]
                                                   (dispatch [:open-all-auto-open-dbs-done auto-open-dbs-info]))))
     {}))

#_(reg-event-fx
   :auto-open/load-child-databases
   (fn [{:keys [db]} [_event-id main-db-key]]
     (let [group-tree-data (get-in-with-db-key db main-db-key [:groups-tree :data])
           ao-group-uuid (get group-tree-data "auto_open_group_uuid")]

       (println "(empty? group-tree-data) (not (nil? ao-group-uuid)) " (empty? group-tree-data) (not (nil? ao-group-uuid)))
       (if (or (empty? group-tree-data) (not (nil? ao-group-uuid)))
         {:fx [[:bg-load-open-all-auto-open-dbs [main-db-key]]]}
         {}))))


