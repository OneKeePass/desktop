(ns onekeepass.frontend.events.auto-open
  (:require
   [onekeepass.frontend.events.common :as cmn-events :refer [update-db-opened
                                                             assoc-in-with-db-key

                                                             check-error]]
   [onekeepass.frontend.constants
    :as const
    :refer [GROUPING_LABEL_TYPES GROUPING_LABEL_TAGS
            GROUPING_LABEL_CATEGORIES GROUPING_LABEL_GROUPS]]
   [onekeepass.frontend.background-auto-open :as bg-ao]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))

;; Called when a database is loaded 
(reg-event-fx
 :auto-open/verify-and-load
 (fn [{:keys [_db]} [_event-id main-db-key]]
   ;; Side effect call
   (bg-ao/auto-open-group-uuid main-db-key 
                               (fn [api-response] 
                                 (when-some [_ao-group-uuid (check-error api-response)]
                                   ;; Load all child databases if we find a AutoOpen group with entries
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

;; TODO: 
;; to-entry-groupings-kind-kw and sort-by-tag-name are copied from entry-category
;; Need to move a common package
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

;; IMPORTANT
;; All steps are done as in reg-event-fx :common/kdbx-database-loading-complete are done here 
;; when we open a kdbx databse. If we add anything that is required on opening a database, we need to include here

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

 (fn [{:keys [db]} [_event-id {:keys [opened-dbs opening-failed-dbs error-messages] :as _auto-open-dbs-info}]]
   ;;(println "opening-failed-dbs error-messages " opening-failed-dbs error-messages)
   ;; TODO: 
   ;; Need to take care of 'opening-failed-dbs' and 'error-messages' 
   ;; and if we find any values there, we need to show those errors 
   (if-not (empty? opened-dbs)
     {:db (-> db (open-all-child-databases opened-dbs))}
     {})))

(comment
  (-> @re-frame.db/app-db keys)
  (require '[clojure.pprint :refer [pprint]])
  (->  @re-frame.db/app-db (get db-key) keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db)))
