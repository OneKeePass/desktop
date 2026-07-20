(ns onekeepass.frontend.entry-form.fields
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.common-components :as cc :refer [theme-text-field-sx]]
   [onekeepass.frontend.constants :as const :refer [OTP PASSWORD URL]]
   [onekeepass.frontend.entry-form.common :as ef-cmn]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]
   [onekeepass.frontend.events.entry-form-ex :as form-events]
   [onekeepass.frontend.events.remote-storage :as rs-events]
   [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom mui-box
                                                     mui-circular-progress
                                                     mui-date-time-picker
                                                     mui-desktop-date-picker
                                                     mui-form-control-label
                                                     mui-icon-autorenew
                                                     mui-icon-button
                                                     mui-icon-delete-outline
                                                     mui-icon-visibility
                                                     mui-icon-visibility-off
                                                     mui-input-adornment
                                                     mui-link
                                                     mui-localization-provider
                                                     mui-menu-item mui-stack
                                                     mui-switch
                                                     mui-text-field
                                                     mui-tooltip
                                                     mui-typography]]
   [onekeepass.frontend.translation :as t :refer [lstr-l lstr-l-cv] :refer-macros [tr-h
                                                                                   tr-l
                                                                                   tr-l-cv
                                                                                   tr-entry-field-name-cv]]
   [reagent.core :as r]))

(defn- to-value
  "Gets the value to be shown in the field
   based on whether it is in edit mode or read mode"
  [{:keys [value read-value edit]}]
  (cond
    edit
    value

    (and (not edit) (not (nil? read-value)))
    read-value

    :else
    value))

(defn translated-label [{:keys [key field-name standard-field]}]
  (cond
    (not (nil? field-name))
    ;; It is assumed translation is done already
    field-name

    standard-field
    (tr-entry-field-name-cv key)

    :else
    ;; It is assumed translation is done already
    key))

(defn- helper-or-error-text
  "Gets the helper text to be shown in the field"
  [{:keys [helper-text error-text password-score]}]
  (cond
    (and (nil? error-text) (not (nil? password-score)))
    (-> password-score :name lstr-l-cv)

    (nil? error-text)
    helper-text

    :else
    error-text))

(defn- html-input-props
  [{:keys [edit protected]}]
  (cond-> {:readOnly (not edit)}
    protected (assoc :data-okp-sensitive-copy "true")))

