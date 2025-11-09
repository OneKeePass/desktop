(ns onekeepass.frontend.db-settings
  (:require [onekeepass.frontend.events.db-settings :as settings-events]
            [onekeepass.frontend.common-components :refer [cipher-algorithms kdf-algorithms]]
            [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                              mui-alert
                                                              mui-box
                                                              mui-button
                                                              mui-dialog
                                                              mui-dialog-actions
                                                              mui-dialog-content
                                                              mui-dialog-title
                                                              mui-icon-button
                                                              mui-icon-feed-outlined
                                                              mui-icon-folder-outlined
                                                              mui-icon-security-outlined
                                                              mui-icon-settings-outlined
                                                              mui-icon-visibility
                                                              mui-icon-visibility-off
                                                              mui-input-adornment
                                                              mui-linear-progress
                                                              mui-list
                                                              mui-list-item-button
                                                              mui-list-item-icon
                                                              mui-list-item-text
                                                              mui-menu-item
                                                              mui-stack
                                                              mui-tooltip
                                                              mui-typography
                                                              theme-color]] 
            [onekeepass.frontend.translation :as t :refer-macros [tr-l
                                                             tr-t
                                                             tr-h
                                                             tr-bl
                                                             tr-dlg-title]]
            [reagent.core :as r]))

(set! *warn-on-infer* true)

(def text-style-m {:primaryTypographyProps
                   {:font-size 15 :font-weight "medium"}})

(defn list-items [{:keys [panel]}]
  [mui-box {:sx {"& .MuiListItemButton-root" {:padding-left "8px"}
                 "& .MuiListItemIcon-root" {:min-width 0 :margin-right "25px"}
                 "& .MuiSvgIcon-root" {:color  (theme-color @custom-theme-atom :db-settings-icons)} ;; primary main "#1976d2"
                 }}
   [mui-list
    [mui-list-item-button {:on-click #(settings-events/db-settings-panel-select :general-info)
                           :selected (= :general-info panel)}
     [mui-list-item-icon [mui-icon-settings-outlined]]
     [mui-list-item-text text-style-m (tr-l general)]]

    [mui-list-item-button {:on-click #(settings-events/db-settings-panel-select :credentials-info)
                           :selected (= :credentials-info panel)}
     [mui-list-item-icon [mui-icon-feed-outlined]]
     [mui-list-item-text text-style-m (tr-l credentials)]]

    [mui-list-item-button {:on-click #(settings-events/db-settings-panel-select :security-info)
                           :selected (= :security-info panel)}
     [mui-list-item-icon [mui-icon-security-outlined]]
     [mui-list-item-text text-style-m (tr-l security)]]]])

(defn- basic-info
  "Incoming settings map has nested maps and are destructred"
  [{:keys [error-fields]
    {:keys [database-file-name]
     {:keys [database-name database-description]} :meta} :data
    :as _settings}]  ;;error-fields is a map
  [mui-stack
   [mui-typography {:text-align "center"} (tr-t basicDatabaseInformation)]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l name)
                    :value database-name
                    :required true
                    :error (contains? error-fields :database-name)
                    :helperText (get error-fields :database-name (tr-h dbDisplayName))
                    :autoFocus true
                    :on-change (settings-events/field-update-factory [:data :meta :database-name])
                    :variant "standard" :fullWidth true}]]

    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l description)
                    :value database-description
                    :on-change (settings-events/field-update-factory [:data :meta :database-description])
                    :variant "standard" :fullWidth true}]]

    [mui-box {:sx {:width "80%"}}
     [mui-typography {:text-align "left" :variant "caption"}  (tr-t databaseFile)]
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
                                    password-use-added
                                    error-fields]
                             {:keys [password password-used]} :data}]
  [mui-box {:sx {:width "80%"}}
   (if-not password-field-show

     (if password-used
       [mui-stack {:direction "row"}
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/password-change-action :remove))}
         (tr-bl removePassword)]
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/password-change-action :change))}
         (tr-bl changePassword)]]
       [mui-stack
        (when password-use-removed
          [mui-alert {:severity "warning" :sx {:mt 1}}
           (tr-h passwordNotUsed)])
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/password-change-action :add))}
         (tr-bl addPassword)]])

     [mui-stack
      [m/text-field {:label (tr-l password)
                     :value password
                     :placeholder (if password-use-added (tr-l addPassword)  (tr-l changePassword))
                     :error (contains? error-fields :password)
                     :helperText (get error-fields :password (tr-h passwordForYourDb))
                     :autoFocus true
                     :on-change (settings-events/field-update-factory [:data :password])
                     :variant "standard" :fullWidth true
                     :type (if password-visible "text" "password")
                     :InputProps {:endAdornment (r/as-element
                                                 [mui-input-adornment {:position "end"}
                                                  (if password-visible
                                                    [mui-icon-button
                                                     {:edge "end" :sx {:mr "-8px"}
                                                      :on-click #(settings-events/database-field-update
                                                                  :password-visible false)}
                                                     [mui-icon-visibility]]
                                                    [mui-icon-button
                                                     {:edge "end" :sx {:mr "-8px"}
                                                      :on-click #(settings-events/database-field-update
                                                                  :password-visible true)}
                                                     [mui-icon-visibility-off]])])}}]

      (when (not password-visible)
        [m/text-field {:label (tr-l confirmPassword)
                       :value password-confirm
                       ;;:required true
                       :placeholder (tr-l confirmPassword)
                       :error (contains? error-fields :password-confirm)
                       :helperText (get error-fields :password-confirm)
                       :on-change (settings-events/field-update-factory :password-confirm)
                       :type "password"
                       :variant "standard" :fullWidth true}])

      (cond
        @(settings-events/password-changed?)
        [mui-alert {:severity "warning" :sx {:mt 1}} (tr-h passwordWillBeChanged)]

        password-use-removed
        [mui-alert {:severity "warning" :sx {:mt 1}} (tr-h passwordIsChanged)])])])

