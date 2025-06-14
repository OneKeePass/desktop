(ns onekeepass.frontend.constants)

;; This is the default Entry type to use 
(def UUID_OF_ENTRY_TYPE_LOGIN "ffef5f51-7efc-4373-9eb5-382d5b501768")

;; This is the entry type id for Auto Open entry type
(def UUID_OF_ENTRY_TYPE_AUTO_OPEN "389368a9-73a9-4256-8247-321a2e60b2c7")

;; Standard Entry Type Names
;; These should match names used in 'standard_entry_types.rs'

(def DATETIME_FORMAT "dd MMM yyyy pp")

(def LOGIN_TYPE_NAME "Login")
(def CREDIT_DEBIT_CARD_TYPE_NAME "Credit/Debit Card")
(def WIRELESS_ROUTER_TYPE_NAME "Wireless Router")
(def PASSPORT_TYPE_NAME "Passport")
(def BANK_ACCOUNT_TYPE_NAME "Bank Account")
(def AUTO_DB_OPEN_TYPE_NAME "Auto Database Open")

;; This list is used in Entry Type select menu items on the new entry form
(def STANDARD_ENTRY_TYPES [LOGIN_TYPE_NAME
                           CREDIT_DEBIT_CARD_TYPE_NAME
                           WIRELESS_ROUTER_TYPE_NAME
                           BANK_ACCOUNT_TYPE_NAME
                           AUTO_DB_OPEN_TYPE_NAME])

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
(def MENU_ID_LOCK_DATABASE "LockDatabase")
(def MENU_ID_CLOSE_DATABASE "CloseDatabase")
(def MENU_ID_MERGE_DATABASE "MergeDatabase")
(def MENU_ID_IMPORT "Import")
(def MENU_ID_PASSWORD_GENERATOR "PasswordGenerator")
(def MENU_ID_NEW_ENTRY "NewEntry")
(def MENU_ID_EDIT_ENTRY "EditEntry")
(def MENU_ID_NEW_GROUP "NewGroup")
(def MENU_ID_EDIT_GROUP "EditGroup")

(def MENU_ENABLE "Enable")
(def MENU_DISABLE "Disable")

(def ADD_TAG_PREFIX "Add New Tag:")

(def DB_CHANGED "DbFileContentChangeDetected")

(def TOUCH_ID "TouchID")
(def FACE_ID "FaceID")
(def NO_BIOMETRIC "None")

(def MACOS "macos")

(def GROUP "Group")

;; Some entry standard fields
(def TITLE "Title")
(def MODIFIED_TIME "Modified Time")
(def CREATED_TIME "Created Time")
(def USERNAME "UserName")
(def PASSWORD "Password")
(def URL "URL")
(def NOTES "Notes")
(def TAGS "Tags")
(def IFDEVICE "IfDevice")

(def ONE_TIME_PASSWORD_TYPE "Field type" "OneTimePassword")

(def OTP "Standard field name used" "otp")

(def OTP_URL_PREFIX "otpauth://totp")

(def MAIN_WINDOW_EVENT "MainWindowEvent")
(def OTP_TOKEN_UPDATE_EVENT "OtpTokenUpdateEvent")
(def TAURI_MENU_EVENT "TauriMenuEvent")
(def WINDOW_FOCUS_CHANGED "WindowFocusChanged")
(def CLOSE_REQUESTED  "CloseRequested")


