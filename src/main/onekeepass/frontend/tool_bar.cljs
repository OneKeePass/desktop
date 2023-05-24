(ns onekeepass.frontend.tool-bar
  (:require
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.tool-bar :as tb-events]
   [onekeepass.frontend.events.open-db-form :as od-events]
   [onekeepass.frontend.events.search :as srch-event]
   [onekeepass.frontend.events.db-settings :as settings-events]
   [onekeepass.frontend.events.password-generator :as gen-events]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.tauri-events :as tauri-events]
   [onekeepass.frontend.db-settings :as settings-form]
   [onekeepass.frontend.search :as search]
   [onekeepass.frontend.open-db-form :as od-form]
   [onekeepass.frontend.new-database :as nd-form]
   [onekeepass.frontend.common-components :refer [message-dialog
                                                  error-info-dialog
                                                  confirm-text-dialog]]
   [onekeepass.frontend.password-generator :as gen-form]
   [onekeepass.frontend.mui-components :as m :refer [color-primary-main
                                                     mui-dialog
                                                     mui-dialog-title
                                                     mui-dialog-content
                                                     mui-dialog-actions
                                                     mui-divider
                                                     mui-linear-progress
                                                     mui-button
                                                     mui-alert
                                                     mui-stack
                                                     mui-box
                                                     mui-icon-button
                                                     mui-app-bar
                                                     mui-toolbar mui-tooltip
                                                     mui-icon-cancel-presentation
                                                     mui-icon-lock-open-outlined
                                                     mui-icon-lock-outlined
                                                     mui-icon-folder
                                                     mui-icon-save
                                                     mui-icon-save-as
                                                     mui-icon-search
                                                     mui-icon-settings-outlined
                                                     mui-typography]]))

(set! *warn-on-infer* true)

