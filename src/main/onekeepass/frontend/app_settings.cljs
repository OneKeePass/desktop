(ns onekeepass.frontend.app-settings
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.events.app-settings :as app-settings-events]
   [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom mui-box
                                                     mui-button mui-checkbox
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-form-control-label
                                                     mui-icon-security-outlined
                                                     mui-icon-settings-outlined
                                                     mui-icon-open-in-browser
                                                     mui-list
                                                     mui-list-item-button
                                                     mui-list-item-icon
                                                     mui-list-item-text
                                                     mui-menu-item mui-stack
                                                     mui-typography
                                                     theme-color]]
   [onekeepass.frontend.translation :as t :refer-macros [tr-l tr-bl tr-h] :refer [lstr-l-cv
                                                                                  tr-dlg-title
                                                                                  tr-t]]
   [reagent.core :as r]))


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
     [mui-list-item-text text-style-m (tr-l security)]]

    [mui-list-item-button {:on-click #(app-settings-events/app-settings-panel-select :browser-integration)
                           :selected (= panel :browser-integration)}
     [mui-list-item-icon [mui-icon-open-in-browser]]
     [mui-list-item-text text-style-m "Browser Integration" #_(tr-l security)]]]])


(def themes [{:name "Light" :value "light"} {:name "Dark" :value "dark"}])

(def entry-groupings [{:name "Groups" :value "Groups"} {:name "Categories" :value "Categories"}
                      {:name "Types" :value "Types"} {:name "Tags" :value "Tags"}])

;; Here we list all lanaguages that we support. 
;; We need to have the corresponding translation json files in the dir resources/public/translations
;; See translation.rs for the backend loading of these json files
(def languages [{:name "en - English" :value "en"}
                {:name "es - Español" :value "es"}
                {:name "de - Deutsch" :value "de"}
                {:name "zh - 中文" :value "zh"}
                {:name "ar - العربية" :value "ar"}

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

(defn browser-integration [{:keys [_error-fields]
                            {:keys  [browser-ext-support]} :preference-data}]
  ;; (println "browser-ext-support: " browser-ext-support)
  [mui-stack
   [mui-stack {:sx {:pt 1 :pb 1}}
    [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
     "Extensions" #_(tr-t "browserIntegration")]]

   [mui-stack {:spacing 2 :sx {:alignItems "center"}}
    [mui-box {:sx {:width "80%"}}
     [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
      [mui-form-control-label
       {:control (r/as-element
                  [mui-checkbox
                   {:checked (:extension-use-enabled browser-ext-support)
                    :on-change (fn [^js/CheckedEvent e]
                                 (let [checked? (-> e .-target  .-checked)]
                                   ;; If user is disabling browser ext support, we need to clear the allowed-browsers list
                                   (when (not checked?)
                                     (app-settings-events/field-update
                                      [:preference-data :browser-ext-support :allowed-browsers] []))
                                   (app-settings-events/field-update [:preference-data :browser-ext-support :extension-use-enabled] checked?)))}])
        :label "Enable browser Integration"}]]]]])

(def ^:private FIREFOX "Firefox")
(def ^:private CHROME "Chrome")
;; (def ^:private EDGE "Edge")
;; (def ^:private BRAVE "Brave")

(defn- named-browser-enabled? [browser-name allowed-browsers]
  (boolean (some #(= browser-name %) allowed-browsers)))

(defn- toggle-browser-enabled [checked? browser-name allowed-browsers]
  (if (not checked?)
    ;; remove the browser from the list
    (vec (remove #(= browser-name %) allowed-browsers))
    ;; add the browser to the list
    (conj (vec allowed-browsers) browser-name)))

(defn supported-browsers [dialog-data]
  (let [browser-ext-support (get-in dialog-data [:preference-data :browser-ext-support])
        {:keys [allowed-browsers extension-use-enabled]} browser-ext-support]
    [mui-stack
     [mui-stack {:sx {:pt 1 :pb 1}}
      [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
       "Supported Browsers"]]

     [mui-stack {:spacing 2 :sx {:alignItems "center"}}
      [mui-box {:sx {:width "80%"}}
       [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox
                     {:disabled (not extension-use-enabled)
                      :checked (named-browser-enabled? FIREFOX allowed-browsers)
                      :on-change (fn [^js/CheckedEvent e]
                                   (app-settings-events/field-update
                                    [:preference-data :browser-ext-support :allowed-browsers]
                                    (toggle-browser-enabled (-> e .-target  .-checked) FIREFOX allowed-browsers)))}])
          :label "Firefox"}]

        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox
                     {:disabled (not extension-use-enabled)
                      :checked (named-browser-enabled? CHROME allowed-browsers)
                      :on-change (fn [^js/CheckedEvent e]
                                   (app-settings-events/field-update
                                    [:preference-data :browser-ext-support :allowed-browsers]
                                    (toggle-browser-enabled (-> e .-target  .-checked) CHROME allowed-browsers)))}])
          :label "Chrome"}]]]]]))


(defn app-settings-dialog [{:keys [dialog-show
                                   panel
                                   error-fields] :as dialog-data}]
  [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
               :dir (t/dir)
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

        :browser-integration
        [mui-stack
         [browser-integration dialog-data]
         [m/mui-divider {:sx {:mt 5 :mb 5}}]
         [supported-browsers dialog-data]]


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
                 :disabled (or (not @(app-settings-events/app-settings-modified)) (-> error-fields seq boolean))
                 ;;:disabled (or (not modified) in-progress? (-> error-fields seq boolean))
                 :on-click app-settings-events/app-settings-save} (tr-bl ok)]]])

(defn app-settings-dialog-main []
  [app-settings-dialog @(app-settings-events/app-settings-dialog-data)])