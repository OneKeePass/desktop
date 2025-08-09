(ns onekeepass.frontend.events.tauri-events
  "Handlers for the backend tauri events"
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.constants :as const :refer
    [BROWSER_CONNECTION_REQUEST_EVENT CLOSE_REQUESTED MAIN_WINDOW_EVENT
     OTP_TOKEN_UPDATE_EVENT TAURI_MENU_EVENT WINDOW_FOCUS_CHANGED]]
   [re-frame.core :refer [dispatch]]))

(defn- to-cljs [js-event-repsonse]
  (->> js-event-repsonse js->clj (cske/transform-keys csk/->kebab-case-keyword)))

;; a map of map 
;; e.g  {MENU_ID_NEW_ENTRY {:callback-fn some-fn  :args args-map-for-callback  }}
(def ^:private all-menu-args (atom {}))

(defn menu-action-call
  "Calls the menu specific callback function"
  [menu-id]
  (let [{:keys [callback-fn]} (get @all-menu-args menu-id)]
    ;; For now empty args list is passed to the callback
    (apply callback-fn '())))

(defn handle-menu-events
  " The arg event-repsonse is of type 
    #js {:event TauriMenuEvent, :windowLabel main, :payload #js {:menu_id Quit}, :id 12011419863226083000}
    The event payload is extracted and UI events are dispatched based on the Menu selected
    "
  [event-repsonse]
  (let [r (to-cljs event-repsonse)
        menu-id (-> r :payload :menu-id)]
    (cond
      (= menu-id const/MENU_ID_QUIT)
      (dispatch [:tool-bar/app-quit-called])

      (= menu-id const/MENU_ID_NEW_DATABASE)
      (dispatch [:new-database-dialog-show])

      (= menu-id const/MENU_ID_PASSWORD_GENERATOR)
      (dispatch [:password-generator/start])

      (= menu-id const/MENU_ID_EDIT_ENTRY)
      (dispatch [:entry-form-ex/edit true])

      (= menu-id const/MENU_ID_NEW_ENTRY)
      (menu-action-call menu-id)

      (= menu-id const/MENU_ID_NEW_GROUP)
      (dispatch [:group-tree-content/new-group])

      (= menu-id const/MENU_ID_EDIT_GROUP)
      (dispatch [:group-tree-content/edit-group])

      (= menu-id const/MENU_ID_LOCK_DATABASE)
      (dispatch [:tool-bar/lock-current-db])

      (= menu-id const/MENU_ID_CLOSE_DATABASE)
      (dispatch [:tool-bar/close-current-db-start])

      (= menu-id const/MENU_ID_OPEN_DATABASE)
      (dispatch [:open-db-form/open-db])

      (= menu-id const/MENU_ID_SAVE_DATABASE)
      (dispatch [:save-current-db false])

      (= menu-id const/MENU_ID_SAVE_DATABASE_AS)
      (dispatch [:common/save-db-file-as false])

      (= menu-id const/MENU_ID_SAVE_DATABASE_BACKUP)
      (dispatch [:common/save-db-file-as true])

      (= menu-id const/MENU_ID_SEARCH)
      (dispatch [:search/dialog-show])

      (= menu-id const/APP_SETTINGS)
      (dispatch [:app-settings/read-start])

      (= menu-id const/MENU_ID_MERGE_DATABASE)
      (dispatch [:merging/open-dbs-start])

      (= menu-id const/MENU_ID_IMPORT)
      (dispatch [:import/import-csv-file-start])

      :else
      (dispatch [:common/message-box-show "Work In Progress" (str "Menu action for " menu-id " will be implemented soon")]))))

(defn register-menu-events []
  (bg/register-event-listener TAURI_MENU_EVENT handle-menu-events))

(defn handle-main-window-event
  "The arg js-event-repsonse is js object of format -  
  #js {:event MainWindowEvent,:windowLabel main, :payload #js {:action CloseRequested}, :id 13571024511454513000}
  "
  [js-event-repsonse]
  (let [cljs-response (to-cljs js-event-repsonse)
        {:keys [action focused]} (-> cljs-response :payload)]
    (cond
      ;; This is called when user closes the main window using window's system closing button  
      (= action CLOSE_REQUESTED)
      (dispatch [:tool-bar/app-quit-called])

      (= action WINDOW_FOCUS_CHANGED)
      #()
      #_(println "Window focused val is " focused)

      :else
      (println "No handler for Main Window event response: " js-event-repsonse))))

(defn register-main-window-events []
  (bg/register-event-listener MAIN_WINDOW_EVENT handle-main-window-event))

;; js-event-repsonse is a javascript object
;; e.g   #js {:event OtpTokenUpdateEvent, :windowLabel main, 
;;       :payload #js {:entry_uuid 6691d1b7-13b7-4f7e-82bc-481629d9f6e3, 
;;       :reply_field_tokens #js {:otp #js {:token nil, :ttl 21}}}, :id 2305274370}
;; We use the simple js->clj to covert this js object to cljs map
;; Only keys are keywordized but they are in snake_case
;; We are not using 'to-cljs' as it will convert all keys recursively to keyword
;; We do not want that as the field names are string. 
;; A string key like "Field name1" will be :Field name1 because of :keywordize-keys use
(defn handle-otp-token-update-event [js-event-repsonse]
  ;;(println "token  response " (js->clj js-event-repsonse :keywordize-keys true) )
  (let [cljs-response (js->clj js-event-repsonse :keywordize-keys true)
        reply (-> cljs-response :payload)
        {:keys [entry_uuid reply_field_tokens]} reply]
    ;; For now only entry form otp tokens are received
    ;; Needs to be changed once entry list tokens update considered
    (dispatch [:entry-form/update-otp-tokens entry_uuid reply_field_tokens])))

(defn register-otp-token-update-events []
  (bg/register-event-listener OTP_TOKEN_UPDATE_EVENT handle-otp-token-update-event))

(defn handle-browser-connection-request-event [js-event]
  (println "handle-browser-connection-request " (js->clj js-event :keywordize-keys true))
  (bg/set-window-focus)
  )

(defn register-browser-connection-request-event []
  (bg/register-event-listener BROWSER_CONNECTION_REQUEST_EVENT handle-browser-connection-request-event))

(defn register-tauri-events []
  (register-menu-events)
  (register-main-window-events)
  (register-otp-token-update-events)
  (register-browser-connection-request-event)
  )

(defn enable-app-menu [menu-id enable? & {:as menu-args}]
  ;; (println "Going to call for menu-id " menu-id enable? menu-args)

  ;; Stores any menu specific args and that is used when menu is selected in the menu bar
  ;; See as an example how the third arg (a map) is passed in entry-list/fn-entry-list-content
  ;; For now only this is used wrt const/MENU_ID_NEW_ENTRY
  (swap! all-menu-args assoc menu-id menu-args)

  (bg/menu-action-requested menu-id
                            (if enable? const/MENU_ENABLE const/MENU_DISABLE)
                            (fn [{:keys [error]}]
                              ;; Response will have non nil error if there is any backend failure
                              (when error
                                (dispatch [:common/message-snackbar-error-open error])))))
