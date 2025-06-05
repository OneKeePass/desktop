(ns onekeepass.frontend.tool-bar
  (:require
   [onekeepass.frontend.translation :as t :refer-macros [tr-bl tr-dlg-title tr-dlg-text]]
   [onekeepass.frontend.app-settings :refer [app-settings-dialog-main]]
   [onekeepass.frontend.auto-type :as at-form]
   [onekeepass.frontend.merging :as merging]
   [onekeepass.frontend.import-file.csv :as csv]
   [onekeepass.frontend.common-components :refer [confirm-text-dialog
                                                  error-info-dialog
                                                  message-dialog
                                                  progress-message-dialog]]
   [onekeepass.frontend.constants :as const :refer [DB_CHANGED]]
   [onekeepass.frontend.db-settings :as settings-form]
   [onekeepass.frontend.events.auto-type :as at-events]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.db-settings :as settings-events]
   [onekeepass.frontend.events.open-db-form :as od-events]
   [onekeepass.frontend.events.password-generator :as gen-events]
   [onekeepass.frontend.events.search :as srch-event]
   [onekeepass.frontend.events.tauri-events :as tauri-events]
   [onekeepass.frontend.events.tool-bar :as tb-events]
   [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                     mui-alert
                                                     mui-app-bar
                                                     mui-box
                                                     mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-divider
                                                     mui-icon-button
                                                     mui-icon-cancel-presentation
                                                     mui-icon-folder
                                                     mui-icon-lock-open-outlined
                                                     mui-icon-lock-outlined
                                                     mui-icon-save
                                                     mui-icon-save-as
                                                     mui-icon-search
                                                     mui-icon-settings-outlined
                                                     mui-linear-progress
                                                     mui-stack
                                                     mui-toolbar
                                                     mui-tooltip
                                                     mui-typography
                                                     theme-color]]
   [onekeepass.frontend.new-database :as nd-form]
   [onekeepass.frontend.open-db-form :as od-form]
   [onekeepass.frontend.password-generator :as gen-form]
   [onekeepass.frontend.search :as search]))

(set! *warn-on-infer* true)

