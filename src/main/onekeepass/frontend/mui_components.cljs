(ns
 onekeepass.frontend.mui-components
  (:require-macros [onekeepass.frontend.mui-comp-classes
                    :refer  [declare-mui-classes]])
  (:require
   [reagent.core :as r]
   [reagent.impl.template :as rtpl]
   [goog.object :as gobj]

   ;; All node package modules (npm) that are loaded by webpack bundler
   ;; Also see src/main/onekeepass/frontend/background.cljs for other npm packages
   [react]
   ["@mui/material" :as mui]
   ["@mui/icons-material" :as mui-icons]
   ["@mui/material/colors" :as mui-colors]
   ["@mui/material/styles" :as mui-mat-styles]
   ["@mui/x-tree-view" :as mui-x-tree-view]
   ["@mui/x-date-pickers" :as mui-x-date-pickers]
   ["@mui/x-date-pickers/AdapterDateFns" :as mui-x-adapter-date-fns]
   ["@mui/material/Autocomplete" :as mui-ac]

   ["@date-io/date-fns" :as DateAdapter]

   ;; This is for local node package to compile and install the local package
   ;; See src-js/README.md for details. For now we are not using
   #_["onekeepass-local" :as okp-local]

   ["react-split-pane" :as sp]
   ["react-window" :as react-window]
   ["react-virtualized-auto-sizer" :as vas]))

(set! *warn-on-infer* true)

;; All npm packages refered in :require above are loaded from node_modules using 'require' fn
;; See target/public/cljs-out/dev/npm_deps.js 
;; Refer https://figwheel.org/docs/npm.html to know how npm 
;; modules are bundled for both dev time and production time using
;; webpack

(def react-use-state ^js/UseState (.-useState ^js/React react)) ;; (.-useState ^js/React react)
(def react-use-effect ^js/UseEffect (.-useEffect ^js/React react))
#_(def react-use-ref (.-useRef ^js/React react))

#_(def use-idle-timer (.-useIdleTimer ^js/ReactIdleTimer react-idle-timer))

;; datetime picker component v 5.x used date-fns DateAdapter
;; datetime picker component v 6.x needs to use this
(def adapter-date-fns ^js/AdapterDateFns (.-AdapterDateFns  mui-x-adapter-date-fns))

;; https://github.com/dmtrKovalenko/date-io
;; DateAdapter is #js{:default #object[DateFnsUtils]}
;; (.-default DateAdapter) is #object[DateFnsUtils]
;; (type date-adapter)  is #object[Function]
(def date-adapter ^js/DateAdapter (.-default  DateAdapter))

;; (Object.keys date-fns-utils) will give all available fuctions from this util
(def ^js/DateAdapter.Utils date-fns-utils (date-adapter.))

;; TODO: Need to replace split-spane use with our custom one or another actively maintained lib
;;s p is #js{:default #object[SplitPane], :Pane #object[Pane]}
(def split-pane
  "A reagent component formed from react componet found in default property of sp "
  (reagent.core/adapt-react-class ^js/SplitPane (.-default ^js/SplitPane sp)))


;;;;;;;;;;;;;;; Issue found while creating reagent component from react component 'react-window' ;;;;; 

