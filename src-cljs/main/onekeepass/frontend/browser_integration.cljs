(ns onekeepass.frontend.browser-integration
  (:require
   [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.events.browser-integration :as br-int-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-box
                                                     mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-stack
                                                     mui-divider
                                                     mui-typography
                                                     get-theme-color]]
   [onekeepass.frontend.translation :as t]))

(set! *warn-on-infer* true)

(defn- browser-extension-connection-permit-dialog-content
  "Dialog to show browser integration related info"
  [{:keys [dialog-show browser-id] :as args}]
  ;; (println "browser-extension-connection-permit-dialog-content args: " args)
  (when dialog-show
    [mui-dialog {:open dialog-show
                 :dir (t/dir)
                 :maxWidth "sm"
                 :fullWidth true}
     [mui-dialog-title {}
      [mui-typography {:variant "h6"} "Permit browser connection"]]
     [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
     [mui-dialog-content {:dividers true
                          :sx {:p 2}}
      [mui-box {}
       [mui-typography {:sx {:mb 2}} (str "A browser extension is requesting permission to connect to OneKeepass. "
                                          "If you trust this extension, please click 'Allow'. "
                                          "Otherwise, click 'Reject'.")]]]
     [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
     [mui-dialog-actions {}
      [mui-button {:onClick
                   (fn []
                     (br-int-events/brower-use-verified browser-id true))} "Allow"]

      [mui-button {:onClick
                   (fn []
                     (br-int-events/brower-use-verified browser-id false))
                   #_(fn []
                       #_(gd-events/browser-extension-connection-permit-dialog-close))}  "Reject"]]]))


(defn browser-extension-connection-permit-dialog []
  (browser-extension-connection-permit-dialog-content
   @(gd-events/browser-extension-connection-permit-dialog-data)))

(defn- browser-extension-install-grant-dialog-content
  "Dialog shown on macOS App Sandbox builds when the app needs the user to
  click Allow on a folder picker so it can write the browser-extension manifest
  to the browser's NativeMessagingHosts directory."
  [{:keys [dialog-show browser-id]}]
  (when dialog-show
    (let [dir-hint (case browser-id
                     "firefox" "~/Library/Application Support/Mozilla/NativeMessagingHosts"
                     "chrome"  "~/Library/Application Support/Google/Chrome/NativeMessagingHosts"
                     "the browser's NativeMessagingHosts folder")]
      [mui-dialog {:open dialog-show
                   :dir (t/dir)
                   :maxWidth "sm"
                   :fullWidth true}
       [mui-dialog-title {}
        [mui-typography {:variant "h6"} "Allow folder access"]]
       [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
       [mui-dialog-content {:dividers true
                            :sx {:p 2}}
        [mui-box {}
         [mui-typography {:sx {:mb 2}}
          (str "macOS requires OneKeePass to have explicit permission to write the "
               "browser-extension manifest file. In the next dialog, navigate to:")]
         [mui-typography {:sx {:mb 2 :fontFamily "monospace" :fontSize "0.85em"}} dir-hint]
         [mui-typography {}
          "Click “Allow” to grant access. This is a one-time prompt per browser."]]]
       [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
       [mui-dialog-actions {}
        [mui-button {:onClick
                     (fn []
                       (br-int-events/dispatch-pick-install-dir browser-id))}
         "Allow"]
        [mui-button {:onClick
                     (fn []
                       (br-int-events/dispatch-close-install-grant-dialog))}
         "Later"]]])))

(defn browser-extension-install-grant-dialog []
  (browser-extension-install-grant-dialog-content
   @(gd-events/browser-extension-install-grant-dialog-data)))