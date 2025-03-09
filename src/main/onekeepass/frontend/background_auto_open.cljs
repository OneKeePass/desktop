(ns onekeepass.frontend.background-auto-open
  (:require
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]
   [onekeepass.frontend.background-common :as bg-cmn :refer [invoke-api]]))

(def GT "groups_tree")

(def GT-KW :groups-tree)

(defn- transform-ao-init-data-response
  "The arg is a map from struct AutoOpenedDbInitData where keys are still string"
  [auto-open-init-data]
  (let [;; We need to exclude 'groups_tree' field from the incoming map 
        without-groups-tree-m (dissoc auto-open-init-data GT)
        ;; Groups tree data 
        groups-tree-data (get auto-open-init-data GT)
        ;; We transform all other keys of maps recursively
        transformed-m (cske/transform-keys csk/->kebab-case-keyword without-groups-tree-m)]
    ;; Add back the Groups tree data to the final map
    (assoc transformed-m GT-KW groups-tree-data)))

(defn- transform-ao-dbs-info
  "The arg auto-open-dbs-info is a map from struct AutoOpenDbsInfo"
  [auto-open-dbs-info]
  (let [;; Only top level keys of the incoming map are transformed
        keys-transformed-m (->> auto-open-dbs-info (map (fn [[k v]] [(csk/->kebab-case-keyword k) v])) (into {}))
        ;; extract the vec of maps (struct AutoOpenedDbInitData) in key :opened-dbs
        init-data-v (:opened-dbs keys-transformed-m)
        ;; Apply the custom key transform to each map found in that vec
        init-data-v (mapv transform-ao-init-data-response init-data-v)]
    ;; Replace the :opened-dbs value to this map with transformed key
    (assoc keys-transformed-m :opened-dbs init-data-v)))

(defn open-all-auto-open-dbs 
  "Called to auto open all child databases using entries found under AutoOpen group of this db"
  [db-key dispatch-fn]
  ;; The dispatch-fn is called with a map corresponding to struct 'AutoOpenDbsInfo'
  ;; We need to take care of transformation of api response by custom transformer fn where  we 
  ;; do 'keywordize-keys' all keys except "groups_tree" in the response
  (invoke-api "open_all_auto_open_dbs" {:db-key db-key}  dispatch-fn :convert-response-fn transform-ao-dbs-info))

(defn resolve-auto-open-properties [auto-open-properties dispatch-fn]
  (invoke-api "resolve_auto_open_properties" {:autoOpenProperties auto-open-properties} dispatch-fn))

(defn auto-open-group-uuid [db-key dispatch-fn]
  ;; ;convert-response false ensures that returned uuid is not transformed to kw
  (invoke-api "auto_open_group_uuid" {:db-key db-key}  dispatch-fn :convert-response false))

(comment
  (-> @re-frame.db/app-db keys)
  (require '[clojure.pprint :refer [pprint]])
  (->  @re-frame.db/app-db (get db-key) keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db))

  (def test-atom (atom nil))

  (open-all-auto-open-dbs (:current-db-file-name @re-frame.db/app-db) #(reset! test-atom %)))