(defn ask-save-dialog [dialog-data]
  [confirm-text-dialog
   "Unsaved Changes"
   "There are changes yet to be saved. Do you want to save and then quit?"
   [{:label "Save" :on-click #(tb-events/on-save-click)}
    {:label "Quit" :on-click #(tb-events/on-do-not-save-click)}
    {:label "Cancel" :on-click #(tb-events/ask-save-dialog-show false)}]
   dialog-data])

(defn close-current-db-save-dialog [dialog-data]
  [confirm-text-dialog
   "Unsaved Changes"
   "There are changes yet to be saved. Do you want to save before closing the database?"
   [{:label "Save" :on-click tb-events/close-current-db-on-save-click}
    {:label "Do not Save" :on-click tb-events/close-current-db-no-save}
    {:label "Cancel" :on-click tb-events/close-current-db-on-cancel-click}]
   dialog-data])

(defn conflict-action-confirm-dialog [{:keys [dialog-show confirm]}]
  (if (= confirm :overwrite)
    [confirm-text-dialog
     "Confirm Overwrite"
     "Do you want to overwrite the externally changed database with your changes?"
     [{:label "Yes,Overwrite" :on-click tb-events/overwrite-external-changes}
      {:label "Cancel" :on-click tb-events/conflict-action-confirm-dialog-hide}]
     {:dialog-show dialog-show}]
    [confirm-text-dialog
     "Confirm Discard"
     "Do you want to discard your changes and close the database?"
     [{:label "Discard" :on-click tb-events/conflict-action-discard}
      {:label "Cancel" :on-click tb-events/conflict-action-confirm-dialog-hide}]
     {:dialog-show dialog-show}]))

(defn content-change-action-dialog [open?]
  [mui-dialog {:open open? :on-click #(.stopPropagation ^js/Event %)}
   [mui-dialog-title "Content conflicts detected"]
   [mui-dialog-content
    [mui-stack
     "The database content of the file has changed since the last opening. Please take one of the following action:"]

    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
    [mui-stack {:style {:align-items "center"}}
     [mui-typography {:sx  {} #_{"&.MuiTypography-root" {:color "secondary"}}
                      :color "secondary" :variant  "button"} "Save as"]]
    [mui-stack
     [mui-typography {:sx {"&.MuiTypography-root" {:color color-primary-main}}}
      "You can save your changes to a new database and manually merge (auto merge feature will be added in the  future release)"]]

    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
    [mui-stack  {:direction "column"}
     [mui-stack {:style {:align-items "center"}}
      [mui-typography {:sx {}
                       :color "error" :variant  "button"} "Overwrite"]]
     [mui-typography {:sx {"&.MuiTypography-root" {:color color-primary-main}}}
      "You can overwrite the database with your changes"]]

    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
    [mui-stack  {:direction "column"}

     [mui-stack {:style {:align-items "center"}}
      [mui-typography {:sx {"&.MuiTypography-root"
                            {:margin-left "5px"}}
                       :color "error" :variant  "button"} "Discard & Close database"]]

     [mui-typography {:sx {"&.MuiTypography-root" {:color color-primary-main}}}
      "You can discard your changes and close the database "]]
    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]]

   [mui-dialog-actions
    [mui-button {:color "error"
                 :on-click tb-events/confirm-overwrite-external-db} "Overwrite"]
    [mui-button {:color "error"
                 :on-click tb-events/confirm-discard-current-db} "Discard & Close database"]
    [mui-button {:color "secondary"
                 :on-click tb-events/conflict-action-save-as} "Save as"]]])


(defn save-info-dialog [{:keys [status api-error-text]}]
  (if (= api-error-text "DbFileContentChangeDetected")
    [content-change-action-dialog true]
    [mui-dialog {:open (or (= status :in-progress) (= status :error)) :on-click #(.stopPropagation ^js/Event %)}
     [mui-dialog-title "Save Database"]
     [mui-dialog-content
      [mui-stack
       "Saving database is in progress"

       (when api-error-text
         [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])

       (when (and (nil? api-error-text) (= status :in-progress))
         [mui-linear-progress {:sx {:mt 2}}])]]
     [mui-dialog-actions
      [mui-button {:color "secondary"
                   :disabled (= status :in-progress)
                   :on-click tb-events/save-current-db-msg-dialog-hide} "Close"]]]))

(defn top-bar
  "A tool bar function component from Reagent a component so that 
   we can use effect to enable/disable certain App menus"
  []
  (fn []
    (let [save-action-data @(tb-events/save-current-db-data)
          locked? @(cmn-events/locked?)
          save-disabled? (or locked? (not @(cmn-events/db-save-pending?)))]
      (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE (not save-disabled?))
      ;; React useEffect 
      (m/react-use-effect (fn []
                            (tauri-events/enable-app-menu const/MENU_ID_PASSWORD_GENERATOR true)
                            (tauri-events/enable-app-menu const/MENU_ID_CLOSE_DATABASE true)
                            (tauri-events/enable-app-menu const/MENU_ID_LOCK_DATABASE true)
                            (tauri-events/enable-app-menu const/MENU_ID_SEARCH true)
                            ;; cleanup fn is returned which is called when this component unmounts
                            (fn []
                              (tauri-events/enable-app-menu const/MENU_ID_PASSWORD_GENERATOR false)
                              (tauri-events/enable-app-menu const/MENU_ID_CLOSE_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_LOCK_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_SEARCH true))) (clj->js []))

      [:div {:style {:flex-grow 1}}
       [mui-app-bar {:position "static" :color "primary"}
        [mui-toolbar {:style {:min-height 32}}
       ;; Using box to provide common styles - left margin -  for all its children - buttons 
       ;; Using "&.MuiIconButton-root" etc did not work
         [mui-box {:sx {"& .MuiButtonBase-root" {:ml "-8px"}}}
          [mui-tooltip {:title "Open" :enterDelay 2000}
           [mui-icon-button
            {:edge "start" :color "inherit"
             :onClick od-events/open-file-explorer-on-click}
            [mui-icon-folder]]]

          [mui-tooltip {:title "Save" :enterDelay 2000}
           [mui-icon-button
            {:edge "start" :color "inherit"
             :disabled  save-disabled? #_(or locked? (not @(cmn-events/db-save-pending?)))
             :on-click  tb-events/save-current-db}
            [mui-icon-save]]]

          [mui-tooltip {:title "Save As" :enterDelay 2000}
           [mui-icon-button
            {:edge "start" :color "inherit"
             :disabled locked?
             :on-click  cmn-events/save-as}
            [mui-icon-save-as]]]

          [mui-tooltip {:title "Close Database" :enterDelay 2000}
           [mui-icon-button
            {:edge "start" :color "inherit"
             :on-click tb-events/close-current-db-on-click}
            [mui-icon-cancel-presentation]]]

          (if locked?
            [mui-tooltip {:title "Unlock Database" :enterDelay 2000}
             [mui-icon-button
              {:edge "start" :color "inherit"
               :on-click tb-events/unlock-current-db}
              [mui-icon-lock-outlined]]]
            [mui-tooltip {:title "Lock Database" :enterDelay 2000}
             [mui-icon-button
              {:edge "start" :color "inherit"
               :on-click tb-events/lock-current-db}
              [mui-icon-lock-open-outlined]]])]
         [:span  {:style {:flex-grow "1"}}]

         [mui-tooltip {:title "Settings" :enterDelay 2000}
          [mui-icon-button {:edge "end"
                            :disabled locked?
                            :color "inherit"
                            :on-click  settings-events/read-db-settings #_dl-events/open-settings-dialog}

           [mui-icon-settings-outlined]]]

         [mui-tooltip {:title "Search" :enterDelay 2000}
          [mui-icon-button {:edge "end"
                            :color "inherit"
                            :disabled locked?
                            :on-click srch-event/search-dialog-show}
           [mui-icon-search]]]]]
       [gen-form/password-generator-dialog @(gen-events/generator-dialog-data)]
       [message-dialog]
       [error-info-dialog]
       [od-form/open-db-dialog-main]
       [save-info-dialog save-action-data]
       [nd-form/new-database-dialog-main]
       [settings-form/settings-dialog-main]
       [search/search-dialog-main]
       [conflict-action-confirm-dialog @(tb-events/conflict-action-confirm-dialog-data)]
       [ask-save-dialog @(tb-events/ask-save-dialog-data)]
       [close-current-db-save-dialog @(tb-events/close-current-db-dialog-data)]])))
