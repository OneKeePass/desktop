(ns onekeepass.frontend.check-for-updates
  (:require
   [onekeepass.frontend.events.check-for-updates]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-box mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-divider
                                                     mui-stack
                                                     mui-typography]]
   [onekeepass.frontend.translation :as t :refer [lstr-bl lstr-dlg-text
                                                  lstr-dlg-title]]))

(set! *warn-on-infer* true)

(defn- update-available-dialog [{:keys [current-version
                                        latest-version
                                        release-notes
                                        download-url]}]
  [mui-dialog {:open true
               :dir (t/dir)
               :on-click #(.stopPropagation ^js/Event %)
               :sx {"& .MuiPaper-root" {:width "520px" :max-width "95vw"}}}
   [mui-dialog-title (lstr-dlg-title 'updateAvailable)]
   [mui-divider]
   [mui-dialog-content {:sx {:pt 2}}
    [mui-stack {:spacing 2}
     [mui-typography {:variant "body1"}
      (lstr-dlg-text 'updateAvailable {:latest-version latest-version
                                        :current-version current-version})]
     (when (and release-notes (seq release-notes))
       [mui-box {:sx {:max-height "260px"
                      :overflow "auto"
                      :p 1
                      :border "1px solid"
                      :border-color "divider"
                      :border-radius 1
                      :background-color "action.hover"}}
        [mui-typography {:variant "body2"
                         :component "pre"
                         :sx {:white-space "pre-wrap"
                              :font-family "inherit"
                              :margin 0}}
         release-notes]])]]
   [mui-dialog-actions
    [mui-button {:on-click (fn []
                             (gd-events/check-for-updates-dialog-close))}
     (lstr-bl "later")]
    [mui-button {:on-click (fn []
                             (cmn-events/open-url download-url)
                             (gd-events/check-for-updates-dialog-close))}
     (lstr-bl "download")]]])

(defn- up-to-date-dialog [{:keys [current-version]}]
  [mui-dialog {:open true
               :dir (t/dir)
               :on-click #(.stopPropagation ^js/Event %)
               :sx {"& .MuiPaper-root" {:width "420px"}}}
   [mui-dialog-title (lstr-dlg-title 'upToDate)]
   [mui-divider]
   [mui-dialog-content {:sx {:pt 2}}
    [mui-typography {:variant "body1"}
     (lstr-dlg-text 'upToDate {:current-version current-version})]]
   [mui-dialog-actions
    [mui-button {:on-click (fn []
                             (gd-events/check-for-updates-dialog-close))}
     (lstr-bl "ok")]]])

(defn check-for-updates-dialog-main []
  (let [{:keys [dialog-show data]} @(gd-events/check-for-updates-dialog-data)]
    (when dialog-show
      (if (:update-available? data)
        [update-available-dialog data]
        [up-to-date-dialog data]))))
