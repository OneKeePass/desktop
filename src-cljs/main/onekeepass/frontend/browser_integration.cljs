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
      [mui-typography {:variant "h6"} (t/lstr-dlg-title "permitBrowserConnection")]]
     [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
     [mui-dialog-content {:dividers true
                          :sx {:p 2}}
      [mui-box {}
       [mui-typography {:sx {:mb 2}} (t/lstr-dlg-text "permitBrowserConnectionTxt")]]]
     [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
     [mui-dialog-actions {}
      [mui-button {:onClick
                   (fn []
                     (br-int-events/brower-use-verified browser-id true))} (t/lstr-bl "allow")]

      [mui-button {:onClick
                   (fn []
                     (br-int-events/brower-use-verified browser-id false))} (t/lstr-bl "reject")]]]))


(defn browser-extension-connection-permit-dialog []
  (browser-extension-connection-permit-dialog-content
   @(gd-events/browser-extension-connection-permit-dialog-data)))

(defn- browser-extension-install-grant-dialog-content
  "Dialog shown on macOS App Sandbox builds when the app needs the user to
  click Allow on a folder picker so it can write the browser-extension manifest
  to the browser's NativeMessagingHosts directory."
  [{:keys [dialog-show browser-id actual-dir]}]
  (when dialog-show
    (let [dir-hint (or actual-dir
                       (case browser-id
                         "firefox" "~/Library/Application Support/Mozilla/NativeMessagingHosts"
                         "chrome"  "~/Library/Application Support/Google/Chrome/NativeMessagingHosts"
                         "the browser's NativeMessagingHosts folder"))]
      [mui-dialog {:open dialog-show
                   :dir (t/dir)
                   :maxWidth "sm"
                   :fullWidth true}
       [mui-dialog-title {}
        [mui-typography {:variant "h6"} (t/lstr-dlg-title "allowFolderAccess")]]
       [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
       [mui-dialog-content {:dividers true
                            :sx {:p 2}}
        [mui-box {}
         [mui-typography {:sx {:mb 2}}
          (t/lstr-dlg-text "installGrantTxt1")]
         [mui-typography {:sx {:mb 2 :fontFamily "monospace" :fontSize "0.85em"}} dir-hint]
         [mui-typography {}
          (t/lstr-dlg-text "installGrantTxt2")]]]
       [mui-divider {:sx {:border-color (get-theme-color :divider-color1)}}]
       [mui-dialog-actions {}
        [mui-button {:onClick
                     (fn []
                       (br-int-events/dispatch-pick-install-dir browser-id))}
         (t/lstr-bl "allow")]
        [mui-button {:onClick
                     (fn []
                       (br-int-events/dispatch-close-install-grant-dialog))}
         (t/lstr-bl "later")]]])))

(defn browser-extension-install-grant-dialog []
  (browser-extension-install-grant-dialog-content
   @(gd-events/browser-extension-install-grant-dialog-data)))
