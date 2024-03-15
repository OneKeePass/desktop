(ns onekeepass.frontend.entry-form.fields
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.entry-form.common :refer []]
   [onekeepass.frontend.common-components :as cc :refer [alert-dialog-factory
                                                         enter-key-pressed-factory
                                                         list-items-factory
                                                         selection-autocomplete tags-field]]
   [onekeepass.frontend.constants :as const]

   [onekeepass.frontend.events.entry-form-ex :as form-events]
   [onekeepass.frontend.events.otp :as otp-events]
   [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]
   [onekeepass.frontend.mui-components :as m :refer [color-primary-main
                                                     date-adapter
                                                     mui-alert
                                                     mui-alert-title
                                                     mui-avatar
                                                     mui-box
                                                     mui-button
                                                     mui-button
                                                     mui-checkbox
                                                     mui-circular-progress
                                                     mui-date-time-picker
                                                     mui-desktop-date-picker
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-divider
                                                     mui-form-control-label mui-grid
                                                     mui-icon-add-circle-outline-outlined
                                                     mui-icon-article-outlined
                                                     mui-icon-autorenew
                                                     mui-icon-button
                                                     mui-icon-button
                                                     mui-icon-check
                                                     mui-icon-delete-outline
                                                     mui-icon-edit-outlined
                                                     mui-icon-more-vert
                                                     mui-icon-more-vert
                                                     mui-icon-save-as-outlined
                                                     mui-icon-visibility
                                                     mui-icon-visibility-off
                                                     mui-input-adornment mui-link
                                                     mui-list-item
                                                     mui-list-item-avatar
                                                     mui-list-item-icon
                                                     mui-list-item-text
                                                     mui-list-item-text
                                                     mui-localization-provider
                                                     mui-menu
                                                     mui-menu-item
                                                     mui-popper
                                                     mui-stack
                                                     mui-text-field
                                                     mui-tooltip
                                                     mui-typography]]

   [reagent.core :as r]
   [onekeepass.frontend.entry-form.common :as ef-cmn]))

