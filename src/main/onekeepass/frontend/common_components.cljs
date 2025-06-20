(ns onekeepass.frontend.common-components
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.constants :refer [ADD_TAG_PREFIX]]
   [onekeepass.frontend.events.common :as cmn-events]
  ;;  [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.mui-components :as m :refer [auto-sizer
                                                     fixed-size-list
                                                     is-light-theme? mui-alert
                                                     mui-autocomplete mui-box
                                                     mui-button mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-content-text
                                                     mui-dialog-title
                                                     mui-icon-button
                                                     mui-icon-close-outlined
                                                     mui-icon-file-copy-outlined
                                                     mui-linear-progress
                                                     mui-snackbar mui-stack
                                                     mui-text-field-type
                                                     mui-text-field
                                                     mui-tooltip
                                                     mui-typography
                                                     react-use-state]]
   [onekeepass.frontend.translation :refer-macros [tr-bl] :refer [lstr-sm]]
   [onekeepass.frontend.utils :refer [contains-val? str->int]]
   [reagent.core :as r]
   [reagent.ratom]))

#_(set! *warn-on-infer* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;; Styles ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def entry-cnt-field-margin-top "16px") ;; twice --mui-theme-spacing-1 

;; "&.MuiInput-root" works; "& .MuiInput-root" did not work 
;; However few other cases like "& .MuiInputBase-input", we need to use with space after &
;; Why? 
;; We should not have space when the sx is meant for the immediate slot and we need a space when we meant sx for 
;; children ? 

#_(defn theme-text-field-read-sx [theme-mode]
    {"&.MuiInput-root"
     {:border 0
      :border-bottom  (if (is-light-theme?)
                        "1px solid #eeeeee" (str "1px solid " "rgba(255, 255, 255, 0.3)"))
      ;;  :border-bottom-color "#eeeeee"
      ;;  :border-bottom-style "solid"
      ;;  :border-bottom-width "1px"
      }

     ;;  "&.Mui-focused"
     ;;  {:border "1px solid var(--color-primary-main)"
     ;;   :border-bottom "1px solid var(--color-primary-main)"}
     })

#_(defn theme-text-field-edit-sx [theme-mode]
    {"&.MuiInput-root"
     {:border "1px solid grey"
      :outline "1px solid transparent"}

     "&.Mui-focused"
     {:border "1px solid var(--color-primary-main)"
      :outline "1px solid var(--color-primary-main)"}})

