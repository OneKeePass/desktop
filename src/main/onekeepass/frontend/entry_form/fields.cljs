(ns onekeepass.frontend.entry-form.fields
  (:require [clojure.string :as str]
            [onekeepass.frontend.common-components :as cc]
            [onekeepass.frontend.constants :as const :refer [OTP PASSWORD]]
            [onekeepass.frontend.entry-form.common :as ef-cmn]
            [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]
            [onekeepass.frontend.events.entry-form-ex :as form-events]
            [onekeepass.frontend.mui-components :as m :refer [mui-box
                                                              mui-circular-progress
                                                              mui-date-time-picker
                                                              mui-icon-autorenew
                                                              mui-icon-button
                                                              mui-icon-button
                                                              mui-icon-delete-outline
                                                              mui-icon-visibility
                                                              mui-icon-visibility-off
                                                              mui-input-adornment
                                                              mui-link
                                                              mui-localization-provider
                                                              mui-menu-item
                                                              mui-stack
                                                              mui-text-field
                                                              mui-tooltip
                                                              mui-typography]]
            [onekeepass.frontend.translation :as t :refer-macros [tr-h tr-l  tr-entry-field-name-cv]]
            [reagent.core :as r]))

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
   (when (and edit protected (= key PASSWORD))
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
                   :label (tr-entry-field-name-cv key)
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
                          standard-field
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
                 :label (if standard-field (tr-entry-field-name-cv key) key)
                 :variant "standard"
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
   [mui-circular-progress {:variant "determinate" 
                           :size 45 ;; default 40
                           :value (js/Math.round (* -100 (/ ttl-time period)))}]
   [mui-box {:sx {:top 0 :left 0 :bottom 0 :right 0
                  :position "absolute" :display "flex" :alignItems "center" :justifyContent "center"}}
    [mui-typography {:vaiant "caption" :component "div"}
     ttl-time]]])

(defn formatted-token
  "Groups digits with spaces between them for easy reading"
  [token]
  (let [len (count token)
        n (cond
            (or (= len 6) (= len 7) (= len 9))
            3

            (or (= len 8) (= len 10))
            4

            :else
            3)
        ;; step = n, pad = ""
        parts (partition n n "" token)
        parts (map (fn [c] (str/join c)) parts)
        spaced (str/join " " parts)]
    spaced))

(defn otp-read-field 
  "This is called only during read time and edit flag is false"
  [_kv]
  (fn [{:keys [key 
               visible 
               error-text
               no-end-icons]
        :or {visible true 
             no-end-icons false}}]

    (let [;; ensure that edit is always false
          edit false
          {:keys [token ttl period]} @(form-events/otp-currrent-token key)
          ;; If otp url is not parseable for any reason, the Entry struct's 'parsed_otp_values' hashmap will 
          ;; have a nil value with this otp field name's as key. Accordingly otp-currrent-token will be nil
          valid-token-found (not (nil? token))]
      [mui-stack {:direction "row" :sx {:width "100%"}}
       [mui-text-field {:sx otp-txt-input-sx
                        :fullWidth true
                        :label (if (= OTP key) (tr-l "oneTimePasswordTotp") key)
                        :variant "standard"
                        :classes {:root "entry-cnt-field"}
                        :value (formatted-token token)
                        ;; Is there any use of suing 'error-text' with otp field ? 
                        :error  (or (not (nil? error-text)) (not valid-token-found))
                        :helperText (if-not valid-token-found (tr-h "invalidOtpUrl") error-text)
                        :required false
                        ;; setting props in this is not working
                        :InputLabelProps {} 
                        :InputProps {:id key
                                     :classes {:root "entry-cnt-text-field-read"
                                               :focused "entry-cnt-text-field-read-focused"} 
                                     :endAdornment (if (or (not valid-token-found) no-end-icons) nil
                                                       (r/as-element
                                                        [mui-input-adornment {:position "end"}
                                                         [end-icons key token false visible edit]]))
                                     :type "text"}

                        :inputProps  {:readOnly true}}]

       (when valid-token-found
         [mui-stack {:sx {:width "10%" 
                          ;; This margin aligns progress circle to field's top margin
                          :margin-top "16px"
                          :align-items "center" 
                          :justify-content "center"}}
          [otp-progress-circle period ttl]])])))

(defn opt-field-no-token [kv]
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

(defn otp-field [{:keys [edit key value section-name group-uuid] :as kv}]
  (let [history-form? @(form-events/history-entry-form-showing)
        deleted? @(form-events/is-entry-parent-group-deleted group-uuid)]
    ;; cond order is important
    (cond
      (or history-form?  deleted?)
      [opt-field-no-token kv]

      (not edit)
      [otp-read-field kv]

      :else
      (if (str/blank? value)
        [mui-stack {:direction "row" :sx {:width "100%" :justify-content "center"}}
         [mui-link {:sx {:color "primary.dark"}
                    :underline "hover"
                    :on-click  #(dlg-events/otp-settings-dialog-show section-name true)}
          [mui-typography {:variant "h6" :sx {:font-size "1.1em"}}
           (tr-l "setUpOneTimePassword")]]]
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




