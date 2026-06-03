(ns onekeepass.frontend.remote-storage
  "Single multi-step MUI dialog for the desktop remote-storage flow.
   Steps:
     :source-pick — lists saved SFTP/WebDAV kdbx-source connections plus an
                    'Enter new connection' link
     :form        — ad-hoc connection form (SFTP or WebDAV based on the
                    selected tab)
     :browse      — server file/folder listing; user picks a kdbx file (open
                    mode) or a parent folder (create mode)"
  (:require
   [reagent.core :as r]
   [clojure.string :as str]
   [onekeepass.frontend.db-icons :refer [render-entry-icon]]
   [onekeepass.frontend.events.remote-storage :as rs-events]
   [onekeepass.frontend.translation :as t
    :refer [lstr-l lstr-bl lstr-dlg-title]]
   [onekeepass.frontend.mui-components :as m
    :refer [mui-alert
            mui-avatar
            mui-box
            mui-button
            mui-dialog
            mui-dialog-actions
            mui-dialog-content
            mui-dialog-title
            mui-divider
            mui-form-control-label
            mui-icon-button
            mui-icon-arrow-upward-outlined
            mui-icon-chevron-right
            mui-icon-close-outlined
            mui-icon-folder-outlined
            mui-icon-launch
            mui-linear-progress
            mui-link
            mui-list
            mui-list-item
            mui-list-item-avatar
            mui-list-item-button
            mui-list-item-icon
            mui-list-item-text
            mui-list-subheader
            mui-stack
            mui-switch
            mui-tab
            mui-tabs
            mui-typography]]))

(set! *warn-on-infer* true)

;; ---- step: source-pick ----

(defn- type-tabs [current-type]
  [mui-tabs {:value (name current-type)
             :on-change (fn [_ev v]
                          (rs-events/type-selected (keyword v)))
             :sx {:mb 1}}
   [mui-tab {:value "sftp" :label "SFTP"}]
   [mui-tab {:value "webdav" :label "WebDAV"}]])

(defn- connection-type-label [current-type]
  (case current-type
    :sftp "SFTP"
    :webdav "WebDav"))

(defn- connection-row [db-key
                       {:keys [connection-id title connection-info
                               icon-id custom-icon-uuid]}]
  ^{:key connection-id}
  [mui-list-item-button
   {:on-click #(rs-events/connect-by-id-start connection-id)
    :sx {:border-radius 1}}
   [mui-list-item-avatar
    [mui-avatar [render-entry-icon {:db-key db-key
                                    :icon-id icon-id
                                    :custom-icon-uuid custom-icon-uuid}]]]
   [mui-list-item-text {:primary (or title connection-id)
                        :secondary connection-info}]
   [mui-icon-launch {:sx {:color "action.active" :ml 1}}]])

(defn- db-group [{:keys [db-key db-name summaries]}]
  ^{:key db-key}
  [:li
   [:ul {:style {:padding 0}}
    [mui-list-subheader
     {:sx {:background-color "rgba(25, 118, 210, 0.08)"}}
     [mui-typography {:variant "subtitle1"} db-name]]
    (doall (for [s summaries] (connection-row db-key s)))]])

(defn- no-saved-connections-content [current-type]
  [mui-stack {:spacing 1.5
              :alignItems "center"
              :justifyContent "center"
              :sx {:min-height "180px"
                   :py 4
                   :px 3
                   :text-align "center"
                   :border "1px solid"
                   :border-color "divider"
                   :border-radius 1
                   :bgcolor "action.hover"}}
   [mui-avatar {:variant "rounded"
                :sx {:width 56
                     :height 56
                     :bgcolor "action.hover"
                     :color "text.secondary"}}
    [mui-icon-folder-outlined]]
   [mui-typography {:variant "body1"
                    :sx {:color "text.secondary"
                         :max-width "560px"
                         :line-height 1.6}}
    (lstr-l "rsNoSavedConnections"
            {:ent-type (connection-type-label current-type)})]])

(defn- saved-connections-content [groups]
  [mui-box {:sx {:border "1px solid"
                 :border-color "divider"
                 :border-radius 1
                 :bgcolor "action.hover"}}
   [mui-list {:dense true
              :sx {:max-height "360px"
                   :overflow "auto"
                   :bgcolor "transparent"
                   "& ul" {:padding 0}}
              :subheader (r/as-element [:li])}
    (doall (for [g groups] (db-group g)))]])

(defn- enter-new-connection-link []
  [mui-box {:sx {:display "flex"
                 :justifyContent "center"
                 :pt 1}}
   [mui-link {:component "button"
              :type "button"
              :variant "subtitle2"
              :sx {:cursor "pointer"
                   :font-weight 600
                   :text-align "center"
                   :py 1
                   :px 1.5
                   :border-radius 1}
              :on-click rs-events/enter-ad-hoc-form}
    (lstr-l "rsEnterNewConnection")]])

