(ns onekeepass.frontend.db-settings
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.events.db-settings :as settings-events]
   [onekeepass.frontend.mui-components :as m :refer [color-primary-main
                                                     mui-list
                                                     mui-list-item-button
                                                     mui-list-item-icon
                                                     mui-list-item-text
                                                     mui-tooltip
                                                     mui-menu-item
                                                     mui-alert
                                                     mui-linear-progress
                                                     mui-input-adornment
                                                     mui-icon-button
                                                     mui-icon-feed-outlined
                                                     mui-icon-security-outlined
                                                     mui-icon-folder-outlined
                                                     mui-icon-visibility
                                                     mui-icon-visibility-off
                                                     mui-typography
                                                     mui-dialog
                                                     mui-dialog-title
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-box mui-stack
                                                     mui-icon-settings-outlined
                                                     mui-button]]))

;;(set! *warn-on-infer* true)

(def algorithms [{:name "AES 256" :value "Aes256"} {:name "ChaCha20 256" :value "ChaCha20"}])

(def text-style-m {:primaryTypographyProps
                   {:font-size 15 :font-weight "medium"}})

(defn list-items [{:keys [panel]}]
  [mui-box {:sx {"& .MuiListItemButton-root" {:padding-left "8px"}
                 "& .MuiListItemIcon-root" {:min-width 0 :margin-right "25px"}
                 "& .MuiSvgIcon-root" {:color color-primary-main} ;; primary main "#1976d2"
                 }}
   [mui-list
    [mui-list-item-button {:on-click #(settings-events/db-settings-panel-select :general-info)
                           :selected (= :general-info panel)}
     [mui-list-item-icon [mui-icon-settings-outlined]]
     [mui-list-item-text text-style-m "General"]]

    [mui-list-item-button {:on-click #(settings-events/db-settings-panel-select :credentials-info)
                           :selected (= :credentials-info panel)}
     [mui-list-item-icon [mui-icon-feed-outlined]]
     [mui-list-item-text text-style-m "Credentials"]]

    [mui-list-item-button {:on-click #(settings-events/db-settings-panel-select :security-info)
                           :selected (= :security-info panel)}
     [mui-list-item-icon [mui-icon-security-outlined]]
     [mui-list-item-text text-style-m "Security"]]]])

(defn- basic-info
  "Incoming settings map has nested maps and are destructred"
  [{:keys [error-fields]
    {:keys [database-file-name]
     {:keys [database-name database-description]} :meta} :data
    :as _settings}]  ;;error-fields is a map
  [mui-stack
   [mui-typography {:text-align "center"} "Basic Database Information"]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label "Name"
                    :value database-name
                    :required true
                    :error (contains? error-fields :database-name)
                    :helperText (get error-fields :database-name "Display name for your database")
                    :autoFocus true
                    :on-change (settings-events/field-update-factory [:data :meta :database-name])
                    :variant "standard" :fullWidth true}]]

    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label "Description"
                    :value database-description
                    :on-change (settings-events/field-update-factory [:data :meta :database-description])
                    :variant "standard" :fullWidth true}]]

    [mui-box {:sx {:width "80%"}}
     [mui-typography {:text-align "left" :variant "caption"}  "Database File"]
     [mui-tooltip {:title database-file-name}
      [mui-typography {:text-align "left" :variant "subtitle2"
                       :sx {:white-space "nowrap"
                            :text-overflow "ellipsis"
                            :overflow "hidden"}}
       database-file-name]]]]])

