(ns onekeepass.frontend.background.merging
  (:require
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]
   [onekeepass.frontend.background-common :as bg-cmn :refer [invoke-api]]))


(defn merge-databases
  "Calls the API to merge two databases.
   Calls the dispatch-fn with the result or error
  "
  [target-db-key source-db-key  password key-file-name dispatch-fn]
  (invoke-api "merge_databases"
              {:target-db-key target-db-key
               :source-db-key source-db-key
               :password password
               :key-file-name key-file-name} dispatch-fn))

(defn merge-kdbx-with-disk-version
  "Merges the on-disk version of the DB into the in-memory version using the
   stored composite key. No credential re-entry needed.
   Calls dispatch-fn with MergeResult on success."
  [db-key dispatch-fn]
  (invoke-api "merge_kdbx_with_disk_version" {:db-key db-key} dispatch-fn))