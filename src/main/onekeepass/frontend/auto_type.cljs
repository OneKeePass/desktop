(ns onekeepass.frontend.auto-type
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.events.auto-type :as at-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-typography
                                                     mui-dialog
                                                     mui-dialog-title
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-box mui-stack
                                                     mui-icon-settings-outlined
                                                     mui-button]
    
    
    ]))


(defn perform-auto-type-dialog [{:keys [dialog-show] :as dialog-data}] 
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)  
               :on-click #(.stopPropagation %)
               :sx {:min-width "600px"
                    "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
   [mui-dialog-title "Perform Auto type"]
   [mui-dialog-content {:sx {:padding-left "10px"}}
    [mui-stack
     [mui-box {:sx {:width "80%"}}
      [m/text-field {:label "Key Sequences"
                     :value ""
                     :required true
                     :autoFocus true
                     :on-change #()
                     :variant "standard" :fullWidth true}]]]]
   [mui-dialog-actions 
    [mui-button {:variant "contained" :color "secondary"
                 :disabled false
                 :on-click at-events/cancel-on-click} "Cancel"]
    [mui-button {:variant "contained" :color "secondary"
                 :disabled false
                 :on-click at-events/cancel-on-click} "Send"]
    ]])