(ns onekeepass.frontend.open-db-form
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.open-db-form :as od-events]
   [onekeepass.frontend.common-components :refer [enter-key-pressed-factory]]
   [onekeepass.frontend.mui-components :as m :refer [mui-alert
                                                     mui-dialog
                                                     mui-button
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-linear-progress
                                                     mui-stack
                                                     mui-input-adornment
                                                     mui-icon-folder-outlined
                                                     mui-icon-button
                                                     mui-icon-visibility
                                                     mui-icon-visibility-off]]))

;;(set! *warn-on-infer* true)

(defn open-db-dialog [{:keys [dialog-show
                              unlock-request
                              file-name
                              password
                              key-file-name
                              password-visibility-on
                              key-file-visibility-on
                              status error-text error-fields]} opened-db-list]
  (let [in-progress? (= :in-progress status)
        passord-error-text (:password error-fields)
        ok-action (if unlock-request
                    #(od-events/unlock-ok-on-click password key-file-name)
                    #(od-events/ok-on-click file-name password key-file-name opened-db-list))]
    [mui-dialog {:open (if (nil? dialog-show) false dialog-show) :on-click #(.stopPropagation %)
                 :classes {:paper "pwd-dlg-root"}}
     [mui-dialog-title (if unlock-request "Unlock Database" "Open Database")]
     [mui-dialog-content {:dividers true}
      [mui-stack
       (if (not in-progress?)
         [:<>
          [m/text-field {:label "File Name" :value file-name
                         :on-change od-events/file-name-on-change
                         :disabled unlock-request
                         :variant "standard" :fullWidth true
                         :InputProps {:endAdornment
                                      (when (not unlock-request)
                                        (r/as-element
                                         [mui-input-adornment {:position "end"}
                                          [mui-icon-button
                                           {:edge "end" :sx {:mr "-8px"}
                                            :onClick od-events/open-file-explorer-on-click}
                                           [mui-icon-folder-outlined]]]))}}]

          [m/text-field {:label "Password"
                         :value password
                         :required true
                         :error (not (nil? passord-error-text))
                         :helperText passord-error-text
                         :on-change od-events/db-password-on-change
                         :on-key-press (enter-key-pressed-factory
                                        ok-action)

                         :variant "standard" :fullWidth true
                         :InputProps {:endAdornment (r/as-element
                                                     [mui-input-adornment {:position "end"}
                                                      (if password-visibility-on
                                                        [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                          :on-click #(od-events/password-visible-change false)}
                                                         [mui-icon-visibility]]
                                                        [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                          :on-click #(od-events/password-visible-change true)}
                                                         [mui-icon-visibility-off]])])}
                         :type (if password-visibility-on "text" "password")}]

          [m/text-field {:label "Key File Name" :value key-file-name
                         :on-change od-events/key-file-name-on-change
                         :variant "standard" :fullWidth true
                         :placeholder "Optional"
                         :helperText "This is required if you had used any random file as key in addition to password"
                         :InputProps {:endAdornment (r/as-element [mui-input-adornment {:position "end"}
                                                                   (if key-file-visibility-on
                                                                     [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                                       :on-click #(od-events/key-file-visible-change false)}
                                                                      [mui-icon-visibility]]
                                                                     [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                                       :on-click #(od-events/key-file-visible-change true)}
                                                                      [mui-icon-visibility-off]])
                                                                   [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                                     :onClick od-events/open-key-file-explorer-on-click}
                                                                    [mui-icon-folder-outlined]]])}
                         :type (if key-file-visibility-on "text" "password")}]]
         [mui-stack "Databse openining is in progress"])
       (cond
         (= status :in-progress)
         [mui-linear-progress {:sx {:mt 2}}]

         ;; Need to show error message to user for correction
         (not (nil? error-text))
         [mui-alert {:severity "error" :sx {:mt 1}} error-text]

         ;; Loading and parsing of db file is done successfully
         :else nil)

         ;; TODO: 
          ;; Should we add check for the nil values in file name and password in UI itself and show errors accordingly ?
       ]]

     [mui-dialog-actions
      [mui-button {:color "secondary"
                   :disabled in-progress?
                   :on-click od-events/cancel-on-click} "Cancel"]
      [mui-button {:color "secondary"
                   :disabled in-progress?
                   :on-click
                   ok-action} "Ok"]]]))

(defn open-db-dialog-main []
  [open-db-dialog @(od-events/dialog-data) @(cmn-events/opened-db-list)])