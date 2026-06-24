(ns onekeepass.frontend.ssh-agent
  "UI for the desktop SSH agent service. Phase 3: the allow/deny dialog shown
  when a 'Require Confirmation' key receives a sign request."
  (:require
   [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.events.ssh-agent :as ssh-agent-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-box
                                                     mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-divider
                                                     mui-typography
                                                     get-theme-color]]
   [onekeepass.frontend.translation :as t]))

(set! *warn-on-infer* true)

(defn- ssh-agent-sign-confirm-dialog-content
  [{:keys [dialog-show request-id title fingerprint]}]
  (when dialog-show
    [mui-dialog {:open dialog-show
                 :dir (t/dir)
                 :maxWidth "sm"
                 :fullWidth true}
     [mui-dialog-title {}
      [mui-typography {:variant "h6"} (t/lstr-dlg-title "sshAgentSignRequest")]]
     [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
     [mui-dialog-content {:dividers true
                          :sx {:p 2}}
      [mui-box {}
       [mui-typography {:sx {:mb 2}} (t/lstr-dlg-text "sshAgentSignRequestTxt")]
       [mui-typography {:sx {:mb 1 :fontWeight "bold"}} title]
       [mui-typography {:sx {:fontFamily "monospace" :fontSize "0.85em"}} fingerprint]]]
     [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
     [mui-dialog-actions {}
      [mui-button {:onClick
                   (fn []
                     (ssh-agent-events/sign-answer request-id true))} (t/lstr-bl "allow")]
      [mui-button {:onClick
                   (fn []
                     (ssh-agent-events/sign-answer request-id false))} (t/lstr-bl "reject")]]]))

(defn ssh-agent-sign-confirm-dialog []
  (ssh-agent-sign-confirm-dialog-content
   @(gd-events/ssh-agent-sign-confirm-dialog-data)))
