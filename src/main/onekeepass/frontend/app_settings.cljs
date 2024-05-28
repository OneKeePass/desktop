(ns onekeepass.frontend.app-settings
  (:require [onekeepass.frontend.events.app-settings :as app-settings-events]
            [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                              mui-box
                                                              mui-button
                                                              mui-dialog
                                                              mui-dialog-actions
                                                              mui-dialog-content
                                                              mui-dialog-title
                                                              mui-icon-security-outlined
                                                              mui-icon-settings-outlined
                                                              mui-list
                                                              mui-list-item-button
                                                              mui-list-item-icon
                                                              mui-list-item-text
                                                              mui-menu-item
                                                              mui-stack
                                                              mui-typography
                                                              theme-color]]
            [onekeepass.frontend.translation  :refer-macros [tr-l tr-bl tr-h] :refer [lstr-l-cv
                                                                                      tr-dlg-title
                                                                                      tr-t]]
            [clojure.string :as str]))


(def text-style-m {:primaryTypographyProps
                   {:font-size 15 :font-weight "medium"}})

(defn list-items [panel]
  [mui-box {:sx {"& .MuiListItemButton-root" {:padding-left "8px"}
                 "& .MuiListItemIcon-root" {:min-width 0 :margin-right "25px"}
                 "& .MuiSvgIcon-root" {:color  (theme-color @custom-theme-atom :db-settings-icons)}}}
   [mui-list
    [mui-list-item-button {:on-click #(app-settings-events/app-settings-panel-select :general-info)
                           :selected (= panel :general-info)}
     [mui-list-item-icon [mui-icon-settings-outlined]]
     [mui-list-item-text text-style-m (tr-l general)]]

    [mui-list-item-button {:on-click #(app-settings-events/app-settings-panel-select :security-info)
                           :selected (= panel :security-info)}
     [mui-list-item-icon [mui-icon-security-outlined]]
     [mui-list-item-text text-style-m (tr-l security)]]]])


(def themes [{:name "Light" :value "light"} {:name "Dark" :value "dark"}])

(def entry-groupings [{:name "Groups" :value "Groups"} {:name "Categories" :value "Categories"}
                      {:name "Types" :value "Types"} {:name "Tags" :value "Tags"}])

(def languages [{:name "en - English" :value "en"}
                {:name "es - Español" :value "es"}
                #_{:name "fr - Français" :value "fr"}])


(defn- user-interface
  "Incoming settings map has nested maps and are destructred"
  [{:keys [_error-fields]
    {:keys  [theme language]} :preference-data}]
  [mui-stack
   [mui-stack {:sx {:pt 1 :pb 1}}  ;;:bgcolor "rgba(25, 118, 210, 0.20)"
    [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
     (tr-t "userInterface")]]
   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l "theme")
                    :value (if (str/blank? theme) "light" theme)
                    :required true
                    :select true
                    :autoFocus true
                    :on-change (app-settings-events/field-update-factory [:preference-data :theme])
                    :variant "standard" :fullWidth true}

      (doall
       (for [{:keys [name value]} themes]
         ^{:key value} [mui-menu-item {:value value} (lstr-l-cv name)]))]]

    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l "language")
                    :value (if (str/blank? language) "en" language)
                    :required true
                    :select true
                    :helperText (tr-h requiresApplicationRestart)
                    :autoFocus true
                    :on-change (app-settings-events/field-update-factory [:preference-data :language])
                    :variant "standard" :fullWidth true}

      (doall
       (for [{:keys [name value]} languages]
         ^{:key value} [mui-menu-item {:value value} name]))]]]])


(defn entry-management [{:keys [_error-fields]
                         {:keys  [default-entry-category-groupings]} :preference-data}]
  [mui-stack
   [mui-stack {:sx {:pt 1 :pb 1}}  ;;:bgcolor "rgba(25, 118, 210, 0.20)"

    [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
     (tr-t "entryManagement")]]

   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [m/text-field {:label (tr-l "entryGroupings")
                    :value (if (str/blank? default-entry-category-groupings) "Groups" default-entry-category-groupings)
                    :required true
                    :select true
                    :autoFocus true
                    :on-change (app-settings-events/field-update-factory [:preference-data :default-entry-category-groupings])
                    :variant "standard" :fullWidth true}

      (doall
       (for [{:keys [name value]} entry-groupings]
         ^{:key value} [mui-menu-item {:value value} (lstr-l-cv name)]))]]]])

(defn general-info [dialog-data]
  [mui-stack
   [user-interface dialog-data]
   [entry-management dialog-data]])


(defn security-info [{:keys [error-fields]
                      {:keys  [clipboard-timeout session-timeout]} :preference-data}]
  [mui-stack
   [mui-stack {:sx {:pt 1 :pb 1}}
    [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
     (tr-t "timeouts")]]

   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [mui-stack
      [m/text-field {:label (tr-l sessionTimeout)
                     :value session-timeout
                     :type "number"
                     :error (contains? error-fields :session-timeout)
                     :helperText (get error-fields :session-timeout)
                     :on-change (app-settings-events/field-update-factory [:preference-data :session-timeout])
                     :variant "standard" :fullWidth true}]]

     ;; Enable this once we add clearing clipboard on timeout feature 
     #_[mui-stack {:sx {:margin-top "16px"}}
        [m/text-field {:label (tr-l "clipboardTimeout")
                       :value clipboard-timeout
                       :type "number"
                       :error (contains? error-fields :clipboard-timeout)
                       :helperText (get error-fields :clipboard-timeout)
                       :on-change (app-settings-events/field-update-factory [:preference-data :clipboard-timeout])
                       :variant "standard" :fullWidth true}]]]]])


(defn app-settings-dialog [{:keys [dialog-show
                                   status
                                   api-error-text
                                   panel
                                   error-fields] :as dialog-data}]
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               ;;:fullScreen true
               ;;:scroll "paper" 
               :on-click #(.stopPropagation %)
               :sx {:min-width "650px"  "& .MuiDialog-paper" {:max-width "750px" :width "90%"}}}
   [mui-dialog-title (tr-dlg-title applicationSettings)]

   [mui-dialog-content {:sx {:padding-left "10px"} :dividers true}
    [mui-stack {:direction "row" :sx {:height "350px " :min-height "300px"}}
     [mui-box {:sx {:width "30%"
                    :background "rgba(241, 241, 241, 0.33)"}}
      [list-items panel]]

     [mui-box {:sx {:width "70%"  :height "100%"
                    :align-self "center"
                    :overflow-y "auto"
                    :background "rgba(241, 241, 241, 0.33)"
                    :margin-left "5px"}}

      (condp = panel

        :general-info
        [general-info dialog-data]

        :security-info
        [security-info dialog-data]

        ;;IMPORATNT: 
        ;; We need this as dialog-data may nil and hence panel when first time  
        ;; [app-settings-dialog] is called. Otherwise we will see
        ;; Error: No matching clause and app UI will fail
        [:div])]]]

   [mui-dialog-actions
    [mui-button {:variant "contained" :color "secondary"
                     ;;:disabled in-progress?
                 :on-click app-settings-events/app-settings-dialog-close} (tr-bl cancel)]
    [mui-button {:variant "contained" :color "secondary"
                     ;;:disabled (or (not modified) in-progress? (-> error-fields seq boolean))
                 :on-click app-settings-events/app-settings-save} (tr-bl ok)]]])

(defn app-settings-dialog-main []
  [app-settings-dialog @(app-settings-events/app-settings-dialog-data)])