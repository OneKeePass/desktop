(ns onekeepass.frontend.new-database
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.events.new-database :as nd-events]
   [onekeepass.frontend.translation :as t :refer-macros [tr-l tr-t tr-h tr-m]]
   [onekeepass.frontend.common-components :refer [cipher-algorithms kdf-algorithms]]
   [onekeepass.frontend.mui-components :as m :refer [mui-menu-item
                                                     mui-alert
                                                     mui-divider
                                                     mui-linear-progress
                                                     mui-input-adornment
                                                     mui-icon-button
                                                     mui-icon-folder-outlined
                                                     mui-icon-visibility
                                                     mui-icon-visibility-off
                                                     mui-link
                                                     mui-tooltip
                                                     mui-typography
                                                     mui-dialog
                                                     mui-dialog-title
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-box mui-stack
                                                     mui-button]]))

(set! *warn-on-infer* true)

(defn- basic-info
  [{:keys [database-name database-description error-fields]}]
  ;; error-fields is a map
  [mui-stack
   [mui-typography (tr-t basicDatabaseInformation)]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l name)
                    :value database-name
                    :required true
                    :error (contains? error-fields :database-name)
                    :helperText (get error-fields :database-name (tr-h dbDisplayName))
                    :autoFocus true
                    :on-change (nd-events/field-update-factory :database-name)
                    :variant "standard" :fullWidth true}]]

    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l description)
                    :value database-description
                    :on-change (nd-events/field-update-factory :database-description)
                    :variant "standard" :fullWidth true}]]]])



#_(when @show-addition-protection
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label "Key File Name"
                    :value key-file-name
                    :placeholder "Optional"
                    :helperText "Any random small file. A hash of the file's content is used as an additional passsord. This is just an optional one and not required"
                    :on-change (nd-events/field-update-factory :key-file-name)
                    :variant "standard" :fullWidth true
                    :InputProps {:endAdornment (r/as-element [mui-input-adornment {:position "end"}
                                                              [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                                :onClick nd-events/open-key-file-explorer-on-click}
                                                               [mui-icon-folder-outlined]]])}}]])

(defn- credentials-info [{:keys [password
                                 password-confirm
                                 password-visible
                                 key-file-name
                                 database-name
                                 show-additional-protection
                                 error-fields]}]

  [mui-stack {:spacing 2}
   [mui-typography (tr-t "databaseCredentials")]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l "password")
                    :value password
                    ;;:required true
                    :error (contains? error-fields :password)
                    :helperText (get error-fields :password (tr-h passwordForYourDb))
                    :autoFocus true
                    :on-change (nd-events/field-update-factory :password)
                    :variant "standard" :fullWidth true
                    :type (if password-visible "text" "password")
                    :InputProps {:endAdornment (r/as-element
                                                [mui-input-adornment {:position "end"}
                                                 (if password-visible
                                                   [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                     :on-click #(nd-events/database-field-update :password-visible false)}
                                                    [mui-icon-visibility]]
                                                   [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                     :on-click #(nd-events/database-field-update :password-visible true)}
                                                    [mui-icon-visibility-off]])])}}]]

    (when (not password-visible)
      [mui-box {:sx {:width "80%"}}
       [m/text-field {:label (tr-l confirmPassword)
                      :value password-confirm
                      ;;:required true
                      :error (contains? error-fields :password-confirm)
                      :helperText (get error-fields :password-confirm)
                      :on-change (nd-events/field-update-factory :password-confirm)
                      :type "password"
                      :variant "standard" :fullWidth true}]])

    ;; aligns to the center
    [mui-box
     [mui-link {:variant "subtitle1"
                :color "primary"
                :onClick (fn []
                           (nd-events/database-field-update :show-additional-protection (not show-additional-protection)))}
      (if-not show-additional-protection (tr-l addAdditionalProtection) (tr-l hideAdditionalProtection))]]

    (when show-additional-protection
      [mui-stack {:sx {:width "80%" :border ".001px solid"}}
       [mui-typography {:sx {:text-align "center"}
                        :variant "h6"} "Key file"]
       [mui-divider {:sx {:margin 1}}]

       (if-not (nil? key-file-name)
         [mui-stack
          [mui-tooltip {:title key-file-name :enterDelay 1500}
           [mui-typography {:sx {:mt 1 :white-space "nowrap" :text-overflow "ellipsis" :overflow "hidden"}} key-file-name]]

          [mui-button  {:sx {:m 1}
                        :variant "text"
                        :on-click #(nd-events/database-field-update :key-file-name nil)} (tr-l remove)]]

         [mui-stack
          [mui-button  {:sx {:m 1}
                        :variant "text"
                        :on-click nd-events/open-key-file-explorer-on-click} (tr-l "browse")]

          [mui-typography {:variant "caption"}
           (tr-m newDbPage txt1)]

          [mui-button  {:sx {:m 1}
                        :variant "text"
                        :on-click #(nd-events/save-as-key-file-explorer-on-click database-name)} (tr-l "generate")]

          [mui-typography {:variant "caption"}
           (tr-m newDbPage txt2)]])])]])

(defn- file-info [{:keys [database-file-name db-file-file-exists database-name]}]
  [mui-stack {:spacing 2}
   [mui-typography (tr-l saveAs)]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l "fileName")
                    :value database-file-name
                    :required true
                    :autoFocus true
                    :on-change (nd-events/field-update-factory :database-file-name)
                    :variant "standard" :fullWidth true

                    :InputProps {:endAdornment (r/as-element [mui-input-adornment {:position "end"}
                                                              [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                                :onClick #(nd-events/save-as-file-explorer-on-click database-name)}
                                                               [mui-icon-folder-outlined]]])}}]

     (when db-file-file-exists
       [mui-alert {:severity "warning" :sx {:mt 1}} (tr-m newDbPage txt4)])]]]) ;;"** Database file already exists and will be replaced with this new one **"

