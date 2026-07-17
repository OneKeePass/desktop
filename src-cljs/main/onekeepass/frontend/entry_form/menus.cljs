(ns onekeepass.frontend.entry-form.menus
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.common-components :as cc]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.clone-entry-to-other-db :as clone-events]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]
   [onekeepass.frontend.events.entry-form-ex :as form-events]
   [onekeepass.frontend.events.remote-storage :as rs-events]
   [onekeepass.frontend.events.tauri-events :as tauri-events]
   [onekeepass.frontend.group-tree-content :as gt-content]
   [onekeepass.frontend.keyboard-shortcuts :as kb-shortcuts]
   [onekeepass.frontend.mui-components :as m :refer [mui-icon-add-circle-outline-outlined
                                                     mui-icon-button
                                                     mui-icon-check
                                                     mui-icon-more-vert
                                                     mui-list-item-icon
                                                     mui-list-item-text
                                                     mui-menu mui-menu-item
                                                     mui-typography]]
   [onekeepass.frontend.translation  :refer [lstr-dlg-title lstr-ml]]
   [reagent.core :as r]))

(defn- menu-action [anchor-el action & action-args]
  (fn [^js/Event e]
    (reset! anchor-el nil)
    (apply action action-args)
    (.stopPropagation ^js/Event e)))

(defn- shortcut-hint
  "The keyboard shortcut hint text shown on the right side of a menu item"
  [os-name key-label & opts]
  [mui-typography {:variant "body2"
                   :sx {:color "text.secondary" :ml "auto" :pl 3}}
   (apply kb-shortcuts/menu-shortcut-hint os-name key-label opts)])