(defn- password-credential [{:keys [password-confirm
                                    password-visible
                                    password-field-show
                                    password-use-removed
                                    error-fields]
                             {:keys [password password-used]} :data}]
  [mui-box {:sx {:width "80%"}}
   (if-not password-field-show
     (if password-used
       [mui-stack {:direction "row"}
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/password-change-action :remove))} "Remove password"]
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/password-change-action :change))} "Change password"]]
       [mui-stack
        (when password-use-removed
          [mui-alert {:severity "warning" :sx {:mt 1}} "Password is not used in master key"])
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/password-change-action :add))} "Add password"]])

     [mui-stack 
      [m/text-field {:label "New Password"
                     :value password
                                 ;;:required true
                     :placeholder "Change Password"
                     :error (contains? error-fields :password)
                     :helperText (get error-fields :password "Password for your database")
                     :autoFocus true
                     :on-change (settings-events/field-update-factory [:data :password])
                     :variant "standard" :fullWidth true
                     :type (if password-visible "text" "password")
                     :InputProps {:endAdornment (r/as-element
                                                 [mui-input-adornment {:position "end"}
                                                  (if password-visible
                                                    [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                      :on-click #(settings-events/database-field-update :password-visible false)}
                                                     [mui-icon-visibility]]
                                                    [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                      :on-click #(settings-events/database-field-update :password-visible true)}
                                                     [mui-icon-visibility-off]])])}}]

      (when (not password-visible)
        [m/text-field {:label "Confirm Password"
                       :value password-confirm
                                           ;;:required true
                       :placeholder "Confirm Password"
                       :error (contains? error-fields :password-confirm)
                       :helperText (get error-fields :password-confirm)
                       :on-change (settings-events/field-update-factory :password-confirm)
                       :type "password"
                       :variant "standard" :fullWidth true}])

      (when @(settings-events/password-changed?)
        [mui-alert {:severity "warning" :sx {:mt 1}} "Password is going to be changed.."])])])

(defn- key-file-credential [{:keys [key-file-use-removed
                                    key-file-field-show]
                             {:keys [key-file-name key-file-used]} :data}]
  [mui-box {:sx {:width "80%"}}
   (if-not key-file-field-show
     (if key-file-used
       [mui-stack {:direction "row"}
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/key-file-change-action :remove))} "Remove key file"]
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/key-file-change-action :change))} "Change key file"]]
       [mui-stack
        (when key-file-use-removed
          [mui-alert {:severity "warning" :sx {:mt 1}} "Key file is not used in master key"])
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/key-file-change-action :add))} "Add key file"]])

     [mui-stack
      [m/text-field {:label "Key File Name"
                     :value key-file-name
                     :placeholder "Optional"
                     :on-change (settings-events/field-update-factory [:data :key-file-name])
                     :variant "standard" :fullWidth true
                     :InputProps {:endAdornment (r/as-element [mui-input-adornment {:position "end"}
                                                               [mui-icon-button {:edge "end" :sx {:mr "-8px"}
                                                                                 :onClick settings-events/open-key-file-explorer-on-click}
                                                                [mui-icon-folder-outlined]]])}}]

      [mui-stack
       [mui-button  {:sx {:m 1}
                     :variant "text"
                     :on-click settings-events/generate-key-file} "Generate new key file"]]
      (when-let [kind @(settings-events/key-file-name-change-kind)]
        [mui-alert {:severity "warning" :sx {:mt 1}} (condp = kind
                                                       :none-to-some "Key file will be used in master key"
                                                       :some-to-none "You are removing the use of key file"
                                                       :some-to-some "You are changing existing key file use"
                                                       "")])])])

(defn- credentials-info [{:keys [error-fields]  :as credentials-info}]

  [mui-stack {:spacing 2}
   [mui-typography {:text-align "center"} "Database Credentials"]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}

    [password-credential credentials-info]

    [key-file-credential credentials-info]

    (when-let [msg (get error-fields :no-credential-set)]
      [mui-stack
       [mui-alert {:severity "error" :sx {:mt 1}} msg]])]])