(defn theme-text-field-sx [edit theme]
  (if edit
    {"&.MuiInput-root"
     {:border "1px solid grey"
      :outline "1px solid transparent"}

     "&.Mui-focused"
     {:border "1px solid var(--color-primary-main)"
      :outline "1px solid var(--color-primary-main)"}}

    {"&.MuiInput-root"
     {:border 0
      :border-bottom  (if (is-light-theme? theme)
                        "1px solid #eeeeee" (str "1px solid " "rgba(255, 255, 255, 0.3)"))
      ;;  :border-bottom-color "#eeeeee"
      ;;  :border-bottom-style "solid"
      ;;  :border-bottom-width "1px"
      }}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;; Using Autocomplte ;;;;;;;;;;;;;;;;;;;;;;;;;;;;  

(def filter-fn (m/auto-complete-filter (clj->js {:matchFrom "start"})))

;; Also see onekeepass.frontend.events.common/fix-tags-selection-prefix how the added 
;; ADD_TAG_PREFIX is removed to form the final list 
(defn tags-filter-options
  "A function that determines the filtered options to be rendered on search
   The arg 'options' is the options to render
   The arg 'params' is the current state of the component
   Returnse a js array 
   "
  [^js/Okp.Options options ^js/Okp.Params params]
  (let [;; value-entered is the value entered in the Tag box  
        ;; instead of selecting any previous Tags from the list
        value-entered (.-inputValue params)

        ;; Find any matching value from the options if any 
        ;; and that is returned in a seq if found
        existing (filter #(= % value-entered) options)

        ;; When user clicks to the input,
        ;; all values are returned to show in select list
        ;; As we type, all matching values are filtered and returned
        ;; and shown in the select list. 
        filtered (js->clj (filter-fn options params))

        final-filtered (if (and (boolean (seq value-entered))
                                (empty? existing))
                         ;; If the option is one added by user, then we need add the prefix
                         ;; and that value is shown as the last option in the selection list
                         (conj filtered (str ADD_TAG_PREFIX " " value-entered))
                         filtered)]
    (clj->js final-filtered)))

(defn tags-field
  "Returns a reagent component for the MUI Autocomplete (react component) with multiple 
   values also known as tags

   The arg 'all-tags' is a vector of all availble tags
   The arg 'tags' is a vector of tags that is used as 'value' 
   The arg 'on-tags-selection' is a fn that is called when the value changes 
   and its Signature is function(event: React.SyntheticEvent, value: Value | Array, reason: string, details?: string) => void
 "
  [all-tags tags on-tags-selection editing]
  [mui-autocomplete
   {:disablePortal true
    :multiple true
    :classes {}
    :disableClearable (not editing)
    ;;removes the X from Chip component
    :ChipProps (when-not editing  {:onDelete nil})
    :disabled (not editing)
    ;;:readOnly (not editing) ;;Not working 

    :id "tags-listing"
    :options (if (nil? all-tags) [] all-tags)

    ;; A function that determines the filtered options to be rendered on search
    :filterOptions tags-filter-options

    ;; The same structure as 'option' passed to getOptionLabel or renderOption
    :value (clj->js tags)
    :on-change on-tags-selection

    ;; Render the input - a fn that returns a ReactNode
    :render-input (fn [^js/Okp.params params]
                    ;; use an atom p in this ns and do (reset! p params)
                    ;; to see props of params in repl

                    ;; Don't call js->clj because that would recursively
                    ;; convert all JS objects (e.g. React ref objects)
                    ;; to Cljs maps, which breaks them
                    (set! (.-variant params) "standard")
                    (set! (.-label params) "Tags")
                    ;;(println "InputProps is " (.-label params)) results in stackoverflow
                    (r/create-element mui-text-field-type params)) ;;mui/TextField
    }])

(defn selection-autocomplete
  "All required fields and optional ones are passed in one map argument
  Returns a form-1 reagent component 'mui-autocomplete' which lists of option for the user select or search and select
  "
  [{:keys [label options current-value on-change id
           disablePortal required error error-text helper-text]
    :or [disablePortal false]}]
  [mui-autocomplete
   {:disablePortal disablePortal
    :disableClearable true
    :id id
    :options options

    ;; The same structure as 'option' passed to getOptionLabel or renderOption
    :value (clj->js current-value)

    :on-change on-change ;; a callback function that accespts two parameters [event info]
    :isOptionEqualToValue (fn [^js/Okp.Option option ^js/Okp.Value value]
                            ;;(println "option" option " and value " value)
                            (= (.-name option) (.-name value)))

    ;; We need to use getOptionLabel if option does not have "label" property
    :getOptionLabel (fn [^js/Okp.Option option] (.-name option))

    ;; Custom rendering of each item in the selection list
    :renderOption (fn [^js props ^js/Okp.Option option]
                    (let [p (js->clj props :keywordize-keys true)
                          p (merge p {:component "li"} p)]
                      (r/as-element [mui-box p (.-name option) #_[:strong (str "Icon-" (.-title option))]])))

    :render-input (fn [^js/Okp.Params params]
                    ;;Calling (println "params " params) or (js/Object.entries params) itself results in Error:
                    ;;RangeError: Maximum call stack size exceeded. 
                    ;;It looks like calling directly or indirectly js->clj resulted in stack overflow 

                    ;;(type params) works
                    ;;(js/Object.keys params) 
                    ;; => #js["id" "disabled" "fullWidth" "size" "InputLabelProps" "InputProps" "inputProps" "variant" "label"]

                    ;;(.-InputProps params) resulted in stack overflow
                    ;;(js/Object.keys (.-InputProps params)) 
                    ;;=> #js["ref" "className" "startAdornment" "endAdornment"]
                    ;;Same error when we do (.-endAdornment (.-InputProps params))
                    ;;(js/Object.keys (.-endAdornment (.-InputProps @pa))) 
                    ;;=> #js["$$typeof" "type" "key" "ref" "props" "_owner" "_store"]

                    ;;Accessing some properties resulted "RangeError: Maximum call stack size exceeded".
                    ;;Not sure why. May be a bug in clojurescript or javascript object circular references?

                    ;;The :renderInput idea used for "mui-date-time-picker" did not work
                    ;;A solution based on
                    ;;From https://stackoverflow.com/questions/63944323/problem-with-autocomplete-material-ui-react-reagent-clojurescript

                    ;; Don't call js->clj because that would recursively
                    ;; convert all JS objects (e.g. React ref objects)
                    ;; to Cljs maps, which breaks them, even when converted back to JS.
                    ;; Best thing is to use r/create-element and
                    ;; pass the JS params to it.
                    ;; If necessary, use JS interop to modify params.
                    ;;(println "required is " (.-required params))
                    (set! (.-variant params) "standard")
                    (set! (.-label params) label)
                    (set! (.-error params) error)
                    (set! (.-helperText params) (if error error-text helper-text))
                    (set! (.-required params) required)
                    (r/create-element mui-text-field-type params)) ;;mui/TextField
    }])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

(defn- render-row-factory
  "Returns a function that can be used as child to fixed-size-list.
  The argument 'row-component-fn' is a reagent fn
  "
  [row-component-fn]
  ;; This function is passed as child to 'fixed-size-list'
  ;; The 'fixed-size-list' component calls this function with appropriate 'props'
  (fn [props]
    (let [props (js->clj props :keywordize-keys true)]
      (r/as-element [row-component-fn props]))))

(defn- row-items-factory
  "Returns a reagent function that is used to create a row item for the row data found in the 
  atom 'items'
  The arg 'row-item-fn' is a function that is passed as child in 'fixed-size-list'. 
  This 'row-item-fn' is the returned value of the function of 'render-row-factory'
  The various options are passed in the map 'options'
  "
  [items row-item-fn  {:keys [item-size scroll-to-item-index list-style div-style]
                       :or {item-size 60
                            scroll-to-item-index 0
                            div-style {}
                            list-style {:max-width 275}}}]
  ;; This component makes use of 'fixed-size-list' and 'auto-sizer' (Function-as-child Components)
  ;; which expect a function as their only child. 
  ;; See 
  ;; https://github.com/reagent-project/reagent/blob/master/doc/InteropWithReact.md#example-function-as-child-components
  ;; https://github.com/bvaughn/react-virtualized/blob/master/docs/usingAutoSizer.md
  ;; https://mui.com/components/lists/#virtualized-list

  (fn
    []
    ;;(println "opts in row-item-fn is" options)
    ;; Need to set this so that splitpane's left side has some min width when content is small 
    [:div {:style (merge {:min-width 200 :height "100%" :width "275"} div-style)}
     ;;AutoSizer needs to be a child of div for its to work within a flex container  
     (let [list-items (if (instance? reagent.ratom/Reaction items) @items items)]
       (when  (not-empty list-items) #_(not-empty @items)
              [auto-sizer {}
               ;; auto-sizer child should be a function 
               (fn [dims]
                 (let [dims (js->clj dims :keywordize-keys true)]
                   ;; (println "dims are " dims)
                   (r/as-element
                    [:div {:style {:min-width 200 :height (:height dims)}}
                     ;; :> m/MyFixedSizeList 
                     [fixed-size-list {:style list-style ;;{:max-width 275} ;; Need to set this so that splitpane's left side  does not expand
                                       :height (:height dims)
                                       :width (:width dims)
                                       ;; this is the size of the row component returned by render-row fn
                                       :itemSize item-size
                                       :overflow-y "scroll"
                                       ;; Need to get ref to access methods scrollToItem or scrollTo
                                       ;; See https://react-window.vercel.app/#/api/FixedSizeList
                                       ;; See https://react-window.vercel.app/#/examples/list/scroll-to-item
                                       :ref (fn [^js/Ref e]
                                              (some-> e (.scrollToItem scroll-to-item-index)))
                                       :itemCount (count list-items)
                                       :overscanCount 1} row-item-fn]])))]))]))

(defn list-items-factory
  "Returns a function that can be used as a child in fixed-size-list.
  The argument 'items' is an atom that has all the rows data
  The argument 'row-component-fn' is a reagent fn
  All optional things are passed as keyword arguments
  "
  [items row-component-fn & {:as opts}]
  ;;(println "opts is" opts)
  (row-items-factory items (render-row-factory row-component-fn) opts))

(defn alert-dialog-factory
  "
  Creates a reagent dialog component with a given text content.
  The args are 
  title     the dialog title
  body-text the dialog body content
  actions   is a vector of maps with keys [label on-click].
  "
  [title body-text actions]
  ;; Form 2 reagent component that expects a map in the argument
  (fn [{:keys [dialog-show status]}]
    [mui-dialog {:open dialog-show
                 :on-click #(.stopPropagation ^js/Event %)
                 :sx {"& .MuiPaper-root" {:width "60%"}}}
     [mui-dialog-title title]
     [mui-dialog-content
      [mui-dialog-content-text body-text]]
     [mui-dialog-actions
      (for [{:keys [label on-click]}  actions]
        ^{:key label} [mui-button {:color "secondary"
                                   :disabled (= status :in-progress)
                                   :on-click on-click} label])]]))

(defn dialog-factory
  "Returns a form-2 reagent component that accepts a map that contains 'dialog-data' and optional additional data
  The args for this factory function are
  title         - the dialog title
  content-comp  - reagent component that accepts one or two parameters as map and provides the dialog content
  actions       - a vector of maps with keys [label on-click-factory]
  props         - an optional map for this factory like passing sx prop of mui-dialog etc

  See group-tree-content/move-dialog how it is used
  "
  [title content-comp actions & _props]
  ;; form-2 reagent component function
  (fn
    [{{:keys [dialog-show status]} :dialog-data :as all-data}]
    [mui-dialog {:open dialog-show
                 :on-click #(.stopPropagation ^js/Event %)
                 :sx {"& .MuiPaper-root" {:width "60%"}}} ;; sx props is equivalent to :classes {:paper "pwd-dlg-root"}

     [mui-dialog-title title]
     [mui-dialog-content [content-comp all-data]]
     [mui-dialog-actions
      ;; Each 'on-click-factory' in the of actions is a factory function which takes the 'all-data' map
      ;; and use any changed data from that map in creating the on-click event function and this returned fuction
      ;; is called when user clicks button  
      ;; TODO: We can pass  on-click or on-click-factory and assign to :on-click prop accordingly
      ;; For example :on-click (if (nil? on-click) (on-click-factory all-data) on-click)
      (for [{:keys [label on-click-factory]} actions]
        ^{:key label} [mui-button {:color "secondary"
                                   :disabled (= status :in-progress)
                                   :on-click (on-click-factory all-data)} label])]]))

;; TODO: 
;; Rename confirm-text-dialog as common-dialog as we can 
;; now use a reagent component as body instead of simple text 

(defn confirm-text-dialog
  "Shows some text message and when user takes some action, a progress bar is shown. The dialog is 
  closed on completting the work requested or shows any error 
  "
  [title content-component actions {:keys [dialog-show status api-error-text] :as dialog-data}]
  [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)}
   [mui-dialog-title title]
   [mui-dialog-content {:dividers true :style {:min-height "100px"}}
    [mui-stack
     ;; content-component may be a simple text string or an valid reagent component accepting a map as arg or nil
     (if (string? content-component)
       content-component
       [content-component dialog-data])

     (when api-error-text
       [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])
     (when (and (nil? api-error-text) (= status :in-progress))
       [mui-stack
        "Working.."
        [mui-linear-progress {:sx {:mt 2}}]])]]
   [mui-dialog-actions
    (for [{:keys [label on-click]}  actions]
      ^{:key label} [mui-button {:color "secondary"
                                 :disabled (= status :in-progress)
                                 ;; The fn 'on-click' may accept a map as an optional arg or nil
                                 :on-click (fn []
                                             (on-click dialog-data))} label])]])

;; TODO: 
;; Do we really need this? It looks like this dialog is not working properly 
;; where it is used now

(defn info-dialog
  "Creates a reagent component to show a dialog with some information when a back ground work is in progress
  Important: Last parameter map should have keys status api-error-text to show error or linear progress"
  [title body-text close-fn {:keys [dialog-show status api-error-text]}]
  ;;(println "info-dialog is called .. m" m)
  [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)}
   [mui-dialog-title title]
   [mui-dialog-content
    [mui-stack
     body-text
     (when api-error-text
       [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])
     (when (and (nil? api-error-text) (= status :in-progress))
       [mui-linear-progress {:sx {:mt 2}}])]]
   [mui-dialog-actions
    [mui-button {:color "secondary"
                 :disabled (= status :in-progress)
                 :on-click close-fn} (tr-bl close)]]])

(defn error-info-dialog
  "Creates a reagent component to show a dialog"
  ([{:keys [title dialog-show message error-text]}]
   [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)}
    [mui-dialog-title title]
    [mui-dialog-content
     [mui-stack
      message
      (when error-text
        [mui-alert {:severity "error" :sx {:mt 1}} error-text])]]
    [mui-dialog-actions
     [mui-button {:color "secondary"
                  :on-click cmn-events/close-error-info-dialog} (tr-bl close)]]])
  ([]
   (error-info-dialog @(cmn-events/error-info-dialog-data))))

(defn message-dialog
  "Called to show any message to the user and no action is done other than closing the dialog"
  ([{:keys [title dialog-show message]}]
   [mui-dialog {:open dialog-show :on-click #(.stopPropagation ^js/Event %)}
    [mui-dialog-title title]
    [mui-dialog-content {:dividers true :style {:min-height "100px"}}
     [mui-dialog-content-text message]]
    [mui-dialog-actions
     [mui-button {:color "secondary"
                  :on-click cmn-events/close-message-dialog} (tr-bl ok)]]])
  ([]
   [message-dialog @(cmn-events/message-dialog-data)]))

;; This modal dialog just shows some progress action and user can not close
;; Need to ensure that :common/progress-message-box-hide is called for every
;; :common/progress-message-box-show
(defn progress-message-dialog
  ([{:keys [title dialog-show message]}]
   [mui-dialog {:classes {:paper "pwd-dlg-root"}
                :open dialog-show
                :on-click #(.stopPropagation ^js/Event %)}
    [mui-dialog-title title]
    [mui-dialog-content {:dividers true}
     [mui-stack message]
     [mui-linear-progress {:sx {:mt 2}}]]
    [mui-dialog-actions]])

  ([]
   [progress-message-dialog @(cmn-events/progress-message-dialog-data)]))

;; TODO: 
;; Need to combine message-sanckbar and message-sanckbar-alert
(defn message-sanckbar
  "Called to show any generic message in a snack bar 
  that pops at the bottom of the app content and stays for 6 sec"
  ([{:keys [open message]}]
   [mui-snackbar {:open  open
                  :action (r/as-element [mui-icon-button
                                         {:color "inherit"
                                          :on-click cmn-events/close-message-snackbar}
                                         [mui-icon-close-outlined]])
                  ;; Mostly message is a quoted symbol key and used 
                  ;; in lstr-sm to get the translated text
                  ;; If the message is a string, then it is assumed either
                  ;; we have already a translated text or some text string which
                  ;; is yet to be translated
                  :message (lstr-sm message)
                  :auto-hide-duration 6000
                  :on-close cmn-events/close-message-snackbar}])
  ([]
   [message-sanckbar @(cmn-events/message-snackbar-data)]))

(defn message-sanckbar-alert
  "Mostly called to show error message"
  ([{:keys [open message severity]
     :or [severity "success"]}]
   [mui-snackbar {:open  open
                  ;;:auto-hide-duration 6000
                  :on-close cmn-events/close-message-snackbar-alert}

    [mui-alert {:sx {:width "100%"}
                :severity severity
                :on-close cmn-events/close-message-snackbar-alert} message]])
  ([]
   [message-sanckbar-alert @(cmn-events/message-snackbar-alert-data)]))

;; When this field has the focus, Shift + Down key will show the menu items and can then be used to move up or down.
;; Also if we press the first letter (case sensitive ) of any options, then cursor moves to that menu item option

;; Saw this warning: MUI: You have provided an out-of-range value `2024` for the select component.
;; See https://lightrun.com/solutions/mui-material-ui-suppress-the-material-ui-select-component-out-of-range-error/
;; May need to fix if any issue comes up

;; TODO: 
;; Also change in 'src/main/onekeepass/frontend/entry_form/fields.cljs' to use this common one

(defn simple-selection-field [{:keys [id
                                      field-name
                                      value
                                      edit
                                      error-text
                                      helper-text
                                      on-change-handler
                                      select-field-options]} & {:as opts}]
  ;; We are using the mui-text-field directly as select component 
  ;; This type of simple select list can also be done using the following components 
  ;; as given in the examples found mui.com which uses now this method
  ;; [mui-form-control [mui-input-label] [mui-select {} [mui-menu-item]] [mui-form-helper-text]  ]
  [mui-text-field {:id (if (nil? id) field-name id)
                   :sx (:sx opts)
                   :required false
                   :select true
                   :label field-name
                   :value value
                   :on-change on-change-handler
                   :error  (not (nil? error-text))
                   :helperText (if (nil? error-text) helper-text error-text)
                   :inputProps  {:readOnly (not edit)}
                   :variant "standard" :fullWidth true}
   (doall (for [y select-field-options]
            (let [{:keys [value label]} (if (string? y) {:value y :label y} y)]
              ^{:key y} [m/mui-menu-item {:value value} label])))])


(defn enter-key-pressed-factory
  "Returns a on-click function which calls the passed function 'to-call' when the Enter key is pressed"
  [to-call]
  (fn [^js/Event e]
    (when (= (.-key e) "Enter")
      (to-call))))

;; Another way of calling focus of an element
#_(defn focus [^js/InputRef comp-ref]
    ;; calling  (.getElementById js/document "search_fld") will also work. But 'id' of input element 
    ;; should be unique
    #_(.focus (.getElementById js/document "search_fld"))
    (if-let [comp-id (some-> comp-ref .-props .-id)]
      (.focus (.getElementById js/document comp-id))
      (println "inputRef called back with invalid ref or nil ref")))

(defn write-to-clipboard [value]
  (bg/write-to-clipboard value))

(defn copy-icon-factory
  "Returns a form-2 reagent component that on click copies the 'value' 
  field to clipboard"
  ([on-click-fn]
   (fn []
     [mui-icon-button {:edge "end"
                       :on-click #(on-click-fn)}
      [mui-icon-file-copy-outlined]]))
  ([]
   (fn [value & {:as props}]
     [mui-icon-button {:edge "end"
                       :sx (:sx props)
                       :on-click #(bg/write-to-clipboard value)}
      [mui-icon-file-copy-outlined]])))

#_(defn on-change-factory
    "A function factory 
   The arg 'handler-name' is a fn that is called with supplier arg 'field-name-kw' and 
   the event value
   Returns a function that can be used in a on-change handler of a text field
  "
    [handler-name field-name-kw]
    (fn [^js/Event e]
      (handler-name field-name-kw (-> e .-target  .-value))))

(defn on-change-factory
  "A function factory 
     The arg 'handler-name' is a fn that is called 
     with supplied arg 'field-name-kw' and the event value
     Returns a function that can be used in a on-change handler of a text field
  "
  ([handler-name field-name-kw int-val]
   (fn [^js/Event e]
     (let [val (-> e .-target  .-value)
           ;; Need to ensure that length is an int as expected by backend api
           val (if int-val  (str->int val) val)]
       (handler-name field-name-kw val))))
  ([handler-name field-name-kw]
   (on-change-factory handler-name field-name-kw false)))

(defn on-check-factory
  "Called in on-change handler of a check field.
   Returns a fn that accepts check event 
   and this fn in turn calls the 'handler-name' fn with 'field-name-kw' and value
   "
  [handler-name field-name-kw]
  (fn [^js/CheckedEvent e]
    (handler-name field-name-kw (-> e .-target  .-checked))))

(defn overflow-tool-tip
  "Tooltip is enabled only when the text has ellipsis shown
  This is based on tips in 
  https://stackoverflow.com/questions/56588625/react-show-material-ui-tooltip-only-for-text-that-has-ellipsis
  We need to use useState though the examples shown above discussion in stackoverflow uses useEffect
  https://github.com/reagent-project/reagent/blob/master/doc/ReactFeatures.md  ( Using Hooks)

  An attempt to use r/atom based for 'disable' flag refreshes this component continuously 
  and was not completely successful
  "
  [display-name]
  (let [[disable set-disable] (react-use-state true)]
    ;;(println display-name " disable value is " disable)
    (when-not (str/blank? display-name)
      [mui-tooltip {:title display-name :disableHoverListener (not disable)}
       [mui-typography {:ref (fn [^js/HTMLParagraphElement e]
                               ;; e is js object - #object[HTMLParagraphElement [object HTMLParagraphElement]]
                               ;; .-scrollWidth .-clientWidth are properties of this Elemment
                               ;; See https://developer.mozilla.org/en-US/docs/Web/API/HTMLParagraphElement
                               (when-not (nil? e)
                                 (set-disable (> (.-scrollWidth e) (.-clientWidth e)))))
                        :sx {:white-space "nowrap"
                             :text-overflow "ellipsis"
                             :overflow "hidden"}}
        display-name]])))

(defn password-helper-text-sx [score]
  (cond
    (contains-val? ["VeryDangerous" "Dangerous" "VeryWeak"] score)
    {"& .MuiFormHelperText-root" {:color "error.dark" :font-weight "700"}}

    (= score "Weak")
    {"& .MuiFormHelperText-root" {:color "warning.dark" :font-weight "700"}}

    (= score "Good")
    {"& .MuiFormHelperText-root" {:color "success.light" :font-weight "700"}}

    (= score "Strong")
    {"& .MuiFormHelperText-root" {:color "success.light" :font-weight "700"}}

    (= score "VeryStrong")
    {"& .MuiFormHelperText-root" {:color "success.main" :font-weight "700"}}

    (= score "VeryStrong")
    {"& .MuiFormHelperText-root" {:color "success.dark" :font-weight "700"}}

    (= score "Invulnerable")
    {"& .MuiFormHelperText-root" {:color "success.dark" :font-weight "700"}}

    :else
    {}))

(defn menu-action
  " The arg 'action' is a fn 
    The arg 'action-args' is one or more arguments that are passed to the action fn
    Returns a fn that is used as on-click handler of a menu item
  "
  [anchor-el action & action-args]
  (fn [^js/Event e]
    (reset! anchor-el nil)
    (apply action action-args)
    (.stopPropagation ^js/Event e)))


(def cipher-algorithms [{:name "AES 256" :value "Aes256"} {:name "ChaCha20 256" :value "ChaCha20"}])

(def kdf-algorithms [{:name "Argon 2d (KDBX 4)" :value "Argon2d"} {:name "Argon 2id (KDBX 4)" :value "Argon2id"}])



#_(fn [e] (println "ref is called ..." (when-not (nil? e)
                                         (js/Object.getOwnPropertyNames e)
                                         (println "  ref acess " (-> e (.scrollToItem 1))
                                                  #_(-> e  .-state .-instance js/Object.entries #_js/Object.getOwnPropertyNames)
                                                  #_(-> e  js/Object.entries)
                                                  #_(js/Object.entries (.-_outerRef e))))))