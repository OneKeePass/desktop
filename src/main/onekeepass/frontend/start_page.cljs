(ns ^:figwheel-always onekeepass.frontend.start-page
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.custom-icons :as cust-icons]
   [onekeepass.frontend.open-db-form :as od-form]
   [onekeepass.frontend.new-database :as nd-form]
   [onekeepass.frontend.common-components :refer [message-dialog]]
   
   [onekeepass.frontend.events.new-database :as nd-events]
   [onekeepass.frontend.events.open-db-form :as od-events]
   [onekeepass.frontend.events.common :as cmn-events]
   
   [onekeepass.frontend.background :as bg]
   
   [onekeepass.frontend.mui-components :as m :refer [mui-link
                                                     mui-typography
                                                     mui-container 
                                                     mui-icon-button 
                                                     mui-icon-folder-outlined 
                                                     mui-box
                                                     mui-divider
                                                     mui-stack 
                                                     mui-tooltip]]))

(set! *warn-on-infer* true)

(defn main-content []
  [mui-container {:sx {:height "100%"
                       ;;:bgcolor "text.disabled" 
                       ;;:bgcolor "primary.main"
                       :bgcolor "var(--mui-color-grey-200)"
                       :color "primary.main"}} ;;:mt 10
   [mui-stack {:direction "row" :gap 2 :alignItems "center"
               :divider (r/as-element [mui-divider {:orientation "vertical" :flexItem true}])
               :sx {:height "100%"}}
    [mui-box {:sx {:display "flex" :width "50%" :height "100%" :flexDirection "column"}}
     [mui-typography {:variant "h6"} "Start"]
     [mui-stack {:direction "row" :gap 2 :alignItems "center"}
      [mui-icon-button  {:edge "start" :color "inherit" :sx {:ml 0}} 
       [cust-icons/database-cog-outline]]
      [mui-link  {:variant "subtitle1"
                  :onClick nd-events/new-database-dialog-show}
       "New Database"]
      [nd-form/new-database-dialog-main]]

     [mui-stack {:direction "row" :gap 2 :alignItems "center"}
      [mui-icon-button  {:edge "start" :color "inherit" :sx {:ml 0}
                         :onClick od-events/open-file-explorer-on-click}
       [mui-icon-folder-outlined]]
      [mui-link {:variant "subtitle1"
                 :onClick od-events/open-file-explorer-on-click}
       "Open Database"]
      [od-form/open-db-dialog-main]]

     [mui-typography {:sx {:mt 4} :variant "h6"} "Recent"]
     [mui-stack {:mt 1}
      (doall
       (for [lnk @(cmn-events/recent-files)]
         ^{:key lnk} [mui-tooltip {:title lnk :enterDelay 1500}
                      [mui-link {:sx {:mt 1 :white-space "nowrap" :text-overflow "ellipsis" :overflow "hidden"}
                                 :variant "body2"
                                 :on-click #(od-events/recent-file-link-on-click lnk)}
                       lnk]]))]]

    [mui-box {:sx {:display "flex"
                   :width "50%"
                   :justify-content "flex-start"
                   :flexDirection "column"}}
     #_[mui-stack {:direction "row" :align-self "center"} ;;:sx {:mt "25%"}
        [mui-typography {:variant "h4" :sx {:color "text.primary"}} "OneKeePass"]]
     [mui-stack {:direction "row" :align-self "center"} ;;:sx {:mt "25%"}
      [mui-typography {:variant "h4" :sx {:color "text.primary"}} "OneKeePass..."]]
     
     [mui-stack
      [mui-link {:on-click #(bg/test-save-key)} "Save key"]
[mui-link {:on-click #(bg/test-read-key)} "Read key"]
      ]
     
     ]]])

(defn welcome-content []
  [:div {:class "box" :style {:overflow "hidden" :background-color "ghostwhite"}} ;; hidden used so to avoid showing scrollbar 
   [:div {:class "cust_row header" :style {:text-align "center"}}
    [mui-stack {:direction "row" :justify-content "center"  :sx {:bgcolor #_(fn [^js/Mui.Theme theme] (-> theme .-status .-danger)) "secondary.main"}}
     [mui-typography {:variant "h5"
                      :sx {:color "secondary.contrastText"}} "Get Started"]]]
   [:div {:class "cust_row content" :style {:height "100%"}}
    [main-content]]
   [message-dialog]])