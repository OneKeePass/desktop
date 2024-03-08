(ns onekeepass.frontend.entry-form.common
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
                                                              mui-input-adornment mui-link
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
            [reagent.core :as r])
  
  )

(def ENTRY_DATETIME_FORMAT "dd MMM yyyy pp")

(def background-color1 "#F1F1F1")
(def popper-border-color "#E7EBF0")
(def popper-button-sx {:color "secondary.light"})

(def popper-box-sx {:bgcolor  "whitesmoke";;"background.paper"
                    ;;:boxShadow 3
                    :p "15px"
                    :pb "5px"
                    :border-color popper-border-color
                    :border-width "thin"
                    :border-style "solid"})

(def content-sx {;;:width "98%"
                 :border-color "lightgrey"
                 :boxShadow 0
                 :borderRadius 1
                 :margin "5px"
                 :background "white"
                 :padding "8px 8px 8px 8px"
                 :border ".1px solid"})


(def delete-totp-confirm-dialog-data (r/atom {:dialog-show false :section-name nil :otp-field-name nil}))

(defn show-delete-totp-confirm-dialog [section-name otp-field-name] 
  (reset! delete-totp-confirm-dialog-data {:dialog-show true
                                           :section-name section-name
                                           :otp-field-name otp-field-name
                                           }))

(defn close-delete-totp-confirm-dialog [] 
  (reset! delete-totp-confirm-dialog-data {:dialog-show false}))
