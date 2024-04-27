(ns onekeepass.frontend.password-generator
  (:require [onekeepass.frontend.common-components :as cc]
            [onekeepass.frontend.events.password-generator :as gen-events]
            [onekeepass.frontend.mui-components :as m
    :refer [mui-alert mui-alert-title mui-button mui-checkbox mui-dialog
            mui-dialog-actions mui-dialog-content mui-dialog-title
            mui-form-control-label mui-icon-button mui-icon-visibility
            mui-icon-visibility-off mui-input mui-input-adornment mui-slider
            mui-stack mui-text-field mui-typography]]
            [onekeepass.frontend.translation :refer-macros [tr-l tr-h tr-bl tr-dlg-title] :refer [lstr-l-cv]]
            [onekeepass.frontend.utils :refer [str->int]]
            [reagent.core :as r]))

(defn on-change-factory [handler-name field-name-kw]
  [handler-name field-name-kw]
  (fn [^js/Event e]
    (let [val (-> e .-target  .-value)
          ;; Need to ensure that length is an int as expected by backend api
          val (if (= :length field-name-kw) (str->int val) val)]
      (handler-name field-name-kw val))))

(defn on-check-factory [handler-name field-name-kw]
  (fn [^js/Event e]
    (handler-name field-name-kw (-> e .-target  .-checked))))

(defn end-icons [value visibile?]
  [:<>
   (if visibile?
     [mui-icon-button 
      {:sx {:mr "-5px"}
       :edge "end"
       :on-click #(gen-events/generator-dialog-data-update :password-visible (not visibile?))}
      [mui-icon-visibility]]
     [mui-icon-button 
      {:sx {:mr "-5px"}
       :edge "end"
       :on-click #(gen-events/generator-dialog-data-update :password-visible (not visibile?))}
      [mui-icon-visibility-off]])
   [(cc/copy-icon-factory 
     (fn []
       (gen-events/generator-password-copied)
       ;; This call will provide an alert to the user
       #_(gen-events/generator-dialog-data-update :text-copied true)))]])

(defn password-generator-dialog 
  [{:keys [dialog-show
           password-visible
           text-copied]
    {:keys [length
            lowercase-letters
            uppercase-letters
            numbers
            symbols]} :password-options
    {:keys [analyzed-password
            score]} :password-result}]
  [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)
               ;; This will set the Paper width in all child components 
               ;; and is equivalent to :classes {:paper "pwd-dlg-root"}
               :sx {"& .MuiPaper-root" {:width "80%"}}}

   [mui-dialog-title (tr-dlg-title passwordGenerator)]
   [mui-dialog-content {:dividers true}
    [mui-stack {:sx {:align-items "center"}}
     [mui-stack {:sx {:width "80%"}}
      [mui-stack {:direction "row" :spacing 2 :sx {:margin-top "10px"}}
       [mui-stack {:sx {:width "25%"}}
        [mui-typography (tr-l length)]]
       [mui-stack {:direction "row" :sx {:width "50%"}}
        [mui-slider {:value length
                     :size "small"
                     :valueLabelDisplay "auto"
                     :min 8
                     :max 100
                     :on-change (fn [_e value]
                                  (gen-events/password-options-update :length value))}]]
       [mui-stack {:direction "row" :sx {:width "25%"}}
        [mui-input {:value length
                    :on-change (on-change-factory gen-events/password-options-update :length)
                    :on-blur (fn []
                               (cond
                                 (< length 8)
                                 (gen-events/password-options-update :length 8)

                                 (> length 100)
                                 (gen-events/password-options-update :length 100)))
                    :inputProps {:min 8 :max 100 :type "number"}}]]]

      [mui-stack {:direction "row"}
       [mui-stack {:sx {:width "50%"}}
        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox 
                     {:checked lowercase-letters
                      :on-change (on-check-factory 
                                  gen-events/password-options-update :lowercase-letters)}])
          :label (tr-l lowerCaseAZ)}]]
       [mui-stack {:sx {:width "50%"}}
        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox 
                     {:checked uppercase-letters
                      :on-change (on-check-factory 
                                  gen-events/password-options-update :uppercase-letters)}])
          :label (tr-l upperCaseAZ)}]]]

      [mui-stack {:direction "row"}
       [mui-stack {:sx {:width "50%"}}
        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox 
                     {:checked numbers
                      :on-change (on-check-factory 
                                  gen-events/password-options-update :numbers)}])
          :label (tr-l numbers)}]]
       [mui-stack {:sx {:width "50%"}}
        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox 
                     {:checked symbols
                      :on-change (on-check-factory 
                                  gen-events/password-options-update :symbols)}])
          :label (tr-l symbols)}]]]

      [mui-stack {:direction "row" :sx {:width "100%"}}
       [mui-text-field
        {:label (tr-l password)
         :value analyzed-password
         :sx   (merge {} (cc/password-helper-text-sx (:name score))) #_{"& .MuiFormHelperText-root" {:color "red"}}
         :helper-text (-> score :name lstr-l-cv ) #_(:score-text score)
         :InputProps {;;:classes {:root  "entry-cnt-text-field-read" :focused "entry-cnt-text-field-read-focused"}
                      :endAdornment (r/as-element
                                     [mui-input-adornment {:position "end"}
                                      [end-icons analyzed-password  password-visible]])
                      :type (if password-visible "text" "password")
                      :readOnly true}
         :variant "standard"
         :fullWidth true}]]

      (when text-copied [mui-stack {:sx {:margin-top "5px"}}
                         [mui-alert {:severity "success" :sx {"&.MuiAlert-root" {:width "100%"}}} ;; need to override the paper width 60% ;;:sx {"&.MuiAlert-root" {:width "100%"}}
                          [mui-alert-title (tr-l success)] (tr-h copiedToClipboard)]])]]]
   [mui-dialog-actions
    [mui-button {:on-click  
                 (fn []
                   (gen-events/generator-password-copied)
                   (gen-events/generator-dialog-data-update :dialog-show false))}
     (tr-bl copyClose)]
    [mui-button {:on-click 
                 #(gen-events/generator-dialog-data-update :dialog-show false)}
     (tr-bl close)]]])


