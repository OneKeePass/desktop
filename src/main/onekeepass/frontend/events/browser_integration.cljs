(ns onekeepass.frontend.events.browser-integration

  (:require
   
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :refer [on-error]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))

(set! *warn-on-infer* true)

(defn brower-use-verified [browser-id permit?]
  (dispatch [:brower-use-verified browser-id permit?]))

(reg-event-fx
 :browser-integration/show-browser-extension-connection-permit-dialog
 (fn [{:keys [_db]} [_event-id browser-id]]
  ;;  (println "show-browser-extension-connection-permit-dialog event: " browser-id)
   {:fx [[:dispatch [:generic-dialog-show-with-state :browser-extension-connection-permit-dialog {:browser-id browser-id}]]]}))

(reg-event-fx
 :brower-use-verified
 (fn [{:keys [_db]} [_event-id browser-id permit?]]
  ;;  (println "brower-use-verified event: " browser-id permit?)
   {:fx [[:dispatch [:generic-dialog-close :browser-extension-connection-permit-dialog]]
         [:bg-browser-ext-use-user-permission {:browser-id browser-id :permit? permit?}]]}))

(reg-fx
 :bg-browser-ext-use-user-permission
 (fn [{:keys [browser-id permit?]}]
  ;;  (println "bg-browser-ext-use-user-permission: " browser-id permit?)
   ;; Call the background api to send the permission result to the browser extension
   (bg/browser-ext-use-user-permission browser-id permit?
                                       (fn [api-response]
                                         (when-not (on-error api-response) 
                                           (bg/minimize-window)
                                           #_(println "Successfully sent browser extension permission result"))))))
 