(defn- source-pick-content [current-type]
  (let [groups @(rs-events/grouped-kdbx-source-connections current-type)
        any? (seq groups)]
    [mui-stack {:spacing 1}
     [type-tabs current-type]
     (when any?
       [mui-typography {:variant "body2" :sx {:color "text.secondary"}}
        (lstr-l "rsPickConnectionHelp")])
     (if any?
       [saved-connections-content groups]
       [no-saved-connections-content current-type])
     [mui-divider]
     [enter-new-connection-link]]))

;; ---- step: form ----

(defn- text-row [{:keys [label value field type-attr placeholder error-text]}]
  [mui-box {:sx {:width "100%"}}
   [m/text-field {:label label
                  :value (or value "")
                  :placeholder placeholder
                  :error (boolean (seq error-text))
                  :helperText error-text
                  :type (or type-attr "text")
                  :on-change (fn [^js/Event e]
                               (rs-events/form-data-update field (.. e -target -value)))
                  :variant "standard"
                  :fullWidth true}]])

(defn- sftp-form []
  (let [data @(rs-events/form-data :sftp)
        errors @(rs-events/form-errors :sftp)
        pk-path (:private-key-full-file-name data)]
    [mui-stack {:spacing 2}
     [text-row {:label (lstr-l "name") :value (:name data) :field :name
                :error-text (:name errors)}]
     [text-row {:label (lstr-l "host") :value (:host data) :field :host
                :placeholder "host.example.com" :error-text (:host errors)}]
     [text-row {:label (lstr-l "port") :value (:port data) :field :port
                :type-attr "number" :error-text (:port errors)}]
     [text-row {:label (lstr-l "userName") :value (:user-name data) :field :user-name
                :error-text (:user-name errors)}]
     [text-row {:label (lstr-l "password") :value (:password data) :field :password
                :type-attr "password" :error-text (:password errors)}]
     [mui-stack {:direction "row" :spacing 1 :alignItems "flex-end"}
      [mui-box {:sx {:flexGrow 1}}
       [m/text-field {:label (lstr-l "rsPrivateKeyFile")
                      :value (or pk-path "")
                      :variant "standard"
                      :InputProps {:readOnly true}
                      :fullWidth true}]]
      [mui-button {:on-click rs-events/pick-private-key
                   :size "small"}
       (lstr-bl 'browse)]
      [mui-button {:on-click rs-events/clear-private-key
                   :size "small"
                   :disabled (not pk-path)}
       (lstr-bl 'clear)]]
     [text-row {:label (lstr-l "startDir") :value (:start-dir data) :field :start-dir}]]))

(defn- webdav-form []
  (let [data @(rs-events/form-data :webdav)
        errors @(rs-events/form-errors :webdav)]
    [mui-stack {:spacing 2}
     [text-row {:label (lstr-l "name") :value (:name data) :field :name
                :error-text (:name errors)}]
     [text-row {:label (lstr-l "rootUrl") :value (:root-url data) :field :root-url
                :placeholder "https://host.example.com/dav"
                :error-text (:root-url errors)}]
     [text-row {:label (lstr-l "userName") :value (:user-name data) :field :user-name
                :error-text (:user-name errors)}]
     [text-row {:label (lstr-l "password") :value (:password data) :field :password
                :type-attr "password" :error-text (:password errors)}]
     [mui-form-control-label
      {:control (r/as-element
                 [mui-switch
                  {:checked (boolean (:allow-untrusted-cert data))
                   :on-change (fn [^js/Event e]
                                (rs-events/form-data-update
                                 :allow-untrusted-cert (.. e -target -checked)))}])
       :label (lstr-l "rsAllowUntrustedCert")}]]))

(defn- form-content [current-type]
  [mui-stack {:spacing 1}
   [type-tabs current-type]
   [mui-typography {:variant "body2" :sx {:color "warning.main"}}
    (lstr-l "rsAdhocConnectionWarning")]
   (if (= current-type :sftp) [sftp-form] [webdav-form])])

;; ---- step: browse ----

(defn- breadcrumb-path [stack]
  (let [last-pd (some-> stack last :parent-dir)]
    (or last-pd "/")))

(defn- browse-content [{:keys [listing mode]}]
  (let [{:keys [stack]} listing
        {:keys [parent-dir sub-dirs files]} (last stack)
        can-go-up? (> (count stack) 1)]
    [mui-stack {:spacing 1}
     [mui-stack {:direction "row" :alignItems "center" :spacing 1}
      [mui-icon-button {:disabled (not can-go-up?)
                        :on-click rs-events/listing-previous
                        :size "small"}
       [mui-icon-arrow-upward-outlined]]
      [mui-typography {:variant "body2"
                       :sx {:font-family "monospace"
                            :white-space "nowrap"
                            :overflow "hidden"
                            :text-overflow "ellipsis"}}
       (breadcrumb-path stack)]]
     [mui-divider]
     [mui-list {:dense true :sx {:max-height "400px" :overflow "auto"}}
      (doall
       (for [d sub-dirs]
         ^{:key (str "d-" d)}
         [mui-list-item-button
          {:on-click #(rs-events/list-sub-dir parent-dir d)}
          [mui-list-item-icon [mui-icon-folder-outlined]]
          [mui-list-item-text {:primary d}]
          [mui-icon-chevron-right]]))
      (doall
       (for [f files
             :let [is-kdbx? (str/ends-with? (str/lower-case f) ".kdbx")
                   clickable? (and (= mode :open) is-kdbx?)]]
         ^{:key (str "f-" f)}
         [mui-list-item-button
          {:disabled (not clickable?)
           :on-click (when clickable?
                       #(rs-events/file-picked-for-open parent-dir f))}
          [mui-list-item-icon [mui-icon-launch]]
          [mui-list-item-text {:primary f}]]))
      (when (and (empty? sub-dirs) (empty? files))
        [mui-list-item
         [mui-list-item-text {:primary (lstr-l "rsEmptyDir")}]])]]))

;; ---- top-level dialog ----

(defn- dialog-title-for-step [step mode]
  (case step
    :source-pick (if (= mode :create)
                   (lstr-dlg-title "rsCreateOnRemote")
                   (lstr-dlg-title "rsOpenFromRemote"))
    :form        (lstr-dlg-title "rsEnterConnection")
    :browse      (lstr-dlg-title "rsBrowse")
    ;; Render-time guard: dialog-data is nil before any ::dialog-show event
    ;; has fired. Reagent still evaluates this call when building the closed
    ;; dialog's hiccup, so case must have a no-step default.
    ""))

(defn- footer-actions [{:keys [step mode listing status direct-open?]}]
  (let [in-progress? (= status :in-progress)
        cur (some-> listing :stack last)
        parent-dir (:parent-dir cur)]
    (case step
      :source-pick
      [mui-button {:on-click rs-events/cancel-dialog :color "secondary"}
       (lstr-bl 'cancel)]

      :form
      [:<>
       [mui-button {:on-click rs-events/back-step :color "secondary"
                    :disabled in-progress?}
        (lstr-bl 'back)]
       [mui-button {:on-click rs-events/cancel-dialog :color "secondary"
                    :disabled in-progress?}
        (lstr-bl 'cancel)]
       [mui-button {:on-click rs-events/connect-ad-hoc-start
                    :color "primary" :variant "contained"
                    :disabled in-progress?}
        (lstr-bl 'connect)]]

      :browse
      [:<>
       (when-not direct-open?
         [mui-button {:sx {:margin-top "20px"} :on-click rs-events/back-step :color "secondary"}
          (lstr-bl 'back)])
       [mui-button {:sx {:margin-top "20px"} :on-click rs-events/cancel-dialog :color "secondary"}
        (lstr-bl 'cancel)]
       (when (= mode :create)
         (let [file-name @(rs-events/new-db-file-name)]
           [mui-stack {:direction "row" :spacing 1 :alignItems "center"}
            [m/text-field {:label (lstr-l "fileName")
                           :value (or file-name "")
                           :variant "standard"
                           :disabled in-progress?
                           :on-change (fn [^js/Event e]
                                        (rs-events/new-db-file-name-update
                                         (.. e -target -value)))}]
            [mui-button {:style {:margin-top "20px"} :on-click #(rs-events/folder-picked-for-new-db
                                     parent-dir file-name)
                         :color "primary" :variant "contained"
                         :disabled (or in-progress? (str/blank? file-name))}
             (lstr-bl 'create)]]))]

      ;; Render-time guard for the initial nil state (see dialog-title-for-step).
      nil)))

(defn remote-storage-dialog []
  (let [{:keys [dialog-show step mode current-type listing
                api-error-text status] :as state}
        @(rs-events/dialog-data)
        in-progress? (= status :in-progress)]
    ;; Don't build any dialog hiccup until ::dialog-show has populated the
    ;; state for the first time. Reagent evaluates function calls inside
    ;; hiccup args eagerly (e.g. dialog-title-for-step), so a nil :step on
    ;; the very first render of the start page would otherwise blow up the
    ;; whole UI tree.
    (when dialog-show
      [mui-dialog {:open true
                   :dir (t/dir)
                   :on-click #(.stopPropagation ^js/Event %)
                   :maxWidth "sm"
                   :fullWidth true
                   :sx {"& .MuiDialog-paper" {:width "85%"}}}
       [mui-dialog-title
        [mui-stack {:direction "row" :justifyContent "space-between" :alignItems "center"}
         [mui-typography {:variant "h6"} (dialog-title-for-step step mode)]
         [mui-icon-button {:on-click rs-events/cancel-dialog :size "small"}
          [mui-icon-close-outlined]]]]
       [mui-dialog-content {:dividers true}
        (case step
          :source-pick [source-pick-content current-type]
          :form        [form-content current-type]
          :browse      [browse-content {:listing listing :mode mode}]
          nil)
        (when in-progress?
          [mui-linear-progress {:sx {:mt 2}}])
        (when api-error-text
          [mui-alert {:severity "error" :sx {:mt 2}} api-error-text])]
       [mui-dialog-actions
        [footer-actions state]]])))

(defn remote-storage-dialog-main []
  [remote-storage-dialog])
