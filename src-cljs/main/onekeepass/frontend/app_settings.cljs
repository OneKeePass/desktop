(ns onekeepass.frontend.app-settings
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.app-settings :as app-settings-events]
   [onekeepass.frontend.events.ssh-agent :as ssh-agent-events]
   [onekeepass.frontend.events.common :as ce]
   [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom mui-box
                                                     mui-alert
                                                     mui-button mui-checkbox
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-form-control-label
                                                     mui-icon-button
                                                     mui-icon-folder-outlined
                                                     mui-icon-open-in-browser
                                                     mui-icon-security-outlined
                                                     mui-icon-settings-outlined
                                                     mui-icon-vpn-key-outlined
                                                     mui-input-adornment
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

    [mui-list-item-button {:on-click #(app-settings-events/app-settings-panel-select :file-management)
                           :selected (= panel :file-management)}
     [mui-list-item-icon [mui-icon-folder-outlined]]
     [mui-list-item-text text-style-m (tr-l "fileManagement")]]

    [mui-list-item-button {:on-click #(app-settings-events/app-settings-panel-select :browser-integration)
                           :selected (= panel :browser-integration)}
     [mui-list-item-icon [mui-icon-open-in-browser]]
     [mui-list-item-text text-style-m (tr-l "browserIntegration")]]

    [mui-list-item-button {:on-click #(app-settings-events/app-settings-panel-select :ssh-agent)
                           :selected (= panel :ssh-agent)}
     [mui-list-item-icon [mui-icon-vpn-key-outlined]]
     [mui-list-item-text text-style-m (t/lstr-l "sshAgent")]]]])


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
                {:name "fi - suomi" :value "fi"}
                {:name "ru - русский" :value "ru"}
                {:name "it - Italiano" :value "it"}
                {:name "pt-BR - Português do Brasil" :value "pt-BR"}

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

     [mui-stack {:sx {:margin-top "16px"}}
      [m/text-field {:label (tr-l "clipboardTimeout")
                     :value clipboard-timeout
                     :type "number"
                     :error (contains? error-fields :clipboard-timeout)
                     :helperText (get error-fields :clipboard-timeout)
                     :on-change (app-settings-events/field-update-factory [:preference-data :clipboard-timeout])
                     :variant "standard" :fullWidth true}]]]]])

(defn file-management [{:keys [error-fields]
                        {:keys [backup]} :preference-data}]
  (let [{:keys [enabled dir]} backup]
    [mui-stack
     [mui-stack {:sx {:pt 1 :pb 1}}
      [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
       (tr-t "backups")]]

     [mui-stack {:spacing 2 :sx {:alignItems "center"}}
      [mui-box {:sx {:width "80%"}}
       [mui-form-control-label
        {:control (r/as-element
                   [mui-checkbox
                    {:checked (boolean enabled)
                     :on-change (fn [^js/CheckedEvent e]
                                  (app-settings-events/field-update
                                   [:preference-data :backup :enabled]
                                   (-> e .-target .-checked)))}])
         :label (tr-l "enableBackup")}]]

      [mui-box {:sx {:width "80%"}}
       [m/text-field {:label (tr-l "backupDir")
                      :value (or dir "")
                      :disabled (not enabled)
                      :error (contains? error-fields :backup-dir)
                      :helperText (get error-fields :backup-dir (tr-h "directoryUsedForDatabaseBackups"))
                      :on-change (app-settings-events/field-update-factory [:preference-data :backup :dir])
                      :variant "standard" :fullWidth true
                      :slotProps {:input {:endAdornment (r/as-element
                                                         [mui-input-adornment {:position "end"}
                                                          [mui-icon-button {:edge "end"
                                                                            :disabled (not enabled)
                                                                            :sx {:mr "-8px"}
                                                                            :on-click app-settings-events/open-backup-dir-dialog}
                                                           [mui-icon-folder-outlined]]])}}}]]]]))

(declare browser-manifest-statuses)

(defn browser-integration [{:keys [_error-fields]
                            {:keys  [browser-ext-support]} :preference-data}]
  ;; (println "browser-ext-support: " browser-ext-support)
  [mui-stack
   [mui-stack {:sx {:pt 1 :pb 1}}
    [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
     (tr-t "extensions")]]

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
        :label (tr-l "enableBrowserIntegration")}]]]]
   #_[browser-manifest-statuses]])

