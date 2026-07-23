(ns onekeepass.frontend.constants
  (:require [clojure.string]))

;; This is the default Entry type to use 
(def UUID_OF_ENTRY_TYPE_LOGIN "ffef5f51-7efc-4373-9eb5-382d5b501768")

;; This is the entry type id for Auto Open entry type
(def UUID_OF_ENTRY_TYPE_AUTO_OPEN "389368a9-73a9-4256-8247-321a2e60b2c7")

;; Entry type ids for remote-storage connection entries (SFTP / WebDAV).
;; The remote-storage resolver uses the entry uuid (= connection id) to find
;; the connection details across all open dbs.
(def UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_SFTP "c5a57a41-4cca-4a46-bac1-78a8803f4da0")
(def UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_WEBDAV "0a14d76d-8c38-4c62-9ad7-390dc020a2af")

;; Entry type id for the SSH Key type (the agent's key source). Must match
;; entry_type_uuid::SSH_KEY in the core constants.rs.
(def UUID_OF_ENTRY_TYPE_SSH_KEY "6421a61a-db18-413a-bdfb-715a5418216a")

;; SSH agent support mode tags. These values must match the Rust
;; SshAgentMode serde representation.
(def SSH_AGENT_MODE_AGENT "agent")
(def SSH_AGENT_MODE_CLIENT "client")

;; Windows-only Client Mode target agent. Values must match the Rust
;; SshAgentClientTransport serde representation.
(def SSH_AGENT_CLIENT_TRANSPORT_OPENSSH "openssh")
(def SSH_AGENT_CLIENT_TRANSPORT_PAGEANT "pageant")

;; Enum tags matching the Rust `RemoteStorageType` variant names. Used
;; verbatim in the JSON sent over the Tauri bridge.
(def V-SFTP "Sftp")
(def V-WEBDAV "Webdav")

;; Page identifiers for the remote-storage screens.
(def RS_CONNECTIONS_PAGE_ID :remote-storage-connections)
(def RS_BROWSE_PAGE_ID :remote-storage-browse)
(def RS_CONNECTION_FORM_PAGE_ID :remote-storage-connection-form)

;; Standard Entry Type Names
;; These should match names used in 'standard_entry_types.rs'

(def DATETIME_FORMAT "dd MMM yyyy pp")

(def LOGIN_TYPE_NAME "Login")
(def CREDIT_DEBIT_CARD_TYPE_NAME "Credit/Debit Card")
(def WIRELESS_ROUTER_TYPE_NAME "Wireless Router")
(def PASSPORT_TYPE_NAME "Passport")
(def BANK_ACCOUNT_TYPE_NAME "Bank Account")
(def AUTO_DB_OPEN_TYPE_NAME "Auto Database Open")
(def REMOTE_CONNECTION_SFTP_TYPE_NAME "SFTP Connection")
(def REMOTE_CONNECTION_WEBDAV_TYPE_NAME "WebDAV Connection")
(def IDENTITY_TYPE_NAME "Identity")
(def DRIVER_LICENSE_TYPE_NAME "Driver License")
(def SSH_KEY_TYPE_NAME "SSH Key")

;; Remote-connection entry types are identified by their stable type uuid (not
;; the display name, which can change). entry-type-uuid comes from the entry's
;; form data / EntrySummary.
(def REMOTE_CONNECTION_TYPE_UUIDS #{UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_SFTP
                                    UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_WEBDAV})

(defn remote-connection-entry-type? [entry-type-uuid]
  (contains? REMOTE_CONNECTION_TYPE_UUIDS entry-type-uuid))

;; Standard entry type names. The new-entry type menu is built from the
;; backend entry-type headers; this list is used to decide whether an entry
;; type name should be translated (see translated-entry-type-name). All
;; standard type names must appear here so their titles get translated.
(def STANDARD_ENTRY_TYPES [LOGIN_TYPE_NAME
                           CREDIT_DEBIT_CARD_TYPE_NAME
                           BANK_ACCOUNT_TYPE_NAME
                           IDENTITY_TYPE_NAME
                           PASSPORT_TYPE_NAME
                           DRIVER_LICENSE_TYPE_NAME
                           SSH_KEY_TYPE_NAME
                           WIRELESS_ROUTER_TYPE_NAME
                           AUTO_DB_OPEN_TYPE_NAME
                           REMOTE_CONNECTION_SFTP_TYPE_NAME
                           REMOTE_CONNECTION_WEBDAV_TYPE_NAME])

(def ADDITIONAL_ONE_TIME_PASSWORDS "Additional One-Time Passwords")
;;
(def CATEGORY_ALL_ENTRIES "AllEntries")
(def CATEGORY_FAV_ENTRIES "Favorites")
(def CATEGORY_DELETED_ENTRIES "Deleted")

#_(def GENERAL_CATEGORIES #{CATEGORY_ALL_ENTRIES CATEGORY_FAV_ENTRIES CATEGORY_DELETED_ENTRIES})

(def GROUPING_LABEL_TYPES "Types")
(def GROUPING_LABEL_TAGS "Tags")
(def GROUPING_LABEL_CATEGORIES "Categories")
(def GROUPING_LABEL_GROUPS "Groups")

(def ASCENDING "Ascending")
(def DESCENDING "Descending")

