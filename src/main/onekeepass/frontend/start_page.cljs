(ns ^:figwheel-always onekeepass.frontend.start-page 
  (:require [onekeepass.frontend.common-components :refer [message-dialog]]
            [onekeepass.frontend.custom-icons :as cust-icons]
            [onekeepass.frontend.events.common :as cmn-events]
            [onekeepass.frontend.events.new-database :as nd-events]
            [onekeepass.frontend.events.open-db-form :as od-events]
            [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                              mui-box
                                                              mui-container
                                                              mui-divider
                                                              mui-icon-button
                                                              mui-icon-folder-outlined
                                                              mui-link
                                                              mui-stack
                                                              mui-tooltip
                                                              mui-typography
                                                              theme-color]]
            [onekeepass.frontend.new-database :as nd-form]
            [onekeepass.frontend.open-db-form :as od-form]
            [onekeepass.frontend.translation :refer-macros [tr tr-l tr-t]]
            [reagent.core :as r]))

(set! *warn-on-infer* true)

(defn main-content []
  [mui-container {:sx {:height "100%"
                       ;;:bgcolor "text.disabled" 
                       :bgcolor "background.default" 
                       :color "primary.main"}} ;;:mt 10
   [mui-stack {:direction "row" :gap 2 :alignItems "center"
               :divider (r/as-element [mui-divider {:sx {:border-color (theme-color @custom-theme-atom :divider-color1)} 
                                                    :orientation "vertical" :flexItem true}])
               :sx {:height "100%"}}
    [mui-box {:sx {:display "flex" :width "50%" :height "100%" :flexDirection "column"}}
     [mui-typography {:variant "h6"} (tr-t start)]
     [mui-stack {:direction "row" :gap 2 :alignItems "center"}
      [mui-icon-button  {:edge "start" :color "inherit" :sx {:ml 0} 
                         :onClick nd-events/new-database-dialog-show} 
       
       #_[mui-icon-post-add-outlined]
       [cust-icons/database-cog-outline]]
      [mui-link  {:variant "subtitle1"
                  :onClick nd-events/new-database-dialog-show} (tr-l newDatabase)]
      [nd-form/new-database-dialog-main]]

     [mui-stack {:direction "row" :gap 2 :alignItems "center"}
      [mui-icon-button  {:edge "start" :color "inherit" :sx {:ml 0}
                         :onClick od-events/open-file-explorer-on-click}
       [mui-icon-folder-outlined {}]]
      [mui-link {:variant "subtitle1"
                 :onClick od-events/open-file-explorer-on-click}
       (tr-l openDatabase) ]
      [od-form/open-db-dialog-main]]

     [mui-typography {:sx {:mt 4} :variant "h6"} (tr-t recent)]
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
     
     ;; The 'example-comp' is based on the react component 
     ;; defined in a local package js file. 
     #_[mui-stack [m/example-comp]]
     
     [mui-stack {:direction "row" :align-self "center"} ;;:sx {:mt "25%"}
      [mui-typography {:variant "h4" :sx {:color "text.primary"}} "OneKeePass"]]]]])

(defn welcome-content []
  [:div {:class "box" :style {:overflow "hidden" :background-color (theme-color @custom-theme-atom :bg-default) #_"ghostwhite"}} ;; hidden used so to avoid showing scrollbar 
   [:div {:class "cust_row header" :style {:text-align "center"}}
    ;; Another way of getting color from theme (fn [^js/Mui.Theme theme] (-> theme .-status .-danger))
    [mui-stack {:direction "row" :justify-content "center"  :sx {:bgcolor "secondary.main"}}
     [mui-typography {:variant "h5"
                      :sx {:color "secondary.contrastText"}} (tr getStarted)]]]
   [:div {:class "cust_row content" :style {:height "100%"}}
    [main-content]]
   [message-dialog]])