;; SSH agent settings panel. Like the other preference panels, the enable
;; checkbox only stages the desired flag into preference-data (lighting up the
;; shared OK button); the agent is actually started/stopped on OK via
;; :app-settings-save. The live status (path / keys / error) is fetched on mount
;; and refreshed after the apply.
(defn ssh-agent-panel [{{:keys [ssh-agent-support]} :preference-data}]
  (r/with-let [_ (ssh-agent-events/init-panel)]
    (let [{:keys [running socket-path key-count error transport mode]} @(ssh-agent-events/agent-status)
          enabled? (boolean (:enabled ssh-agent-support))
          configured-mode (or (:mode ssh-agent-support) const/SSH_AGENT_MODE_AGENT)
          active-selected-mode? (and running (= mode configured-mode))
          client-mode? (= configured-mode const/SSH_AGENT_MODE_CLIENT)]
      [mui-stack
       [mui-stack {:sx {:pt 1 :pb 1}}
        [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
         (t/lstr-l "sshAgentService")]]

       [mui-stack {:spacing 2 :sx {:alignItems "center"}}
        [mui-box {:sx {:width "80%"}}
         [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
          [mui-form-control-label
           {:control (r/as-element
                      [mui-checkbox
                       {:checked enabled?
                        :on-change (fn [^js/CheckedEvent e]
                                     (app-settings-events/field-update
                                      [:preference-data :ssh-agent-support :enabled]
                                      (-> e .-target .-checked)))}])
            :label (t/lstr-l "enableSshAgent")}]]

         (when enabled?
           [m/text-field {:label (t/lstr-l "sshAgentMode")
                          :value configured-mode
                          :select true
                          :on-change (app-settings-events/field-update-factory
                                      [:preference-data :ssh-agent-support :mode])
                          :variant "standard"
                          :fullWidth true
                          :helperText (if client-mode?
                                        (t/lstr-l "sshAgentClientModeHelp")
                                        (t/lstr-l "sshAgentAgentModeHelp"))}
            [mui-menu-item {:value const/SSH_AGENT_MODE_AGENT} (t/lstr-l "sshAgentModeAgent")]
            [mui-menu-item {:value const/SSH_AGENT_MODE_CLIENT} (t/lstr-l "sshAgentModeClient")]])

         (when (and enabled? active-selected-mode? error)
           [mui-alert {:severity "error" :sx {:mt 1}} error])

         (when (and enabled? active-selected-mode?)
           [mui-stack {:spacing 1 :sx {:mt 2}}
            [mui-typography {:variant "caption" :sx {:color "text.secondary"}}
             (if client-mode?
               (t/lstr-l "sshAgentTransport")
               (t/lstr-l "sshAgentSocketPath"))]
            (if client-mode?
              [mui-typography {:sx {:fontFamily "monospace" :fontSize "0.8rem"
                                    :wordBreak "break-all"}}
               transport]
              [mui-stack {:direction "row" :sx {:alignItems "center" :justify-content "space-between"}}
               [mui-typography {:sx {:fontFamily "monospace" :fontSize "0.8rem"
                                     :wordBreak "break-all" :mr 1}}
                socket-path]
               #_[mui-button {:size "small"
                            :on-click #(bg/write-to-clipboard socket-path)}
                (t/lstr-bl "copy")]])
            [mui-typography {:variant "caption" :sx {:color "text.secondary"}}
             (str (if client-mode?
                    (t/lstr-l "sshAgentKeysAdded")
                    (t/lstr-l "sshAgentKeysServed"))
                  ": " (or key-count 0))]
            [m/mui-divider {:sx {:mt 1 :mb 1}}]
            [mui-typography {:variant "caption" :sx {:color "text.secondary"}}
             (if client-mode?
               (t/lstr-l "sshAgentClientUsageHint")
               (t/lstr-l "sshAgentUsageHint"))]])]]])))

(def ^:private FIREFOX "Firefox")
(def ^:private CHROME "Chrome")
(def ^:private BRAVE "Brave")
(def ^:private CHROME_AND_BRAVE [CHROME BRAVE])
;; (def ^:private EDGE "Edge")