(defn- key-file-credential [{:keys [key-file-use-removed
                                    key-file-field-show]
                             {:keys [key-file-name key-file-used]} :data}]
  [mui-box {:sx {:width "80%"}}
   (if-not key-file-field-show
     (if key-file-used
       [mui-stack {:direction "row"}
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/key-file-change-action :remove))}
         (tr-bl removeKeyFile)]
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/key-file-change-action :change))}
         (tr-bl changeKeyFile)]]
       [mui-stack
        (when key-file-use-removed
          [mui-alert {:severity "warning" :sx {:mt 1}}
           (tr-h keyFileNotUsed)])
        [mui-button  {:sx {:m 1}
                      :variant "text"
                      :on-click #((settings-events/key-file-change-action :add))}
         (tr-bl addKeyFile)]])

     [mui-stack
      [m/text-field {:label (tr-l keyFileName)
                     :value key-file-name
                     :placeholder "Optional"
                     :on-change (settings-events/field-update-factory [:data :key-file-name])
                     :variant "standard" :fullWidth true
                     :InputProps {:endAdornment (r/as-element [mui-input-adornment {:position "end"}
                                                               [mui-icon-button
                                                                {:edge "end" :sx {:mr "-8px"}
                                                                 :onClick settings-events/open-key-file-explorer-on-click}
                                                                [mui-icon-folder-outlined]]])}}]

      [mui-stack
       [mui-button  {:sx {:m 1}
                     :variant "text"
                     :on-click settings-events/generate-key-file} (tr-bl generateNewKeyFile)]]
      (when-let [kind @(settings-events/key-file-name-change-kind)]
        [mui-alert {:severity "warning" :sx {:mt 1}} (condp = kind
                                                       :none-to-some (tr-h keyFileWillBeUsed)
                                                       :some-to-none (tr-h keyFileWillBeRemoved)
                                                       :some-to-some (tr-h keyFileWillBeChanged)
                                                       "")])])])

(defn- credentials-info [{:keys [error-fields]  :as credentials-m}]

  [mui-stack {:spacing 2}
   [mui-typography {:text-align "center"} (tr-t databaseCredentials)]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}

    [password-credential credentials-m]

    [key-file-credential credentials-m]

    (when-let [msg (get error-fields :no-credential-set)]
      [mui-stack
       [mui-alert {:severity "error" :sx {:mt 1}} msg]])]])

