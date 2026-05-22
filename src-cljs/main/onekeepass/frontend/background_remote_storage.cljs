(ns onekeepass.frontend.background-remote-storage
  "Invoke wrappers for the desktop Tauri remote-storage commands. Desktop
   is kdbx-only: there's no rs_remote_storage_configs / rs_delete_config
   / rs_read_configs surface here — those are mobile-only blob-store
   CRUD. To delete a desktop connection, the user edits the underlying
   kdbx entry directly."
  (:require
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.background-common :as bg-cmn :refer [invoke-api]]))

(set! *warn-on-infer* true)

(def kw-type-to-enum-tag {:sftp const/V-SFTP :webdav const/V-WEBDAV})

(defn as-rs-type [value]
  (if (keyword? value) (value kw-type-to-enum-tag) value))

;; ----- Connection / browse -----

(defn connect-and-retrieve-root-dir
  "Connects using the inline connection-info map and lists the server root
   dir. The rs-operation-type follows the enum RemoteStorageOperationType:
   {:type 'Sftp'|'Webdav' :connection-info <SftpConnectionConfig|WebdavConnectionConfig>}."
  [type connection-info dispatch-fn]
  (invoke-api "rs_connect_and_retrieve_root_dir"
              {:rs-operation-type {:type (as-rs-type type)
                                   :connection-info connection-info}}
              dispatch-fn))

(defn connect-by-id-and-retrieve-root-dir
  "Connects using a previously stored connection (kdbx-entry source on
   desktop) identified by connection-id and lists the server root dir."
  [type connection-id dispatch-fn]
  (invoke-api "rs_connect_by_id_and_retrieve_root_dir"
              {:rs-operation-type {:type (as-rs-type type)
                                   :connection-id connection-id}}
              dispatch-fn))

(defn list-sub-dir
  "Lists files/sub-dirs under <parent-dir>/<sub-dir> on the connected
   remote server."
  [type connection-id parent-dir sub-dir dispatch-fn]
  (invoke-api "rs_list_sub_dir"
              {:rs-operation-type {:type (as-rs-type type)
                                   :connection-id connection-id
                                   :parent-dir parent-dir
                                   :sub-dir sub-dir}}
              dispatch-fn))

;; ----- Db read / save / create over remote -----

(defn read-kdbx
  "Reads a kdbx file from remote storage and opens it. The db-file-name
   is the prefixed remote db_key (e.g. \"Sftp-<uuid>-/path/to/db.kdbx\")."
  [db-file-name password key-file-name dispatch-fn]
  (invoke-api "rs_read_kdbx"
              {:db-file-name db-file-name
               :password password
               :key-file-name key-file-name}
              dispatch-fn))

(defn save-kdbx
  "Saves the in-memory db to remote storage. overwrite=false enables
   intra-session conflict detection (returns DbFileContentChangeDetected
   if the remote file's mtime has changed since open or last save)."
  [db-key overwrite dispatch-fn]
  (invoke-api "rs_save_kdbx"
              {:db-key db-key
               :overwrite overwrite}
              dispatch-fn))

(defn create-kdbx
  "Creates a new kdbx file on remote storage. The new-db map's
   :database-file-name is the prefixed remote db_key."
  [new-db dispatch-fn]
  (invoke-api "rs_create_kdbx" {:new-db new-db} dispatch-fn))

(defn check-remote-modified
  "Returns true if the remote file's mtime has diverged from the value
   recorded at open / last save. Used by the focus-poll detector."
  [db-key dispatch-fn]
  (invoke-api "rs_check_remote_modified" {:db-key db-key} dispatch-fn))

(defn merge-with-remote
  "Downloads the current remote bytes and merges them into the in-memory
   db. Returns a MergeResult on success."
  [db-key dispatch-fn]
  (invoke-api "rs_merge_with_remote" {:db-key db-key} dispatch-fn))

(defn acknowledge-remote-change
  "Refreshes the cached remote mtime to the current server value so the
   next focus-poll won't re-prompt. Called when user chose 'Ignore' on
   the conflict dialog for a remote db."
  [db-key dispatch-fn]
  (invoke-api "rs_acknowledge_remote_change" {:db-key db-key} dispatch-fn))

;; ----- Picker support: discover kdbx-source connections + fetch one -----

(defn list-kdbx-source-connections
  "Returns summaries (db-key, connection-id, title) for every
   REMOTE_CONNECTION_SFTP / _WEBDAV entry across currently open kdbx
   databases."
  [type dispatch-fn]
  (invoke-api "rs_list_kdbx_source_connections"
              {:rs-storage-type (as-rs-type type)}
              dispatch-fn))

(defn get-remote-storage-config
  "Fetches the full connection config (host/port/credentials/etc.) for a
   given kdbx-source connection. Used to show a read-only view, or to
   bootstrap a connect-by-id call when the cached summary doesn't carry
   enough info."
  [type connection-id dispatch-fn]
  (invoke-api "rs_get_remote_storage_config"
              {:rs-storage-type (as-rs-type type)
               :connection-id connection-id}
              dispatch-fn))