(defn end-icons [key value protected visibile? edit]
  [:<>
   (when protected
     (if visibile?
       [mui-icon-button {:sx {:margin-right "-8px"}
                         :edge "end"
                         :on-click #(form-events/entry-form-field-visibility-toggle key)}
        [mui-icon-visibility]]
       [mui-icon-button {:sx {:margin-right "-8px"}
                         :edge "end"
                         :on-click #(form-events/entry-form-field-visibility-toggle key)}
        [mui-icon-visibility-off]]))
   ;; Password generator 
   (when (and edit protected (= key const/PASSWORD))
     [mui-icon-button {:sx {:margin-right "-8px"}
                       :edge "end"
                       :on-click form-events/password-generator-show}
      [mui-icon-autorenew]])
   ;; Copy 
   [(cc/copy-icon-factory) value {:sx {:mr "-1px"}}]])

(defn simple-selection-field [{:keys [key
                                      value 
                                      edit
                                      error-text
                                      helper-text
                                      on-change-handler
                                      select-field-options]}]
  ;; We are using the mui-text-field directly as select component 
  ;; Another way also, this type of simple select list can be done using the following. 
  ;; The examples given in mui.com uses now this method
  ;; [mui-form-control [mui-input-label] [mui-select {} [mui-menu-item]] [mui-form-helper-text]  ]
  [mui-text-field {:id key 
                   :required false
                   :classes {:root "entry-cnt-field"}
                   :select true
                   :label key
                   :value value
                   :on-change on-change-handler
                   :error  (not (nil? error-text))
                   :helperText (if (nil? error-text) helper-text error-text)
                   :inputProps  {:readOnly (not edit)}
                   :variant "standard" :fullWidth true}
   (doall (for [y select-field-options]
            ^{:key y} [mui-menu-item {:value y} y]))])

(defn text-field [{:keys [key
                          value
                          protected
                          visible
                          edit
                          on-change-handler
                          required
                          disabled
                          password-score
                          placeholder
                          helper-text
                          error-text
                          no-end-icons]
                   :or {visible true
                        edit false
                        no-end-icons false
                        protected false
                        disabled false
                        on-change-handler #(println (str "No on change handler yet registered for " key))
                        required false}}]

  [m/text-field {:sx   (merge {} (cc/password-helper-text-sx (:name password-score)))
                 :fullWidth true
                 :label key :variant "standard"
                 :classes {:root "entry-cnt-field"}
                 :value value
                 :placeholder placeholder
                 :error  (not (nil? error-text))
                 :helperText (cond
                               (and (nil? error-text) (not (nil? password-score)))
                               (:score-text password-score)

                               (nil? error-text)
                               helper-text

                               :else
                               error-text)
                 :onChange  on-change-handler
                 ;;:required required
                 :required false
                 :disabled disabled
                 :InputLabelProps {}
                 :InputProps {:id key
                              :classes {:root (if edit "entry-cnt-text-field-edit" "entry-cnt-text-field-read")
                                        :focused  (if edit "entry-cnt-text-field-edit-focused" "entry-cnt-text-field-read-focused")}
                                 ;;:sx (if editing {} read-sx1)
                              :endAdornment (if no-end-icons nil
                                                (r/as-element
                                                 [mui-input-adornment {:position "end"}
                                                  [end-icons key value protected visible edit]
                                                  #_(seq icons)]))
                              :type (if (or (not protected) visible) "text" "password")}
                         ;;attributes for 'input' tag can be added here
                         ;;It seems adding these 'InputProps' also works
                         ;;We need to use 'readOnly' and not 'readonly'
                 :inputProps  {:readOnly (not edit)

                                   ;;:readonly "readonly"
                               }}])


(def otp-txt-input-sx {"& .MuiInputBase-input" {:color "green"
                                                :font-size "1.75em"
                                                :font-weight "300"  ;; To make it bold
                                                }})

(defn otp-progress-circle [period ttl-time]
  [mui-box {:position "relative" :display "inline-flex"}
   [mui-circular-progress {:variant "determinate" :value (js/Math.round (* -100 (/ ttl-time period)))}]
   [mui-box {:sx {:top 0 :left 0 :bottom 0 :right 0
                  :position "absolute" :display "flex" :alignItems "center" :justifyContent "center"}}
    [mui-typography {:vaiant "caption" :component "div"}
     ttl-time]]])

(defn otp-read-field [kv]
  (fn [{:keys [key
               value
               visible
               ;;current-opt-token
               edit
               error-text
               no-end-icons]
        :or {visible true
             edit false
             no-end-icons false}}]

    (let [{:keys [token ttl period]} @(form-events/otp-currrent-token key)
        ;;   ttl-time @(form-events/otp-ttl-indicator key)
        ;;   ttl-time (if (nil? ttl-time) ttl ttl-time)
          ]
      [mui-stack {:direction "row" :sx {:width "100%"}}
       [mui-text-field {:sx (if-not edit otp-txt-input-sx  {})
                        :fullWidth true
                        :label (if (= "otp" key) "One-Time Password(TOTP)" key)
                        :variant "standard"
                        :classes {:root "entry-cnt-field"}
                        :value (if edit value token)
                        :error  (not (nil? error-text))
                        :helperText error-text
                        :required false
                        :InputLabelProps {} ;; setting props in this is not working
                        :InputProps {:id key
                                     :classes {:root   (if edit "entry-cnt-text-field-edit" "entry-cnt-text-field-read")
                                               :focused (if edit "entry-cnt-text-field-edit-focused" "entry-cnt-text-field-read-focused")}
                                                ;;:sx (if editing {} read-sx1)
                                     :endAdornment (if no-end-icons nil
                                                       (r/as-element
                                                        [mui-input-adornment {:position "end"}
                                                         [end-icons key value false visible edit]
                                                         #_(seq icons)]))
                                     :type "text"}

                        :inputProps  {:readOnly true}}]



             ;; :border "1px solid black"
       [mui-stack {:sx {:width "10%" :align-items "center" :justify-content "center"}}
        [otp-progress-circle period ttl]]])))

(defn otp-field-in-history-form [kv]
  [mui-stack {:direction "row" :sx {:width "100%"}}
   [mui-stack {:direction "row" :sx {:width "100%"}}
    [text-field (assoc kv
                       :edit false
                       :protected false
                       :disabled false
                       :error-text nil
                       :visible true
                       :on-change-handler #())]]
   [mui-stack {:direction "row" :sx {:align-items "flex-end"}}]])

(defn otp-field [{:keys [edit key value section-name] :as kv}]
  (let [history-form? @(form-events/history-entry-form-showing)]
    ;; cond order is important
    (cond
      history-form?
      [otp-field-in-history-form kv]

      (not edit)
      [otp-read-field kv]

      :else
      (if (str/blank? value)
        [mui-stack {:direction "row" :sx {:width "100%" :justify-content "center"}}
         [mui-link {:sx {:color "primary.dark"}
                    :underline "hover"
                    :on-click  #(dlg-events/otp-settings-dialog-show section-name)}
          [mui-typography {:variant "h6" :sx {:font-size "1.1em"}}
           "Set up One-Time Password"]]]
        [mui-stack {:direction "row" :sx {:width "100%"}}
         [mui-stack {:direction "row" :sx {:width "100%"}}
          [text-field (assoc kv
                             :edit edit
                             :protected false
                             :disabled true
                             :error-text nil
                             :visible true
                             :on-change-handler #())]]
         [mui-stack {:direction "row" :sx {:align-items "flex-end"}}

          [mui-tooltip  {:title "Delete Field" :enterDelay 2500}
           [mui-icon-button {:edge "end"
                             :on-click #(ef-cmn/show-delete-totp-confirm-dialog section-name key)}
            [mui-icon-delete-outline]]]]]))))

;; see https://mui.com/x/migration/migration-pickers-v5/ as we are using now "@mui/x-date-pickers": "^6.16.0"
;; https://mui.com/x/migration/migration-pickers-v5/#component-slots-component-slot-props 
(defn datetime-field
  [{:keys [key value on-change-handler]
    :or [on-change-handler #()]}]
  [mui-localization-provider {:dateAdapter m/adapter-date-fns}
   [mui-date-time-picker {:label key
                          ;; value should be of Date type (type value) => #object[Date]
                          ;; and it is in UTC. The view side shown in local time 
                          :value value
                          :onChange on-change-handler
                          :slotProps {:textField {:variant "standard"
                                                  :classes {:root "entry-cnt-field"}
                                                  :fullWidth true}}}]])

(defn text-area-field [{:keys [key value edit on-change-handler]
                        :or {edit false
                             on-change-handler #()}}]
  [m/text-field {:classes {:root "entry-cnt-field"}
                 :fullWidth true
                 :id key :label "";;name
                 :variant "standard"
                 :value value
                 :onChange on-change-handler
                 :multiline true
                 :rows 4
                    ;;minRows and maxRows should not be used with the fixed 'm/text-field'
                    ;;:minRows 3 
                    ;;:maxRows 10
                    ;;:classes {:root "entry-cnt-notes-field"}
                 :InputLabelProps {:shrink true}
                 :InputProps {:id key
                                  ;; This did not fix the cursor jumping
                                  ;;  :inputComponent
                                  ;;  (r/reactify-component
                                  ;;   (fn [props]
                                  ;;     ;;(println "props are" props)
                                  ;;     [:input (-> props
                                  ;;                    (assoc :ref (:inputRef props))
                                  ;;                    (dissoc :inputRef))]))
                              }

                 :inputProps  {:readOnly (not edit)
                               :sx {:ml ".5em" :mr ".5em"}
                               :style {:resize "vertical"}}}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This is based om mui x date time picker 5.x version and does not work with 6.x version
#_(defn datetime-field
    [{:keys [key value on-change-handler]
      :or [on-change-handler #()]}]
    [mui-localization-provider {:dateAdapter date-adapter}
     (println "Value is " value)
     [mui-date-time-picker {:label key
                            :value value
                            :onChange on-change-handler
                            :renderInput (fn [props]
                                           (let [p (js->clj props :keywordize-keys true)
                                                 p (merge p {:variant "standard"
                                                             :classes {:root "entry-cnt-field"}
                                                             :fullWidth true})]
                                             #_(println "Props called: " (-> p   keys))
                                             (r/as-element [mui-text-field p])))}]])


;; Not used and it is based on old mui x datepicker version and also did not work 
;; Need to replaced with new version based one if required
#_(defn date-field
    [{:keys [key value edit on-change-handler]
      :or [on-change-handler #()]}]
    (if-not edit
      [text-field  {:key (str key " (MM/DD/YYYY)") :value value :edit false}]
      [mui-stack {:direction "row" :sx {:width "40%"}}
       [mui-localization-provider {:dateAdapter date-adapter}
        [mui-desktop-date-picker
         {:label (str key " (MM/DD/YYYY)")
          :value value
          :onChange on-change-handler
          :renderInput (fn [props]
                         (let [p (js->clj props :keywordize-keys true)
                               p (merge p {:variant "standard"
                                           :classes {:root "entry-cnt-field"}
                                           :fullWidth true})]
                           #_(println "Props called: " (-> p   keys))
                           (r/as-element [mui-text-field p])))}]]]))

