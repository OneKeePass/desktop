(ns onekeepass.frontend.entry-form-ex
  (:require
   [reagent.core :as r]
   [clojure.string :as str]
   [onekeepass.frontend.utils :as u :refer [contains-val?]]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.tauri-events :as tauri-events]
   [onekeepass.frontend.events.common :as ce]
   [onekeepass.frontend.events.entry-form-ex :as form-events]
   [onekeepass.frontend.events.move-group-entry :as move-events]
   [onekeepass.frontend.group-tree-content :as gt-content]
   [onekeepass.frontend.db-icons :as db-icons :refer [entry-icon entry-type-icon]]
   [onekeepass.frontend.common-components :as cc :refer [tags-field
                                                         enter-key-pressed-factory
                                                         list-items-factory
                                                         alert-dialog-factory
                                                         selection-autocomplete]]
   [onekeepass.frontend.mui-components :as m :refer [color-primary-main
                                                     mui-link
                                                     mui-popper
                                                     mui-divider
                                                     mui-list-item-avatar
                                                     mui-avatar
                                                     mui-alert
                                                     mui-alert-title
                                                     mui-form-control-label
                                                     mui-checkbox
                                                     mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-list-item mui-list-item-text
                                                     mui-icon-button mui-icon-more-vert
                                                     mui-button
                                                     mui-icon-button
                                                     mui-text-field
                                                     ;;mui-text-field-type ;;to use with create-element
                                                     mui-icon-check
                                                     mui-list-item-icon
                                                     mui-list-item-text
                                                     mui-box
                                                     mui-stack
                                                     mui-grid
                                                     mui-input-adornment
                                                     mui-typography
                                                     mui-menu
                                                     mui-menu-item
                                                     mui-tooltip
                                                     mui-icon-add-circle-outline-outlined
                                                     mui-icon-more-vert
                                                     mui-icon-visibility-off
                                                     mui-icon-visibility
                                                     mui-icon-delete-outline
                                                     mui-icon-autorenew
                                                     mui-icon-edit-outlined
                                                     mui-date-time-picker
                                                     mui-desktop-date-picker
                                                     mui-localization-provider
                                                     date-adapter]]))

(def background-color1 "#F1F1F1")
(def popper-border-color "#E7EBF0")
(def popper-button-sx {:color "secondary.light"})

(def popper-box-sx {:bgcolor  "whitesmoke";;"background.paper"
                    ;;:boxShadow 3
                    :p "15px"
                    :pb "5px"
                    :border-color popper-border-color
                    :border-width "thin"
                    :border-style "solid"})

(def content-sx {;;:width "98%"
                 :border-color "lightgrey"
                 :boxShadow 0
                 :borderRadius 1
                 :margin "5px"
                 :background "white"
                 :padding "8px 8px 8px 8px"
                 :border ".1px solid"})

(defn on-change-factory [handler-name field-name-kw]
  [handler-name field-name-kw]
  (fn [^js/Event e]
    (if-not (= :protected field-name-kw)
      (handler-name field-name-kw (-> e .-target  .-value))
      (handler-name field-name-kw (-> e .-target  .-checked)))))

(defn on-check-factory [handler-name field-name-kw]
  (fn [e]
    (handler-name field-name-kw (-> e .-target  .-checked))))

(defn on-change-factory2
  [handler-name]
  (fn [^js/Event e]
    ;;(println "e is " (-> e .-target .-value))
    (handler-name  (-> e .-target  .-value))))

(defn- menu-action [anchor-el action & action-args]
  (fn [^js/Event e]
    (reset! anchor-el nil)
    (apply action action-args)
    (.stopPropagation ^js/Event e)))

(defn entry-form-top-menu-items []
  (fn [anchor-el entry-uuid favorites?]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el form-events/edit-mode-menu-clicked)}
      [mui-list-item-text {:inset true} "Edit"]]

     (if favorites?
       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action anchor-el form-events/favorite-menu-checked false)}
        [mui-list-item-icon [mui-icon-check]] "Favorites"]
       [mui-menu-item {:divider false
                       :sx {:padding-left "1px"}
                       :on-click (menu-action anchor-el form-events/favorite-menu-checked true)}
        [mui-list-item-text {:inset true} "Favorites"]])


     [mui-menu-item {:divider true
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/entry-delete-start entry-uuid)}
      [mui-list-item-text {:inset true} "Delete"]]

     [mui-menu-item {:divider false
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/perform-auto-type-start entry-uuid)}
      [mui-list-item-text {:inset true} "Perform auto type"]]
     
     [mui-menu-item {:divider true
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/entry-auto-type-edit)}
      [mui-list-item-text {:inset true} "Edit auto type"]]

     [mui-menu-item {:divider false
                     :sx {:padding-left "1px"}
                     :on-click (menu-action anchor-el form-events/load-history-entries-summary entry-uuid)
                     :disabled (not @(form-events/history-available))}
      [mui-list-item-text {:inset true} "History"]]]))

(defn entry-form-top-menu [entry-uuid]
  (let [anchor-el (r/atom nil)
        favorites? @(form-events/favorites?)]
    [:div
     [mui-icon-button {:edge "start"
                       :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))
                       :style {:color "#000000"}} [mui-icon-more-vert]]
     [entry-form-top-menu-items anchor-el entry-uuid favorites?]
     [cc/info-dialog "Entry Delete" "Deleting entry is in progress"
      form-events/entry-delete-info-dialog-close
      @(form-events/entry-delete-dialog-data)]]))

(defn- form-menu-internal
  "A functional reagent component.This component helps to enable/disable 
  Edit Entry app menu using react effect"
  [entry-uuid]
  (let [deleted-cat? @(form-events/deleted-category-showing)
        recycle-bin? @(form-events/recycle-group-selected?)
        group-in-recycle-bin? @(form-events/selected-group-in-recycle-bin?)
        edit-menu?  (not (or deleted-cat? recycle-bin? group-in-recycle-bin?))]
    ;; useEffect is used to enable/disable as when the form-menu is visible or not
    (m/react-use-effect (fn []
                          (tauri-events/enable-app-menu const/MENU_ID_EDIT_ENTRY edit-menu?)
                          ;; cleanup fn is returned which is called when this component unmounts
                          (fn []
                            (tauri-events/enable-app-menu const/MENU_ID_EDIT_ENTRY false))) (clj->js []))
    (when edit-menu?
      [entry-form-top-menu entry-uuid])))

(defn form-menu
  "Called to show relevant menus for a selected entry. 
  This is used in the entry-form and entry-list panels"
  [entry-uuid]
  [:f> form-menu-internal entry-uuid])

(defn box-caption [text]
  [mui-typography {:sx {"&.MuiTypography-root" {:color color-primary-main}}
                   :variant  "button"} text])

