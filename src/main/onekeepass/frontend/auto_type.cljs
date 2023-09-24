(ns onekeepass.frontend.auto-type
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.events.auto-type :as at-events]
   [onekeepass.frontend.mui-components :as m :refer [color-primary-main
                                                     color-primary-dark
                                                     mui-alert
                                                     mui-typography
                                                     mui-dialog
                                                     mui-dialog-title
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-divider
                                                     mui-box
                                                     mui-stack
                                                     mui-icon-settings-outlined
                                                     mui-button]]))


(def sx1 {"&.MuiTypography-root" {:color color-primary-dark}})

(defn get-sequence [auto-type-m]
  (let [s (:default-sequence auto-type-m)]
    (if-not (nil? s)
      s 
      at-events/DEFAULT_SEQUENCE
      )
    )
  #_(println "s is " (get auto-type-m :default-sequence at-events/DEFAULT_SEQUENCE))
  #_(get auto-type-m :default-sequence at-events/DEFAULT_SEQUENCE))

(defn perform-auto-type-dialog [{:keys [dialog-show window-info auto-type] :as dialog-data}]
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :on-click #(.stopPropagation %)
               :sx {:min-width "600px"
                    "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
   [mui-dialog-title "Perform Auto type ?"]
   [mui-dialog-content {:sx {:padding-left "24px"}}
    [mui-stack
     [mui-stack
      [mui-stack [mui-typography {:sx sx1 :variant "heading6"} "Window Title"]]
      [mui-typography {:variant "body2"} (:title window-info)]]
     [mui-stack {:sx {:margin-bottom 1}}]
     [mui-stack
      [mui-stack [mui-typography {:sx sx1 :variant "heading6"}  "App name"]]
      [mui-typography {:variant "body2"} (:owner window-info)]]
     [mui-stack {:sx {:margin-bottom 2}}]
     [mui-divider]
     [mui-stack {:sx {:width "95%"}}
      [mui-stack
       [mui-stack [mui-typography {:sx sx1
                                   :variant "heading6"}
                   "Key Sequences"]]
       [mui-typography {:variant "body2"} (get-sequence auto-type)]]]]]
   [mui-dialog-actions
    [mui-button {:variant "contained" :color "secondary"
                 :disabled false
                 :on-click at-events/cancel-on-click} "Cancel"]
    [mui-button {:variant "contained" :color "secondary"
                 :disabled false
                 :on-click at-events/cancel-on-click} "Send"]]])


(defn auto-type-edit-dialog [{:keys [dialog-show api-error-text auto-type]}]
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :on-click #(.stopPropagation %)
               :sx {:min-width "600px"
                    "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
   [mui-dialog-title "Auto type Sequence"]
   [mui-dialog-content {:sx {:padding-left "24px"}}
    [m/text-field {:label "Key Sequences"
                   :value (get-sequence auto-type)
                           ;;:autoFocus true
                   :on-change (fn [^js/Event e] 
                                (at-events/auto-type-edit-dialog-update :default-sequence (-> e .-target .-value)))
                   :variant "standard"
                   :fullWidth true
                   :InputProps {}}]
    (when-not (nil? api-error-text)
      [mui-stack
       [mui-alert {:severity "error" :sx {:mt 1}} api-error-text]
       #_[mui-typography {} api-error-text]])

    [mui-dialog-actions
     [mui-button {:variant "contained" :color "secondary"
                  :disabled false
                  :on-click at-events/auto-type-edit-dialog-close} "Cancel"]
     [mui-button {:variant "contained" :color "secondary"
                  :disabled false
                  :on-click at-events/auto-type-edit-dialog-ok} "Ok"]]]])















#_[m/text-field {:label "Key Sequences"
                 :value (:default-sequence auto-type)
                     ;;:autoFocus true
                 :on-change #()
                 :variant "standard"
                 :fullWidth true
                 :classes {:root "entry-cnt-field"}
                 :InputProps {:classes {:root "entry-cnt-text-field-read"
                                        :focused "entry-cnt-text-field-read"}}
                 :inputProps  {:readOnly true}}]