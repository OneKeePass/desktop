(ns onekeepass.frontend.background.csv
  (:require
   [onekeepass.frontend.background-common :as bg-cmn
    :refer [invoke-api request-argon2key-transformer to-snake-case]]))


(defn import-cvs-file [file-full-path dispatch-fn]
  (invoke-api "import_csv_file" {:file-full-path file-full-path} dispatch-fn))

(defn clear-csv-data-cache [dispatch-fn]
  (invoke-api "clear_csv_data_cache" {} dispatch-fn))

;; TODO 
;; Need to fix using request-argon2key-transformer here and in create-kdbx when we handle 
;; the 'Kdf' enum serialization properly
;; Other palces fixing get-db-settings, set-db-settings
(defn create-new-db-with-imported-csv [new-db mapping dispatch-fn]
  ;; mapping is map from struct CsvImportMapping
  (invoke-api "create_new_db_with_imported_csv"
              ;; Note, here we are not using the automatic request transformation of keys
              (clj->js {:newDb (request-argon2key-transformer new-db)
                        :mapping (to-snake-case mapping)}) dispatch-fn :convert-request false))


(comment
  (-> @re-frame.db/app-db keys))