;; After Tauri 2 upgrade, saw some issues with compling all @tauri-apps/* packages by cljs in REPL.
;; Upgraded clojurescript and it worked.

;; However during production build time, we could compile cljs 'main_bundle.js' with advanced option of closure compiler. 
;; But when the final built app was lauched,the cljs bundle failed to load because of failure of 'adapt-react-class' call with 'my-fix-list' with error
;; "Error: Assert failed: Component must not be nil" 
;; And the UI part of app failed to lauch

;; But when compiled the cljs 'main_bundle.js' with simple option, the final app build worked without any runtime error

;; Could not find where the problem is - is it in advanced compiled code (main.js) or the final webpacking the code 'main_bundle.js' ?

;; For now decided to use 'simple' option. The size of the final build increase around 3 to 4 MB as the 
;; size of 'main_bundle.js' increased

(def ^js/FixedSizeListObj my-fix-list ^js/FixedSizeList (.-FixedSizeList react-window))

#_(js/console.log "my-fix-list is " my-fix-list)

;;rw is #js{:VariableSizeGrid #object[Grid], :VariableSizeList #object[List], :FixedSizeGrid #object[Grid], :FixedSizeList #object[List], :areEqual #object[areEqual], :shouldComponentUpdate #object[shouldComponentUpdate]}
(def fixed-size-list
  "A reagent component formed from react componet FixedSizeList"
  (reagent.core/adapt-react-class my-fix-list #_(gobj/get react-window "FixedSizeList") #_(.-FixedSizeList react-window)))

#_(def MyFixedSizeList (-> (js->clj react-window :keywordize-keys true) :FixedSizeList))

;;;;;;;;;;;;;

;; vas is #js{:default #object[AutoSizer]}
(def auto-sizer
  "A reagent component formed from react componet AutoSizer"
  (reagent.core/adapt-react-class ^js/VirtualAutoSizer (.-default vas)))

(def auto-complete-filter
  "Autocomplete component exposes a factory to create a filter method 
   that can be provided to the filterOptions prop. 
   This is used to change the default option filter behavior."
  ^js/Mui.Autocomplete (.-createFilterOptions mui-ac))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  OneKeePass Custom Theme  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private create-theme mui-mat-styles/createTheme)

(def color-grey ^js/Mui.Colors (.-grey  mui-colors))

;; We can change the theme type to 'light' or 'dark' by reseting this atom
(defonce theme-mode (r/atom "light"))

(defn is-light-theme? [^js/Mui.Theme theme]
  (= "light" (-> theme .-palette .-mode)))

;; IMPORTANT: 
;; We should not use something like  
;; '(def bg-color (theme-color @custom-them-atom :entry-content-box-border))'
;; The 'custom-them-atom' might have not been created yet
;; The 'create-custom-theme' is only called when the app start page is mounted and 
;; then only we can use 'custom-them-atom'. Using inside component's is fine
(defonce custom-theme-atom (r/atom {}))

(defn prepare-theme-colors [^js/Mui.Theme theme]
  (let [mode (-> theme .-palette .-mode)
        light? (= mode "light")
        pm  (->  theme .-palette .-primary .-main)
        bg (->  theme .-palette .-background .-default)
        dc1 (if light? "rgba(0, 0, 0, 0.12)" (gobj/get ^js/Mui.Colors (.-blueGrey mui-colors) 500))
        c1 (if light? (gobj/get color-grey 200) (-> theme .-palette .-text .-disabled))]
    {:color1 c1
     :divider-color1 dc1
     :db-settings-icons pm
     :header-footer c1
     :primary-main pm
     :background-default bg
     :popper-box-bg (if light? "whitesmoke" bg)
     :section-header pm
     :text-primary (->  theme .-palette .-text .-primary)}))

(defn theme-color [^js/Mui.Theme theme color-kw]
  (cond

    (= color-kw :bg-default)
    (->  theme .-palette .-background .-default)

    (= color-kw :section-header)
    (-> theme .-customColors .-sectionHeader)

    (= color-kw :category-item)
    (->  theme .-palette .-secondary .-dark)

    (= color-kw :color1)
    (-> theme .-customColors .-color1)

    (= color-kw :divider-color1)
    (-> theme .-customColors .-dividerColor1)

    (= color-kw :db-settings-icons)
    (-> theme .-customColors .-dbSettingsIcons)

    (= color-kw :entry-category-icons)
    (-> theme .-customColors .-dbSettingsIcons)

    (= color-kw :header-footer)
    (-> theme .-headerFooter .-color)

    (= color-kw :popper-box-bg)
    (-> theme .-popperBox .-bg)

    (= color-kw :entry-content-bg)
    (-> theme .-entryContent .-bg)

    (= color-kw :entry-content-box-border)
    (-> theme .-entryContent .-boxBorderColor)

    (= color-kw :primary-main)
    (->  theme .-palette .-primary .-main)

    (= color-kw :info-main)
    (->  theme .-palette .-info .-main)

    :else
    (->  theme .-palette .-primary .-main)))

(defn get-theme-color [color-kw]
  (theme-color @custom-theme-atom color-kw))

(declare set-colors)

;; Theme customization based on the following
;; https://mui.com/customization/theme-components/

;; Just by changing main color of primary and secondary all other colors are
;; automatically calculated and set in the theme by mui system
;; See the following for selecting suitable color combinations
;; https://mui.com/material-ui/customization/color/#picking-colors 
;; https://material.io/inline-tools/color/
;; https://bareynol.github.io/mui-theme-creator/ may be used to see some color combinations
;; Default primary #1976d2, secondary #9c27b0 (https://mui.com/material-ui/customization/palette/) 


;; https://mui.com/customization/default-theme/  
;; https://v5.mui.com/material-ui/customization/default-theme/  ( The new url for v5 as the other one is meant for the latest mui)
;; Here we can see all the props of the theme object that are shown for MUI's organization branding theme
;; From this link: 
;; If you want to learn more about how the theme is assembled, 
;; take a look at material-ui/style/createTheme.js, and the related imports which createTheme uses.
(defn create-custom-theme [mode]

  (let [p-main (if (= mode "light") "#1976d2" "#90caf9")
        s-main (if (= mode "light") "#9c27b0" "#ce93d8")
        ;; Need to use creating the theme in two steps and 
        ;; First one is to use the palette colors and that theme can be used in second 
        ;; See page :https://mui.com/material-ui/customization/palette/#generate-tokens-using-augmentcolor-utility

        theme-data1 (clj->js  {:palette {:mode mode
                                         :primary {:main  p-main}
                                         :secondary {:main s-main}}})
        ;; Theme 1 just the pallete color setting so that we can use in the following data
        theme1 ^js/Mui.Theme (create-theme theme-data1)

        ;; txt-primary (->  theme1 .-palette .-text .-primary)
        ;; hf-color (if (= mode "light") (gobj/get color-grey 200) (-> theme1 .-palette .-text .-disabled))
        ;; color1 (if (= mode "light") (gobj/get color-grey 200) (-> theme1 .-palette .-text .-disabled)) ;;rgb(241, 241, 241)

        light? (= mode "light")

        {:keys [color1
                divider-color1
                db-settings-icons
                primary-main
                background-default
                popper-box-bg
                header-footer
                section-header
                text-primary]} (prepare-theme-colors theme1)

        ;;IMPORTANT: All theme properties name should be in camelCase
        theme-data (clj->js {;; We can add custom properties in theme like status->danger 
                             ;; Such custom theme values can be accessed and used in 'sx' as shown here 
                             ;; {:sx {:bgcolor (fn [theme] (-> theme .-status .-danger))}

                             :status {:danger "#e53e3e"}

                             :headerFooter {:color header-footer}

                             :customColors {:color1 color1
                                            ;;property name in camelCase is expected
                                            :dbSettingsIcons db-settings-icons
                                            :dividerColor1 divider-color1
                                            :sectionHeader section-header}

                             :popperBox {:bg popper-box-bg}

                             :entryContent {:bg background-default
                                            :boxBorderColor (if light? text-primary  "rgba(255, 255, 255, 0.75)")}

                             :components
                             {:MuiInput
                              {:styleOverrides
                               {:root {:border (str "1px solid" (gobj/get color-grey 500))
                                       :outline "1px solid transparent"
                                       "&.Mui-focused" {:border "1px solid"
                                                        :border-color primary-main
                                                        :outline "1px solid"
                                                        :outline-color primary-main}}}
                               :defaultProps
                               {:disableUnderline true}}

                              :MuiDialogTitle
                              {:styleOverrides
                               {:root {:color primary-main}}}

                              :MuiTypography
                              {:styleOverrides
                               {:root {:color text-primary}}}

                              :MuiButton
                              {:defaultProps
                               {:variant "contained"
                                :color "secondary"
                                :disableElevation true
                                :disableRipple false
                                :size "small"}}
                              :MuiLink {:defaultProps {:color "inherit" :underline "hover" :href "#"}} ;;
                              :MuiSvgIcon
                              {:styleOverrides
                               {;;:root {:color text-primary}
                                }
                               :defaultProps {:fontSize "small"}}
                              :MuiInputLabel {:defaultProps {:shrink true}}
                              :MuiTooltip {:defaultProps {:arrow true}}}})

        ;; Thme2 which use theme1 created earlier in 'theme-data'  
        theme (create-theme theme1 theme-data)]
    (reset! custom-theme-atom theme)
    #_(set-colors theme)
    theme))


#_(def primary-main-color (r/atom nil))
#_(def primary-dark-color (r/atom nil))
#_(def secondary-main-color (r/atom nil))
#_(def background-default-color (r/atom nil))

#_(def entry-content-bg-color (r/atom nil))
#_(def entry-content-box-border-color (r/atom nil))
#_(def entry-category-icons-color (r/atom nil))

#_(def start-page-divider-color (r/atom nil))
#_(def db-settings-icons-color (r/atom nil))
#_(def section-name-color (r/atom nil))
#_(def entry-list-header-footer-color (r/atom nil))


;;rgba(255, 255, 255, 0.3)
#_(defn set-colors [^js/Mui.Theme theme]
    (let [mode (->  theme .-palette .-mode)
          ;; primary-main (->  theme .-palette .-primary .-main)
          ;; bg-default  (->  theme .-palette .-background .-default)
          ;; txt-primary (->  theme .-palette .-text .-primary)
          ;;el-color (if (= mode "light") (gobj/get color-grey 200) (-> theme .-palette .-text .-disabled))
          ;;bx-color (if (= mode "light") txt-primary "rgba(255, 255, 255, 0.75)")
          ]
      #_(reset! primary-main-color primary-main)
      #_(reset! primary-dark-color (->  theme .-palette .-primary .-dark))
      #_(reset! secondary-main-color (->  theme .-palette .-secondary .-dark))
      #_(reset! background-default-color bg-default)

      #_(reset! start-page-divider-color (gobj/get (.-blueGrey  ^js/Mui.Colors mui-colors) 500) #_(-> theme .-palette .-text .-primary))

      #_(reset! entry-list-header-footer-color el-color #_(-> theme .-palette .-text .-disabled) #_(gobj/get (.-blueGrey  ^js/Mui.Colors mui-colors) 800))

      #_(reset! entry-content-box-border-color bx-color)
      #_(reset! entry-content-bg-color bg-default)
      #_(reset! entry-category-icons-color primary-main)
      #_(reset! db-settings-icons-color primary-main)
      #_(reset! section-name-color primary-main)))

#_(defn theme-primary-main-color [^js/Mui.Theme theme]
    (->  theme .-palette .-primary .-main))


#_(def color-grey-200 (gobj/get color-grey 200)) ;; replacement for var (--mui-color-grey-200)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def mui-theme-provider
  "A reagent component for ThemeProvider"
  (reagent.core/adapt-react-class mui-mat-styles/ThemeProvider)) ;;mui-styles mui-mat-styles

(def mui-styled-engine-provider
  "A reagent component for the StyledEngineProvider"
  (reagent.core/adapt-react-class mui-mat-styles/StyledEngineProvider)) ;;

(def mui-text-field-type
  "TextField is a React Component" mui/TextField)

;; declare-mui-classes macro is used to define 'def's of all Material React components 
;; as Reagent components in this name space
;; e.g  
;;    (def mui-icon-button (reagent.core/adapt-react-class mui/IconButton))

(declare-mui-classes
 [AppBar
  Alert
  AlertTitle
  Accordion
  AccordionSummary
  AccordionDetails
  Autocomplete
  Avatar
  Box
  Button
  Backdrop
  CssBaseline
  Checkbox
  Chip
  ClickAwayListener
  CircularProgress
  Container
  Dialog
  DialogActions
  DialogContent
  DialogContentText
  DialogTitle
  Divider
  FormControl
  FormControlLabel
  FormHelperText
  FormGroup
  Grid
  Paper
  Popper
  InputLabel
  IconButton
  Icon
  InputAdornment
  Input
  Link
  LinearProgress
  List
  ListItem
  ListItemButton
  ListItemSecondaryAction
  ListItemText
  ListItemAvatar
  ListItemIcon
  ListSubheader
  Menu
  MenuItem
  Switch
  Select
  Snackbar
  SnackbarContent
  Stack
  Slider
  SvgIcon
  Tabs
  Tab
  TextField
  Toolbar
  Tooltip
  Typography]
 "mui-" "mui/")

;; Thesse components do not have name space prefix 
;; e.g (def mui-tree-item  (reagent.core/adapt-react-class TreeItem))
(declare-mui-classes
 [TreeItem
  TreeView]
 "mui-", "mui-x-tree-view/")

;; DateTimePicker and LocalizationProvider are moved from mui lab to mui-x date pickers
;; See https://mui.com/blog/lab-date-pickers-to-mui-x/
(declare-mui-classes
 [DateTimePicker
  DesktopDatePicker
  LocalizationProvider]
 "mui-", "mui-x-date-pickers/")

;; Incon names can be found from https://mui.com/material-ui/material-icons/
;;All material icons are defined here
(declare-mui-classes
 [ArrowDropUpOutlined
  ArrowDropDownOutlined
  ArrowUpwardOutlined
  ArrowDownwardOutlined
  KeyboardArrowDownOutlined
  KeyboardArrowUpOutlined
  NavigateNextOutlined
  Save
  SaveAs
  SaveAsOutlined
  AccessTime
  ArrowDropDown
  ArrowRight
  AccountBalanceOutlined   ;; for bank
  ArticleOutlined
  PreviewOutlined
  Autorenew
  CancelPresentation
  CancelPresentationSharp
  CancelPresentationOutlined
  Check
  ClearOutlined
  CloseOutlined
  CreditCardOutlined
  DeleteOutline
  DoneAll
  EditOutlined
  Fingerprint
  Folder
  FolderOutlined
  FavoriteBorder
  FeedOutlined
  FlightTakeoffOutlined
  Launch
  LoginOutlined
  LockOpenOutlined
  LockOutlined
  PostAddOutlined
  Search
  SettingsOutlined
  SecurityOutlined
  SellOutlined
  VpnKeyOutlined
  AccountBalance
  FileCopyOutlined
  VisibilityOff
  Visibility
  AddCircleOutlineOutlined
  MoreVert
  ExpandMore
  ChevronRight
  MoreHoriz
  GroupWorkOutlined
  ;;TextSnippetOutlined
  WifiOutlined] "mui-icon-" "mui-icons/")

;;;;;;;;;;;;;;;;
;;; Follwings are based on this example
;;; https://github.com/reagent-project/reagent/blob/v1.2.0/examples/material-ui/src/example/core.cljs
(def ^:private input-component
  (react/forwardRef
   (fn [props ref]
     (r/as-element
      [:input (-> (js->clj props :keywordize-keys true)
                  (assoc :ref ref))]))))

(def ^:private textarea-component
  (react/forwardRef
   (fn [props ref]
     (r/as-element
      [:textarea (-> (js->clj props :keywordize-keys true)
                     (assoc :ref ref))]))))

;; To fix cursor jumping when controlled input value is changed,
;; use wrapper input element created by Reagent instead of
;; letting Material-UI to create input element directly using React.
;; Create-element + convert-props-value is the same as what adapt-react-class does.
(defn text-field [props & children]
  (let [props (-> props
                  (assoc-in [:InputProps :inputComponent] (cond
                                                            (and (:multiline props) (:rows props) (not (:maxRows props)))
                                                            textarea-component

                                                            ;; FIXME: Autosize multiline field is broken.
                                                            (:multiline props)
                                                            nil

                                                            ;; Select doesn't require cursor fix so default can be used.
                                                            (:select props)
                                                            nil

                                                            :else
                                                            input-component))
                  rtpl/convert-prop-value)]
    (apply r/create-element mui/TextField props (map r/as-element children))))

;;NOTE:
;; Following error is seen in the js console during developmemnt. However the above fix works and no other problem encountered
;; MUI: You have provided a `inputComponent` to the input component
;; that does not correctly handle the `ref` prop.
;; Make sure the `ref` prop is called with a HTMLInputElement.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; An example react component that uses JSX from the local node package 
;; See src-js/README.md for details to compile and install the local package
;; See the use of ["onekeepass-local" :as okp-local] in the package use above. For now we are not using

#_(def example-comp
    "A reagent component formed from react componet AutoSizer"
    (reagent.core/adapt-react-class (.-CustomizedBadges ^js/CustomizedBadges okp-local)))

(comment
  ;; An example using reactify-component
  #_(def ^:private textarea-component
      (r/reactify-component
       (fn [props]
         [:textarea (-> props
                        (assoc :ref (:inputRef props))
                        (dissoc :inputRef))])))
  ;;To see all vars defined in this namespace
  (clojure.repl/dir onekeepass.frontend.mui-components))