(defn- end-icons [{:keys [key protected visible edit] :as kv}]
  (let [val (to-value kv)
        entry-type-uuid @(form-events/entry-form-data-fields :entry-type-uuid)
        entry-uuid @(form-events/entry-form-data-fields :uuid)
        ;; Read-mode launch of the remote Storage Browser from a connection
        ;; entry's connection field: Host for SFTP, URL for WebDAV.
        rs-conn-launch? (and (not edit)
                             (or (and (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_SFTP)
                                      (= key const/HOST))
                                 (and (= entry-type-uuid const/UUID_OF_ENTRY_TYPE_REMOTE_CONNECTION_WEBDAV)
                                      (= key URL))))]
    [:<>
     (when protected
       (if visible
         [mui-icon-button {:sx {:margin-right "-8px"}
                           :edge "end"
                           :on-click #(form-events/entry-form-field-visibility-toggle key)}
          [mui-icon-visibility]]
         [mui-icon-button {:sx {:margin-right "-8px"}
                           :edge "end"
                           :on-click #(form-events/entry-form-field-visibility-toggle key)}
          [mui-icon-visibility-off]]))
     ;; Launch the remote Storage Browser using this connection entry
     (when rs-conn-launch?
       [mui-icon-button {:sx {:margin-right "-8px"}
                         :edge "end"
                         :on-click #(rs-events/open-entry-remote entry-type-uuid entry-uuid)}
        [m/mui-icon-launch]])
     ;; Open with the url (suppressed for a WebDAV connection entry's URL field,
     ;; which shows the storage-browser launch above instead)
     (when (and (not edit) (= key URL) (not rs-conn-launch?))
       [mui-icon-button {:sx {:margin-right "-8px"}
                         :edge "end"
                         :on-click #(form-events/entry-form-open-url val)}
        [m/mui-icon-launch]])
     ;; Password generator 
     (when (and edit protected (= key PASSWORD))
       [mui-icon-button {:sx {:margin-right "-8px"}
                         :edge "end"
                         :on-click form-events/password-generator-show}
        [mui-icon-autorenew]])
     ;; Copy 
     [(if protected
        (cc/copy-icon-factory #(cmn-events/write-sensitive-to-clipboard val))
        (cc/copy-icon-factory))
      val
      {:sx {:mr "-1px"}}]]))

(defn- single-line-end-adornment
  "InputAdornment that wraps the trailing action icons for single-line fields
   (text-field, single-line-text-field, otp-read-field).

   A standard MUI Input has no right padding, so its content box ends right at
   the field border. The last icon uses edge=\"end\", which pushes its rounded
   hover/press highlight onto - and past - that border. The right margin here
   pushes the whole icon cluster inward so the highlight stays inside the field
   in edit mode. This is most visible when the field shows multiple icons.

   The multiline fields don't need this: they position their adornment
   absolute at right:5px, which already gives the same clearance."
  [icons-kv]
  [mui-input-adornment {:position "end" :sx {:mr "2px"}}
   [end-icons icons-kv]])

(defn simple-selection-field [{:keys [key
                                      value
                                      edit
                                      error-text
                                      helper-text
                                      on-change-handler
                                      select-field-options]}]
  ;; We are using the mui-text-field directly as select component 
  ;; This type of simple select list can also be done using the following components 
  ;; as given in the examples found mui.com which uses now this method
  ;; [mui-form-control [mui-input-label] [mui-select {} [mui-menu-item]] [mui-form-helper-text]  ]
  [mui-text-field {:id key
                   :sx {:margin-top cc/entry-cnt-field-margin-top}
                   :required false
                   ;;:classes {:root "entry-cnt-field"}
                   :select true
                   :label (tr-entry-field-name-cv key)
                   :value value
                   :on-change on-change-handler
                   :error  (not (nil? error-text))
                   :helperText (if (nil? error-text) helper-text error-text)
                   :slotProps {:htmlInput {:readOnly (not edit)}}
                   :variant "standard" :fullWidth true}
   (doall (for [y select-field-options]
            ^{:key y} [mui-menu-item {:value y} y]))])

;; Added field-name and read-value 
;; as additional fields to get the label and value
;; See fn to-value how final value is determined using value and read-value

(defn text-field [{:keys [key
                          _field-name
                          _read-value
                          protected
                          visible
                          edit
                          on-change-handler
                          disabled
                          password-score
                          placeholder
                          helper-text
                          error-text
                          _standard-field
                          no-end-icons]
                   :or {visible true
                        edit false
                        no-end-icons false
                        protected false
                        disabled false
                        on-change-handler #(println "No on change handler yet registered for the key")}
                   :as kv}]

  ;;:margin-top "16px" here is equivalent to the one used by "entry-cnt-field"
  (let [label (translated-label kv)
        val  (to-value kv)]
    [m/text-field {:sx   (merge {:margin-top "16px"} (cc/password-helper-text-sx (:name password-score)))
                   :fullWidth true
                   :label label
                   :variant "standard"
                   :value val
                   :placeholder placeholder
                   :error  (not (nil? error-text))
                   :helperText (helper-or-error-text kv)
                   :onChange  on-change-handler
                   :required false
                   :disabled disabled
                   :slotProps {:input {:id key
                                      :sx (theme-text-field-sx edit @custom-theme-atom)
                                      :endAdornment (if no-end-icons nil
                                                        (r/as-element
                                                         [single-line-end-adornment kv]))
                                      :type (if (or (not protected) visible) "text" "password")}
                              :htmlInput (html-input-props kv)}}]))

;; Both works
;;"& .MuiInputBase-input" 
;; "& .MuiInput-input"
(def otp-txt-input-sx
  {"& .MuiInputBase-input"
   {:color "green"
    :font-size "1.75em"
    :font-weight "300"  ;; To make it bold
    }
   :margin-top cc/entry-cnt-field-margin-top})

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
  [_m]
  (fn [{:keys [key
               visible
               error-text
               no-end-icons]
        :or {visible true
             no-end-icons false}
        :as kv}]

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
                        ;;:classes {:root "entry-cnt-field"}
                        :value (formatted-token token)
                        ;; Is there any use of suing 'error-text' with otp field ? 
                        :error  (or (not (nil? error-text)) (not valid-token-found))
                        :helperText (if-not valid-token-found (tr-h "invalidOtpUrl") error-text)
                        :required false
                        :slotProps {:input {:id key
                                           :sx (theme-text-field-sx edit @custom-theme-atom)
                                           :endAdornment (if (or (not valid-token-found) no-end-icons) nil
                                                             (r/as-element
                                                              [single-line-end-adornment (assoc kv
                                                                                                :value token
                                                                                                :read-value nil
                                                                                                :protected false)]))
                                           :type "text"}
                                   :htmlInput {:readOnly true}}}]

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
    :or {on-change-handler #()}}]
  [mui-localization-provider {:dateAdapter m/adapter-date-fns}
   [mui-date-time-picker {:label (tr-l-cv key)
                          ;; value should be of Date type (type value) => #object[Date]
                          ;; and it is in UTC. The view side shown in local time 
                          :value value
                          :onChange on-change-handler
                          :slotProps {:textField {:variant "standard"
                                                  :sx {:margin-top cc/entry-cnt-field-margin-top}
                                                  :fullWidth true}}}]])

;; A date-only picker for entry-type fields whose data-type is Date (core FieldDataType::Date).
;; The stored value is always a 'yyyy-MM-dd' string (locale-independent, sortable). This is used
;; only in edit mode; in read mode the field falls through to the plain text field, showing the
;; same 'yyyy-MM-dd' string like any other field.
(defn date-field
  [{:keys [key value error-text helper-text on-change-handler]
    :or {on-change-handler #()}
    :as kv}]
  (let [label (translated-label kv)
        ;; value is a 'yyyy-MM-dd' string; parse to a Date for the picker (nil when blank)
        date-val (when-not (str/blank? value)
                   (.parse m/date-fns-utils value "yyyy-MM-dd"))]
    [mui-localization-provider {:dateAdapter m/adapter-date-fns}
     [mui-desktop-date-picker
      {:label label
       :value (or date-val nil)
       :format "yyyy-MM-dd"
       ;; onChange gives a Date (or nil when cleared); store back as a 'yyyy-MM-dd' string
       :onChange (fn [^js/Date d]
                   (on-change-handler
                    (if (and d (not (js/isNaN (.getTime d))))
                      (.formatByString m/date-fns-utils d "yyyy-MM-dd")
                      "")))
       :slotProps {:textField
                   {:variant "standard"
                    :error (not (nil? error-text))
                    :helperText (if (nil? error-text) helper-text error-text)
                    :fullWidth true
                    :InputProps {:disableUnderline true}
                    ;; Give the picker the same bordered look as the surrounding standard text
                    ;; fields in edit mode. This mirrors the edit branch of
                    ;; common-components/theme-text-field-sx, applied to the picker's inner Input
                    ;; root via a descendant selector so the calendar button is left untouched.
                    ;; MUI-X v8 renders PickersTextField, whose standard picker Input needs
                    ;; InputProps.disableUnderline to suppress its own underline pseudo-elements.
                    :sx {:margin-top cc/entry-cnt-field-margin-top
                         "& .MuiPickersInputBase-root"
                         {:border "1px solid grey"
                          :outline "1px solid transparent"}

                         "& .MuiPickersInputBase-root.Mui-focused"
                         {:border "1px solid"
                          :border-color "primary.main"
                          :outline "1px solid"
                          :outline-color "primary.main"}}}}}]]))

(defn text-area-field [{:keys [key value edit on-change-handler]
                        :or {edit false
                             on-change-handler #()}}]
  [m/text-field {;;:classes {:root "entry-cnt-field"}
                 :sx {:margin-top "4px"}
                 :fullWidth true
                 :id key
                 :label "";;name
                 :variant "standard"
                 :value value
                 :onChange on-change-handler
                 :multiline true
                 :rows 4
                 ;;minRows and maxRows should not be used with the fixed 'm/text-field'
                 ;;:minRows 3 
                 ;;:maxRows 10 
                 :slotProps {:inputLabel {:shrink true}
                             :input {:id key}
                             :htmlInput {:readOnly (not edit)
                                         :sx {:ml ".5em" :mr ".5em"}
                                         :style {:resize "vertical"}}}}])

(defn- single-line-text-field
  "A single line text field"
  [{:keys [key
           label
           val
           protected
           visible
           edit
           on-change-handler
           disabled
           password-score
           placeholder
           helper-text
           error-text
           no-end-icons]
    :or {visible true
         edit false
         no-end-icons false
         protected false
         disabled false
         on-change-handler #(println "No on change handler yet registered for the key")}
    :as kv}]
  [m/text-field {:sx   (merge {:margin-top "16px"} (cc/password-helper-text-sx (:name password-score)))
                 :fullWidth true
                 :label label
                 :variant "standard"
                 :value val
                 :placeholder placeholder
                 :error  (not (nil? error-text))
                 :helperText helper-text
                 :onChange  (fn [e]
                              ;; IMPORTANT: We need to have a valid id is set for the input element
                              ;; so that getElementById works
                              ;; We are setting id in InputProps below
                              ;; We are checking here whether the text is overflowing the field width
                              ;; and if so, we set the multiline flag for this field to true
                              ;; This will cause the field to be rendered as a text area field
                              ;; We are using the 'inputRef' below to check the scrollWidth and clientWidth
                              ;; of the input element when it is rendered
                              ;; We cannot use the ref callback here as it is called only once when the component is
                              (let [fld (js/document.getElementById key)]
                                (if  (> (.-scrollWidth fld) (.-clientWidth fld))
                                  (form-events/entry-form-multiline-field-toggle key true)
                                  (form-events/entry-form-multiline-field-toggle key false)))
                              (on-change-handler e))
                 :required false
                 :disabled disabled
                 :inputRef (fn [ref-val]
                             (when ref-val
                               #_(js/console.log "scrollWidth: " (.-scrollWidth ref-val) " clientWidth: " (.-clientWidth ref-val) "for ref-val"  ref-val)
                               (if  (> (.-scrollWidth ref-val) (.-clientWidth ref-val))
                                 (form-events/entry-form-multiline-field-toggle key true)
                                 (form-events/entry-form-multiline-field-toggle key false))))
                 :slotProps {:input {:id key
                                    :sx (theme-text-field-sx edit @custom-theme-atom)
                                    :endAdornment (if no-end-icons nil
                                                      (r/as-element
                                                       [single-line-end-adornment kv]))
                                    :type (if (or (not protected) visible) "text" "password")}
                             :htmlInput (html-input-props kv)}}])



(defn- multiline-text-field
  "A text area field with label on top"
  [{:keys [key
           label
           val
           helper-text
           error-text
           password-score
           no-end-icons
           edit on-change-handler
           protected visible]
    :or {edit false
         no-end-icons false
         on-change-handler #()
         protected false
         visible true}
    :as kv}]
  [m/text-field {:sx (merge {:margin-top "16px"} (cc/password-helper-text-sx (:name password-score)))
                 :fullWidth true
                 :id key
                 ;; Label is already translated
                 :label label
                 :variant "standard"
                 :value val
                 :error  (not (nil? error-text))
                 :helperText helper-text
                 :onChange on-change-handler
                 :multiline true
                 :rows (if (= key const/ADDITIONAL_URLS) 4 2)
                 ;;minRows and maxRows should not be used with the fixed 'm/text-field'
                 ;;:minRows 3 
                 ;;:maxRows 10 
                 :slotProps {:inputLabel {:shrink true}
                             :input {:id key
                                     :endAdornment (if no-end-icons nil
                                                       (r/as-element
                                                        ;; Need to position the input adornment 'absolute' to override the default position style of mui
                                                        ;; so that it is aligned properly when there are multiple lines and not pusing the text area to left
                                                        [mui-input-adornment
                                                         {:sx {:position "absolute"
                                                               :right "5px"}
                                                          :position "end"}
                                                         [end-icons kv]]))}
                             :htmlInput {:readOnly (not edit)
                                         :sx {:ml ".5em" :mr ".5em"}
                                         ;; {:WebkitTextSecurity "disc", :resize "vertical"} or {:resize "vertical"}
                                         :style (cond-> {:resize "vertical"}
                                                  (and protected (not visible))
                                                  (assoc :WebkitTextSecurity "disc"))}}}])


(defn ssh-key-multiline-field
  "A dedicated multi-row text area for the SSH Key entry type's 'Private Key' and
   'Public Key' fields. Unlike the generic field, the action icons sit in a row
   above the text area (instead of overlapping the text as an end adornment), and
   the field is always a tall, monospaced text area with room for a full key:
     - load-from-file (folder) — edit mode only, reads a key file into the field
     - visibility toggle       — protected fields only (Private Key)
     - copy"
  [{:keys [key protected visible edit section-name error-text on-change-handler] :as kv}]
  (let [label (translated-label kv)
        val (to-value kv)
        helper-text (helper-or-error-text kv)]
    [mui-stack {:sx {:margin-top "16px" :width "100%"}}
     ;; Action row above the text area
     [mui-stack {:direction "row"
                 :sx {:width "100%" :align-items "center" :justify-content "space-between"}}
      [mui-typography {:variant "caption" :sx {:color "text.secondary"}} label]
      [mui-stack {:direction "row" :sx {:align-items "center"}}
       ;; Load the field content from a key file (edit mode only)
       (when edit
         [mui-tooltip {:title (lstr-l 'loadFromFile) :enterDelay 2500}
          [mui-icon-button {:edge "end"
                            :on-click #(form-events/load-section-field-from-file section-name key)}
           [m/mui-icon-folder-outlined]]])
       ;; Visibility toggle for the protected Private Key field
       (when protected
         [mui-icon-button {:edge "end"
                           :on-click #(form-events/entry-form-field-visibility-toggle key)}
          (if visible [mui-icon-visibility] [mui-icon-visibility-off])])
       ;; Copy
       [(if protected
          (cc/copy-icon-factory #(cmn-events/write-sensitive-to-clipboard val))
          (cc/copy-icon-factory))
        val]]]
     [m/text-field {:fullWidth true
                    :id key
                    :variant "standard"
                    :value val
                    :error (not (nil? error-text))
                    :helperText helper-text
                    :onChange on-change-handler
                    :multiline true
                    ;; :rows 8
                    :rows 4
                    :slotProps {:input {:id key
                                        :sx (theme-text-field-sx edit @custom-theme-atom)}
                                :htmlInput {:readOnly (not edit)
                                            :sx {:ml ".5em" :mr ".5em"}
                                            :style (cond-> {:resize "vertical"
                                                            :font-family "monospace"}
                                                     (and protected (not visible))
                                                     (assoc :WebkitTextSecurity "disc"))}}}]]))

(defn single-or-multiline-text-field
  "Decides whether to use text-field or text-area-field based on the
   length of the value."
  [{:keys [key
           _field-name
           _error-text
           _helper-text
           _password-score
           _standard-field]
    :as kv}]
  (let [label (translated-label kv)
        val  (to-value kv)

        helper-text  (helper-or-error-text kv)

        multiline-field @(form-events/multiline? key)]
    ;; (println "Helper text: " helper-text " for key: " key)
    (if-not multiline-field
      [single-line-text-field (assoc kv :label label :val val :helper-text helper-text)]
      [multiline-text-field (assoc kv :label label :val val :helper-text helper-text)])))

(defn- bool-field-border-sx
  "Border styling for the boolean field's container so it matches the text fields:
   a rectangular border in edit mode and a single bottom line in read mode (theme-aware).
   Mirrors common-components/theme-text-field-sx, which can't be reused directly here
   because it targets the MuiInput-root that the Switch row does not have."
  [edit theme]
  (if edit
    {:border "1px solid grey"
     :outline "1px solid transparent"
     "&:focus-within" {:border "1px solid var(--color-primary-main)"
                       :outline "1px solid var(--color-primary-main)"}}
    {:border 0
     :border-bottom (if (m/is-light-theme? theme)
                      "1px solid #eeeeee"
                      "1px solid rgba(255, 255, 255, 0.3)")}))

(defn bool-switch-field
  "Renders a boolean field (core FieldDataType::Bool) as a labeled Switch in both edit and
   read mode. In read mode the Switch is shown but disabled (not editable). The value is
   stored as a string ('True'/'False'); the core treats true/yes/1 (case-insensitive) as true."
  [{:keys [value edit on-change-handler] :as kv}]
  (let [label (translated-label kv)
        checked? (contains? #{"true" "yes" "1"}
                            (some-> value str/trim str/lower-case))]
    ;; :margin-top matches the text fields (single-line-text-field) so the boolean row
    ;; has the same vertical spacing as the other fields in the form. The border sx gives
    ;; it the same rectangular (edit) / underlined (read) look as the text fields.
    [mui-stack {:direction "row"
                :sx (merge {:width "100%" :align-items "center" :margin-top "16px"
                            :px "5px"}
                           (bool-field-border-sx edit @custom-theme-atom))}
     [mui-form-control-label
      {:label-placement "start"
       :sx {:ml 0 :mr 0 :width "100%" :justify-content "space-between"}
       :control (r/as-element
                 [mui-switch
                  {:checked checked?
                   :disabled (not edit)
                   :on-change (fn [^js/Event e]
                                (when edit
                                  (on-change-handler (.. e -target -checked))))}])
       :label (r/as-element
               [mui-typography {:variant "caption"
                                :sx {:color "text.secondary"}}
                label])}]]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(defn text-area-field-1 [{:keys [key value edit on-change-handler]
                            :or {edit false
                                 on-change-handler #()}
                            :as kv}]
    [m/text-field {;;:classes {:root "entry-cnt-field"}
                   :sx {:margin-top "4px"}
                   :fullWidth true
                   :id key
                   :label "Additional URLs";;name
                   :variant "standard"
                   :value value
                   :onChange on-change-handler
                   :multiline true
                   :rows 4
                   ;;minRows and maxRows should not be used with the fixed 'm/text-field'
                   ;;:minRows 3 
                   ;;:maxRows 10 
                   :InputLabelProps {:shrink true}
                   :inputRef (fn [ref-val]
                               #_(when ref-val
                                   (js/console.log "scrollWidth: " (.-scrollWidth ref-val) " clientWidth: " (.-clientWidth ref-val) "for ref-val"  ref-val)
                                   #_(when (> (.-scrollWidth ref-val) (.-clientWidth ref-val))
                                       (println "Setting multiline true for key: " key)
                                       (set! (.-multiline ref-val) true))))
                   :InputProps {:id key
                                :endAdornment (if false nil
                                                  (r/as-element
                                                   [mui-input-adornment  {:sx {:position "absolute"
                                                                               :right "5px"}
                                                                          :position "end"}
                                                    [end-icons kv]
                                                    #_(seq icons)]))}

                   :inputProps  {:readOnly (not edit)
                                 :sx {:ml ".5em" :mr ".5em"}
                                 :style {:resize "vertical"}}}])

#_(defn text-field-1 [{:keys [key
                              field-name
                              value
                              read-value
                              protected
                              visible
                              edit
                              on-change-handler
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
                            on-change-handler #(println (str "No on change handler yet registered for the key"))}
                       :as kv}]

    ;;:margin-top "16px" here is equivalent to the one used by "entry-cnt-field"
    (let [label (cond
                  (not (nil? field-name))
                  ;; It is assumed translation is done already
                  field-name

                  standard-field
                  (tr-entry-field-name-cv key)

                  :else
                  ;; It is assumed translation is done already
                  key)
          val  (to-value kv)
          multiline-field @(form-events/multiline? key)]
      (println "Rendering text-field-1 for key: " key " multiline-field: " multiline-field)

      (if-not multiline-field
        [m/text-field {:sx   (merge {:margin-top "16px"} (cc/password-helper-text-sx (:name password-score)))
                       :fullWidth true
                       :label label
                       :variant "standard"
                       :value val
                       :placeholder placeholder
                       :error  (not (nil? error-text))
                       :helperText (cond
                                     (and (nil? error-text) (not (nil? password-score)))
                                     (-> password-score :name lstr-l-cv)

                                     (nil? error-text)
                                     helper-text

                                     :else
                                     error-text)
                       :onChange  (fn [e]
                                    (let [fld (js/document.getElementById key)]
                                      (js/console.log "getElementById field is "  fld)
                                      (if  (> (.-scrollWidth fld) (.-clientWidth fld))
                                        (form-events/entry-form-multiline-field-toggle key true)
                                        (form-events/entry-form-multiline-field-toggle key false)))

                                    (on-change-handler e))
                       :required false
                       :disabled disabled
                       ::inputRef (fn [ref-val]
                                    (when ref-val
                                      (js/console.log "scrollWidth: " (.-scrollWidth ref-val) " clientWidth: " (.-clientWidth ref-val) "for ref-val"  ref-val)
                                      (if  (> (.-scrollWidth ref-val) (.-clientWidth ref-val))
                                        (form-events/entry-form-multiline-field-toggle key true)
                                        (form-events/entry-form-multiline-field-toggle key false))))
                       :InputLabelProps {}
                       :InputProps {:id key
                                    :sx (theme-text-field-sx edit @custom-theme-atom)
                                    :endAdornment (if no-end-icons nil
                                                      (r/as-element
                                                       [mui-input-adornment {:position "end"}
                                                        [end-icons kv]
                                                        #_(seq icons)]))
                                    :type (if (or (not protected) visible) "text" "password")}
                       ;;attributes for 'input' tag can be added here
                       ;;It seems adding these 'InputProps' also works
                       ;;We need to use 'readOnly' and not 'readonly'        
                       :inputProps  (html-input-props kv)
                                     ;;:sx (if edit sx2 sx1) ;;:readonly "readonly"
                                     }]

        [text-area-field-1 kv])))


  
