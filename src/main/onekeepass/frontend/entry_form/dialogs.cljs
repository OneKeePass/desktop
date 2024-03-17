(ns onekeepass.frontend.entry-form.dialogs
  (:require [onekeepass.frontend.entry-form.common :as ef-cmn] 
            [onekeepass.frontend.events.common :as ce :refer [on-change-factory]]
            [onekeepass.frontend.events.entry-form-ex :as form-events] 
            [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]
            [onekeepass.frontend.common-components :refer [confirm-text-dialog]]
            [onekeepass.frontend.mui-components :as m :refer [mui-alert 
                                                              mui-button
                                                              mui-button 
                                                              mui-dialog
                                                              mui-dialog-actions
                                                              mui-dialog-content
                                                              mui-dialog-title
                                                              mui-link
                                                              mui-menu-item
                                                              mui-stack 
                                                              mui-tooltip
                                                              mui-typography]]))

(defn delete-totp-confirm-dialog [{:keys [section-name otp-field-name] :as dialog-data}]
  ;; we can use either 'alert-dialog-factory' or confirm-text-dialog for this
  [confirm-text-dialog
   "Delete One-Time password?"
   "Are you sure you want to delete this TOTP field?"
   [{:label "Yes" :on-click (fn []
                              (form-events/entry-form-delete-otp-field section-name otp-field-name)
                              (ef-cmn/close-delete-totp-confirm-dialog))}
    {:label "No" :on-click (fn []
                             (ef-cmn/close-delete-totp-confirm-dialog))}]
   dialog-data])


;;;;;;;;
(def algorithms [{:name "SHA-1" :value "SHA1"}
                 {:name "SHA-256" :value "SHA256"}
                 {:name "SHA-512" :value "SHA512"}])

;;:sx {:min-width "600px" 
;;     "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}

(defn set-up-totp-dialog [{:keys [dialog-show
                                  secret-code
                                  otp-uri-used
                                  section-name
                                  field-name
                                  hash-algorithm
                                  period
                                  digits
                                  show-custom-settings
                                  api-error-text
                                  error-fields]}] 
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :on-click #(.stopPropagation %)
               :sx {"& .MuiPaper-root" {:width "60%"}}}

   [mui-dialog-title "TOTP Setup"]
   [mui-dialog-content
    [mui-stack
     (when (not= field-name "otp")
       [m/text-field {:label "Field Name" 
                      :value field-name
                      :required true
                      :error (contains? error-fields :field-name)
                      :helperText (get error-fields :field-name)
                      :on-change (on-change-factory dlg-events/otp-settings-dialog-update :field-name) ;;(on-change-factory form-events/section-field-dialog-update :field-name)
                      :variant "standard" :fullWidth true}])

     [m/text-field {:label "Secret or TOTPAuth URL"
                    :placeholder "Please enter encoded key or full TOTPAuth URL"
                    :value secret-code
                    :required true
                    :error (contains? error-fields :secret-code)
                    :helperText (get error-fields :secret-code)
                    :on-change (on-change-factory dlg-events/otp-settings-dialog-update :secret-code)
                    :variant "standard" :fullWidth true}]
     
     (when (and (not otp-uri-used) (not show-custom-settings) ) 
       [mui-stack {:direction "row" :sx {:mt 3 :mb 3 :justify-content "center"}}
        [mui-tooltip  {:title "Optional custom settings" :enterDelay 2500}
         [mui-link {:sx {:color "primary.dark"}
                    :underline "hover"
                    :on-click  dlg-events/otp-settings-dialog-custom-settings-show}
          [mui-typography {:variant "h6" :sx {:font-size "1.1em"}}
           "Custom Settings"]]]])

     (when (and (not otp-uri-used) show-custom-settings)
       [mui-stack {:direction "row" :sx {:mt 4}}
        [mui-stack {:sx {:width "40%"}}
         [m/text-field {:label "Algorithm"
                        :value hash-algorithm
                        :required true
                        :select true
                        :autoFocus true
                        :on-change (on-change-factory dlg-events/otp-settings-dialog-update :hash-algorithm)
                        :variant "standard" :fullWidth true}
          (doall
           (for [{:keys [name value]} algorithms]
             ^{:key value} [mui-menu-item {:value value} name]))]]
        [mui-stack {:sx {:width "30%" :ml 3}}
         [m/text-field {:label "Period(sec)"
                        :value period ;;(:memory kdf)
                        :type "number"
                        :error (contains? error-fields :period)
                        :helperText (get error-fields :period)
                        :on-change (on-change-factory dlg-events/otp-settings-dialog-update :period)
                        :inputProps {:min 1 :max 60}
                        :variant "standard" :fullWidth true}]]

        [mui-stack {:sx {:width "30%" :ml 3}}
         [m/text-field {:label "Token length"
                        :value digits ;;(:memory kdf)
                        :type "number"
                        :error (contains? error-fields :digits)
                        :helperText (get error-fields :digits)
                        :on-change (on-change-factory dlg-events/otp-settings-dialog-update :digits)
                        :inputProps {:min 6 :max 10}
                        :variant "standard" :fullWidth true}]]])

     (when api-error-text
       [mui-stack {:sx {:width "100%" :justify-content "center"}}
        [mui-alert {:severity "error"
                    :style {:width "100%"}
                    :sx {:mt 1
                         ;; Need to use above :style
                         ;; Not working
                         ;; :width "100%" 
                         ;; "&.MuiPaper-root-MuiAlert-root" {:width "100%"} 
                         }}
         api-error-text]])]]
   [mui-dialog-actions
    [mui-stack  {:sx {:justify-content "end"} :direction "row" :spacing 1}   ;;{:sx {:align-items "end"}}
     [mui-button {:on-click  dlg-events/otp-settings-dialog-close} "Cancel"]
     [mui-button {:on-click dlg-events/otp-settings-dialog-ok}
      "Ok"]]]])



