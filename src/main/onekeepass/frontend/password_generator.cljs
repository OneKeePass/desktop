(ns onekeepass.frontend.password-generator
  (:require [onekeepass.frontend.common-components :as cc]
            [onekeepass.frontend.events.password-generator :as gen-events]
            [onekeepass.frontend.mui-components :as m
             :refer [mui-alert mui-alert-title mui-button mui-checkbox mui-dialog
                     mui-dialog-actions mui-dialog-content mui-dialog-title
                     mui-form-control-label mui-icon-button mui-icon-visibility
                     mui-icon-visibility-off mui-input mui-input-adornment mui-slider
                     mui-stack mui-text-field mui-typography mui-tabs mui-tab]]
            [onekeepass.frontend.translation :refer-macros [tr-l tr-h tr-bl tr-dlg-title] :refer [lstr-l-cv]]
            [reagent.core :as r]))

(defn- end-icons [visibile?]
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


(defn- tab-panel-selection [panel-shown]
  [mui-tabs {:value panel-shown
             :on-change (fn [_event val]
                          ;; val is of string type and need to be coverted as keyword
                          (gen-events/set-active-password-generator-panel (keyword val)))}
   [mui-tab {:label "Password" :value :password}]
   [mui-tab {:label "Password Phrase" :value :pass-phrase}]])


;; TODO: Move to 'common-components' and also change in 'src/main/onekeepass/frontend/entry_form/fields.cljs'
;; to use this common one
(defn simple-selection-field [{:keys [key
                                      value
                                      edit
                                      error-text
                                      helper-text
                                      on-change-handler
                                      select-field-options]} & {:as opts}]
  ;; We are using the mui-text-field directly as select component 
  ;; This type of simple select list can also be done using the following components 
  ;; as given in the examples found mui.com which uses now this method
  ;; [mui-form-control [mui-input-label] [mui-select {} [mui-menu-item]] [mui-form-helper-text]  ]
  [mui-text-field {:id key
                   :sx (merge {:margin-top cc/entry-cnt-field-margin-top} (:sx opts))
                   :required false
                   ;;:classes {:root "entry-cnt-field"}
                   :select true
                   :label key #_(tr-entry-field-name-cv key)
                   :value value
                   :on-change on-change-handler
                   :error  (not (nil? error-text))
                   :helperText (if (nil? error-text) helper-text error-text)
                   :inputProps  {:readOnly (not edit)}
                   :variant "standard" :fullWidth true}
   (doall (for [y select-field-options]
            ^{:key y} [m/mui-menu-item {:value y} y]))])

(def EFFLarge "EFFLarge")
(def EFFShort1 "EFFShort1")
(def EFFShort2 "EFFShort2")

(def all-wl [EFFLarge EFFShort1 EFFShort2])

(def capitalize-word-choices ["Always" "Never" "Sometimes"])

(def capitalize-first-choices ["Always" "Never" "Sometimes"])


(defn pass-phrase-panel [{:keys [password-visible
                                 text-copied]
                          {:keys [words separator capitalize-words capitalize-first]} :phrase-generator-options
                          {:keys [password
                                  score]} :password-result}]
  [mui-stack {:sx {:align-items "center"}}
   [mui-stack {:sx {:width "80%"}}
    [mui-stack {:direction "row"}
     [simple-selection-field {:key "Wordlist" :value EFFLarge :edit true :on-change-handler #() :select-field-options all-wl}]]
    [mui-stack {:direction "row" :spacing 2 :sx {:margin-top "10px"}}
     [mui-stack {:sx {:width "25%"}}
      [mui-typography "Words" #_(tr-l "words")]]
     [mui-stack {:direction "row" :sx {:width "50%"}}
      [mui-slider {:value words
                   :size "small"
                   :valueLabelDisplay "auto"
                   :min 1
                   :max 40
                   :on-change (fn [_e value]
                                (gen-events/pass-phrase-options-update :words value))}]]
     [mui-stack {:direction "row" :sx {:width "25%"}}
      [mui-input {:value words
                  :on-change (cc/on-change-factory gen-events/pass-phrase-options-update :words true)
                  :on-blur (fn []
                             (cond
                               (< words 1)
                               #() #_(gen-events/pass-phrase-options-update :length 8)

                               (> words 40)
                               #() #_(gen-events/pass-phrase-options-update :length 100)))
                  :inputProps {:min 1 :max 40 :type "number"}}]]]
    [mui-stack {:direction "row" :sx {:width "50%"}}
     [m/text-field {:label "Separator"
                    :value separator
                    :variant "standard"
                    :on-change (cc/on-change-factory gen-events/pass-phrase-options-update :separator)}]]
    [mui-stack {:direction "row"}
     [mui-stack {:direction "row" :sx {:width "50%"}}
      [simple-selection-field {:key "Capitalize words" :value "Always" :select-field-options capitalize-word-choices}]]
     [mui-stack {:width 8}]
     [mui-stack {:direction "row" :sx {:width "50%"}}
      [simple-selection-field {:key "Capitalize first letter" :value "Always" :select-field-options capitalize-first-choices}]]]

    [mui-stack {:direction "row" :sx {:width "100%" :margin-top "10px"}}
     [mui-text-field
      {:label (tr-l password)
       :value password
       :sx   (merge {} (cc/password-helper-text-sx (:name score)))
       :helper-text (-> score :name lstr-l-cv)
       :InputProps {:endAdornment (r/as-element
                                   [mui-input-adornment {:position "end"}
                                    [end-icons password-visible]])
                    :type (if password-visible "text" "password")
                    :readOnly true}
       :variant "standard"
       :fullWidth true}]]]])

;; [mui-typography "Pass phrase will come here"]

(defn password-panel [{:keys [password-visible
                              text-copied]
                       {:keys [length
                               lowercase-letters
                               uppercase-letters
                               numbers
                               symbols]} :password-options
                       {:keys [analyzed-password
                               score]} :password-result}]
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
                  :on-change (cc/on-change-factory gen-events/password-options-update :length true)
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
                    :on-change (cc/on-check-factory
                                gen-events/password-options-update :lowercase-letters)}])
        :label (tr-l lowerCaseAZ)}]]
     [mui-stack {:sx {:width "50%"}}
      [mui-form-control-label
       {:control (r/as-element
                  [mui-checkbox
                   {:checked uppercase-letters
                    :on-change (cc/on-check-factory
                                gen-events/password-options-update :uppercase-letters)}])
        :label (tr-l upperCaseAZ)}]]]

    [mui-stack {:direction "row"}
     [mui-stack {:sx {:width "50%"}}
      [mui-form-control-label
       {:control (r/as-element
                  [mui-checkbox
                   {:checked numbers
                    :on-change (cc/on-check-factory
                                gen-events/password-options-update :numbers)}])
        :label (tr-l numbers)}]]
     [mui-stack {:sx {:width "50%"}}
      [mui-form-control-label
       {:control (r/as-element
                  [mui-checkbox
                   {:checked symbols
                    :on-change (cc/on-check-factory
                                gen-events/password-options-update :symbols)}])
        :label (tr-l symbols)}]]]

    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-text-field
      {:label (tr-l password)
       :value analyzed-password
       :sx   (merge {} (cc/password-helper-text-sx (:name score)))
       :helper-text (-> score :name lstr-l-cv)
       :InputProps {:endAdornment (r/as-element
                                   [mui-input-adornment {:position "end"}
                                    [end-icons password-visible]])
                    :type (if password-visible "text" "password")
                    :readOnly true}
       :variant "standard"
       :fullWidth true}]]

    (when text-copied [mui-stack {:sx {:margin-top "5px"}}
                       [mui-alert {:severity "success"
                                   :sx {"&.MuiAlert-root" {:width "100%"}}} ;; need to override the paper width 60% ;;:sx {"&.MuiAlert-root" {:width "100%"}}
                        [mui-alert-title (tr-l success)] (tr-h copiedToClipboard)]])]])

