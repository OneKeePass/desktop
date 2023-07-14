(ns onekeepass.frontend.events.tauri-events
  "Handlers for the backend tauri events"
  (:require
   [re-frame.core :refer [dispatch]]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.background :as bg]
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]))

(defn- to-cljs [js-event-repsonse]
  (->> js-event-repsonse js->clj (cske/transform-keys csk/->kebab-case-keyword)))

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

      (= menu-id const/MENU_ID_LOCK_DATABASE)
      (dispatch [:tool-bar/lock-current-db])

      (= menu-id const/MENU_ID_CLOSE_DATABASE)
      (dispatch [:tool-bar/close-current-db-start])

      (= menu-id const/MENU_ID_OPEN_DATABASE)
      (dispatch [:open-db-form/open-db])

      (= menu-id const/MENU_ID_SEARCH)
      (dispatch [:search/dialog-show])

      :else
      (dispatch [:common/message-box-show "Work In Progress" (str "Menu action for " menu-id " will be implemented soon")]))))

(defn register-menu-events []
  (bg/register-event-listener "TauriMenuEvent" handle-menu-events))


(defn handle-main-window-event
  "The arg js-event-repsonse is js object of format -  
  #js {:event MainWindowEvent,:windowLabel main, :payload #js {:action CloseRequested}, :id 13571024511454513000}
  "
  [js-event-repsonse]
  (let [cljs-response (to-cljs js-event-repsonse)
        action (-> cljs-response :payload :action)]
    (cond
      ;; This is called when user closes the main window using window's system closing button  
      (= action "CloseRequested")
      (dispatch [:tool-bar/app-quit-called])

      :else
      (println "No handler for Main Window event response: " js-event-repsonse))))

(defn register-main-window-events []
  (bg/register-event-listener "MainWindowEvent" handle-main-window-event))

(defn register-tauri-events []
  (register-menu-events)
  (register-main-window-events))

(defn enable-app-menu [menu-id enable?]
  (bg/menu-action-requested menu-id 
                            (if enable? const/MENU_ENABLE const/MENU_DISABLE) 
                            (fn [{:keys [error]}] 
                              ;; Response will have non nil error if there is any backend failure
                              (when error
                                (dispatch [:common/message-snackbar-error-open error])))))
