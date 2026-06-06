(ns onekeepass.frontend.background-custom-icons
  "Tauri command wrappers for per-database custom icons — listing, fetching
   bytes, adding from URL / file, removing, and assigning to entries / groups."
  (:require
   [onekeepass.frontend.background-common :as bg-cmn :refer [invoke-api]]))

(defn list-custom-icons [db-key dispatch-fn]
  (invoke-api "list_custom_icons" {:db-key db-key} dispatch-fn))

(defn get-custom-icon-data [db-key custom-icon-uuid dispatch-fn]
  (invoke-api "get_custom_icon_data" {:db-key db-key :custom-icon-uuid custom-icon-uuid} dispatch-fn :convert-response false))

(defn add-custom-icon-from-url [db-key url dispatch-fn]
  (invoke-api "add_custom_icon_from_url" {:db-key db-key :url url} dispatch-fn))

(defn add-custom-icon-from-file [db-key file-path dispatch-fn]
  (invoke-api "add_custom_icon_from_file" {:db-key db-key :file-path file-path} dispatch-fn))

(defn remove-custom-icon [db-key custom-icon-uuid dispatch-fn]
  (invoke-api "remove_custom_icon" {:db-key db-key :custom-icon-uuid custom-icon-uuid} dispatch-fn))

(defn set-entry-custom-icon [db-key entry-uuid custom-icon-uuid dispatch-fn]
  (invoke-api "set_entry_custom_icon" {:db-key db-key :entry-uuid entry-uuid :custom-icon-uuid custom-icon-uuid} dispatch-fn))

(defn set-group-custom-icon [db-key group-uuid custom-icon-uuid dispatch-fn]
  (invoke-api "set_group_custom_icon" {:db-key db-key :group-uuid group-uuid :custom-icon-uuid custom-icon-uuid} dispatch-fn))