(def THEME_LIGHT "light")


(def APP_SETTINGS "AppSettings")
(def MENU_ID_QUIT "Quit")
(def MENU_ID_SEARCH "Search")
(def MENU_ID_NEW_DATABASE "NewDatabase")
(def MENU_ID_SAVE_DATABASE "SaveDatabase")
(def MENU_ID_SAVE_DATABASE_AS "SaveDatabaseAs")
(def MENU_ID_SAVE_DATABASE_BACKUP "SaveDatabaseBackup")
(def MENU_ID_OPEN_DATABASE "OpenDatabase")
(def MENU_ID_OPEN_REMOTE "OpenRemote")
(def MENU_ID_OPEN_RECENT "OpenRecent")
(def MENU_ID_LOCK_DATABASE "LockDatabase")
(def MENU_ID_CLOSE_DATABASE "CloseDatabase")
(def MENU_ID_MERGE_DATABASE "MergeDatabase")
(def MENU_ID_MERGE_OPENED_DATABASES "MergeOpenedDatabases")
(def MENU_ID_CHECK_REMOTE_CHANGES "CheckRemoteChanges")
(def MENU_ID_IMPORT "Import")
(def MENU_ID_PASSWORD_GENERATOR "PasswordGenerator")
(def MENU_ID_NEW_ENTRY "NewEntry")
(def MENU_ID_EDIT_ENTRY "EditEntry")
(def MENU_ID_COPY_USERNAME "CopyUsername")
(def MENU_ID_COPY_PASSWORD "CopyPassword")
(def MENU_ID_COPY_URL "CopyUrl")
(def MENU_ID_OPEN_URL "OpenUrl")
(def MENU_ID_COPY_TOTP "CopyTotp")
(def MENU_ID_NEW_GROUP "NewGroup")
(def MENU_ID_EDIT_GROUP "EditGroup")
(def MENU_ID_ABOUT "About")
(def MENU_ID_CHECK_FOR_UPDATES "CheckForUpdates")

(def MENU_ENABLE "Enable")
(def MENU_DISABLE "Disable")

(def ADD_TAG_PREFIX "Add New Tag:")

(def DB_CHANGED "DbFileContentChangeDetected")
(def MERGE_FAILED_CREDENTIALS_CHANGED "MergeFailedCredentialsChanged")
;; Marker error returned by unlock_kdbx_with_biometric when the biometric
;; verification (Touch ID / Face ID / Windows Hello) is cancelled or fails, so
;; the unlock flow falls back to the password dialog instead of a hard error.
(def BIOMETRIC_AUTH_FAILED "BiometricAuthenticationFailed")

(def TOUCH_ID "TouchID")
(def FACE_ID "FaceID")
;; Windows Hello (PIN or biometric) re-unlock; matches biometric.rs on Windows.
(def WINDOWS_HELLO "WindowsHello")
(def NO_BIOMETRIC "None")

(def MACOS "macos")
(def WINDOWS "windows")
(def LINUX "linux")

(def GROUP "Group")

;; Some entry standard fields
(def TITLE "Title")
(def MODIFIED_TIME "Modified Time")
(def CREATED_TIME "Created Time")
(def USERNAME "UserName")
(def PASSWORD "Password")
(def URL "URL")
(def HOST "Host")
(def NOTES "Notes")
(def TAGS "Tags")
(def IFDEVICE "IfDevice")
(def ADDITIONAL_URLS "Additional URLs")

;; Passkey entry protected field keys (match onekeepass-core constants of the same name)
(def KPEX_PASSKEY_USER_HANDLE "KPEX_PASSKEY_USER_HANDLE")
(def KPEX_PASSKEY_CREDENTIAL_ID "KPEX_PASSKEY_CREDENTIAL_ID")

(def ONE_TIME_PASSWORD_TYPE "Field type" "OneTimePassword")

(def TEXT_TYPE "Field type matching Rust enum FieldDataType::Text" "Text")

(def BOOL_TYPE "Field type matching Rust enum FieldDataType::Bool" "Bool")

(def DATE_TYPE "Field type matching Rust enum FieldDataType::Date" "Date")

(def OTP "Standard field name used" "otp")

(def OTP_URL_PREFIX "otpauth://totp")

;; See constants defined in src-tauri/src/constants.rs
(def MAIN_WINDOW_EVENT "MainWindowEvent")
(def OTP_TOKEN_UPDATE_EVENT "OtpTokenUpdateEvent")
(def TAURI_MENU_EVENT "TauriMenuEvent")
(def BROWSER_CONNECTION_REQUEST_EVENT "BrowserConnectionRequestEvent")
(def PASSKEY_DATA_CHANGED_EVENT "PasskeyDataChangedEvent")
(def DB_FILE_CHANGED_EVENT "DbFileChangedEvent")
(def SSH_AGENT_SIGN_REQUEST_EVENT "SshAgentSignRequestEvent")

(def WINDOW_FOCUS_CHANGED "WindowFocusChanged")
(def CLOSE_REQUESTED  "CloseRequested")
(def FILE_DROP "FileDrop")
;; Backend has locked all open databases in response to an OS suspend/sleep
(def DATABASES_LOCKED "DatabasesLocked")
