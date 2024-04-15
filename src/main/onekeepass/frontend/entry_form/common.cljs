(ns onekeepass.frontend.entry-form.common
  (:require [reagent.core :as r]))

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
                                           :otp-field-name otp-field-name}))

(defn close-delete-totp-confirm-dialog []
  (reset! delete-totp-confirm-dialog-data {:dialog-show false}))