(defn- security-info [{:keys [error-fields]
                       {:keys [cipher-id]
                        {:keys [iterations memory parallelism algorithm]} :kdf} :data}] 
  [mui-stack {:spacing 2}
   [mui-typography {:text-align "center"} (tr-t security)]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}} ;;:alignItems "center"
    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "50%" :ml 3}}
      [m/text-field {:label (tr-l encriptionAlgorithm)
                     :value cipher-id
                     :required true
                     :select true
                     :autoFocus true
                     :on-change (settings-events/field-update-factory [:data :cipher-id])
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
                     :on-change (fn [^js/Event e]  ;;^js/EventT (.-target)
                                  (settings-events/db-settings-kdf-algorithm-select (->  e ^js/EventT (.-target)  .-value)))
                     :variant "standard" :fullWidth true}
       (doall
        (for [{:keys [name value]} kdf-algorithms]
          ^{:key value} [mui-menu-item {:value value} name]))]]]

    [mui-stack {:direction "row" :sx {:width "100%"}}
     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label (tr-l transformRounds)
                     :value iterations ;;(:iterations kdf)
                     :type "number"
                     :error (contains? error-fields :iterations)
                     :helperText (get error-fields :iterations)
                     :on-change (settings-events/field-update-factory [:data :kdf :iterations])
                     :variant "standard" :fullWidth true}]]

     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label (tr-l memoryUsage)
                     :value memory ;;(:memory kdf)
                     :type "number"
                     :error (contains? error-fields :memory)
                     :helperText (get error-fields :memory)
                     :on-change (settings-events/field-update-factory [:data :kdf :memory])
                     :variant "standard" :fullWidth true}]]
     [mui-stack {:sx {:width "33.33%" :ml 3}}
      [m/text-field {:label (tr-l parallelism)
                     :value parallelism ;;(:parallelism kdf)
                     :type "number"
                     :error (contains? error-fields :parallelism)
                     :helperText (get error-fields :parallelism)
                     ;; Using min for "number" type is not working
                     ;;:InputProps {:min "2"}
                     ;;:min 2 
                     :on-change (settings-events/field-update-factory [:data :kdf :parallelism])
                     :variant "standard" :fullWidth true}]]]]])

(defn settings-dialog [{:keys [dialog-show
                               status
                               api-error-text
                               panel
                               error-fields] :as dialog-data}]
  (let [in-progress? (= :in-progress status)
        modified @(settings-events/db-settings-modified)]
    [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
                 :dir (t/dir)
                 :on-click #(.stopPropagation ^js/Event %)
                 :sx {:min-width "600px"
                      "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
     [mui-dialog-title (tr-dlg-title databaseSettings)]
     [mui-dialog-content {:sx {:padding-left "10px"} :dir (t/dir)}
      [mui-stack
       [mui-stack {:direction "row" :sx {:height "350px " :min-height "300px"}}
        ;; Left side list
        [mui-box {:sx {:width "30%"
                       :background "rgba(241, 241, 241, 0.33)"}}
         [:div {:class "gbox"
                :style {:margin 0
                        :width "100%"}}
          [:div {:class "gcontent" :style {}}
           [list-items dialog-data]]
          [:div {:class "gfooter"}
           [mui-stack {:justify-content "center"}
            [mui-button {:variant "text"
                         :disabled (or modified in-progress? (-> error-fields seq boolean))
                         :color "secondary"
                         :on-click settings-events/app-settings-dialog-read-start}
             (tr-l "appSettings")]]]]]

        ;; Right side panel
        [mui-box {:sx {:width "70%"  :height "100%"
                       :align-self "center"
                       :background "rgba(241, 241, 241, 0.33)"
                       :margin-left "5px"}}

         (cond
           (= panel :general-info)
           [basic-info dialog-data]

           (= panel :credentials-info)
           [credentials-info dialog-data]

           (= panel :security-info)
           [security-info dialog-data]

           in-progress?
           [mui-stack (tr-h databaseSettingsChange)]

           ;; IMPORATNT: Need this clause when dialog-data is empty
           ;; This happens when the component is created first time with 
           ;; default dialog-data
           :else
           [:div])]]

       (when api-error-text
         [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])

       (when (and (nil? api-error-text) in-progress?)
         [mui-linear-progress {:sx {:mt 2}}])]]

     [mui-dialog-actions
      [mui-button {:variant "contained" :color "secondary"
                   :disabled in-progress?
                   :on-click settings-events/cancel-on-click} (tr-bl cancel)]
      [mui-button {:variant "contained" :color "secondary"
                   :disabled (or (not modified) in-progress? (-> error-fields seq boolean))
                   :on-click settings-events/ok-on-click} (tr-bl ok)]]]))

(defn settings-dialog-main []
  [settings-dialog @(settings-events/dialog-data)])