;; TODO: Something similar to what we do for close database
(defn ask-save-on-lock [dialog-data]
  [confirm-text-dialog
   (tr-dlg-title unsavedChanges)
   (tr-dlg-text "unsavedChangesTxt1")
   [{:label (tr-bl ok) :on-click #(tb-events/on-lock-ask-save-dialog-hide)}]
   dialog-data])

(defn ask-save-dialog [dialog-data]
  [confirm-text-dialog
   (tr-dlg-title unsavedChanges)
   (tr-dlg-text "unsavedChangesTxt2")
   [{:label (tr-bl save) :on-click #(tb-events/on-save-click)}
    {:label (tr-bl quit) :on-click #(tb-events/on-do-not-save-click)}
    {:label (tr-bl cancel) :on-click #(tb-events/ask-save-dialog-show false)}]
   dialog-data])

(defn close-current-db-save-dialog [dialog-data]
  [confirm-text-dialog
   (tr-dlg-title unsavedChanges)
   (tr-dlg-text "unsavedChangesTxt3")
   [{:label (tr-bl save) :on-click tb-events/close-current-db-on-save-click}
    {:label (tr-bl doNotSave)  :on-click tb-events/close-current-db-no-save}
    {:label (tr-bl cancel) :on-click tb-events/close-current-db-on-cancel-click}]
   dialog-data])

(defn conflict-action-confirm-dialog [{:keys [dialog-show confirm]}]
  (if (= confirm :overwrite)
    [confirm-text-dialog
     (tr-dlg-title confirmOverwrite)
     (tr-dlg-text confirmOverwrite)
     [{:label (tr-bl yesOverwrite) :on-click tb-events/overwrite-external-changes}
      {:label (tr-bl cancel) :on-click tb-events/conflict-action-confirm-dialog-hide}]
     {:dialog-show dialog-show}]
    [confirm-text-dialog
     (tr-dlg-title confirmDiscard)
     (tr-dlg-text confirmDiscard)
     [{:label (tr-bl discard) :on-click tb-events/conflict-action-discard}
      {:label (tr-bl cancel) :on-click tb-events/conflict-action-confirm-dialog-hide}]
     {:dialog-show dialog-show}]))

(defn content-change-action-dialog [open?]
  [mui-dialog {:open open? :on-click #(.stopPropagation ^js/Event %)}
   [mui-dialog-title (tr-dlg-title conflictOnSave)]
   [mui-dialog-content
    [mui-stack (tr-dlg-text "conflictOnSaveTxt1")]

    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
    [mui-stack {:style {:align-items "center"}}
     [mui-button {:color "secondary"
                  :variant "text"
                  :on-click tb-events/conflict-action-save-as} (tr-bl saveAs)]]
    [mui-stack
     [mui-typography {:sx {"&.MuiTypography-root" {:color (theme-color @custom-theme-atom :primary-main)}}}
      (tr-dlg-text "conflictOnSaveTxt2")]]

    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
    [mui-stack  {:direction "column"}
     [mui-stack {:style {:align-items "center"}}
      [mui-button {:color "error"
                   :variant "text"
                   :on-click tb-events/confirm-overwrite-external-db} (tr-bl overwrite)]]

     [mui-typography {:sx {"&.MuiTypography-root" {:color (theme-color @custom-theme-atom :primary-main)}}}
      (tr-dlg-text "conflictOnSaveTxt3")]]

    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
    [mui-stack  {:direction "column"}

     [mui-stack {:style {:align-items "center"}}
      [mui-button {:color "error"
                   :variant "text"
                   :on-click tb-events/confirm-discard-current-db} (tr-bl discardClose)]]

     [mui-typography {:sx {"&.MuiTypography-root" {:color (theme-color @custom-theme-atom :primary-main)}}}
      (tr-dlg-text "conflictOnSaveTxt4")]]
    [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]]

   [mui-dialog-actions

    [mui-button {:color "secondary"
                 :on-click tb-events/save-current-db-msg-dialog-hide} (tr-bl cancel)]]])

(defn save-info-dialog [{:keys [status api-error-text]}]
  (if (= api-error-text DB_CHANGED)
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
          biometric-type @(cmn-events/biometric-type-available)
          save-disabled? (or locked? (not @(cmn-events/db-save-pending?)))]
      (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE (not save-disabled?))
      (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_AS (not locked?))
      (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_BACKUP (not locked?))
      ;; React useEffect 
      (m/react-use-effect (fn []
                            #_(tauri-events/enable-app-menu const/MENU_ID_PASSWORD_GENERATOR true)
                            (tauri-events/enable-app-menu const/MENU_ID_CLOSE_DATABASE true)
                            (tauri-events/enable-app-menu const/MENU_ID_LOCK_DATABASE true)
                            (tauri-events/enable-app-menu const/MENU_ID_SEARCH true)
                            (tauri-events/enable-app-menu const/MENU_ID_MERGE_DATABASE (not locked?))

                            ;; cleanup fn is returned which is called when this component unmounts
                            (fn []
                              #_(tauri-events/enable-app-menu const/MENU_ID_PASSWORD_GENERATOR false)
                              (tauri-events/enable-app-menu const/MENU_ID_CLOSE_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_LOCK_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_AS false)
                              (tauri-events/enable-app-menu const/MENU_ID_MERGE_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_BACKUP false)
                              (tauri-events/enable-app-menu const/MENU_ID_SEARCH true))) (clj->js [locked?]))

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
            [mui-tooltip {:title "Quick Unlock Database" :enterDelay 2000}
             [mui-icon-button
              {:edge "start" :color "inherit"
               :on-click #(tb-events/unlock-current-db biometric-type)}
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

       ;; Include all dialogs that we need to use when the toll bar is visibible
       ;; Also see start_page.cljs for other dialogs  
       
       ;; Auto type dialogs
       [at-form/perform-auto-type-dialog @(at-events/auto-type-perform-dialog-data)]
       [at-form/auto-type-edit-dialog @(at-events/auto-type-edit-dialog-data)]

       ;; These are used here and in start_page.cljs
       [message-dialog]
       [app-settings-dialog-main]

       [gen-form/password-generator-dialog @(gen-events/generator-dialog-data)]

       [progress-message-dialog]
       [error-info-dialog]
       [od-form/open-db-dialog-main]
       [save-info-dialog save-action-data]
       [nd-form/new-database-dialog-main]
       [settings-form/settings-dialog-main]
       [search/search-dialog-main]
       [conflict-action-confirm-dialog @(tb-events/conflict-action-confirm-dialog-data)]
       [ask-save-dialog @(tb-events/ask-save-dialog-data)]
       [ask-save-on-lock @(tb-events/on-lock-ask-save-dialog-data)]
       [close-current-db-save-dialog @(tb-events/close-current-db-dialog-data)]
       [csv/csv-columns-mapping-dialog]
       [csv/csv-imoprt-start-dialog]
       [merging/merge-result-dialog]])))
