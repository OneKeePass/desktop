(ns onekeepass.frontend.entry-form.dialogs
  (:require [onekeepass.frontend.common-components
             :refer [alert-dialog-factory confirm-text-dialog
                     enter-key-pressed-factory]]
            [onekeepass.frontend.db-icons :as db-icons]
            [onekeepass.frontend.entry-form.common :as ef-cmn :refer [popper-box-sx
                                                                      popper-button-sx]]
            [onekeepass.frontend.events.common :as ce :refer [on-change-factory]]
            [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]
            [onekeepass.frontend.events.entry-form-ex :as form-events]
            [onekeepass.frontend.events.move-group-entry :as move-events]
            [onekeepass.frontend.mui-components :as m
             :refer [mui-alert mui-alert-title mui-box mui-button mui-button
                     mui-checkbox mui-dialog mui-dialog-actions
                     mui-dialog-content mui-dialog-title
                     mui-form-control-label mui-grid mui-link mui-menu-item
                     mui-popper mui-stack mui-tooltip mui-typography]]
            [onekeepass.frontend.translation :as t
             :refer [lstr-dlg-title]
             :refer-macros [tr-l tr-dlg-title tr-dlg-text tr-h tr-bl]]
            [reagent.core :as r]))

;;;;;;;;  OTP ;;;;;;;;;;;;;

(defn delete-totp-confirm-dialog [{:keys [section-name otp-field-name] :as dialog-data}]
  ;; we can use either 'alert-dialog-factory' or confirm-text-dialog for this
  [confirm-text-dialog
   (tr-dlg-title otpDelete)
   (tr-dlg-text otpDelete)
   [{:label (tr-bl yes) :on-click (fn []
                                    (form-events/entry-form-delete-otp-field section-name otp-field-name)
                                    (ef-cmn/close-delete-totp-confirm-dialog))}
    {:label (tr-bl no) :on-click (fn []
                                   (ef-cmn/close-delete-totp-confirm-dialog))}]
   dialog-data])

(def algorithms [{:name "SHA-1" :value "SHA1"}
                 {:name "SHA-256" :value "SHA256"}
                 {:name "SHA-512" :value "SHA512"}])

