(ns onekeepass.frontend.events.ssh-agent
  "Events for the desktop SSH agent service: the sign-request confirmation dialog
  (Phase 3) and the settings-panel enable toggle + live status (Phase 4)."
  (:require
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :refer [check-error on-error]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub subscribe]]))

(set! *warn-on-infer* true)

;; ---- Settings panel: enable toggle + live status ----

;; AgentStatus map {:running :socket-path :key-count :error} kept in app-db.

(defn load-status []
  (dispatch [:ssh-agent/load-status]))

(defn set-enabled [enabled?]
  (dispatch [:ssh-agent/set-enabled enabled?]))

(defn agent-status []
  (subscribe [:ssh-agent/status]))

(reg-event-fx
 :ssh-agent/load-status
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:bg-ssh-agent-status]]}))

(reg-fx
 :bg-ssh-agent-status
 (fn [_]
   (bg/ssh-agent-status
    (fn [api-response]
      (when-let [status (check-error api-response)]
        (dispatch [:ssh-agent-status-loaded status]))))))

;; Enabling/disabling takes effect immediately (the command both persists the
;; flag and starts/stops the listener) and returns the fresh status.
(reg-event-fx
 :ssh-agent/set-enabled
 (fn [{:keys [_db]} [_event-id enabled?]]
   {:fx [[:bg-ssh-agent-set-enabled enabled?]]}))

(reg-fx
 :bg-ssh-agent-set-enabled
 (fn [enabled?]
   (let [cb (fn [api-response]
              (when-let [status (check-error api-response)]
                (dispatch [:ssh-agent-status-loaded status])))]
     (if enabled?
       (bg/start-ssh-agent cb)
       (bg/stop-ssh-agent cb)))))

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
