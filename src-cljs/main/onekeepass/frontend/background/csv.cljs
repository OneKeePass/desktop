(ns onekeepass.frontend.background.csv
  (:require
   [onekeepass.frontend.background-common :as bg-cmn
    :refer [invoke-api]]))


(defn import-cvs-file [file-full-path dispatch-fn]
  (invoke-api "import_csv_file" {:file-full-path file-full-path} dispatch-fn))

(defn clear-csv-data-cache [dispatch-fn]
  (invoke-api "clear_csv_data_cache" {} dispatch-fn))

(defn csv-import-profiles
  "Products offered in the 'exported from' dropdown of the mapping dialog"
  [dispatch-fn]
  (invoke-api "csv_import_profiles" {} dispatch-fn))

(defn csv-import-profile-mapping
  "Column mapping a chosen profile suggests for the loaded header row"
  [profile-id headers dispatch-fn]
  (invoke-api "csv_import_profile_mapping" {:profile-id profile-id :headers headers} dispatch-fn))

(defn create-new-db-with-imported-csv [new-db mapping dispatch-fn]
  ;; mapping is map from struct CsvImportMapping
  (invoke-api "create_new_db_with_imported_csv" {:new-db new-db :mapping mapping} dispatch-fn))

(defn update-db-with-imported-csv [db-key mapping dispatch-fn]
  (invoke-api "update_db_with_imported_csv" {:db-key db-key
                                             :mapping mapping} dispatch-fn))


(comment
  (-> @re-frame.db/app-db keys))
