(ns onekeepass.frontend.events.ssh-agent
  "Events for the desktop SSH agent service: the sign-request confirmation dialog
  (Phase 3) and the settings-panel enable toggle + live status (Phase 4)."
  (:require
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :refer [check-error on-error]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub subscribe]]))

(set! *warn-on-infer* true)

;; ---- Settings panel: live status display ----
;;
;; The enable flag itself is an ordinary preference: the checkbox stages it into
;; `[:preference-data :ssh-agent-support :enabled]` and OK persists it through the
;; normal `update_preference` flow, which also starts/stops the listener (see
;; AppState::update_preference). This namespace only owns the *live* runtime
;; status (path / keys / error) shown alongside the checkbox, since that is not
;; part of the persisted preference.
;;
;; AgentStatus map {:running :socket-path :key-count :error} is kept in app-db.

;; Called on panel mount: fetch the live runtime status for display.
(defn init-panel []
  (dispatch [:ssh-agent/init-panel]))

(defn agent-status []
  (subscribe [:ssh-agent/status]))

(reg-event-fx
 :ssh-agent/init-panel
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:bg-ssh-agent-status]]}))

(reg-fx
 :bg-ssh-agent-status
 (fn [_]
   (bg/ssh-agent-status
    (fn [api-response]
      (when-let [status (check-error api-response)]
        (dispatch [:ssh-agent-status-loaded status]))))))

(reg-event-db
 :ssh-agent-status-loaded
 (fn [db [_event-id status]]
   (assoc-in db [:ssh-agent :status] status)))

(reg-sub
 :ssh-agent/status
 (fn [db [_event-id]]
   (get-in db [:ssh-agent :status])))

;; ---- Sign-request confirmation dialog (Phase 3) ----

;; Called from the dialog UI when the user clicks Allow / Deny.
(defn sign-answer [request-id allow?]
  (dispatch [:ssh-agent/sign-answer request-id allow?]))

;; Raised by the Tauri SSH_AGENT_SIGN_REQUEST_EVENT listener. Shows the
;; allow/deny dialog seeded with the request id, key title and fingerprint.
(reg-event-fx
 :ssh-agent/show-sign-confirm-dialog
 (fn [{:keys [_db]} [_event-id {:keys [request-id title fingerprint]}]]
   {:fx [[:dispatch [:generic-dialog-show-with-state
                     :ssh-agent-sign-confirm-dialog
                     {:request-id request-id
                      :title title
                      :fingerprint fingerprint}]]]}))

;; Closes the dialog and sends the user's answer back to the parked signer.
(reg-event-fx
 :ssh-agent/sign-answer
 (fn [{:keys [_db]} [_event-id request-id allow?]]
   {:fx [[:dispatch [:generic-dialog-close :ssh-agent-sign-confirm-dialog]]
         [:bg-ssh-agent-sign-confirm-result {:request-id request-id :allow allow?}]]}))

(reg-fx
 :bg-ssh-agent-sign-confirm-result
 (fn [{:keys [request-id allow]}]
   (bg/ssh-agent-sign-confirm-result
    request-id allow
    (fn [api-response]
      (when-not (on-error api-response)
        #_(println "SSH agent sign confirmation delivered"))))))