(defn- entry-field-copy-open-menu-items
  "Menu items to copy/open field values of the entry shown in the form.
   These match the keyboard shortcuts - see ns onekeepass.frontend.keyboard-shortcuts"
  [anchor-el os-name]
  [:<>
   [mui-menu-item {:sx {:padding-left "1px"}
                   :divider false
                   :on-click (menu-action anchor-el form-events/copy-entry-form-field-to-clipboard const/USERNAME)}
    [mui-list-item-text {:inset true} (lstr-ml 'copyUsername)]
    [shortcut-hint os-name "B"]]

   [mui-menu-item {:sx {:padding-left "1px"}
                   :divider false
                   :on-click (menu-action anchor-el form-events/copy-entry-form-field-to-clipboard const/PASSWORD)}
    [mui-list-item-text {:inset true} (lstr-ml 'copyPassword)]
    [shortcut-hint os-name "C"]]

   [mui-menu-item {:sx {:padding-left "1px"}
                   :divider false
                   :on-click (menu-action anchor-el form-events/copy-entry-form-field-to-clipboard const/URL)}
    [mui-list-item-text {:inset true} (lstr-ml 'copyUrl)]
    [shortcut-hint os-name "U" :shift? true]]

   [mui-menu-item {:sx {:padding-left "1px"}
                   :divider false
                   :on-click (menu-action anchor-el form-events/open-selected-entry-url)}
    [mui-list-item-text {:inset true} (lstr-ml 'openUrl)]
    [shortcut-hint os-name "U"]]

   [mui-menu-item {:sx {:padding-left "1px"}
                   :divider true
                   ;; Disabled when the entry does not have any otp field set up
                   ;; The sub value is a map of keys - token ttl period
                   :disabled (str/blank? (:token @(form-events/otp-currrent-token const/OTP)))
                   :on-click (menu-action anchor-el form-events/copy-entry-form-otp-token-to-clipboard)}
    [mui-list-item-text {:inset true} (lstr-ml 'copyTotp)]
    [shortcut-hint os-name "T"]]])

(defn- auto-type-menu-items [anchor-el entry-uuid]
  [:<>
   [mui-menu-item {:divider false
                   :sx {:padding-left "1px"}
                   :on-click (menu-action anchor-el form-events/perform-auto-type-start entry-uuid)}
    [mui-list-item-text {:inset true} (lstr-ml 'performAutoType)]]

   [mui-menu-item {:divider true
                   :sx {:padding-left "1px"}
                   :on-click (menu-action anchor-el form-events/entry-auto-type-edit)}
    [mui-list-item-text {:inset true} (lstr-ml 'editAutoType)]]])

(defn entry-form-top-menu-items []
  (fn [anchor-el entry-uuid favorites? os-name mas-build? remote-connection-entry? entry-type-uuid]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               ;; disableEnforceFocus: the copy field menu items copy via the webview's
               ;; native copy (document.execCommand 'copy') which briefly focuses a
               ;; temporary textarea. The menu is still mounted when the copy action runs
               ;; and with MUI's default focus trap, the menu synchronously yanks focus
               ;; back before execCommand runs failing the copy. See the same fix in
               ;; onekeepass.frontend.context-menu/context-menu-root
               :disableEnforceFocus true
               :on-close #(reset! anchor-el nil)}
     ;; Need to use a reagent component instead of fragments using :<> as MUI complains
     ;; that Menu item child should not be a fragment (see auto-type-menu-items use below)
     [entry-field-copy-open-menu-items anchor-el os-name]

     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el form-events/edit-mode-menu-clicked)}
      [mui-list-item-text {:inset true} (lstr-ml 'edit)]]

     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el dlg-events/clone-entry-options-dialog-show entry-uuid)}
      [mui-list-item-text {:inset true} (lstr-ml 'clone)]]

     (when @(clone-events/multi-db-open?)
       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action anchor-el clone-events/clone-entry-to-other-db-dialog-show entry-uuid)}
        [mui-list-item-text {:inset true} (lstr-ml "cloneToDatabase")]])

     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el gt-content/move-group-or-entry-dialog-show-with-state
                                            :entry
                                            (lstr-dlg-title 'moveEntry)
                                            entry-uuid
                                            @(form-events/entry-form-data-fields :group-uuid)
                                            @(cmn-events/active-db-key))}
      [mui-list-item-text {:inset true} (lstr-ml 'move)]]

     [mui-menu-item {:divider true
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/entry-delete-start entry-uuid)}
      [mui-list-item-text {:inset true} (lstr-ml 'delete)]]

     (if favorites?
       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider true
                       :on-click (menu-action anchor-el form-events/favorite-menu-checked false)}
        [mui-list-item-icon [mui-icon-check]] (lstr-ml 'favorites)]
       [mui-menu-item {:divider true
                       :sx {:padding-left "1px"}
                       :on-click (menu-action anchor-el form-events/favorite-menu-checked true)}
        [mui-list-item-text {:inset true} (lstr-ml 'favorites)]])

     ;; Auto type related menu options are available only on macOS, and only
     ;; when this is NOT a Mac App Store build. Auto-type relies on
     ;; CGEvent.post(.cgSessionEventTap) which is kernel-blocked under App
     ;; Sandbox (events silently dropped) and on CGWindowListCopyWindowInfo
     ;; which requires Screen Recording entitlement App Review denies for
     ;; password managers — so the entire feature is non-functional in MAS.
     ;; The Rust auto_type module + Tauri commands are also compile-gated
     ;; out of the MAS binary; this `when` guard hides the corresponding UI.
     (when (and (= os-name const/MACOS) (not mas-build?))
       ;; Need to use this reagent component instead of fragmets using :<> as MUI complains
       ;; that Menu item child should not be a fragment and instead suggested to use an array of child
       [auto-type-menu-items anchor-el entry-uuid])

     [mui-menu-item {:divider remote-connection-entry?
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/load-history-entries-summary entry-uuid)
                     :disabled (not @(form-events/history-available))}
      [mui-list-item-text {:inset true} (lstr-ml 'history)]]

     (when remote-connection-entry?
       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action
                                  anchor-el
                                  rs-events/open-entry-remote entry-type-uuid
                                  entry-uuid)}
        [mui-list-item-text {:inset true} (lstr-ml "openRemote")]])]))

(defn entry-form-top-menu [entry-uuid]
  (let [anchor-el (r/atom nil)
        favorites? @(form-events/favorites?)
        os-name @(cmn-events/os-name)
        mas-build? @(cmn-events/is-mas-build?)
        entry-type-uuid @(form-events/entry-form-data-fields :entry-type-uuid)
        remote-connection-entry? (const/remote-connection-entry-type? entry-type-uuid)]
    [:div
     [mui-icon-button {:edge "start"
                       :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))
                       :style {}} [mui-icon-more-vert]]
     [entry-form-top-menu-items anchor-el entry-uuid favorites? os-name mas-build? remote-connection-entry? entry-type-uuid]
     [cc/info-dialog "Entry Delete" "Deleting entry is in progress"
      form-events/entry-delete-info-dialog-close
      @(form-events/entry-delete-dialog-data)]]))

;; System 'Entries' menu items that follow the entry selection. These are enabled
;; only while an entry is shown (this component is mounted) - see form-menu-internal
(def ^:private entry-action-app-menu-ids
  [const/MENU_ID_EDIT_ENTRY
   const/MENU_ID_COPY_USERNAME
   const/MENU_ID_COPY_PASSWORD
   const/MENU_ID_COPY_URL
   const/MENU_ID_OPEN_URL])

(defn- form-menu-internal
  "A functional reagent component.This component helps to enable/disable
  Edit Entry and entry field copy/open app menus using react effect"
  [entry-uuid]
  (let [deleted-cat? @(form-events/deleted-category-showing)
        recycle-bin? @(form-events/recycle-group-selected?)
        group-in-recycle-bin? @(form-events/selected-group-in-recycle-bin?)
        edit-menu?  (not (or deleted-cat? recycle-bin? group-in-recycle-bin?))
        ;; The sub value is a map of keys - token ttl period
        otp-available? (not (str/blank? (:token @(form-events/otp-currrent-token const/OTP))))]
    ;; useEffect is used to enable/disable as when the form-menu is visible or not
    ;; The effect is rerun when any of the deps 'edit-menu?' 'otp-available?' changes
    (m/react-use-effect (fn []
                          (doseq [menu-id entry-action-app-menu-ids]
                            (tauri-events/enable-app-menu menu-id edit-menu?))
                          ;; Copy TOTP is enabled only when the entry has an otp field set up
                          (tauri-events/enable-app-menu const/MENU_ID_COPY_TOTP (and edit-menu? otp-available?))
                          ;; cleanup fn is returned which is called when this component unmounts
                          (fn []
                            (doseq [menu-id entry-action-app-menu-ids]
                              (tauri-events/enable-app-menu menu-id false))
                            (tauri-events/enable-app-menu const/MENU_ID_COPY_TOTP false)))
                        (clj->js [edit-menu? otp-available?]))
    (when edit-menu?
      [entry-form-top-menu entry-uuid])))

(defn form-menu
  "Called to show relevant menus for a selected entry. 
  This is used in the entry-form and entry-list panels"
  [entry-uuid]
  [:f> form-menu-internal entry-uuid])

;;;;;;;;;;;;;  
;; Folllowing are may be used as menu options in a section header when we want to 
;; add a regular field or otp field 

(defn add-additional-field-menu-items  []
  (fn [anchor-el section-name]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el  #(form-events/open-section-field-dialog section-name nil))}
      [mui-list-item-text {:inset true} (lstr-ml 'addField)]]

     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el #(dlg-events/otp-settings-dialog-show section-name false))}
      [mui-list-item-text {:inset true} (lstr-ml 'setUpTOPT)]]]))

(defn add-additional-field-menu [section-name]
  (let [anchor-el (r/atom nil)]
    [:div
     [mui-icon-button {:edge "end" :color "primary"
                       :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))}
      [mui-icon-add-circle-outline-outlined]]
     [add-additional-field-menu-items anchor-el section-name]]))