(defn- security-info [{:keys [cipher-id error-fields]
                       {:keys [iterations memory parallelism algorithm]} :kdf}]
  [mui-stack {:spacing 2}
   [mui-typography (tr-l security)]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}} ;;:alignItems "center"
    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "50%" :ml 3}}
      [m/text-field {:label "Encription Algorithm"
                     :value cipher-id
                     :required true
                     :select true
                     :autoFocus true
                     :on-change (nd-events/field-update-factory :cipher-id)
                     :variant "standard" :fullWidth true}
       (doall
        (for [{:keys [name value]} cipher-algorithms]
          ^{:key value} [mui-menu-item {:value value} name]))]]]

    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "50%" :ml 3}}
      [m/text-field {:label (tr-l kdf)
                     :value algorithm
                     :required true
                     :select true
                     :autoFocus true
                     :on-change (fn [^js/Event e]
                                  (nd-events/new-database-kdf-algorithm-select (->  e ^js/EventT (.-target) .-value)))
                     :variant "standard" :fullWidth true}
       (doall
        (for [{:keys [name value]} kdf-algorithms]
          ^{:key value} [mui-menu-item {:value value} name]))]]]

    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label (tr-l "transformRounds")
                     :value iterations
                     :type "number"
                     :error (contains? error-fields :iterations)
                     :helperText (get error-fields :iterations)
                     :on-change (nd-events/field-update-factory [:kdf :iterations])
                     :variant "standard" :fullWidth true}]]

     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label (tr-l "memoryUsage")
                     :value memory
                     :type "number"
                     :error (contains? error-fields :memory)
                     :helperText (get error-fields :memory)
                     :on-change (nd-events/field-update-factory [:kdf :memory])
                     :variant "standard" :fullWidth true}]]
     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label (tr-l "parallelism")
                     :value parallelism
                     :type "number"
                     :error (contains? error-fields :parallelism)
                     :helperText (get error-fields :parallelism)
                     ;; Using min for "number" type is not working
                     ;;:InputProps {:min "2"}
                     ;;:min 2 
                     :on-change (nd-events/field-update-factory [:kdf :parallelism])
                     :variant "standard" :fullWidth true}]]]]])

(defn new-database-dialog [{:keys [dialog-show panel call-to-create-status api-error-text] :as m}]
  (let [in-progress? (= :in-progress call-to-create-status)]
    [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
                 :on-click #(.stopPropagation ^js/Event %)
                 :classes {:paper "pwd-dlg-root"}}

     [mui-dialog-title (tr-l newDatabase)]
     [mui-dialog-content {:dividers true}
      (cond
        (= panel :basic-info)
        [basic-info m]

        (= panel :credentials-info)
        [credentials-info m]

        (= panel :file-info)
        [file-info m]

        in-progress?
        [mui-stack (tr-m newDbPage txt3)]

        (= panel :security-info)
        [security-info m])
      (when api-error-text
        [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])

      (when (and (nil? api-error-text) in-progress?)
        [mui-linear-progress {:sx {:mt 2}}])]

     [mui-dialog-actions
      [mui-button  {:sx {:mr 1}
                    :disabled in-progress?
                    :on-click nd-events/cancel-on-click :tabIndex 4} (tr-l "cancel")]
      (when (not (= panel :basic-info))
        [mui-button {:disabled in-progress?
                     :on-click nd-events/previous-on-click} (tr-l "previous")])
      (when (not (= panel :security-info))
        [mui-button {:disabled in-progress?
                     :on-click nd-events/next-on-click :tabIndex 2} (tr-l "next")])
      (when (= panel :security-info)
        [mui-button {:disabled in-progress?
                     :on-click nd-events/done-on-click} (tr-l "done")])]]))

(defn new-database-dialog-main []
  (let [m @(nd-events/dialog-data)]
    ;; Following shows how to use backdrop when a background action is initiated in a dialog box
    #_[:div
       (if (= :in-progress (:call-to-create-status m))
         [mui-backdrop {:open true}
          [mui-circular-progress {:color "inherit"}]]
         [new-database-dialog m])]

    [new-database-dialog m]))
