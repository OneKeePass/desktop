(ns onekeepass.frontend.tool-bar
  (:require
   [onekeepass.frontend.about :as about]
   [onekeepass.frontend.app-settings :refer [app-settings-dialog-main]]
   [onekeepass.frontend.check-for-updates :as check-updates]
   [onekeepass.frontend.manage-custom-icons :refer [manage-custom-icons-dialog-main custom-icons-delete-confirm-dialog]]
   [onekeepass.frontend.events.custom-icons :as ci-events]
   [onekeepass.frontend.auto-type :as at-form]
   [onekeepass.frontend.browser-integration :as browser-integration]
   [onekeepass.frontend.ssh-agent :as ssh-agent]
   [onekeepass.frontend.common-components :refer [app-bar-themed-props
                                                  confirm-text-dialog
                                                  error-info-dialog
                                                  message-dialog
                                                  progress-message-dialog]]
   [onekeepass.frontend.constants :as const :refer [DB_CHANGED]]
   [onekeepass.frontend.db-settings :as settings-form]
   [onekeepass.frontend.remote-storage :as rs-form]
   [onekeepass.frontend.events.auto-type :as at-events]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.db-settings :as settings-events]
   [onekeepass.frontend.events.group-tree-content :as gt-events]
   [onekeepass.frontend.events.open-db-form :as od-events]
   [onekeepass.frontend.events.password-generator :as gen-events]
   [onekeepass.frontend.events.search :as srch-event]
   [onekeepass.frontend.events.tauri-events :as tauri-events]
   [onekeepass.frontend.events.tool-bar :as tb-events]
   [onekeepass.frontend.import-file.csv :as csv]
   [onekeepass.frontend.events.merging :as merging-events]
   [onekeepass.frontend.merging :as merging]
   [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                     mui-alert mui-app-bar
                                                     mui-box mui-button
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
                                                     mui-icon-image
                                                     mui-linear-progress
                                                     mui-stack mui-toolbar
                                                     mui-tooltip
                                                     mui-typography
                                                     theme-color]]
   [onekeepass.frontend.new-database :as nd-form]
   [onekeepass.frontend.open-db-form :as od-form]
   [onekeepass.frontend.open-recent :as open-recent]
   [onekeepass.frontend.password-generator :as gen-form]
   [onekeepass.frontend.search :as search]
   [onekeepass.frontend.translation :as t :refer-macros [tr-bl tr-dlg-title tr-dlg-text]]))

(set! *warn-on-infer* true)