(defn- named-browser-enabled? [browser-name allowed-browsers]
  (boolean (some #(= browser-name %) allowed-browsers)))

;; Used by grouped browser controls, where one checkbox represents multiple
;; browser ids persisted in the same allowed-browsers vector.
(defn- any-named-browser-enabled? [browser-names allowed-browsers]
  (boolean (some #(named-browser-enabled? % allowed-browsers) browser-names)))

(defn- manifest-status-for [browser-name statuses]
  (some #(when (= browser-name (:browser-id %)) %) statuses))

(defn- manifest-status-for-any [browser-names statuses]
  (some #(manifest-status-for % statuses) browser-names))

;; Prefer the first installed/owned status when a grouped control has multiple
;; browser statuses; fall back to any status so missing groups still render.
(defn- active-manifest-status-for-any [browser-names statuses]
  (or (some #(let [status (manifest-status-for % statuses)]
               (when (and status (not= "missing" (:owner status)))
                 status))
            browser-names)
      (manifest-status-for-any browser-names statuses)))

;; Re-labels a concrete browser status for grouped display, for example
;; Chrome/Brave sharing one user-facing row.
(defn- display-status [browser-id status]
  (assoc status :browser-id browser-id))

(defn- browser-checkbox-checked? [browser-name allowed-browsers reconnect-browsers statuses]
  (let [status (manifest-status-for browser-name statuses)]
    (and (named-browser-enabled? browser-name allowed-browsers)
         (or (not= "other-app" (:owner status))
             (named-browser-enabled? browser-name reconnect-browsers)))))

;; Same as browser-checkbox-checked?, but for platforms where Chrome and Brave
;; use one effective native-messaging integration.
(defn- browser-group-checkbox-checked? [browser-names allowed-browsers reconnect-browsers statuses]
  (let [status (manifest-status-for-any browser-names statuses)]
    (and (any-named-browser-enabled? browser-names allowed-browsers)
         (or (not= "other-app" (:owner status))
             (any-named-browser-enabled? browser-names reconnect-browsers)))))

(defn- toggle-browser-enabled [checked? browser-name allowed-browsers]
  (if (not checked?)
    ;; remove the browser from the list
    (vec (remove #(= browser-name %) allowed-browsers))
    ;; add the browser to the list
    (conj (vec allowed-browsers) browser-name)))

;; Adds without duplicating, so grouped toggles can safely preserve existing
;; preferences while inserting every browser id represented by the group.
(defn- add-browser [browsers browser-name]
  (if (named-browser-enabled? browser-name browsers)
    (vec browsers)
    (conj (vec browsers) browser-name)))

;; Expands a grouped checkbox into the concrete browser ids stored by the
;; backend preference model.
(defn- toggle-browser-group-enabled [checked? browser-names allowed-browsers]
  (if (not checked?)
    (vec (remove #(some #{%} browser-names) allowed-browsers))
    (reduce add-browser (vec allowed-browsers) browser-names)))

(defn- path-tail [path]
  (when-not (str/blank? path)
    (let [parts (str/split path #"/")
          tail (take-last 4 parts)]
      (str/join "/" tail))))

(defn- browser-manifest-reconnect-dialog-content [{:keys [status]}]
  (let [{:keys [browser-id installed-proxy-path expected-proxy-path]} status]
    [mui-stack {:spacing 1}
     [mui-typography
      (str browser-id " is currently connected to another OneKeePass installation.")]
     [mui-typography {:sx {:font-size "0.82rem" :word-break "break-all"}}
      (str "Current: " installed-proxy-path)]
     [mui-typography {:sx {:font-size "0.82rem" :word-break "break-all"}}
      (str "This app: " expected-proxy-path)]
     [mui-typography
      "Reconnect this browser to this app?"]]))

(defn- browser-manifest-reconnect-dialog []
  (let [{:keys [dialog-show] :as dialog-data} @(app-settings-events/browser-reconnect-confirm-dialog-data)]
    [mui-dialog {:open (boolean dialog-show)
                 :on-click #(.stopPropagation ^js/Event %)}
     [mui-dialog-title "Reconnect browser extension"]
     [mui-dialog-content {:dividers true :style {:min-height "120px"}}
      [browser-manifest-reconnect-dialog-content dialog-data]]
     [mui-dialog-actions
      [mui-button {:color "secondary"
                   :on-click app-settings-events/browser-reconnect-confirm-dialog-close}
       "Cancel"]
      [mui-button {:color "secondary"
                   :on-click app-settings-events/browser-reconnect-confirmed}
       "Reconnect"]]]))

(defn- manifest-status-text [{:keys [owner installed-proxy-path]}]
  (cond
    (= owner "this-app")
    "Connected to this app"

    (= owner "other-app")
    (str "Connected to another OneKeePass installation"
         (when-not (str/blank? installed-proxy-path)
           (str ": .../" (path-tail installed-proxy-path))))

    :else
    "Not connected"))

(defn- browser-manifest-statuses []
  (let [statuses @(app-settings-events/browser-manifest-statuses)
        chromium-shared? (some #(= @(ce/os-name) %) [const/MACOS const/WINDOWS])
        statuses (if chromium-shared?
                   (keep identity
                         [(manifest-status-for FIREFOX statuses)
                          (some->> statuses
                                   (active-manifest-status-for-any CHROME_AND_BRAVE)
                                   (display-status "Chrome and Brave"))])
                   statuses)]
    (when (seq statuses)
      [mui-stack {:spacing 2 :sx {:alignItems "center"}}
       [mui-box {:sx {:width "80%"}}

        [mui-stack {:spacing 1 :sx {:mt 2}}
         (for [{:keys [browser-id owner] :as status} statuses]
           ^{:key browser-id}
           [mui-alert {:severity (if (= owner "other-app") "warning" "info")
                       :variant "outlined"
                       :sx {:font-size "0.82rem"}}
            (str browser-id ": " (manifest-status-text status))])]]])))

(defn supported-browsers [dialog-data]
  (let [browser-ext-support (get-in dialog-data [:preference-data :browser-ext-support])
        {:keys [allowed-browsers reconnect-browsers extension-use-enabled]} browser-ext-support
        manifest-statuses @(app-settings-events/browser-manifest-statuses)
        chromium-shared? (some #(= @(ce/os-name) %) [const/MACOS const/WINDOWS])
        browser-checkbox (fn [browser-name]
                           (let [status (manifest-status-for browser-name manifest-statuses)]
                             [mui-form-control-label
                              {:control (r/as-element
                                         [mui-checkbox
                                          {:disabled (not extension-use-enabled)
                                           :checked (browser-checkbox-checked?
                                                     browser-name
                                                     allowed-browsers
                                                     reconnect-browsers
                                                     manifest-statuses)
                                           :on-change
                                           (fn [^js/CheckedEvent e]
                                             (let [checked? (-> e .-target .-checked)]
                                               (cond
                                                 (and checked? (= "other-app" (:owner status)))
                                                 (app-settings-events/browser-reconnect-confirm-dialog-show status)

                                                 (not= "other-app" (:owner status))
                                                 (app-settings-events/field-update
                                                 [:preference-data :browser-ext-support :allowed-browsers]
                                                  (toggle-browser-enabled checked? browser-name allowed-browsers)))))}])
                               :label browser-name}]))
        browser-group-checkbox (fn [browser-names label]
                                 (let [status (active-manifest-status-for-any browser-names manifest-statuses)]
                                   [mui-form-control-label
                                    {:control (r/as-element
                                               [mui-checkbox
                                                {:disabled (not extension-use-enabled)
                                                 :checked (browser-group-checkbox-checked?
                                                           browser-names
                                                           allowed-browsers
                                                           reconnect-browsers
                                                           manifest-statuses)
                                                 :on-change
                                                 (fn [^js/CheckedEvent e]
                                                   (let [checked? (-> e .-target .-checked)]
                                                     (cond
                                                       (and checked? (= "other-app" (:owner status)))
                                                       (app-settings-events/browser-reconnect-confirm-dialog-show status)

                                                       (not= "other-app" (:owner status))
                                                       (app-settings-events/field-update
                                                        [:preference-data :browser-ext-support :allowed-browsers]
                                                        (toggle-browser-group-enabled checked? browser-names allowed-browsers)))))}])
                                     :label label}]))]
    [mui-stack
     [mui-stack {:sx {:pt 1 :pb 1}}
      [mui-typography {:text-align "center" :sx {:color (theme-color @custom-theme-atom :info-main)}}
       (tr-t "supportedBrowsers")]]

     [mui-stack {:spacing 2 :sx {:alignItems "center"}}
      [mui-box {:sx {:width "80%"}}
       [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
        [browser-checkbox FIREFOX]
        (if chromium-shared?
          [browser-group-checkbox CHROME_AND_BRAVE "Chrome and Brave"]
          [:<>
           [browser-checkbox CHROME]
           [browser-checkbox BRAVE]])]]]

     [browser-manifest-statuses]]))


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
   [browser-manifest-reconnect-dialog]

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

        :file-management
        [file-management dialog-data]

        :browser-integration
        [mui-stack
         [browser-integration dialog-data]
         [m/mui-divider {:sx {:mt 1 :mb 1}}]
         [supported-browsers dialog-data]]

        :ssh-agent
        [ssh-agent-panel dialog-data]


        ;;IMPORATNT:
        ;; We need this as dialog-data may nil and hence panel when first time
        ;; [app-settings-dialog] is called. Otherwise we will see
        ;; Error: No matching clause and app UI will fail
        [:div])]]]

   [mui-dialog-actions
    [mui-button {:variant "contained" :color "secondary"
                 ;;:disabled in-progress?
                 :on-click app-settings-events/app-settings-dialog-close} (t/lstr-bl 'cancel)]
    [mui-button {:variant "contained" :color "secondary"
                 :disabled (or (not @(app-settings-events/app-settings-modified)) (-> error-fields seq boolean))
                 ;;:disabled (or (not modified) in-progress? (-> error-fields seq boolean))
                 :on-click app-settings-events/app-settings-save} (t/lstr-bl 'ok)]]])

(defn app-settings-dialog-main []
  [app-settings-dialog @(app-settings-events/app-settings-dialog-data)])
