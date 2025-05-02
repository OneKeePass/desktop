(ns onekeepass.frontend.entry-form.common
  (:require [onekeepass.frontend.mui-components :as m :refer [theme-color]]
            [reagent.core :as r]))

;; Use DATETIME_FORMAT from constants
(def ENTRY_DATETIME_FORMAT "dd MMM yyyy pp")

(def popper-border-color "#E7EBF0")
(def popper-button-sx {:color "secondary.light"})

;; IMPORTANT: 
;; We should not use something like  '(def bg-color (theme-color @custom-them-atom :entry-content-box-border))'
;; The 'custom-them-atom' might have not been created yet
;; The 'create-custom-theme' is only called when the app start page is mounted 
;; Then only we can use 'custom-them-atom'

(defn theme-popper-box-sx
  "Uses current them from reagent atom to get the colors based on the mode
   IMPORTANT: We need to use a fn from the component to get sx props for the created theme 
  "
  [theme]
  {:bgcolor  (theme-color theme :popper-box-bg)
   :p "15px"
   :pb "5px"
   :border-color popper-border-color
   :border-width "thin"
   :border-style "solid"})

(defn theme-content-sx [theme]
  {:border-color (theme-color theme :entry-content-box-border)
   :border-style "solid"
   :border-width ".5px"
   :background (theme-color theme :entry-content-bg)
   :boxShadow 0
   :borderRadius 1
   :margin "5px"
   :padding "8px 8px 8px 8px"})

(def delete-totp-confirm-dialog-data (r/atom {:dialog-show false :section-name nil :otp-field-name nil}))

(defn show-delete-totp-confirm-dialog [section-name otp-field-name]
  (reset! delete-totp-confirm-dialog-data {:dialog-show true
                                           :section-name section-name
                                           :otp-field-name otp-field-name}))

(defn close-delete-totp-confirm-dialog []
  (reset! delete-totp-confirm-dialog-data {:dialog-show false}))