#_(defn set-up-totp-dialog [{:keys [dialog-show
                                  secret-code
                                  otp-uri-used
                                  section-name
                                  field-name
                                  hash-algorithm
                                  period
                                  digits
                                  show-custom-settings
                                  api-error-text
                                  error-fields]}]
  (println "otp-uri-used " otp-uri-used)
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :on-click #(.stopPropagation %)
               :sx {"& .MuiPaper-root" {:width "60%"}}}

   [mui-dialog-title "TOTP Setup"]
   [mui-dialog-content
    [mui-stack
     (when (not= field-name "otp")
       [m/text-field {:label "Field Name"
                      :value field-name
                      :required true
                      :error (contains? error-fields :field-name)
                      :helperText (get error-fields :field-name)
                      :on-change (on-change-factory dlg-events/otp-settings-dialog-update :field-name) ;;(on-change-factory form-events/section-field-dialog-update :field-name)
                      :variant "standard" :fullWidth true}])

     [m/text-field {:label "Secret code or TOTP Uri"
                    :value secret-code
                    :required true
                    :error (contains? error-fields :secret-code)
                    :helperText (get error-fields :secret-code)
                    :on-change (on-change-factory dlg-events/otp-settings-dialog-update :secret-code)
                    :variant "standard" :fullWidth true}]


     (when (not show-custom-settings)
       (println "Second inside show-custom-settings ")
       [mui-stack {:direction "row" :sx {:mt 3 :mb 3 :justify-content "center"}}
        [mui-tooltip  {:title "Optional custom settings" :enterDelay 2500}
         [mui-link {:sx {:color "primary.dark"}
                    :underline "hover"
                    :on-click  dlg-events/otp-settings-dialog-custom-settings-show}
          [mui-typography {:variant "h6" :sx {:font-size "1.1em"}}
           "Custom Settings"]]]])
     
     (when show-custom-settings
       [mui-stack {:direction "row" :sx {:mt 4}}
        [mui-stack {:sx {:width "40%"}}
         [m/text-field {:label "Algorithm"
                        :value hash-algorithm
                        :required true
                        :select true
                        :autoFocus true
                        :on-change (on-change-factory dlg-events/otp-settings-dialog-update :hash-algorithm)
                        :variant "standard" :fullWidth true}
          (doall
           (for [{:keys [name value]} algorithms]
             ^{:key value} [mui-menu-item {:value value} name]))]]
        [mui-stack {:sx {:width "30%" :ml 3}}
         [m/text-field {:label "Period(sec)"
                        :value period ;;(:memory kdf)
                        :type "number"
                        :error (contains? error-fields :period)
                        :helperText (get error-fields :period)
                        :on-change (on-change-factory dlg-events/otp-settings-dialog-update :period)
                        :inputProps {:min 1 :max 60}
                        :variant "standard" :fullWidth true}]]
     
        [mui-stack {:sx {:width "30%" :ml 3}}
         [m/text-field {:label "Token length"
                        :value digits ;;(:memory kdf)
                        :type "number"
                        :error (contains? error-fields :digits)
                        :helperText (get error-fields :digits)
                        :on-change (on-change-factory dlg-events/otp-settings-dialog-update :digits)
                        :inputProps {:min 6 :max 10}
                        :variant "standard" :fullWidth true}]]])

     
     (when api-error-text
       [mui-stack {:sx {:width "100%" :justify-content "center"}}
        [mui-alert {:severity "error"
                    :style {:width "100%"}
                    :sx {:mt 1
                         ;; Need to use above :style
                         ;; Not working
                         ;; :width "100%" 
                         ;; "&.MuiPaper-root-MuiAlert-root" {:width "100%"} 
                         }}
         api-error-text]])]]
   [mui-dialog-actions
    [mui-stack  {:sx {:justify-content "end"} :direction "row" :spacing 1}   ;;{:sx {:align-items "end"}}
     [mui-button {:on-click  dlg-events/otp-settings-dialog-close} "Cancel"]
     [mui-button {:on-click dlg-events/otp-settings-dialog-ok}
      "Ok"]]]])