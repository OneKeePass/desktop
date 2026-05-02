(ns onekeepass.frontend.events.browser-integration

  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :refer [on-error]]
   [re-frame.core :refer [dispatch reg-event-fx reg-fx]]))

(set! *warn-on-infer* true)

(defn brower-use-verified [browser-id permit?]
  (dispatch [:brower-use-verified browser-id permit?]))

(defn dispatch-pick-install-dir [browser-id]
  (dispatch [:browser-integration/pick-install-dir browser-id]))

(defn dispatch-close-install-grant-dialog []
  (dispatch [:generic-dialog-close :browser-extension-install-grant-dialog]))

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

;; Shown when the MAS sandbox requires a folder-picker grant before the
;; native-messaging manifest can be written for the given browser.
(reg-event-fx
 :browser-integration/needs-user-grant
 (fn [{:keys [_db]} [_event-id browser-id]]
   {:fx [[:dispatch [:generic-dialog-show-with-state
                     :browser-extension-install-grant-dialog
                     {:browser-id browser-id}]]]}))

;; Dispatched when the user clicks Allow in the explainer dialog.
;; Calls browser_ext_pick_install_dir on the Tauri side, then re-dispatches
;; :app-settings-save so the full preference update (including other fields
;; changed in the same settings dialog) completes.
(reg-event-fx
 :browser-integration/pick-install-dir
 (fn [{:keys [_db]} [_event-id browser-id]]
   {:fx [[:bg-browser-ext-pick-install-dir {:browser-id browser-id}]]}))

(reg-fx
 :bg-browser-ext-pick-install-dir
 (fn [{:keys [browser-id]}]
   (bg/browser-ext-pick-install-dir
    browser-id
    (fn [api-response]
      (dispatch [:generic-dialog-close :browser-extension-install-grant-dialog])
      (when-not (on-error api-response)
        ;; Re-trigger the full settings save now that the bookmark is stored.
        (dispatch [:app-settings-save]))))))
 