(defn- security-info [{:keys [error-fields]
                       {:keys [cipher-id]
                        {{:keys [iterations memory parallelism]} :Argon2} :kdf} :data}]
  [mui-stack {:spacing 2}
   [mui-typography {:text-align "center"} "Security"]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}} ;;:alignItems "center"
    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "50%" :ml 3}}
      [m/text-field {:label "Encription Algorithm"
                     :value cipher-id
                     :required true
                     :select true
                     :autoFocus true
                     :on-change (settings-events/field-update-factory [:data :cipher-id])
                     :variant "standard" :fullWidth true}
       (doall
        (for [{:keys [name value]} algorithms]
          ^{:key value} [mui-menu-item {:value value} name]))]]]

    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "50%" :ml 3}}
      [m/text-field {:label "Key Derivation Function"
                     :value :Argon-2d
                     :required true
                     :select true
                     :autoFocus true
                    ;;:on-change 
                     :variant "standard" :fullWidth true}
       [mui-menu-item {:value :Argon-2d} "Argon 2d"]]]]

    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label "Transform Rounds"
                     :value iterations ;;(:iterations kdf)
                     :type "number"
                     :error (contains? error-fields :iterations)
                     :helperText (get error-fields :iterations)
                     :on-change (settings-events/field-update-factory [:data :kdf :Argon2 :iterations])
                     :variant "standard" :fullWidth true}]]

     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label "Memory Usage"
                     :value memory ;;(:memory kdf)
                     :type "number"
                     :error (contains? error-fields :memory)
                     :helperText (get error-fields :memory)
                     :on-change (settings-events/field-update-factory [:data :kdf :Argon2 :memory])
                     :variant "standard" :fullWidth true}]]
     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label "Parallelism"
                     :value parallelism ;;(:parallelism kdf)
                     :type "number"
                     :error (contains? error-fields :parallelism)
                     :helperText (get error-fields :parallelism)
                     ;; Using min for "number" type is not working
                     ;;:InputProps {:min "2"}
                     ;;:min 2 
                     :on-change (settings-events/field-update-factory [:data :kdf :Argon2 :parallelism])
                     :variant "standard" :fullWidth true}]]]]])

(defn settings-dialog [{:keys [dialog-show status api-error-text panel] :as dialog-data}]
  (let [in-progress? (= :in-progress status)
        modified @(settings-events/db-settings-modified)]
    [mui-dialog {:open (if (nil? dialog-show) false dialog-show)  :on-click #(.stopPropagation %)
                 :sx {:min-width "600px"
                      "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}
               ;;:classes {:paper "pwd-dlg-root"} 
                 }
     [mui-dialog-title "Database Settings"]
     [mui-dialog-content {:sx {:padding-left "10px"}}
      [mui-stack
       [mui-stack {:direction "row" :sx {:height "350px " :min-height "300px"}}
        [mui-box {:sx {:width "30%"  :background "#F1F1F1"}} [list-items dialog-data]  #_"List comes here"]
        [mui-box {:sx {:width "70%"  :height "100%"
                       :align-self "center"
                       :background "rgba(241, 241, 241, 0.33)"
                       :margin-left "5px"}}  ;;:text-align "center"

         (cond
           (= panel :general-info)
           [basic-info dialog-data]

           (= panel :credentials-info)
           [credentials-info dialog-data]

           (= panel :security-info)
           [security-info dialog-data]

           in-progress?
           [mui-stack "Database settings change is in progress"]

           :else
           [:div])]]

       (when api-error-text #_(not (nil? api-error-text))
             [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])

       (when (and (nil? api-error-text) in-progress?)
         [mui-linear-progress {:sx {:mt 2}}])]]

     [mui-dialog-actions
      [mui-button {:variant "contained" :color "secondary"
                   :disabled in-progress?
                   :on-click settings-events/cancel-on-click} "Cancel"]
      [mui-button {:variant "contained" :color "secondary"
                   :disabled (or (not modified) in-progress?)
                   :on-click settings-events/ok-on-click} "Ok"]]]))

(defn settings-dialog-main []
  [settings-dialog @(settings-events/dialog-data)])
