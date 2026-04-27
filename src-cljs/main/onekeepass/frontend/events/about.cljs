(ns onekeepass.frontend.events.about
  (:require [re-frame.core :refer [reg-event-fx]]))

(reg-event-fx
 :about/dialog-show
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:generic-dialog-show :about-dialog]]]}))


;; (defn about-dialog-show []
;;   (dispatch [:about/dialog-show]))

;; (defn about-dialog-close []
;;   (dispatch [:about/dialog-close]))
;; 
;; (defn about-dialog-data []
;;   (subscribe [:generic-dialog-data :about-dialog]))

;; 
;; (reg-event-fx
;;  :about/dialog-close
;;  (fn [{:keys [_db]} [_event-id]]
;;    {:fx [[:dispatch [:generic-dialog-close :about-dialog]]]}))
