(ns onekeepass.frontend.about
  (:require
   [onekeepass.frontend.events.about]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.generic-dialogs :as gd-events] ;; This is required to ensure that events of about are registred
   [onekeepass.frontend.mui-components :as m :refer [mui-button mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-divider
                                                     mui-icon-launch mui-link
                                                     mui-stack mui-typography]]
   [onekeepass.frontend.translation :as t :refer-macros [tr-bl tr-dlg-text tr-dlg-title tr-l] :refer [lstr-dlg-text]]))

(set! *warn-on-infer* true)

(defn- about-dialog-content [{:keys [dialog-show]}]
  (when dialog-show
    (let [app-version @(cmn-events/app-version)
          os-name     @(cmn-events/os-name)
          os-version  @(cmn-events/os-version)]
      [mui-dialog {:open true
                   :dir (t/dir)
                   :on-click #(.stopPropagation ^js/Event %)
                   :sx {"& .MuiPaper-root" {:width "440px"}}}
       [mui-dialog-title (tr-dlg-title "about")]
       [mui-divider]
       [mui-dialog-content {:sx {:pt 2}}
        [mui-stack {:spacing 1}
         [mui-stack {:direction "row" :spacing 1 :alignItems "center"}
          [mui-typography {:variant "subtitle2" :sx {:min-width "120px"}} (tr-l "version")]
          [mui-typography {:variant "body2"} (or app-version "")]]
         [mui-stack {:direction "row" :spacing 1 :alignItems "center"}
          [mui-typography {:variant "subtitle2" :sx {:min-width "120px"}} (tr-l "platform")]
          [mui-typography {:variant "body2"} (str os-name " " os-version)]]
         [mui-divider {:sx {:my 1}}]
         [mui-typography {:variant "body2"} (tr-dlg-text "aboutDescription")]
         [mui-divider {:sx {:my 1}}]
         [mui-stack {:direction "row" :spacing 1 :alignItems "center"}
          [mui-typography {:variant "subtitle2" :sx {:min-width "120px"}} (tr-l "github")]
          [mui-link {:component "button"
                     :variant "body2"
                     :sx {:display "flex" :alignItems "center" :gap "4px"}
                     :onClick #(cmn-events/open-url "https://github.com/OneKeePass/desktop")}
           "OneKeePass Desktop"
           [mui-icon-launch {:sx {:fontSize "14px"}}]]]
         [mui-divider {:sx {:my 1}}]
         [mui-typography {:variant "caption" :sx {:color "text.secondary"}}
          (lstr-dlg-text 'aboutCopyright {:year-and-prouct "2024–2026 OneKeePass"} )
          #_(tr-dlg-text "aboutCopyright")]]] ;;2024–2026 OneKeePass Contributors (lstr-dlg-title "sectionField2" {:section-name section-name})
       [mui-dialog-actions
        [mui-button {:on-click gd-events/about-dialog-close} (tr-bl "close")]]])))

(defn about-dialog-main []
  (about-dialog-content @(gd-events/about-dialog-data)))
