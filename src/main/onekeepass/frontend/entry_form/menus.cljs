(ns onekeepass.frontend.entry-form.menus
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.entry-form.common :refer []]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.common-components :as cc]
   [onekeepass.frontend.mui-components :as m :refer [mui-icon-add-circle-outline-outlined
                                                     mui-icon-button
                                                     mui-icon-button
                                                     mui-icon-check
                                                     mui-icon-more-vert
                                                     mui-icon-more-vert
                                                     mui-list-item-icon
                                                     mui-list-item-text
                                                     mui-list-item-text
                                                     mui-menu
                                                     mui-menu-item]]
   [onekeepass.frontend.events.tauri-events :as tauri-events]
   [onekeepass.frontend.events.common :as ce]
   [onekeepass.frontend.events.entry-form-ex :as form-events]
   [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]))

(defn- menu-action [anchor-el action & action-args]
  (fn [^js/Event e]
    (reset! anchor-el nil)
    (apply action action-args)
    (.stopPropagation ^js/Event e)))

(defn- auto-type-menu-items [anchor-el entry-uuid]
  [:<>
   [mui-menu-item {:divider false
                   :sx {:padding-left "1px"}
                   :on-click (menu-action anchor-el form-events/perform-auto-type-start entry-uuid)}
    [mui-list-item-text {:inset true} "Perform auto type"]]

   [mui-menu-item {:divider true
                   :sx {:padding-left "1px"}
                   :on-click (menu-action anchor-el form-events/entry-auto-type-edit)}
    [mui-list-item-text {:inset true} "Edit auto type"]]])

(defn entry-form-top-menu-items []
  (fn [anchor-el entry-uuid favorites? os-name]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el form-events/edit-mode-menu-clicked)}
      [mui-list-item-text {:inset true} "Edit"]]

     (if favorites?
       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action anchor-el form-events/favorite-menu-checked false)}
        [mui-list-item-icon [mui-icon-check]] "Favorites"]
       [mui-menu-item {:divider false
                       :sx {:padding-left "1px"}
                       :on-click (menu-action anchor-el form-events/favorite-menu-checked true)}
        [mui-list-item-text {:inset true} "Favorites"]])


     [mui-menu-item {:divider true
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/entry-delete-start entry-uuid)}
      [mui-list-item-text {:inset true} "Delete"]]

     ;; Auto type related menu options are avilable only for macos
     (when (= os-name const/MACOS)
       ;; Need to use this reagent component instead of fragmets using :<> as MUI complains
       ;; that Menu item child should not be a fragment and instead suggested to use an array of child
       [auto-type-menu-items anchor-el entry-uuid])

     [mui-menu-item {:divider false
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/load-history-entries-summary entry-uuid)
                     :disabled (not @(form-events/history-available))}
      [mui-list-item-text {:inset true} "History"]]]))

(defn entry-form-top-menu [entry-uuid]
  (let [anchor-el (r/atom nil)
        favorites? @(form-events/favorites?)
        os-name @(ce/os-name)]
    [:div
     [mui-icon-button {:edge "start"
                       :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))
                       :style {:color "#000000"}} [mui-icon-more-vert]]
     [entry-form-top-menu-items anchor-el entry-uuid favorites? os-name]
     [cc/info-dialog "Entry Delete" "Deleting entry is in progress"
      form-events/entry-delete-info-dialog-close
      @(form-events/entry-delete-dialog-data)]]))

(defn- form-menu-internal
  "A functional reagent component.This component helps to enable/disable 
  Edit Entry app menu using react effect"
  [entry-uuid]
  (let [deleted-cat? @(form-events/deleted-category-showing)
        recycle-bin? @(form-events/recycle-group-selected?)
        group-in-recycle-bin? @(form-events/selected-group-in-recycle-bin?)
        edit-menu?  (not (or deleted-cat? recycle-bin? group-in-recycle-bin?))]
    ;; useEffect is used to enable/disable as when the form-menu is visible or not
    (m/react-use-effect (fn []
                          (tauri-events/enable-app-menu const/MENU_ID_EDIT_ENTRY edit-menu?)
                          ;; cleanup fn is returned which is called when this component unmounts
                          (fn []
                            (tauri-events/enable-app-menu const/MENU_ID_EDIT_ENTRY false))) (clj->js []))
    (when edit-menu?
      [entry-form-top-menu entry-uuid])))

(defn form-menu
  "Called to show relevant menus for a selected entry. 
  This is used in the entry-form and entry-list panels"
  [entry-uuid]
  [:f> form-menu-internal entry-uuid])

(defn add-additional-field-menu-items  []
  (fn [anchor-el section-name]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el  #(form-events/open-section-field-dialog section-name nil))}
      [mui-list-item-text {:inset true} "Add field"]]

     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el #(dlg-events/otp-settings-dialog-show section-name false))}
      [mui-list-item-text {:inset true} "Set up TOPT"]]]))

(defn add-additional-field-menu [section-name]
  (let [anchor-el (r/atom nil)]
    [:div
     [mui-icon-button {:edge "end" :color "primary"
                       :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))}
      [mui-icon-add-circle-outline-outlined]]
     [add-additional-field-menu-items anchor-el section-name]]))
