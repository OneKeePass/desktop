(ns onekeepass.frontend.events.group-tree-content
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub dispatch subscribe]]
   [onekeepass.frontend.utils :refer [contains-val?]]
   [onekeepass.frontend.events.common  :refer [active-db-key
                                               get-in-key-db
                                               assoc-in-key-db
                                               on-error
                                               check-error]]
   [onekeepass.frontend.background :as bg]))

(set! *warn-on-infer* true)

(defn initiate-new-blank-group-form [parent-group-uuid]
  (dispatch [:group-form/create-blank-group parent-group-uuid]))

(defn groups-tree-data
  "Gets the group tree data"
  []
  (subscribe [:groups-tree-data-updated]))

(defn selected-group
  "The group uuid of the selected group on the tree item"
  []
  (subscribe [:selected-group-uuid]))

(defn recycle-group-selected? []
  (subscribe [:group-tree-content/recycle-group-selected]))

(defn selected-group-in-recycle-bin? []
  (subscribe [:group-tree-content/selected-group-in-recycle-bin]))

(defn root-group-selected? []
  (subscribe [:group-tree-content/root-group-selected]))

#_(defn root-group-uuid []
    (subscribe [:group-tree-content/root-group-uuid]))

(defn groups-listing []
  (subscribe [:group-tree-content/groups-listing]))

(defn node-on-select
  "Called when a tree item is selected with value found ':nodeId' attribute of mui-tree-item.
  The value of ':nodeId' is the group uuid of the group selected
  "
  [_e group-id]
  (dispatch [:group-selected group-id])
  ;;Reset other panels 
  (dispatch [:entry-form-ex/show-welcome])
  (dispatch [:entry-category/clear-selected-category-info])

  ;;Loads entry list items to show for this group
  (dispatch [:entry-list/load-entry-items {:group group-id}]))

(defn on-node-toggle [_e node-ids]
  (dispatch [:mark-expanded-nodes node-ids]))

(defn expanded-nodes []
  (subscribe [:expanded-nodes]))

(defn recycle-bin-empty-check []
  (subscribe [:recycle-bin-empty-check]))


;;;;;;;;;

(reg-event-db
 :group-tree-content/clear-group-selection
 (fn [db [_eid]]
   (assoc-in-key-db db [:groups-tree :selected-group-uuid] nil)))

(reg-event-fx
 :group-selected
 (fn [{:keys [db]} [_id group-uuid]]
   {:db (assoc-in-key-db db [:groups-tree :selected-group-uuid]  group-uuid)
    ;;:fx [[:dispatch [:onekeepass.frontend.events.entry-list/show {:group group-uuid}]]]
    }))

;; Called from system menu
(reg-event-fx
 :group-tree-content/new-group
 (fn [{:keys [db]} [_id]]
   (let [parent-group-uuid (get-in-key-db db [:groups-tree :selected-group-uuid])]
     (if-not (nil? parent-group-uuid)
       {:fx [[:dispatch [:group-form/create-blank-group parent-group-uuid]]]}
       {}))))

;; Called from system menu
(reg-event-fx
 :group-tree-content/edit-group
 (fn [{:keys [db]} [_id]]
   (let [group-uuid (get-in-key-db db [:groups-tree :selected-group-uuid])]
     (if-not (nil? group-uuid)
       {:fx [[:dispatch [:group-form/find-group-by-id group-uuid :edit]]]}
       {}))))

;; Called when nodes are expanded or collapsed in Treeview 
(reg-event-db
 :mark-expanded-nodes
 (fn [db [_eid node-ids]] ;; node-ids is a js array
   (assoc-in-key-db db [:groups-tree :expanded-nodes] (js->clj node-ids))))

(defn- group-summary-load-callback [api-response]
  (when-let [result (check-error api-response)]
    (dispatch [:groups-tree-data-update (js->clj result)])))

;;;;;;; 

;;This event handler loads the group summary data once only.  
(reg-event-fx
 :group-tree-content/load-groups-once
 (fn [{:keys [db]} [_event-id]]
   (let [data (get-in-key-db db [:groups-tree :data])]
     (if  (nil? data)
       {:fx [[:dispatch [:groups-tree-data-update nil]]
             [:load-bg-groups-summary-data (active-db-key db)]]}
       {}))))

;; TODO: It appears, 'load-groups' is not called for all db changes
;; For example, when a new entry is created, the tree data is not loaded 
;; and because of that 'entry_uuids' does not include the newly created 
;; entry under the parent group
;; Needs fixing

;;An event to reload group tree data whenever any group data update or insert is done
(reg-event-fx
 :group-tree-content/load-groups
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:groups-tree-data-update nil]]
         [:load-bg-groups-summary-data (active-db-key db)]]}))

(reg-fx
 :load-bg-groups-summary-data
 (fn [db-key]
   (bg/groups-summary-data db-key group-summary-load-callback)))

(reg-event-db
 :groups-tree-data-update
 (fn [db [_event-id v]]
   (assoc-in-key-db db [:groups-tree :data] v)))

;; Called when a new entry is created under a group selected in the category view
(reg-event-fx
 :group-tree-content/entry-inserted
 (fn [{:keys [_db]} [_event-id entry-uuid  group-uuid]]
   {:fx [[:dispatch [:group-selected group-uuid]]
         [:dispatch [:entry-list/entry-inserted entry-uuid {:group group-uuid}]]]}))

(reg-sub
 :groups-tree-data-updated
 (fn [db _query-vec]
   (get-in-key-db db [:groups-tree :data])))

(reg-sub
 :group-tree-content/root-group-uuid
 :<- [:groups-tree-data-updated]
 (fn [data _query-vec]
   (get data "root_uuid")))