(defn datetime-field
  [{:keys [key value on-change-handler]
    :or [on-change-handler #()]}]
  [mui-localization-provider {:dateAdapter date-adapter}
   [mui-date-time-picker {:label key
                          :value value
                          :onChange on-change-handler
                          :renderInput (fn [props]
                                         (let [p (js->clj props :keywordize-keys true)
                                               p (merge p {:variant "standard"
                                                           :classes {:root "entry-cnt-field"}
                                                           :fullWidth true})]
                                           #_(println "Props called: " (-> p   keys))
                                           (r/as-element [mui-text-field p])))}]])

(declare text-field)

(defn date-field
  [{:keys [key value edit on-change-handler]
    :or [on-change-handler #()]}]
  (if-not edit
    [text-field  {:key (str key " (MM/DD/YYYY)") :value value :edit false}]
    [mui-stack {:direction "row" :sx {:width "40%"}}
     [mui-localization-provider {:dateAdapter date-adapter}
      [mui-desktop-date-picker
       {:label (str key " (MM/DD/YYYY)")
        :value value
        :onChange on-change-handler
        :renderInput (fn [props]
                       (let [p (js->clj props :keywordize-keys true)
                             p (merge p {:variant "standard"
                                         :classes {:root "entry-cnt-field"}
                                         :fullWidth true})]
                         #_(println "Props called: " (-> p   keys))
                         (r/as-element [mui-text-field p])))}]]]))

(defn expiry-content []
  (let [edit @(form-events/form-edit-mode)
        expiry-duration-selection @(form-events/entry-form-field :expiry-duration-selection)
        ;; :expiry-time is a datetime str in tz UTC of format "2022-12-10T17:36:10"
        ;; chrono::NaiveDateTime is serialized in this format   
        expiry-dt @(form-events/entry-form-data-fields :expiry-time)]
    (when edit
      [mui-box {:sx content-sx}
       [mui-stack {:direction "row" :sx {:align-items "flex-end"}}
        [mui-stack {:direction "row" :sx {:width "30%"}}
         [mui-text-field {:classes {:root "entry-cnt-field"}
                          :select true
                          :label "Expiry Duration"
                          :value  expiry-duration-selection
                       ;;;;TODO Need to work
                          :on-change #(form-events/expiry-duration-selection-on-change (-> % .-target  .-value)) ;;ee/expiry-selection-change-factory
                          :variant "standard" :fullWidth true
                          :style {:padding-right "16px"}}
          [mui-menu-item {:value "no-expiry"} "No Expiry"]
          [mui-menu-item {:value "three-months"} "3 Months"]
          [mui-menu-item {:value "six-months"} "6 Months"]
          [mui-menu-item {:value "one-year"} "1 Year"]
          [mui-menu-item {:value "custom-date"} "Custom Date"]]]
        (when-not (= expiry-duration-selection "no-expiry")
          [mui-stack {:direction "row" :sx {:width "40%"}}
           [datetime-field {:key "Expiry Date"
                            :value (u/to-local-datetime-str expiry-dt) ;; datetime str UTC to Local 
                            :on-change-handler (form-events/expiry-date-on-change-factory)}]])]]

      #_(when-not (= expiry-duration-selection "no-expiry")
          [mui-box {:sx content-sx}
           [mui-stack {:direction "row" :sx {}}
            [mui-typography (str "Expires: " (u/to-local-datetime-str expiry-dt "dd MMM yyyy HH:mm:ss p"))]]]))))

(def ENTRY_DATETIME_FORMAT "dd MMM yyyy pp")

(defn uuid-times-content []
  (let [edit @(form-events/form-edit-mode)
        expiry-duration-selection @(form-events/entry-form-field :expiry-duration-selection)
        expiry-dt @(form-events/entry-form-data-fields :expiry-time)
        creation-time @(form-events/entry-form-data-fields :creation-time)
        last-modification-time @(form-events/entry-form-data-fields :last-modification-time)]
    (when-not edit
      [mui-box {:sx content-sx}

       [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
        [mui-typography "Uuid:"]
        [mui-typography @(form-events/entry-form-data-fields :uuid)]]

       [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
        [mui-typography "Created:"]
        [mui-typography (u/to-local-datetime-str creation-time ENTRY_DATETIME_FORMAT)]]

       [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
        [mui-typography "Last Modified:"]
        [mui-typography (u/to-local-datetime-str last-modification-time ENTRY_DATETIME_FORMAT)]]

       (when-not (= expiry-duration-selection "no-expiry")
         [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-top "10px"}}
          [mui-typography "Expires:"]
          [mui-typography (u/to-local-datetime-str expiry-dt ENTRY_DATETIME_FORMAT)]])])))

(defn text-area-field [{:keys [key value edit on-change-handler]
                        :or {edit false
                             on-change-handler #()}}]
  [m/text-field {:classes {:root "entry-cnt-field"}
                 :fullWidth true
                 :id key :label "";;name
                 :variant "standard"
                 :value value
                 :onChange on-change-handler
                 :multiline true
                 :rows 4
                    ;;minRows and maxRows should not be used with the fixed 'm/text-field'
                    ;;:minRows 3 
                    ;;:maxRows 10
                    ;;:classes {:root "entry-cnt-notes-field"}
                 :InputLabelProps {:shrink true}
                 :InputProps {:id key
                                  ;; This did not fix the cursor jumping
                                  ;;  :inputComponent
                                  ;;  (r/reactify-component
                                  ;;   (fn [props]
                                  ;;     ;;(println "props are" props)
                                  ;;     [:input (-> props
                                  ;;                    (assoc :ref (:inputRef props))
                                  ;;                    (dissoc :inputRef))]))
                              }

                 :inputProps  {:readOnly (not edit)
                               :style {:resize "vertical"}}}])

(defn end-icons [key value protected visibile? edit]
  [:<>
   (when protected
     (if visibile?
       [mui-icon-button {:sx {:margin-right "-8px"}
                         :edge "end"
                         :on-click #(form-events/entry-form-field-visibility-toggle key)}
        [mui-icon-visibility]]
       [mui-icon-button {:sx {:margin-right "-8px"}
                         :edge "end"
                         :on-click #(form-events/entry-form-field-visibility-toggle key)}
        [mui-icon-visibility-off]]))
   ;; Password generator 
   (when (and edit protected (= key const/PASSWORD))
     [mui-icon-button {:sx {:margin-right "-8px"}
                       :edge "end"
                       :on-click form-events/password-generator-show}
      [mui-icon-autorenew]])
   ;; Copy 
   [(cc/copy-icon-factory) value]])

(defn text-field [{:keys [key
                          value
                          protected
                          required
                          visible
                          edit
                          on-change-handler
                          required
                          password-score
                          placeholder
                          helper-text
                          error-text]
                   :or {visible true
                        edit false
                        protected false
                        on-change-handler #(println (str "No on change handler yet registered for " key))
                        required false}}]
  [m/text-field {:sx   (merge {} (cc/password-helper-text-sx (:name password-score)))
                 :fullWidth true
                 :label key :variant "standard"
                 :classes {:root "entry-cnt-field"}
                 :value value
                 :placeholder placeholder
                 :error  (not (nil? error-text))
                 :helperText (cond
                               (and (nil? error-text) (not (nil? password-score)))
                               (:score-text password-score)

                               (nil? error-text)
                               helper-text

                               :else
                               error-text)
                 :onChange  on-change-handler
                 :required required
                 :InputLabelProps {}
                 :InputProps {:id key
                              :classes {:root (if edit "entry-cnt-text-field-edit" "entry-cnt-text-field-read")
                                        :focused  (if edit "entry-cnt-text-field-edit-focused" "entry-cnt-text-field-read-focused")}
                                 ;;:sx (if editing {} read-sx1)
                              :endAdornment (r/as-element
                                             [mui-input-adornment {:position "end"}
                                              [end-icons key value protected visible edit]
                                              #_(seq icons)])
                              :type (if (or (not protected) visible) "text" "password")}
                         ;;attributes for 'input' tag can be added here
                         ;;It seems adding these 'InputProps' also works
                         ;;We need to use 'readOnly' and not 'readonly'
                 :inputProps  {:readOnly (not edit)

                                   ;;:readonly "readonly"
                               }}])

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
    [mui-stack [mui-typography (if (= mode :add) "New section name" "Modify section name")]]
    [mui-stack [mui-dialog-content {:dividers true}
                [m/text-field {:label "Section Name"
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
      "Cancel"]
     [mui-button {:variant "text"
                  :sx  popper-button-sx
                  :on-click #(form-events/section-name-add-modify dialog-data)}
      "Ok"]]]])

(defn add-modify-section-field-popper
  [{:keys [dialog-show
           popper-anchor-el
           field-name
           protected
           required
           _data-type
           mode
           error-fields]
    :as m}]
  #_[mui-click-away-listener #_{:onClickAway #(form-events/section-field-dialog-update :dialog-show false)}]
  (let [ok-fn (fn [_e]
                (if (= mode :add)
                  (form-events/section-field-add
                   (select-keys m [:field-name :protected :required :section-name :data-type]))
                  (form-events/section-field-modify
                   (select-keys m [:field-name :current-field-name :data-type :protected :required :section-name]))))]
    [mui-popper {:anchorEl popper-anchor-el
                 :id "field"
                 :open dialog-show
                 :sx {:z-index 2 :min-width "400px"}}
     [mui-box {:sx popper-box-sx}
      [mui-stack [mui-typography (if (= mode :add) "Add field" "Modify field")]]
      [mui-stack
       [mui-dialog-content {:dividers true}
        [mui-stack
         [m/text-field {:label "Field Name"
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
                      [mui-checkbox {:checked protected
                                     :on-change (on-change-factory form-events/section-field-dialog-update :protected)}])
            :label "Protected"}]
          [mui-form-control-label
           {:control (r/as-element
                      [mui-checkbox {:checked required
                                     :on-change (on-check-factory form-events/section-field-dialog-update :required)}])
            :label "Required"}]]]]]
      [mui-stack  {:sx {:justify-content "end"} :direction "row"}   ;;{:sx {:align-items "end"}}
       [mui-button {:variant "text"
                    :sx popper-button-sx
                    :on-click  (fn [_e]
                                 (form-events/section-field-dialog-update :dialog-show false))} "Cancel"]
       [mui-button {:sx popper-button-sx
                    :variant "text"
                    :on-click ok-fn}
        "Ok"]]]]))

(defn add-modify-section-field-dialog
  [{:keys [dialog-show
           section-name
           field-name
           protected
           required
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
                 :sx {"& .MuiPaper-root" {:width "60%"}} ;; equivalent to :classes {:paper "pwd-dlg-root"}
                 }
     [mui-dialog-title [mui-stack [mui-typography
                                   (if (= mode :add)
                                     (str "Add field in " section-name)
                                     (str "Modify field in " section-name))]]]
     [mui-dialog-content {:dividers true}
      [mui-stack
       [m/text-field {:label "Field Name"
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
                    [mui-checkbox {:checked protected
                                   :on-change (on-change-factory form-events/section-field-dialog-update :protected)}])
          :label "Protected"}]
        [mui-form-control-label
         {:control (r/as-element
                    [mui-checkbox {:checked required
                                   :on-change (on-check-factory form-events/section-field-dialog-update :required)}])
          :label "Required"}]]

       (when add-more [mui-stack
                       [mui-alert {:severity "success" :sx {"&.MuiAlert-root" {:width "100%"}}} ;; need to override the paper width 60%
                        [mui-alert-title "Success"] "Custom field added. You can add more field or cancel to close"]])]]
     [mui-dialog-actions
      [mui-stack  {:sx {:justify-content "end"} :direction "row" :spacing 1}   ;;{:sx {:align-items "end"}}
       [mui-button {:on-click  (fn [_e]
                                 (form-events/section-field-dialog-update :dialog-show false))} "Cancel"]
       [mui-button {:on-click ok-fn}
        "Ok"]]]]))

(defn custom-field-delete-confirm [dialog-data]
  [(alert-dialog-factory "Delete Field"
                         "Are you sure you want to delete this field permanently?"
                         [{:label "Yes" :on-click #(form-events/field-delete-confirm true)}
                          {:label "No" :on-click  #(form-events/field-delete-confirm false)}])
   dialog-data])

(defn custom-section-delete-confirm [dialog-data]
  [(alert-dialog-factory "Delete Field"
                         "Are you sure you want to delete this section and all its fields permanently?"
                         [{:label "Yes" :on-click #(form-events/section-delete-confirm true)}
                          {:label "No" :on-click  #(form-events/section-delete-confirm false)}])
   dialog-data])

(defn simple-selection-field [{:keys [key
                                      value
                                      required
                                      edit
                                      error-text
                                      helper-text
                                      on-change-handler
                                      select-field-options]}]
  ;; We are using the mui-text-field directly as select component 
  ;; Another way also, this type of simple select list can be done using the following. 
  ;; The examples given in mui.com uses now this method
  ;; [mui-form-control [mui-input-label] [mui-select {} [mui-menu-item]] [mui-form-helper-text]  ]
  [mui-text-field {:id key
                   :required required
                   :classes {:root "entry-cnt-field"}
                   :select true
                   :label key
                   :value value
                   :on-change on-change-handler
                   :error  (not (nil? error-text))
                   :helperText (if (nil? error-text) helper-text error-text)
                   :inputProps  {:readOnly (not edit)}
                   :variant "standard" :fullWidth true}
   (doall (for [y select-field-options]
            ^{:key y} [mui-menu-item {:value y} y]))])

(defn section-header [section-name]
  (let [edit @(form-events/form-edit-mode)
        standard-sections @(form-events/entry-form-data-fields :standard-section-names)
        comp-ref (atom nil)]
    [mui-stack {:direction "row"
                :ref (fn [e]
                         ;;(println "ref is called " e)
                       (reset! comp-ref e))}
     [mui-stack {:direction "row" :sx {:width "85%"}}
      [box-caption section-name]]
     (when edit
       [mui-stack {:direction "row" :sx {:width "15%" :justify-content "center"}}
        ;; Allow section name change only if the section name is not the standard one
        (when-not (contains-val? standard-sections section-name)
          [:<>
           [mui-tooltip  {:title "Modify section name" :enterDelay 2500}
            [mui-icon-button {:edge "end"
                              :on-click  #(form-events/open-section-name-modify-dialog section-name @comp-ref)}
             [mui-icon-edit-outlined]]]

           [mui-tooltip  {:title "Delete complete section" :enterDelay 2500}
            [mui-icon-button {:edge "end"
                              :on-click  #(form-events/section-delete section-name)}
             [mui-icon-delete-outline]]]])

        [mui-tooltip {:title "Add a field" :enterDelay 2500}
         [mui-icon-button {:edge "end" :color "primary"
                           :on-click #(form-events/open-section-field-dialog section-name @comp-ref)}
          [mui-icon-add-circle-outline-outlined]]]
        [add-modify-section-popper @(form-events/section-name-dialog-data)]
        [add-modify-section-field-dialog @(form-events/section-field-dialog-data)]
        #_[add-modify-section-field-popper @(form-events/section-field-dialog-data)]])]))

(defn section-content [edit section-name section-data]
  (let [errors @(form-events/entry-form-field :error-fields)]
    ;; Show a section in edit mode irrespective of its contents; In non edit mode a section is shown only 
    ;; if it has some fields with non blank value. It is assumed the 'required' fileds will have some valid values
    (when (or edit (boolean (seq (filter (fn [kv] (not (str/blank? (:value kv)))) section-data))))  ;;(seq section-data)
      (let [refs (atom {})]
        [mui-box {:sx content-sx}
         [section-header section-name]
         (doall
          (for [{:keys [key value
                        protected
                        required
                        data-type
                        select-field-options
                        standard-field
                        password-score] :as kv} section-data]
          ;; All fields of this section is shown in edit mode. In case of non edit mode, all required fields and other 
          ;; fields with values are shown
            (when (or edit (or required (not (str/blank? value))))
              ^{:key key}
              [mui-stack {:direction "row"
                          :ref (fn [e]
                             ;; We keep a ref to the underlying HtmlElememnt - #object[HTMLDivElement [object HTMLDivElement]] 
                             ;; The ref is kept for each filed's enclosing container 'Stack' component so that we can position the Popper 
                             ;; Sometimes the value of e is nil as react redraws the node
                                 (swap! refs assoc key e))}
               [mui-stack {:direction "row" :sx {:width (if edit "92%" "100%")}}
                (cond
                  (not (nil? select-field-options))
                  [simple-selection-field (assoc kv
                                                 :edit edit
                                                 :error-text (get errors key)
                                                 :on-change-handler #(form-events/update-section-value-on-change
                                                                      section-name key (-> % .-target  .-value)))]
                  ;; (= data-type "Date")
                  ;; [date-field {:key key
                  ;;              :value (if (str/blank? value)
                  ;;                       (.toLocaleDateString (js/Date.)) value)
                  ;;              :edit edit
                  ;;              :on-change-handler (form-events/section-date-field-on-change-factory section-name key)}]

                  :else
                  [text-field (assoc kv
                                     :edit edit
                                     :error-text (get errors key)
                                     :password-score password-score
                                     :visible @(form-events/visible? key)
                                     :on-change-handler #(form-events/update-section-value-on-change
                                                          section-name key (-> % .-target  .-value)))])]

               (when (and edit (not standard-field))
                 [mui-stack {:direction "row" :sx {:width "8%" :align-items "flex-end"}}
                  [mui-tooltip  {:title "Modify Field" :enterDelay 2500}
                   [mui-icon-button {:edge "end"
                                     :on-click  #(form-events/open-section-field-modify-dialog
                                                  {:key key
                                                   :protected protected
                                                   :required required
                                                   :popper-anchor-el (get @refs key)
                                                   :section-name section-name})}
                    [mui-icon-edit-outlined]]]
                  [mui-tooltip  {:title "Delete Field" :enterDelay 2500}
                   [mui-icon-button {:edge "end"
                                     :on-click #(form-events/field-delete section-name key)}
                    [mui-icon-delete-outline]]]])])))
         [custom-field-delete-confirm @(form-events/field-delete-dialog-data)]
         [add-modify-section-field-popper @(form-events/section-field-dialog-data)]
         [custom-section-delete-confirm @(form-events/section-delete-dialog-data)]]))))

(defn all-sections-content []
  (let [{:keys [edit]
         {:keys [section-names section-fields]} :data} @(form-events/entry-form-all)]
    [mui-stack
     (doall
      (for [section-name section-names]
        ^{:key section-name} [section-content edit section-name (get section-fields section-name)]))]))

(def icons-dialog-flag (r/atom false))

(defn close-icons-dialog []
  (reset! icons-dialog-flag false))

(defn show-icons-dialog []
  (reset! icons-dialog-flag true))

(defn icons-dialog
  ([dialog-open? call-on-icon-selection]
   (fn [dialog-open? call-on-icon-selection]
     [:div [mui-dialog {:open (if (nil? dialog-open?) false dialog-open?)
                        :on-click #(.stopPropagation ^js/Event %) ;;prevents on click for any parent components to avoid closing dialog by external clicking
                        :classes {:paper "group-form-flg-root"}}
            [mui-dialog-title "Icons"]
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
                          :on-click close-icons-dialog} "Close"]]]]))
  ([dialog-open?]
   [icons-dialog dialog-open? (fn [idx] (form-events/entry-form-data-update-field-value :icon-id idx))]))

(defn title-with-icon-field  []
  ;;(println "title-with-icon-field called ")
  (let [fields @(form-events/entry-form-data-fields [:title :icon-id])
        edit @(form-events/form-edit-mode)
        errors @(form-events/entry-form-field :error-fields)]
    (when edit
      [mui-box {:sx content-sx}
       [mui-stack {:direction "row" :spacing 1}
        [mui-stack {:direction "row" :sx {:width "90%" :justify-content "center"}}
         [text-field {:key "Title"
                      :value (:title fields)
                      :edit true
                      :required true
                      :helper-text "Title of this entry"
                      :error-text (:title errors)
                      :on-change-handler #(form-events/entry-form-data-update-field-value
                                           :title (-> % .-target  .-value))}]]

        [mui-stack {:direction "row" :sx {:width "10%" :justify-content "center" :align-items "center"}}
         [mui-typography {:align "center" :paragraph false :variant "subtitle1"} "Icon"]
         [mui-icon-button {:edge "end" :color "primary" :sx {;;:margin-top "16px"
                                                             ;;:margin-right "-8px"
                                                             }
                           :on-click  show-icons-dialog}
          [entry-icon (:icon-id fields)]]]
        [icons-dialog @icons-dialog-flag]]])))

(defn tags-selection []
  (let [all-tags @(ce/all-tags)
        tags @(form-events/entry-form-data-fields :tags) #_(:tags @(form-events/entry-form-data-fields [:tags]))
        edit @(form-events/form-edit-mode)]
    ;;(println "tags-selection called " tags)
    (when (or edit (boolean (seq tags)))
      [mui-box {:sx content-sx}
       [mui-stack {:direction "row"}]
       [tags-field all-tags tags form-events/on-tags-selection edit]
       (when edit
         [mui-typography {:variant "caption"} "Select a tag or start entering a new tag and add"])])))

(defn notes-content []
  (let [edit @(form-events/form-edit-mode)
        notes @(form-events/entry-form-data-fields :notes)]
    (when (or edit (not (str/blank? notes)))
      [mui-box {:sx content-sx}
       [mui-stack {:direction "row"} [box-caption "Notes"]]
       [mui-stack
        [text-area-field {:key "Notes"
                          :value notes
                          :edit edit
                          :on-change-handler (on-change-factory form-events/entry-form-data-update-field-value :notes)
                          #_#(form-events/entry-form-data-update-field-value :notes (-> % .-target  .-value))}]]])))

#_(defn custom-field-dialog [{:keys [dialog-show
                                     field-name
                                     field-value
                                     protected
                                     data-type
                                     error-fields
                                     mode
                                     add-more] :as m}]
    [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)
               ;; This will set the Paper width in all child components 
                 :sx {"& .MuiPaper-root" {:width "60%"}} ;; equivalent to :classes {:paper "pwd-dlg-root"}
                 }
     [mui-dialog-title (if (= mode :add) "Add Custom Field" "Modify Custom Field")]
     [mui-dialog-content {:dividers true}
      [mui-stack
       [:<>
        [m/text-field {:label "Field Name"
                     ;; If we set ':value key', the dialog refreshes when on change fires for each key press in this input
                     ;; Not sure why. Using different name like 'field-name' works fine
                       :value field-name
                       :error (boolean (seq error-fields))
                       :helperText (get error-fields field-name)
                       :InputProps {}
                       :on-change (on-change-factory form-events/update-custom-field-dialog-data :field-name)
                       :variant "standard" :fullWidth true}]

        (when (= mode :add)
          [m/text-field {:label "Field Value"
                         :value field-value
                         :InputProps {}
                         :on-change (on-change-factory form-events/update-custom-field-dialog-data :field-value)
                         :variant "standard" :fullWidth true}])

        [mui-form-control-label {:control (r/as-element [mui-checkbox {:checked protected
                                                                       :on-change (on-change-factory form-events/update-custom-field-dialog-data :protected)}])
                                 :label "Protected"}]]

       (when add-more [mui-stack
                       [mui-alert {:severity "success" :sx {"&.MuiAlert-root" {:width "100%"}}} ;; need to override the paper width 60%
                        [mui-alert-title "Success"] "Custom field added. You can add more field or cancel to close"]])]]
     [mui-dialog-actions
      [mui-button {:variant "contained" :color "secondary"
                   :on-click form-events/close-custom-field-dialog} "Cancel"]
      (if (= mode :add)
        [mui-button {:variant "contained" :color "secondary"
                     :on-click #(form-events/custom-field-add (select-keys m [:field-name :field-value :protected :data-type]))} "Add"]
        [mui-button {:variant "contained" :color "secondary"
                     :on-click #(form-events/custom-field-modify (select-keys m [:field-name :current-field-name :protected]))} "Modify"])]])

#_(defn custom-field-dialogs []
    [:<>
     [custom-field-dialog @(form-events/custom-field-dialog-data)]
     [custom-field-delete-confirm @(form-events/field-delete-dialog-data)]])

;; deprecate and keep a copy?
#_(defn custom-fields-content []
    (let [edit @(form-events/form-edit-mode)
          section-data @(form-events/entry-form-section-data section-name-custom-fields)]
      (when (or edit (boolean (seq section-data)))
        [mui-box {:sx content-sx}
         [mui-stack {:direction "row"}
          [mui-stack {:direction "row" :sx {:width "90%"}}
           [box-caption "Custom Fields"]]
          (when edit
            [mui-stack {:direction "row" :sx {:width "10%" :justify-content "flex-end"}}
             [mui-tooltip {:title "Add"}
              [mui-icon-button {:edge "end" :color "primary"
                                :on-click
                                form-events/open-custom-field-dialog}
               [mui-icon-add-circle-outline-outlined]]]])]
         (doall
          (for [{:keys [key] :as kv} section-data]
            ^{:key key} [mui-stack {:direction "row"}
                         [mui-stack {:direction "row" :sx {:width (if edit "92%" "100%")}}
                          [text-field (assoc kv
                                             :edit edit
                                             :visible @(form-events/visible? key)
                                             :on-change-handler #(form-events/update-section-value-on-change section-name-custom-fields key (-> % .-target  .-value)))]]

                         (when edit
                           [mui-stack {:direction "row" :sx {:width "8%" :align-items "flex-end"}}
                            [mui-tooltip  {:title "Modify Custom Field"}
                             [mui-icon-button {:edge "end"
                                               :on-click #(form-events/oepn-custom-field-modify-dialog (select-keys kv [:key :protected]))}
                              [mui-icon-edit-outlined]]]
                            [mui-tooltip  {:title "Delete Custom Field"}
                             [mui-icon-button {:edge "end"
                                               :on-click #(form-events/field-delete section-name-custom-fields key)}
                              [mui-icon-delete-outline]]]])]))])))

;; deprecate
#_(defn add-section-popper [anchor-el]
    (let [{:keys [section-name error-fields]} @(form-events/section-add-data)]
    ;;(println "section-name " section-name "anchor-el is " @anchor-el)
      [mui-popper {:anchorEl @anchor-el
                   :open (if @anchor-el true false)
                   :sx {:z-index 2 :min-width "400px"}}
       [mui-box {:sx {:bgcolor "background.paper"
                      :p "15px"
                      :pb "5px"
                      :border-color "#E7EBF0"
                      :border-width "thin"
                      :border-style "solid"}}
        [mui-stack [mui-typography "Add a section"]]
        [mui-stack [mui-dialog-content {:dividers true}
                    [m/text-field {:label "Section Name"
                                   :value section-name
                                   :error (boolean (seq error-fields))
                                   :helperText (get error-fields :section-name)
                                   :InputProps {}
                                   :on-change (on-change-factory2 form-events/section-add-name-update)
                                   :variant "standard" :fullWidth true}]]]
        [mui-stack  {:sx {:align-items "end"}}
         [mui-button {:variant "text"
                      :color "primary" :on-click (fn [_e]
                                                   (form-events/section-add-done)
                                                   (reset! anchor-el nil))} "Close"]]]]))

;; attachments-content is not yet complete and will be completed next release
(defn attachments-content []
  (let [edit @(form-events/form-edit-mode)
        attachments @(form-events/attachments)]

    (when (or edit (boolean (seq attachments)))
      [mui-box {:sx content-sx}
       [mui-stack {:direction "row"}
        [mui-stack {:direction "row" :sx {:width "90%"}}
         [box-caption "Attachments"]]
        (when edit
          [mui-stack {:direction "row"
                      :sx {:width "10%"
                           :justify-content "flex-end"}}
           [mui-tooltip {:title "Upload a file"}
            [mui-icon-button {:edge "end" :color "primary"
                              :on-click #()}
             [mui-icon-add-circle-outline-outlined]]]])]

       (doall
      ;; In case of attachment the 'key' has the file name
      ;; and the 'value' field is empty
        (for [{:keys [key] :as kv} attachments]
          ^{:key key} [mui-stack {:direction "row"}
                       [mui-stack {:direction "row" :sx {:width (if edit "92%" "95%")}}
                        [text-field (assoc kv
                                           :edit false
                                           :on-change-handler #())]]
                       (when edit
                         [mui-stack {:direction "row"
                                     :sx {:width "8%"
                                          :align-items "flex-end"
                                          :justify-content "flex-end"}}
                          [mui-tooltip  {:title "More"}
                           [mui-icon-button {:edge "end"
                                             :on-click #()}
                            [mui-icon-more-vert]]]])]))])))

(defn add-section-content []
  (let [edit @(form-events/form-edit-mode)]
    (when edit
      (let [;;anchor-el (r/atom nil)
            comp-ref (atom nil)]
        [mui-box {;; This box's ref is used as achoring element for the Popper
                  :ref (fn [e]
                         ;;(println "ref is called " e)
                         (reset! comp-ref e))}
         [mui-stack {:direction "row" :sx {:justify-content "center"}}
          [mui-stack {:direction "row"}
           [mui-tooltip  {:title "Add a new section and fields" :enterDelay 2500}
            [mui-icon-button {:edge "end"
                              :on-click #(form-events/open-section-name-dialog @comp-ref)
                              #_(fn [^js/Event _e]
                                  (reset! anchor-el @comp-ref #_(-> e .-currentTarget)))}
             [mui-icon-add-circle-outline-outlined]]]]
          [mui-stack {:direction "row" :sx {:align-items "center" :margin-left "10px"}}
           [mui-tooltip  {:title "Add a new section and fields" :enterDelay 2500}
            [mui-link {:sx {:color "primary.dark"}
                       :underline "hover"
                       :on-click  #(form-events/open-section-name-dialog @comp-ref)
                       #_(fn [^js/Event _e]
                           (reset! anchor-el @comp-ref))}
             [mui-typography {:variant "h6" :sx {:font-size "1.1em"}}
              "Add Section"]]]]]
         [add-modify-section-popper @(form-events/section-name-dialog-data)]
         #_[add-section-popper anchor-el]]))))

(defn center-content []
  (fn []
    [mui-box
     [title-with-icon-field]
     [all-sections-content]
     [add-section-content]
     [notes-content]
     [tags-selection]
     ;; attachments-content is not yet complete
     #_[attachments-content]
     [uuid-times-content]
     [expiry-content]]))

(defn delete-permanent-dialog [dialog-data entry-uuid]
  [(alert-dialog-factory "Entry Delete Permanent"
                         "Are you sure you want to delete this entry permanently?"
                         [{:label "Yes" :on-click #(move-events/delete-permanent-group-entry-ok :entry entry-uuid)}
                          {:label "No" :on-click #(move-events/delete-permanent-group-entry-dialog-show :entry false)}])
   dialog-data])

(defn entry-content []
  (fn []
    (let [title @(form-events/entry-form-data-fields :title)
          icon-id @(form-events/entry-form-data-fields :icon-id)
          entry-uuid  @(form-events/entry-form-data-fields :uuid)
          edit @(form-events/form-edit-mode)
          deleted-cat? @(form-events/deleted-category-showing)
          recycle-bin? @(form-events/recycle-group-selected?)
          group-in-recycle-bin? @(form-events/selected-group-in-recycle-bin?)
          pd-dlg-data  @(move-events/delete-permanent-group-entry-dialog-data :entry)]
      [:div {:class "gbox"
             :style {:margin 0
                     :width "100%"}}

       [:div {:class "gheader" :style {:background background-color1}}
        (when-not edit
          [mui-stack {:direction "row"}
           [mui-stack {:direction "row"  :sx {:width "95%" :justify-content "center"}}
            [entry-icon icon-id]
            [mui-typography {;; Need to use this margin value so that text aligns properly with icon
                             :style {:margin-left 2 :margin-top 2}
                             ;; If we use :sx, we need to use "2px" instead of 2
                             ;; Otherwise mui will interpret as 16px
                             ;;:sx {:margin-left "2px" :margin-top "2px"}
                             :align "center" :paragraph false :variant "h6"} title]]
           [mui-stack {:direction "row" :sx {:width "5%"}}
            [:div {:style {:margin-right "8px"}}
             [form-menu entry-uuid]]]])]

       [:div {:class "gcontent" :style {:overflow-y "scroll"
                                        :background background-color1}}
        [center-content]
        #_[custom-field-dialogs]]

       [:div {:class "gfooter" :style {:margin-top 2
                                       :min-height "46px" ;; needed to align this footer with entry list 
                                       :background m/color-grey-200
                                       ;;:background "var(--mui-color-grey-200)"
                                       }}

        [mui-stack {:sx {:align-items "flex-end"}}
         [:div.buttons1
          (when (or deleted-cat? recycle-bin? group-in-recycle-bin?)
            [:<>
             [mui-button {:variant "contained"
                          :color "secondary"
                          :on-click #(move-events/move-group-entry-dialog-show :entry true)} "Put back"]
             [mui-button {:variant "contained"
                          :color "secondary"
                          :on-click #(move-events/delete-permanent-group-entry-dialog-show :entry true)} "Delete Permanently"]])
          (if edit
            [:<>
             [mui-button {:variant "contained"
                          :color "secondary"
                          :on-click form-events/entry-update-cancel-on-click} "Cancel"]
             [mui-button {:variant "contained"
                          :color "secondary"
                          :disabled  (not @(form-events/modified))
                          :on-click form-events/ok-edit-on-click} "Apply"]]
            [:<>
             [mui-button {:variant "contained"
                          :color "secondary"
                          :on-click form-events/close-on-click} "Close"]
             [mui-button {:variant "contained"
                          :color "secondary"
                          :on-click form-events/edit-mode-menu-clicked} "Edit"]])]

         [gt-content/move-dialog
          {:dialog-data @(move-events/move-group-entry-dialog-data :entry)
           :title "Put back"
           :groups-listing @(form-events/groups-listing)
           :selected-entry-uuid entry-uuid
           :on-change (move-events/move-group-entry-group-selected-factory :entry)
           :cancel-on-click-factory (fn [_data]
                                      #(move-events/move-group-entry-dialog-show :entry false))
           :ok-on-click-factory (fn [data]
                                  #(move-events/move-group-entry-ok :entry (:selected-entry-uuid data)))}]

         [delete-permanent-dialog pd-dlg-data entry-uuid]]]])))

(defn entry-type-group-selection
  "Used in entry new form. Prvides from-1 type component for
   entry type and group selection for an entry"
  []
  (let [groups-listing (form-events/groups-listing)
        group-selected (form-events/entry-form-field :group-selection-info)
        entry-type-headers (ce/all-entry-type-headers)
        entry-type-uuid @(form-events/entry-form-data-fields :entry-type-uuid)
        field-error-text (:group-selection @(form-events/entry-form-field :error-fields))]
    ;;(println "entry-type-uuid is " entry-type-uuid)
    [mui-box {:sx content-sx}
     [mui-stack {:spacing 1}
      [mui-stack {:direction "row" :sx {:width "100%"}}
       [mui-stack {:direction "row" :sx {:width "90%"}}
        [mui-text-field  {:id  "select"
                          :select true
                          :required true
                          :label "Entry Type"
                          :value entry-type-uuid ;;field-type
                          :helper-text "An entry's type determines available fields"
                          ;;:InputProps {:classes {:focused "dialog-field-edit-focused"}}
                          :on-change (on-change-factory2 form-events/entry-type-uuid-on-change) #_de/custom-field-type-edit-on-change
                          :variant "standard" :fullWidth true}
         ;; select fields options
         (doall
          (for [{:keys [name uuid]} @entry-type-headers]
            ^{:key uuid} [mui-menu-item {:value uuid} name]))]]
       [mui-stack {:direction "row"
                   :sx {:width "10%"
                        :align-items "center"
                        :justify-content "center"}}
        [mui-tooltip {:title "Add custom entry type" :enterDelay 2500}
         [mui-icon-button {:edge "end"
                           :color "primary"
                           :on-click form-events/new-custom-entry-type}
          [mui-icon-add-circle-outline-outlined]]]]]


      [selection-autocomplete {:label "Group/Category"
                               :options @groups-listing
                               :current-value @group-selected
                               :on-change form-events/on-group-selection
                               :required true
                               :helper-text "An entry's group/category"
                               :error (not (nil? field-error-text))
                               :error-text field-error-text}]]]))

(defn entry-content-new []
  ;;(println "entry-content-new called")
  (let [title @(form-events/entry-form-data-fields :title)
        form-title (if (str/blank? title) "New Entry" (str "New Entry" "-" title))]
    [:div {:class "gbox"
           :style {:margin 0
                   :width "100%"}}

     [:div {:class "gheader" :style {:background background-color1}}
      [mui-stack {:direction "row"  :sx {:width "100%" :justify-content "center"}}
       [mui-typography {:align "center"
                        :paragraph false
                        :variant "h6"}
        form-title]]]
     [:div {:class "gcontent" :style {:overflow-y "scroll"
                                      :background background-color1}}
      [entry-type-group-selection]
      [center-content]
      #_[custom-field-dialogs]]

     [:div {:class "gfooter" :style {:margin-top 2
                                     :min-height "46px" ;; needed to align this footer with entry list 
                                     :background "var(--mui-color-grey-200)"}}

      [mui-stack {:sx {:align-items "flex-end"}}
       [:div.buttons1
        [mui-button {:variant "contained" :color "secondary"
                     :on-click form-events/new-entry-cancel-on-click #_ee/new-entry-cancel-on-click} "Cancel"]
        [mui-button {:variant "contained" :color "secondary"
                     :on-click form-events/ok-new-entry-add} "Ok"]]]]]))

(defn entry-content-welcome
  []
  (let [text-to-show @(form-events/entry-form-field :welcome-text-to-show)]
    [:div {:class "gbox"
           :style {:margin 0
                   :width "100%"
                   :background "var(--secondary)"}}
     [:div {:class "gheader"}]
     [:div {:class "gcontent"}
      [mui-stack {:sx {:height "100%"
                       :align-items "center"
                       :justify-content "center"}}
       [mui-typography  {:variant "h6"}
        (if (not (nil? text-to-show))
          text-to-show
          "No entry content is selected. Select or create a new one")]]]
     [:div {:class "gfooter"}]]))

(declare history-entry-content)

(declare custom-entry-type-new-content)

(defn entry-content-core []
  (let [s (form-events/entry-form-field :showing)]
    (condp = @s
      :welcome
      [entry-content-welcome]

      :new
      [entry-content-new]

      :selected
      [entry-content]

      :history-entry-selected
      [history-entry-content]

      :custom-entry-type-new
      [custom-entry-type-new-content]

      [entry-content-welcome])))

;;;;;;;;;;;;;;;;;;;;;;;;; Custom Entry Type ;;;;;;;;;;;;;;;;;;;;;;;;

(defn entry-type-section-content [edit section-name section-data]
  (let [errors @(form-events/entry-form-field :error-fields)]
    [mui-box {:sx content-sx}
     [section-header section-name]
     (doall
      (for [{:keys [key
                    protected required] :as kv} section-data]
        ^{:key key} [mui-stack {:direction "row"}
                     [mui-stack {:direction "row" :sx {:width (if edit "92%" "100%")}}
                      [text-field (assoc kv
                                         :edit edit
                                         :error-text (get errors key)
                                         :visible @(form-events/visible? key)
                                         :placeholder "Some field value"
                                         :on-change-handler #())]]
                     [mui-stack {:direction "row" :sx {:width "8%" :align-items "flex-end"}}
                      [mui-tooltip  {:title "Modify Field" :enterDelay 2500}
                       [mui-icon-button {:edge "end"
                                         :on-click  #(form-events/open-section-field-modify-dialog {:key key
                                                                                                    :protected protected
                                                                                                    :required required
                                                                                                    :section-name section-name})}
                        [mui-icon-edit-outlined]]]
                      [mui-tooltip  {:title "Delete Field" :enterDelay 2500}
                       [mui-icon-button {:edge "end"
                                         :on-click #(form-events/field-delete section-name key)}
                        [mui-icon-delete-outline]]]]]))

     [custom-field-delete-confirm @(form-events/field-delete-dialog-data)]
     [custom-section-delete-confirm @(form-events/section-delete-dialog-data)]
     [add-modify-section-field-dialog @(form-events/section-field-dialog-data)]]))

(defn entry-type-all-sections-content []
  (let [{:keys [error-fields]
         {:keys [section-names section-fields]} :data} @(form-events/entry-form-all)
        error (:general error-fields)]
    [mui-stack
     [mui-stack
      (doall
       (for [section-name section-names]
         ^{:key section-name} [:<>
                               [entry-type-section-content
                                true section-name
                                (get section-fields section-name)]]))]
     (when error
       [mui-stack
        [mui-alert {:severity "error"} error]])]))

;; TODO: 
;; Duplicate name for entry type is possible as we use uuid to uniquely identify entry types
;; Need to warn user about the name duplication and proceed accordingly
(defn type-name-with-icon-field  []
  (let [{:keys [entry-type-name entry-type-icon-name]} @(form-events/entry-form-data-fields
                                                         [:entry-type-name :entry-type-icon-name])
        errors @(form-events/entry-form-field :error-fields)]
    [mui-box {:sx content-sx}
     [mui-stack {:direction "row" :spacing 1}
      [mui-stack {:direction "row" :sx {:width "90%" :justify-content "center"}}
       [text-field {:key "Entry Type"
                    :value entry-type-name
                    :edit true
                    :required true
                    :helper-text "Entry type name"
                    :error-text (:entry-type-name errors)
                    :on-change-handler (on-change-factory
                                        form-events/entry-form-data-update-field-value
                                        :entry-type-name)}]]

      [mui-stack {:direction "row"
                  :sx {:width "10%"
                       :justify-content "center"
                       :align-items "center"}}
       [mui-typography {:align "center"
                        :paragraph false
                        :variant "subtitle1"} "Icon"]
       [mui-icon-button {:edge "end" :color "primary" :sx {}
                         :on-click  show-icons-dialog}
        [entry-type-icon entry-type-name entry-type-icon-name]]]
      [icons-dialog @icons-dialog-flag (fn [idx]
                                         (form-events/entry-form-data-update-field-value
                                          :entry-type-icon-name (str idx)))]]]))

(defn entry-type-center-content []
  [mui-box
   [type-name-with-icon-field]
   [entry-type-all-sections-content]
   [add-section-content]])

(defn custom-entry-type-new-content []
  [:div {:class "gbox"
         :style {:margin 0
                 :width "100%"}}

   [:div {:class "gheader" :style {:background background-color1}}
    [mui-stack {:direction "row"  :sx {:width "100%" :justify-content "center"}}
     [mui-typography {:align "center"
                      :paragraph false
                      :variant "h6"}
      "New Custom Entry Type"]]]
   [:div {:class "gcontent" :style {:overflow-y "scroll"
                                    :background background-color1}}
    [entry-type-center-content]]

   [:div {:class "gfooter" :style {:margin-top 2
                                   :min-height "46px" ;; needed to align this footer with entry list 
                                   :background "var(--mui-color-grey-200)"}}

    [mui-stack {:sx {:align-items "flex-end"}}
     [:div.buttons1
      [mui-button {:variant "contained" :color "secondary"
                   :on-click form-events/cancel-new-custom-entry-type} "Cancel"]
      [mui-button {:variant "contained" :color "secondary"
                   :on-click form-events/create-custom-entry-type} "Create"]]]]

   #_[cc/message-sanckbar]
   #_[cc/message-sanckbar-alert (merge @(ce/message-snackbar-data) {:severity "info"})]])

;;;;;;;;;;;;;;;;;;;;;;;; Entry History ;;;;;;;;;;;;;;;;;;;;;;

(defn restore-confirm-dialog [dialog-show]
  [(alert-dialog-factory
    "Do you want to replace the current entry?"
    "The existing entry will be replaced with this histrory entry"
    [{:label "Yes" :on-click form-events/restore-entry-from-history}
     {:label "No" :on-click form-events/close-restore-confirm-dialog}])
   {:dialog-show dialog-show}])

(defn delete-confirm-dialog [dialog-show entry-uuid index]
  [(alert-dialog-factory
    "Do you want to delete the selected history entry?"
    "This version of history will be deleted permanently"
    [{:label "Yes" :on-click #(form-events/delete-history-entry-by-index entry-uuid index)}
     {:label "No" :on-click form-events/close-delete-confirm-dialog}])
   {:dialog-show dialog-show}])

(defn delete-all-confirm-dialog [dialog-show entry-uuid]
  [(alert-dialog-factory
    "Do you want to delete all history entries?"
    "All history entries for this entry will be deleted permanently"
    [{:label "Yes" :on-click #(form-events/delete-all-history-entries entry-uuid)}
     {:label "No" :on-click form-events/close-delete-all-confirm-dialog}])
   {:dialog-show dialog-show}])

(defn history-entry-content []
  (fn []
    (let [{:keys [title icon-id]} @(form-events/entry-form-data-fields [:title :icon-id])
          edit @(form-events/form-edit-mode)]
      [:div {:class "gbox"
             :style {:margin 0
                     :width "100%"}}

       [:div {:class "gheader" :style {:background background-color1}}
        (when-not edit
          [mui-stack {:direction "row"}
           [mui-stack {:direction "row"  :sx {:width "95%" :justify-content "center"}}
            [entry-icon icon-id]
            [mui-typography {:align "center" :paragraph false :variant "h6"} title]]])]

       [:div {:class "gcontent" :style {:overflow-y "scroll"
                                        :background background-color1}}
        [center-content]
        [restore-confirm-dialog @(form-events/restore-flag)]
        [delete-confirm-dialog
         @(form-events/delete-flag)
         @(form-events/loaded-history-entry-uuid)
         @(form-events/selected-history-index)]]

       [:div {:class "gfooter" :style {:margin-top 2
                                       :min-height "46px" ;; needed to align this footer with entry list 
                                       :background "var(--mui-color-grey-200)"}}

        [mui-stack {:sx {:align-items "flex-end"}}
         [:div.buttons1
          [mui-button {:variant "contained" :color "secondary"
                       :on-click form-events/show-delete-confirm-dialog} "Delete"]
          [mui-button {:variant "contained" :color "secondary"
                       :on-click form-events/show-restore-confirm-dialog} "Restore"]]]]])))

(defn history-entry-row-item
  "Renders a list item. 
  The arg 'props' is a map passed from 'fixed-size-list'
  "
  [props]
  (fn [props]
    (let [items  (form-events/history-summary-list)
          {:keys [uuid
                  title
                  secondary-title ;; Datetime formatted string in Local tz
                  icon-id
                  history-index]} (nth @items (:index props))
          selected-id (form-events/selected-history-index)]
      ;;(println "props " props)
      ;;(println "secondary-title is " secondary-title)
      [mui-list-item {:style (:style props)
                      ;; Need to work on this
                      ;;:sx {:border-bottom ".0001px solid" :border-color "grey"}
                      :button true
                      :value uuid
                      :on-click #(do
                                   (form-events/update-history-index-selection  uuid history-index))
                      :selected (if (= @selected-id history-index) true false)
                      ;;:secondaryAction (when (= @selected-id (:uuid item)) (r/as-element [mui-icon-button {:edge "end"} [mui-icon-more-vert]]))
                      }
       [mui-list-item-avatar
        [mui-avatar [entry-icon icon-id]]
        #_[mui-avatar [mui-icon-vpn-key-outlined]]]
       [mui-list-item-text
        {:primary title
         :secondary  secondary-title}]])))

(defn history-list-content []
  (let [entry-items (list-items-factory
                     (form-events/history-summary-list)
                     history-entry-row-item
                     :item-size 60
                     :list-style {:max-width nil}
                     :div-style {:width "100%"})]
    [mui-stack {:sx {:width "100%"}}
     [mui-stack {:sx {:height "40px"
                      :justify-content "center"
                      :align-items "center"}}
      [mui-typography {:align "center" :paragraph false :variant "subtitle2"} "Previous Versions"]]

     [entry-items]
     [delete-all-confirm-dialog
      @(form-events/delete-all-flag)
      @(form-events/loaded-history-entry-uuid)]

     [mui-stack {:sx {:min-height "46px"
                      :align-items "center"
                      :background m/color-grey-200}}
      [:div {:style {:margin-top 10 :margin-bottom 10 :margin-right 5 :margin-left 5}}
       [mui-button {:variant "outlined"
                    :color "inherit"
                    :on-click form-events/show-delete-all-confirm-dialog} "Delete All"]]]]))

(defn entry-history-content-main []
  (let [entry-uuid  @(form-events/loaded-history-entry-uuid)]
    ;;(println "entry-uuid is " entry-uuid)
    [mui-stack {:direction "row"
                :divider (r/as-element [mui-divider {:orientation "vertical" :flexItem true}])
                :sx {:height "100%" :overflow-y "scroll"}}
     [mui-stack {:direction "row" :sx {:width "45%"}}
      #_"Some details come here"
      [mui-stack {:direction "row"
                  :divider (r/as-element [mui-divider {:orientation "vertical" :flexItem true}])
                  :sx {:width "100%"}}

       [mui-stack {:direction "row"
                   :sx {:width "50%"
                        :justify-content "center"
                        :align-items "center"}}
        [mui-box
         [mui-typography "Found History Entries"]
         [mui-stack
          [mui-link {:sx {:text-align "center"
                          :color "primary.main"}
                     :underline "always"
                     :variant "subtitle1"
                     :onClick #(form-events/history-content-close entry-uuid)}
           "Back to Entry"]]]]
       [mui-stack {:direction "row" :sx {:width "50%" :overflow-y "scroll"}}
        [history-list-content]]]]
     [mui-stack {:direction "row"
                 :sx {:width "55%"}}
      [entry-content-core]]]))