(defn password-generator-dialog
  [{:keys [dialog-show panel-shown] :as pass-options}]
  (let [current-selection @(gen-events/generator-panel-selected)]
    [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)
                   ;; This will set the Paper width in all child components 
                   ;; and is equivalent to :classes {:paper "pwd-dlg-root"}
                 :sx {"& .MuiPaper-root" {:width "80%"}}}

     [mui-dialog-title  [tab-panel-selection panel-shown]]
     [mui-dialog-content {:dividers true}
      (if (= panel-shown :password)
        [password-panel pass-options]
        [pass-phrase-panel pass-options])]
     [mui-dialog-actions
      [mui-button {:on-click
                   (fn []
                     (gen-events/generator-password-copied)
                     (gen-events/generator-dialog-data-update :dialog-show false))}
       (tr-bl copyClose)]
      [mui-button {:on-click
                   #(gen-events/generator-dialog-data-update :dialog-show false)}
       (tr-bl close)]]]))



#_(defn password-generator-dialog
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
           :sx   (merge {} (cc/password-helper-text-sx (:name score)))
           :helper-text (-> score :name lstr-l-cv)
           :InputProps {:endAdornment (r/as-element
                                       [mui-input-adornment {:position "end"}
                                        [end-icons analyzed-password  password-visible]])
                        :type (if password-visible "text" "password")
                        :readOnly true}
           :variant "standard"
           :fullWidth true}]]

        (when text-copied [mui-stack {:sx {:margin-top "5px"}}
                           [mui-alert {:severity "success"
                                       :sx {"&.MuiAlert-root" {:width "100%"}}} ;; need to override the paper width 60% ;;:sx {"&.MuiAlert-root" {:width "100%"}}
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


