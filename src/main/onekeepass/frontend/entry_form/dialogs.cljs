(ns onekeepass.frontend.entry-form.dialogs
  (:require [clojure.string :as str]
            [onekeepass.frontend.common-components :as cc :refer [alert-dialog-factory
                                                                  enter-key-pressed-factory
                                                                  list-items-factory
                                                                  selection-autocomplete tags-field]]

            [onekeepass.frontend.constants :as const]
            [onekeepass.frontend.db-icons :as db-icons :refer [entry-icon
                                                               entry-type-icon]]
            [onekeepass.frontend.events.common :as ce]
            [onekeepass.frontend.events.entry-form-ex :as form-events]
            [onekeepass.frontend.events.move-group-entry :as move-events]
            [onekeepass.frontend.events.tauri-events :as tauri-events]
            [onekeepass.frontend.group-tree-content :as gt-content]
            [onekeepass.frontend.events.otp :as otp-events]
            [onekeepass.frontend.mui-components :as m :refer [color-primary-main
                                                              date-adapter
                                                              mui-alert
                                                              mui-alert-title
                                                              mui-avatar
                                                              mui-box
                                                              mui-button
                                                              mui-button
                                                              mui-checkbox
                                                              mui-circular-progress
                                                              mui-date-time-picker
                                                              mui-desktop-date-picker
                                                              mui-dialog
                                                              mui-dialog-actions
                                                              mui-dialog-content
                                                              mui-dialog-title
                                                              mui-divider
                                                              mui-form-control-label mui-grid
                                                              mui-icon-add-circle-outline-outlined
                                                              mui-icon-article-outlined
                                                              mui-icon-autorenew
                                                              mui-icon-button
                                                              mui-icon-button
                                                              mui-icon-check
                                                              mui-icon-delete-outline
                                                              mui-icon-edit-outlined
                                                              mui-icon-more-vert
                                                              mui-icon-more-vert
                                                              mui-icon-save-as-outlined
                                                              mui-icon-visibility
                                                              mui-icon-visibility-off
                                                              mui-input-adornment
                                                              mui-link
                                                              mui-list-item
                                                              mui-list-item-avatar
                                                              mui-list-item-icon
                                                              mui-list-item-text
                                                              mui-list-item-text
                                                              mui-localization-provider
                                                              mui-menu
                                                              mui-menu-item
                                                              mui-popper
                                                              mui-stack
                                                              mui-text-field
                                                              mui-tooltip
                                                              mui-typography]]
            [onekeepass.frontend.utils :as u :refer [contains-val?
                                                     to-file-size-str]]
            [reagent.core :as r]))

;;:sx {:min-width "600px" 
;;     "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}

(defn set-up-totp-dialog [{:keys [dialog-show
                                  section-name
                                  secret-code
                                  field-name
                                  api-error-text
                                  error-fields] :as dialog-data}]
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :on-click #(.stopPropagation %)
               :sx {"& .MuiPaper-root" {:width "60%"}}}

   [mui-dialog-title "TOTP Setup"]
   [mui-dialog-content
    [mui-stack
     [m/text-field {:label "Field Name"
                          ;; If we set ':value key', the dialog refreshes when on change fires for each key press in this input
                          ;; Not sure why. Using different name like 'field-name' works fine
                    :value field-name
                    :error (boolean (seq error-fields))
                    :helperText (get error-fields field-name)
                    :on-change #() ;;(on-change-factory form-events/section-field-dialog-update :field-name)
                    :variant "standard" :fullWidth true}]]]
   [mui-dialog-actions
    [mui-stack  {:sx {:justify-content "end"} :direction "row" :spacing 1}   ;;{:sx {:align-items "end"}}
     [mui-button {:on-click  (fn [_e]
                               (form-events/section-field-dialog-update :dialog-show false))} "Cancel"]
     [mui-button {:on-click #()}
      "Ok"]]]])