(defn set-up-totp-dialog [{:keys [dialog-show
                                  secret-code
                                  otp-uri-used
                                  section-name
                                  standard-field
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

   [mui-dialog-title (tr-dlg-title otpSetup)]
   [mui-dialog-content
    [mui-stack
     (when (not standard-field)
       [m/text-field {:sx {:mb 2}
                      :label (tr-l "fieldName")
                      :value field-name
                      :required true
                      :error (contains? error-fields :field-name)
                      :helperText (get error-fields :field-name)
                      :on-change (on-change-factory dlg-events/otp-settings-dialog-update :field-name)
                      :variant "standard" :fullWidth true}])

     [m/text-field {:label (tr-l "secretOrTotpAuthUrl")
                    :placeholder (tr-h "keyOrAuthurl")
                    :value secret-code
                    :required true
                    :error (contains? error-fields :secret-code)
                    :helperText (get error-fields :secret-code)
                    :on-change (on-change-factory dlg-events/otp-settings-dialog-update :secret-code)
                    :variant "standard" :fullWidth true}]

     (when (and (not otp-uri-used) (not show-custom-settings))
       [mui-stack {:direction "row" :sx {:mt 3 :mb 3 :justify-content "center"}}
        [mui-tooltip  {:title "Optional custom settings" :enterDelay 2500}
         [mui-link {:sx {:color "primary.dark"}
                    :underline "hover"
                    :on-click  dlg-events/otp-settings-dialog-custom-settings-show}
          [mui-typography {:variant "h6" :sx {:font-size "1.1em"}}
           (tr-l "customSettings")]]]])

     (when (and (not otp-uri-used) show-custom-settings)
       [mui-stack {:direction "row" :sx {:mt 4}}
        [mui-stack {:sx {:width "40%"}}
         [m/text-field {:label (tr-l "algorithm")
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
         [m/text-field {:label (tr-l "period")
                        :value period ;;(:memory kdf)
                        :type "number"
                        :error (contains? error-fields :period)
                        :helperText (get error-fields :period)
                        :on-change (on-change-factory dlg-events/otp-settings-dialog-update :period)
                        :inputProps {:min 1 :max 60}
                        :variant "standard" :fullWidth true}]]

        [mui-stack {:sx {:width "30%" :ml 3}}
         [m/text-field {:label (tr-l "tokenlength")
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
     [mui-button {:on-click  dlg-events/otp-settings-dialog-close} (tr-bl "cancel")]
     [mui-button {:on-click dlg-events/otp-settings-dialog-ok}
      (tr-bl "ok")]]]])

;;;;;;;;;;

(defn add-modify-section-popper [{:keys [dialog-show
                                         popper-anchor-el
                                         mode
                                         section-name
                                         error-fields] :as dialog-data}]

  [mui-popper {:anchorEl popper-anchor-el
               :id "section"
               :open dialog-show
                ;; need to use z-index as background elements from left panel may be visible when a part of popper is shown
                ;; overlapps that panel  
               :sx {:z-index 2 :min-width "400px"}}
   #_[mui-click-away-listener #_{:onClickAway (fn [e] (println "Clicked outside box" (.-currentTarget e)))}]

   [mui-box {:sx popper-box-sx}
    [mui-stack [mui-typography (if (= mode :add)
                                 (tr-l "newsectionName") (tr-l "modifySectionName"))]]
    [mui-stack [mui-dialog-content {:dividers true}
                [m/text-field {:label (tr-l "sectionName")
                               :value section-name
                               :error (boolean (seq error-fields))
                               :helperText (get error-fields :section-name)
                               :on-key-press (enter-key-pressed-factory
                                              #(form-events/section-name-add-modify dialog-data))
                               :InputProps {}
                               :on-change (on-change-factory form-events/section-name-dialog-update :section-name)
                               :variant "standard" :fullWidth true}]]]
    [mui-stack  {:sx {:justify-content "end"} :direction "row"}
     [mui-button {:variant "text"
                  :sx popper-button-sx
                  :on-click  #(form-events/section-name-dialog-update :dialog-show false)}
      (tr-bl cancel)]
     [mui-button {:variant "text"
                  :sx  popper-button-sx
                  :on-click #(form-events/section-name-add-modify dialog-data)}
      (tr-bl ok)]]]])

(defn add-modify-section-field-dialog
  [{:keys [dialog-show
           section-name
           field-name
           protected
           _data-type
           mode
           add-more
           error-fields]
    :as m}]
  (let [ok-fn (fn [_e]
                (if (= mode :add)
                  (form-events/section-field-add
                   (select-keys m [:field-name :protected :required :section-name :data-type]))
                  (form-events/section-field-modify
                   (select-keys m [:field-name :current-field-name :data-type :protected :required :section-name]))))]
    [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)
                 ;; This will set the Paper width in all child components 
                 ;; equivalent to :classes {:paper "pwd-dlg-root"}
                 :sx {"& .MuiPaper-root" {:width "60%"}}}
     [mui-dialog-title
      [mui-stack [mui-typography
                  (if (= mode :add)
                    (lstr-dlg-title "sectionField1" {:section-name section-name})
                    (lstr-dlg-title "sectionField2" {:section-name section-name}))]]]
     [mui-dialog-content {:dividers true}
      [mui-stack
       [m/text-field {:label (tr-l fieldName)
                       ;; If we set ':value key', the dialog refreshes when on change fires for each key press in this input
                       ;; Not sure why. Using different name like 'field-name' works fine
                      :value field-name
                      :error (boolean (seq error-fields))
                      :helperText (get error-fields field-name)
                      :on-key-press (enter-key-pressed-factory ok-fn)

                      ;; Needs some tweaking as the input remains focus till the error is cleared
                      :inputRef (fn [comp-ref]
                                  (when (and (boolean (seq error-fields)) (not (nil? comp-ref)))
                                    (when-let [comp-id (some-> comp-ref .-props .-id)]
                                      (.focus (.getElementById js/document comp-id)))))
                      :InputProps {}
                      :on-change (on-change-factory form-events/section-field-dialog-update :field-name)
                      :variant "standard" :fullWidth true}]
       [mui-stack {:direction "row"}
        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox
                     {:checked protected
                      :on-change (on-change-factory form-events/section-field-dialog-update :protected)}])
          :label (tr-l protected)}]
        #_[mui-form-control-label
           {:control (r/as-element
                      [mui-checkbox {:checked required
                                     :on-change (on-check-factory form-events/section-field-dialog-update :required)}])
            :label "Required"}]]

       (when add-more
         [mui-stack
          [mui-alert {:severity "success"
                      :sx {"&.MuiAlert-root" {:width "100%"}}} ;; need to override the paper width 60%
           [mui-alert-title (tr-l success)]
           (tr-h "customFieldAdded")]])]]
     [mui-dialog-actions
      [mui-stack
       {:sx {:justify-content "end"} :direction "row" :spacing 1}   ;;{:sx {:align-items "end"}}
       [mui-button
        {:on-click  (fn [_e]
                      (form-events/section-field-dialog-update :dialog-show false))}
        (tr-bl cancel)]
       [mui-button
        {:on-click ok-fn}
        (tr-bl ok)]]]]))

(defn custom-field-delete-confirm [dialog-data]
  [(alert-dialog-factory
    (tr-dlg-title deleteField)
    (tr-dlg-text deleteField)
    [{:label (tr-bl yes) :on-click #(form-events/field-delete-confirm true)}
     {:label (tr-bl no) :on-click  #(form-events/field-delete-confirm false)}])
   dialog-data])

(defn custom-section-delete-confirm [dialog-data]
  [(alert-dialog-factory
    (tr-dlg-title deleteSectionAndField)
    (tr-dlg-text deleteSectionAndField)
    [{:label (tr-bl yes) :on-click #(form-events/section-delete-confirm true)}
     {:label (tr-bl no) :on-click  #(form-events/section-delete-confirm false)}])
   dialog-data])

(defn attachment-delete-confirm-dialog [dialog-data]
  [(alert-dialog-factory
    (tr-dlg-title deleteAttachment)
    (tr-dlg-text deleteAttachment)
    [{:label (tr-bl yes) :on-click #(form-events/attachment-delete-dialog-ok)}
     {:label (tr-bl no) :on-click  #(form-events/attachment-delete-dialog-close)}])
   dialog-data])

(defn delete-permanent-dialog [dialog-data entry-uuid]
  [(alert-dialog-factory
    (tr-dlg-title entryDeletePermanent)
    (tr-dlg-text entryDeletePermanent) 
    [{:label (tr-bl yes) :on-click #(move-events/delete-permanent-group-entry-ok :entry entry-uuid)}
     {:label (tr-bl no) :on-click #(move-events/delete-permanent-group-entry-dialog-show :entry false)}])
   dialog-data])

;;;;;;;;;;;;;;;;;;;;; icons dialog ;;;;;;;;;;;;;;;;;;;

(def icons-dialog-flag (r/atom false))

(defn close-icons-dialog []
  (reset! icons-dialog-flag false))

(defn show-icons-dialog []
  (reset! icons-dialog-flag true))

(defn icons-dialog
  ([_dialog-open? _call-on-icon-selection]
   (fn [dialog-open? call-on-icon-selection]
     [:div [mui-dialog {:open (if (nil? dialog-open?) false dialog-open?)
                        :on-click #(.stopPropagation ^js/Event %) ;;prevents on click for any parent components to avoid closing dialog by external clicking
                        :classes {:paper "group-form-flg-root"}}
            [mui-dialog-title (tr-dlg-title "icons")]
            [mui-dialog-content {:dividers true}
             [mui-grid {:container true :xs true :spacing 0}
              (for [[idx svg-icon] db-icons/all-icons]
                ^{:key idx} [:div {:style {:margin "4px"} ;;:border "1px solid blue"
                                   :on-click #(do
                                                (call-on-icon-selection idx)
                                                #_(form-events/entry-form-data-update-field-value :icon-id idx)
                                                (close-icons-dialog))} [mui-tooltip {:title "Icon"} svg-icon]])]]
            [mui-dialog-actions
             [mui-button {:variant "contained" :color "secondary"
                          :on-click close-icons-dialog}
              (tr-bl cancel)]]]]))
  ([dialog-open?]
   [icons-dialog dialog-open? (fn [idx] (form-events/entry-form-data-update-field-value :icon-id idx))]))

;;;;;;;;;;;;;;;;;;;;;;;; Entry History ;;;;;;;;;;;;;;;;;;;;;;

(defn restore-confirm-dialog 
  "Called to confirm before restoring an entry from history"
  [dialog-show]
  [(alert-dialog-factory
    (tr-dlg-title restoreEntry)
    (tr-dlg-text restoreEntry)
    [{:label (tr-bl yes) :on-click form-events/restore-entry-from-history}
     {:label (tr-bl no) :on-click form-events/close-restore-confirm-dialog}])
   {:dialog-show dialog-show}])

(defn delete-confirm-dialog 
  "Called to cofirm a history entry"
  [dialog-show entry-uuid index]
  [(alert-dialog-factory
    (tr-dlg-title deleteHistory)
    (tr-dlg-text deleteHistory)
    [{:label (tr-bl yes) :on-click #(form-events/delete-history-entry-by-index entry-uuid index)}
     {:label (tr-bl no) :on-click form-events/close-delete-confirm-dialog}])
   {:dialog-show dialog-show}])

(defn delete-all-confirm-dialog 
  "Called to confirm whether to delete all the history entries of an entry or not"
  [dialog-show entry-uuid]
  [(alert-dialog-factory
    (tr-dlg-title deleteAllHistories)
    (tr-dlg-text deleteAllHistories)
    [{:label (tr-bl yes) :on-click #(form-events/delete-all-history-entries entry-uuid)}
     {:label (tr-bl no) :on-click form-events/close-delete-all-confirm-dialog}])
   {:dialog-show dialog-show}])
