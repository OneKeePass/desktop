(ns onekeepass.frontend.auto-type
  (:require
   [reagent.core :as r]
   [clojure.string :as str]
   [onekeepass.frontend.utils :refer [contains-val?]]
   [onekeepass.frontend.events.auto-type :as at-events :refer [get-sequence is-custom-sequence]]
   [onekeepass.frontend.mui-components :as m :refer [color-primary-dark
                                                     mui-alert
                                                     mui-typography
                                                     mui-dialog
                                                     mui-dialog-title
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-divider
                                                     mui-stack
                                                     mui-button]]))


(def sx1 {"&.MuiTypography-root" {:color color-primary-dark}})

(def typography-porps1 {:sx {"&.MuiTypography-root" {:color color-primary-dark}
                             :variant "heading6"}})

(defn perform-auto-type-dialog [{:keys [dialog-show window-info auto-type] :as _dialog-data}]
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :on-click #(.stopPropagation %)
               :sx {:min-width "600px"
                    "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
   [mui-dialog-title "Perform Auto type ?"]
   [mui-dialog-content {:sx {:padding-left "24px"}}
    [mui-stack
     [mui-stack
      [mui-stack [mui-typography typography-porps1 "Window Title"]]
      [mui-typography {:variant "body2"} (:title window-info)]]
     [mui-stack {:sx {:margin-bottom 1}}]
     [mui-stack
      [mui-stack [mui-typography typography-porps1  "App name"]]
      [mui-typography {:variant "body2"} (:owner window-info)]]
     [mui-stack {:sx {:margin-bottom 2}}]
     [mui-divider]
     [mui-stack {:sx {:width "95%"}}
      [mui-stack
       [mui-stack [mui-typography typography-porps1
                   (if (is-custom-sequence (get-sequence auto-type)) "Key Sequence"  "Default Sequence")]]
       [mui-typography {:variant "body2"} (get-sequence auto-type)]]]]]
   [mui-dialog-actions
    [mui-button {:variant "contained" :color "secondary"
                 :disabled false
                 :on-click at-events/perform-dialog-show-close} "Cancel"]
    [mui-button {:variant "contained" :color "secondary"
                 :disabled false
                 :on-click at-events/send-auto-sequence} "Send"]]])

;; See parsing module in the backend
(def standard-fields ["{USERNAME}", "{PASSWORD}", "{TITLE}", "{URL}"])

(defn supported-form-fields
  "Returns a string with possible entry field variables
   e.g {USERNAME},{PASSWORD},{TITLE},{URL},{S:NAME2}
   "
  [entry-fields]
  (let [flds (reduce (fn [acc k]
                       (if (or (= k "Additional URLs") (contains-val? acc (str "{" (str/upper-case k) "}"))) 
                         acc
                         (conj acc (str "{S:" (str/upper-case k) "}"))))
                     standard-fields (keys entry-fields))]
    (str/join "," flds)))

(defn auto-type-edit-dialog [{:keys [dialog-show api-error-text auto-type entry-form-fields]}]
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :on-click #(.stopPropagation %)
               :sx {:min-width "600px"
                    "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
   [mui-dialog-title "Auto type Sequence"]
   [mui-dialog-content {:sx {:padding-left "24px"}}
    [mui-stack
     [m/text-field {:label (if (is-custom-sequence (get-sequence auto-type))
                             "Key Sequence"
                             "Default Sequence")
                    :value (get-sequence auto-type)
                    :on-change (fn [^js/Event e]
                                 (at-events/auto-type-edit-dialog-update
                                  :default-sequence (-> e .-target .-value)))
                    :variant "standard"
                    :fullWidth true
                    :InputProps {}}]
     (when-not (nil? api-error-text)
       [mui-stack
        [mui-alert {:severity "error" :sx {:mt 1}} api-error-text]])]

    [mui-stack {:sx {:margin-bottom 2}}]
    [mui-divider]

    [mui-stack
     [mui-typography typography-porps1 "Supported Auto-Type Actions:"]
     [mui-stack
      [mui-typography "{TAB}, {ENTER},{SPACE} or {TAB 3}..,{DELAY X} {DELAY=X}"]]]

    [mui-stack {:sx {:margin-bottom 2}}]
    [mui-divider]

    [mui-stack
     [mui-typography typography-porps1 "Entry Form Fields:"]
     [mui-stack
      [mui-typography (supported-form-fields entry-form-fields)]]]


    [mui-dialog-actions
     [mui-button {:variant "contained" :color "secondary"
                  :disabled false
                  :on-click at-events/auto-type-edit-dialog-close} "Cancel"]
     [mui-button {:variant "contained" :color "secondary"
                  :disabled (not @(at-events/auto-type-modified))
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