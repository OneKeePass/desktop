(ns onekeepass.frontend.open-recent
  (:require
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.open-db-form :as od-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-button mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-icon-button
                                                     mui-icon-close-outlined
                                                     mui-list mui-list-item
                                                     mui-list-item-secondary-action
                                                     mui-list-item-text
                                                     mui-tooltip
                                                     mui-typography]]
   [onekeepass.frontend.translation :as t :refer-macros [tr-dlg-title tr-bl]]))

(set! *warn-on-infer* true)

(defn- open-recent-dialog [{:keys [dialog-show]} recent-files]
  [mui-dialog {:open (boolean dialog-show)
               :dir (t/dir)
               :on-close cmn-events/open-recent-dialog-hide
               :on-click #(.stopPropagation ^js/Event %)
               :sx {"& .MuiDialog-paper" {:width "60%" :max-width "600px"}}}
   [mui-dialog-title (tr-dlg-title "openRecent")]
   [mui-dialog-content
    (if (empty? recent-files)
      [mui-typography {:variant "body1" :sx {:py 2}}
       "No recent files"]
      [mui-list {:dense true :disablePadding true}
       (doall
        (for [file-path recent-files]
          ^{:key file-path}
          [mui-list-item {:divider true}
           [mui-tooltip {:title file-path :enterDelay 1000}
            [mui-list-item-text
             {:primary file-path
              :primaryTypographyProps {:noWrap true
                                       :sx {:cursor "pointer"
                                            :max-width "480px"}}
              :on-click (fn []
                          (cmn-events/open-recent-dialog-hide)
                          (od-events/recent-file-link-on-click file-path))}]]
           [mui-list-item-secondary-action
            [mui-icon-button {:edge "end"
                              :size "small"
                              :on-click #(cmn-events/remove-recent-file file-path)}
             [mui-icon-close-outlined]]]]))])]
   [mui-dialog-actions
    [mui-button {:on-click cmn-events/open-recent-dialog-hide}
     (tr-bl "close")]]])

(defn open-recent-dialog-main []
  (let [dialog-data @(cmn-events/open-recent-dialog-data)
        recent-files @(cmn-events/recent-files)]
    (when (:dialog-show dialog-data)
      [open-recent-dialog dialog-data recent-files])))