;; TODO: Something similar to what we do for close database
(defn ask-save-on-lock [dialog-data]
  [confirm-text-dialog
   (tr-dlg-title unsavedChanges)
   (tr-dlg-text "unsavedChangesTxt1")
   [{:label (t/lstr-bl 'ok) :on-click #(tb-events/on-lock-ask-save-dialog-hide)}]
   dialog-data])

(defn- locked-db-name-list
  "Renders the locked db names one per line so the dialog stays readable."
  [names]
  (into [mui-stack {:sx {:pl 2 :mt 1}}]
        (for [nm names]
          ^{:key nm} [mui-typography {:variant "body2"} (str "• " nm)])))

(defn- ask-save-dialog-content
  "Quit-time unsaved-changes message. Three cases:
     - no locked dirty dbs -> all changes can be saved (plain save/quit)
     - all dirty dbs locked -> nothing can be saved (locked ones listed;
       one/many wording), so only quit-without-saving/cancel is offered
     - mixed -> Save covers only the unlocked dbs; the locked ones are listed."
  [_dialog-data]
  (let [locked-dbs @(tb-events/quit-locked-dirty-dbs)
        all-locked? @(tb-events/quit-all-dirty-locked?)]
    [mui-stack {:spacing 1}
     (cond
       (empty? locked-dbs)
       [mui-typography (t/lstr-dlg-text "quitUnsavedTxt")]

       all-locked?
       [:<>
        [mui-typography (t/lstr-dlg-text (if (= 1 (count locked-dbs))
                                           "quitLockedOneTxt"
                                           "quitLockedManyTxt"))]
        [locked-db-name-list locked-dbs]]

       :else
       [mui-stack
        [mui-typography (t/lstr-dlg-text "quitMixedSaveTxt")]
        [mui-typography {:sx {:mt 2}} (t/lstr-dlg-text "quitMixedLockedTxt")]
        [locked-db-name-list locked-dbs]])]))

(defn ask-save-dialog [dialog-data]
  ;; On quit, any locked db among the modified ones cannot be saved (its content
  ;; is encrypted in memory). When every dirty db is locked, "Save" would be a
  ;; no-op, so it is dropped and only quit-without-saving/cancel is offered.
  (let [locked-dbs @(tb-events/quit-locked-dirty-dbs)
        all-locked? @(tb-events/quit-all-dirty-locked?)]
    [confirm-text-dialog
     (t/lstr-dlg-title 'unsavedChanges)
     ask-save-dialog-content
     (if (and (seq locked-dbs) all-locked?)
       [{:label (t/lstr-bl 'quitWithoutSaving) :on-click #(tb-events/on-do-not-save-click)}
        {:label (t/lstr-bl 'cancel) :on-click #(tb-events/ask-save-dialog-show false)}]
       [{:label (t/lstr-bl 'save) :on-click #(tb-events/on-save-click)}
        {:label (t/lstr-bl 'quitWithoutSaving) :on-click #(tb-events/on-do-not-save-click)}
        {:label (t/lstr-bl 'cancel) :on-click #(tb-events/ask-save-dialog-show false)}])
     dialog-data]))

(defn close-current-db-save-dialog [dialog-data]
  ;; A locked db cannot be saved (its content is encrypted in memory). Instead of
  ;; a broken "Save", offer "Unlock" - which cancels the close and starts the
  ;; unlock flow so the user can save then close - plus "Close Anyway" (discard)
  ;; and "Cancel". An unlocked db keeps the usual Save / Do Not Save / Cancel.
  (let [locked? @(cmn-events/locked?)
        biometric-type @(cmn-events/biometric-type-available)]
    [confirm-text-dialog
     (if locked? (t/lstr-dlg-title 'databaseLocked) (t/lstr-dlg-title 'unsavedChanges))
     (if locked? (t/lstr-dlg-text "closeLockedDbTxt") (t/lstr-dlg-text "unsavedChangesTxt3"))
     (if locked?
       [{:label (t/lstr-bl 'unlockDatabase) :on-click #(tb-events/close-current-db-unlock biometric-type)}
        {:label (t/lstr-bl 'closeAnyway) :on-click tb-events/close-current-db-no-save}
        {:label (t/lstr-bl 'cancel) :on-click tb-events/close-current-db-on-cancel-click}]
       [{:label (t/lstr-bl 'save) :on-click tb-events/close-current-db-on-save-click}
        {:label (t/lstr-bl 'doNotSave) :on-click tb-events/close-current-db-no-save}
        {:label (t/lstr-bl 'cancel) :on-click tb-events/close-current-db-on-cancel-click}])
     dialog-data]))

(defn conflict-action-confirm-dialog [{:keys [dialog-show confirm]}]
  (if (= confirm :overwrite)
    [confirm-text-dialog
     (tr-dlg-title confirmOverwrite)
     (tr-dlg-text confirmOverwrite)
     [{:label (tr-bl yesOverwrite) :on-click tb-events/overwrite-external-changes}
      {:label (t/lstr-bl 'cancel) :on-click tb-events/conflict-action-confirm-dialog-hide}]
     {:dialog-show dialog-show}]
    [confirm-text-dialog
     (tr-dlg-title confirmDiscard)
     (tr-dlg-text confirmDiscard)
     [{:label (tr-bl discard) :on-click tb-events/conflict-action-discard}
      {:label (t/lstr-bl 'cancel) :on-click tb-events/conflict-action-confirm-dialog-hide}]
     {:dialog-show dialog-show}]))

;; This is somewhat similar to the modal dialog fn 'save-error-modal' of 
;; src-cljs/cljs-main-app-src/main/onekeepass/mobile/save_error_dialog.cljs
;; TODO: Need to move these save related fns to a separate ns

(defn- content-change-action-dialog [open?]
  ;; The conflict is resolved against the db whose save failed. That is normally
  ;; the active one, but an auto-save may have been saving a background db
  (let [active-key (or @(tb-events/saving-db-key) @(cmn-events/active-db-key))]
    [mui-dialog {:open open? :on-click #(.stopPropagation ^js/Event %)}
     [mui-dialog-title (tr-dlg-title conflictOnSave)]
     [mui-dialog-content
      [mui-stack (tr-dlg-text "conflictOnSaveTxt1")]

      ;; Merge is offered for both local (disk-version) and remote
      ;; (remote-version) dbs; :external-change-merge-start routes by db type.
      [:<>
       [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
       [mui-stack {:style {:align-items "center"}}
        [mui-button {:color "primary"
                     :variant "text"
                     :on-click #(tb-events/conflict-action-merge active-key)}
         (tr-bl merge)]]
       [mui-stack
        [mui-typography {:sx {"&.MuiTypography-root" {:color (theme-color @custom-theme-atom :primary-main)}}}
         (tr-dlg-text "conflictOnSaveMergeTxt")]]]

      [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
      [mui-stack {:style {:align-items "center"}}
       [mui-button {:variant "text"
                    :on-click tb-events/conflict-action-save-as} (tr-bl saveAs)]]
      [mui-stack
       [mui-typography {:sx {"&.MuiTypography-root" {:color (theme-color @custom-theme-atom :primary-main)}}}
        (tr-dlg-text "conflictOnSaveTxt2")]]

      [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
      [mui-stack {:direction "column"}
       [mui-stack {:style {:align-items "center"}}
        [mui-button {:color "error"
                     :variant "text"
                     :on-click tb-events/confirm-overwrite-external-db} (tr-bl overwrite)]]
       [mui-typography {:sx {"&.MuiTypography-root" {:color (theme-color @custom-theme-atom :primary-main)}}}
        (tr-dlg-text "conflictOnSaveTxt3")]]

      [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]
      [mui-stack {:direction "column"}
       [mui-stack {:style {:align-items "center"}}
        [mui-button {:color "error"
                     :variant "text"
                     :on-click tb-events/confirm-discard-current-db} (tr-bl discardClose)]]
       [mui-typography {:sx {"&.MuiTypography-root" {:color (theme-color @custom-theme-atom :primary-main)}}}
        (tr-dlg-text "conflictOnSaveTxt4")]]
      [mui-divider {:style {:margin-bottom 5 :margin-top 5}}]]

     [mui-dialog-actions
      [mui-button {:on-click tb-events/save-current-db-msg-dialog-hide} (t/lstr-bl 'cancel)]]]))

(defn save-info-dialog [{:keys [status api-error-text quiet?]}]
  (if (= api-error-text DB_CHANGED)
    [content-change-action-dialog true]
    ;; An auto-save runs quietly - no progress modal. Errors are never quiet:
    ;; the user has to act on them, so :error still opens the dialog
    [mui-dialog {:open (or (and (= status :in-progress) (not quiet?))
                           (= status :error))
                 :on-click #(.stopPropagation ^js/Event %)}
     [mui-dialog-title "Save Database"]
     [mui-dialog-content
      [mui-stack
       "Saving database is in progress"

       (when api-error-text
         [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])

       (when (and (nil? api-error-text) (= status :in-progress))
         [mui-linear-progress {:sx {:mt 2}}])]]
     [mui-dialog-actions
      [mui-button {:disabled (= status :in-progress)
                   :on-click tb-events/save-current-db-msg-dialog-hide} "Close"]]]))

(defn top-bar
  "A tool bar function component from Reagent a component so that 
   we can use effect to enable/disable certain App menus"
  []
  (fn []
    (let [save-action-data @(tb-events/save-current-db-data)
          locked? @(cmn-events/locked?)
          biometric-type @(cmn-events/biometric-type-available)
          save-disabled? (or locked? (not @(cmn-events/db-save-pending?)))
          unlocked-count (count @(merging-events/multiple-unlocked-dbs?))
          multiple-dbs? (>= unlocked-count 2)
          ;; "Lock All Databases" is meaningful whenever at least one open db is unlocked
          any-unlocked? (pos? unlocked-count)
          ;; "Check Remote Changes" is only meaningful for an unlocked remote db
          remote? (cmn-events/remote-db-key? @(cmn-events/active-db-key))
          ;; New Group / Edit Group (native "Groups" menu) act on the selected
          ;; group. They stay active whenever an unlocked db has a normal (non
          ;; recycle-bin) group selected - the default state right after a db is
          ;; opened (root selected). Previously these were toggled from the group
          ;; tree-item three-dot menu's mount/unmount, which left them disabled.
          selected-group-uuid @(gt-events/selected-group-uuid)
          recycle-bin-selected? @(gt-events/recycle-group-selected?)
          root-group-selected? @(gt-events/root-group-selected?)
          group-menus-enabled? (and (not locked?)
                                    (some? selected-group-uuid)
                                    (not recycle-bin-selected?))
          ;; Clone Group / Delete Group additionally cannot act on the root group
          ;; (there is no parent to clone under, and root cannot be deleted).
          clone-delete-menus-enabled? (and group-menus-enabled?
                                           (not root-group-selected?))]
      (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE (not save-disabled?))
      (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_AS (not locked?))
      (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_BACKUP (not locked?))
      ;; Reactive: re-evaluated on every render (i.e. when db lock state or group
      ;; selection changes), so the native New/Edit Group menu tracks the current
      ;; selection instead of a tree-item component's lifecycle.
      (tauri-events/enable-app-menu const/MENU_ID_NEW_GROUP group-menus-enabled?)
      (tauri-events/enable-app-menu const/MENU_ID_EDIT_GROUP group-menus-enabled?)
      (tauri-events/enable-app-menu const/MENU_ID_CLONE_GROUP clone-delete-menus-enabled?)
      (tauri-events/enable-app-menu const/MENU_ID_DELETE_GROUP clone-delete-menus-enabled?)
      ;; React useEffect
      (m/react-use-effect (fn []
                            #_(tauri-events/enable-app-menu const/MENU_ID_PASSWORD_GENERATOR true)
                            (tauri-events/enable-app-menu const/MENU_ID_CLOSE_DATABASE true)
                            ;; "Lock Database" only applies to the current db when it is unlocked
                            (tauri-events/enable-app-menu const/MENU_ID_LOCK_DATABASE (not locked?))
                            ;; "Lock All Databases" applies when any open db is still unlocked
                            (tauri-events/enable-app-menu const/MENU_ID_LOCK_ALL_DATABASES any-unlocked?)
                            (tauri-events/enable-app-menu const/MENU_ID_SEARCH true)
                            (tauri-events/enable-app-menu const/MENU_ID_MERGE_DATABASE (not locked?))
                            (tauri-events/enable-app-menu const/MENU_ID_MERGE_OPENED_DATABASES multiple-dbs?)
                            ;; Active for an unlocked remote db. Kept inside the effect (not the
                            ;; render body) so it isn't clobbered by this effect's own cleanup,
                            ;; which runs on every deps change - remote? is in the deps below.
                            (tauri-events/enable-app-menu const/MENU_ID_CHECK_REMOTE_CHANGES (and remote? (not locked?)))

                            ;; cleanup fn is returned which is called when this component unmounts
                            (fn []
                              #_(tauri-events/enable-app-menu const/MENU_ID_PASSWORD_GENERATOR false)
                              (tauri-events/enable-app-menu const/MENU_ID_CLOSE_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_LOCK_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_LOCK_ALL_DATABASES false)
                              (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_AS false)
                              (tauri-events/enable-app-menu const/MENU_ID_MERGE_DATABASE false)
                              (tauri-events/enable-app-menu const/MENU_ID_MERGE_OPENED_DATABASES false)
                              (tauri-events/enable-app-menu const/MENU_ID_SAVE_DATABASE_BACKUP false)
                              (tauri-events/enable-app-menu const/MENU_ID_NEW_GROUP false)
                              (tauri-events/enable-app-menu const/MENU_ID_EDIT_GROUP false)
                              (tauri-events/enable-app-menu const/MENU_ID_CLONE_GROUP false)
                              (tauri-events/enable-app-menu const/MENU_ID_DELETE_GROUP false)
                              (tauri-events/enable-app-menu const/MENU_ID_CHECK_REMOTE_CHANGES false)
                              (tauri-events/enable-app-menu const/MENU_ID_SEARCH true))) (clj->js [locked? multiple-dbs? any-unlocked? remote?]))

      [:div {:style {:flex-grow 1}}
       ;; Light theme: override the default bright primary blue with the chosen
       ;; toolbar-style (see options above). For light bars :fg flips the icons to
       ;; dark so they stay legible. Dark theme is left as-is (MUI's default dark
       ;; app-bar surface).

       ;; Previous one used for both light and dark theme cases
       ;; mui-app-bar {:position "static" :color "primary" :dir (t/dir)}

       [mui-app-bar (app-bar-themed-props)
        ;; :min-height gives the compact bar a bit more room for the larger icons.
        ;; :font-size below enlarges every toolbar icon glyph (theme default is
        ;; 'small' ~20px); bump/reduce the rem value to taste.
        [mui-toolbar {:style {:min-height 36}
                      :sx {"& .MuiSvgIcon-root" {:font-size "1.4rem"}}}
         ;; Using box to provide common styles - horizontal spacing -  for all its children - buttons
         ;; Using "&.MuiIconButton-root" etc did not work.
         ;; :mx spaces the left-group buttons apart (was a tightening -8px);
         ;; increase for more gap between Open/Save/Save-As/Close/Lock.
         [mui-box {:sx {"& .MuiButtonBase-root" {:mx "0.5px"}}}
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
               :on-click #(tb-events/unlock-current-db biometric-type)}
              [mui-icon-lock-outlined]]]
            [mui-tooltip {:title "Lock Database" :enterDelay 2000}
             [mui-icon-button
              {:edge "start" :color "inherit"
               :on-click tb-events/lock-current-db}
              [mui-icon-lock-open-outlined]]])]
         [:span  {:style {:flex-grow "1"}}]

         ;; Right-side group. Same :mx spacing as the left box so both groups
         ;; are loosened by the same amount.
         [mui-box {:sx {"& .MuiButtonBase-root" {:mx "0.5px"}}}
          [mui-tooltip {:title "Manage Custom Icons" :enterDelay 2000}
           [mui-icon-button {:edge "end"
                             :disabled locked?
                             :color "inherit"
                             :on-click #(do (ci-events/refresh-icons-for-db)
                                            (ci-events/show-manage-dialog))}
            ;;"🖼"
            [mui-icon-image]]]

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
            [mui-icon-search]]]]]]

       ;; Include all dialogs that we need to use when the toll bar is visibible
       ;; Also see start_page.cljs for other dialogs  

       ;; Auto type dialogs
       [at-form/perform-auto-type-dialog @(at-events/auto-type-perform-dialog-data)]
       [at-form/auto-type-edit-dialog @(at-events/auto-type-edit-dialog-data)]

       ;; These are used here and in start_page.cljs
       [message-dialog]
       [app-settings-dialog-main]
       [about/about-dialog-main]
       [check-updates/check-for-updates-dialog-main]
       [browser-integration/browser-extension-connection-permit-dialog]
       [browser-integration/browser-extension-install-grant-dialog]
       [ssh-agent/ssh-agent-sign-confirm-dialog]

       [gen-form/password-generator-dialog @(gen-events/generator-dialog-data)]

       [progress-message-dialog]
       [error-info-dialog]
       [od-form/open-db-dialog-main]
       [open-recent/open-recent-dialog-main]
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
       [merging/merge-result-dialog]
       [merging/merge-opened-dbs-dialog]
       [merging/external-db-change-dialog]
       [manage-custom-icons-dialog-main]
       [custom-icons-delete-confirm-dialog]
       [rs-form/remote-storage-dialog-main]])))