(reg-sub
 :group-tree-content/root-group-selected
 :<- [:group-tree-content/root-group-uuid]
 :<- [:selected-group-uuid]
 (fn [[root-uuid selected-uuid] _query-vec]
   (= root-uuid selected-uuid)))

(reg-sub
 :selected-group-uuid
 (fn [db _query-vec]
   (get-in-key-db db [:groups-tree :selected-group-uuid])))

;; Returns true if the current selected group in tree view is recycle group or false
(reg-sub
 :group-tree-content/recycle-group-selected
 :<- [:groups-tree-data-updated]
 :<- [:selected-group-uuid]
 (fn [[data selected] _query-vec]
   (= (get data "recycle_bin_uuid") selected)))

;; Checks whether there are any deleted entry or group in recycle bin
;; Returns true if both lists are empty
(reg-sub
 :recycle-bin-empty-check
 :<- [:groups-tree-data-updated]
 (fn [data _query-vec]
   (let [rc-uuid (get data "recycle_bin_uuid")
         {:strs [entry_uuids group_uuids]} (-> data (get "groups") (get rc-uuid))]
     (if (or (> (count entry_uuids) 0) (> (count group_uuids) 0))
       false
       true))))

;;Checks whether the selected group is the deleted group uuids 
(reg-sub
 :group-tree-content/selected-group-in-recycle-bin
 :<- [:groups-tree-data-updated]
 :<- [:selected-group-uuid]
 (fn [[data selected]]
   ;;deleted_group_uuids does not include recycle bin group uuid itself
   ;;Seed method deleted_uuids method in db_content.rs
   (let [recycle-groups  (get data "deleted_group_uuids")]
     ;; the following is equivalent to  '(not (empty? (filter #(= % selected)  recycle-groups)))' 
     ;;(boolean (seq (filter #(= % selected)  recycle-groups)))
     (contains-val? recycle-groups selected))))

;; Checks whether a given group uuid is a recycle bin group or found in deleted_group_uuids vector
(reg-sub
 :group-tree-content/group-in-recycle-bin
 :<- [:groups-tree-data-updated]
 (fn [data [_query-id group-uuid]]
   (let [recycled-groups  (get data "deleted_group_uuids")
         recycle-bin-group (get data "recycle_bin_uuid")]
     (or (= recycle-bin-group group-uuid) 
         (contains-val?  recycled-groups group-uuid)))))

;; Forms a group info map for any selected group in group tree view or a group category 
;; in category view
(reg-sub
 :group-tree-content/group-summary-info
 :<- [:entry-category/group-uuid-of-category]
 :<- [:selected-group-uuid]
 :<- [:groups-tree-data-updated]
 (fn [[uuid1 uuid2 data] _query-vec]
   ;; Either uuid1 or uuid2 should be available 
   ;; uuid1  is non nil if :showing-group-as has the ':category' value in entry-category. 
   ;; uuid2 is non nil if group tree panel is active 
   ;; with some group is selected
   (let [uuid (if (nil? uuid1) uuid2 uuid1)
         g (-> data (get "groups") (get uuid))]
     ;; TODO g should not be nil. If nil, need to log and return root group
     (when-not (nil? g)
       {:name (get g "name")
        :uuid (get g "uuid")
        :icon-id 0}))))

(reg-sub
 :group-tree-content/groups-listing
 :<- [:groups-tree-data-updated]
 (fn [data _query-vec]
   (let [groups (-> data (get "groups"))
         recycle-groups  (get data "deleted_group_uuids")
         recycle-groups (conj recycle-groups  (get data "recycle_bin_uuid"))]
     ;;(println "groups size... " (count groups) ) 
     (as-> (vals groups) coll
       ;; Only groups that are not deleted
       (filter #(not (contains-val? recycle-groups (get % "uuid"))) coll)
       ;; Few fiedlds for each group
       (map (fn [v] {:name (get v "name")
                     :uuid (get v "uuid")
                     :icon-id 0}) (filter #(not (contains-val? recycle-groups %))
                                          coll))
       ;; Sort by name - Note kw :name vs "name" used previously
       (sort-by (fn [v] (:name v)) coll)))))

(reg-sub
 :expanded-nodes
 (fn [db _query-vec]
   (get-in-key-db db [:groups-tree :expanded-nodes])))


;;;;;;;;;;;;;;;;;; Group Delete      ;;;;;;;;;;;;;;;;;

(defn group-delete-start [group-uuid]
  (dispatch [:group-delete-start group-uuid]))

(reg-event-fx
 :group-delete-start
 (fn [{:keys [db]} [_event-id  group-uuid]]
   {:db (-> db (assoc-in-key-db  [:group-delete :status] :in-progress))
    :fx [[:bg-move-group-to-recycle-bin [(active-db-key db) group-uuid]]]}))

(reg-fx
 :bg-move-group-to-recycle-bin
 (fn [[db-key group-uuid]]
   ;;(println "Going to call bg/move-group-to-recycle_bin")
   (bg/move-group-to-recycle_bin db-key group-uuid (fn [api-response]
                                                     (when-not (on-error api-response)
                                                       (dispatch [:group-delete-completed]))))))

(reg-event-fx
 :group-delete-completed
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in-key-db  [:group-delete :status] :completed))
    :fx [[:dispatch [:common/message-snackbar-open "Group is deleted"]]
         [:dispatch [:common/refresh-forms-2]]]}))

;;;;;;;;;;;;;;;;;; Group Delete End ;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;; Group Put back  ;;;;;;;;;;;;;;;;;;;;

;; See onekeepass.frontend.events.move-group-entry for corresponding events


(comment

  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) :groups-tree)
  (re-frame.core/clear-subscription-cache!))
