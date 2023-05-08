(ns
 onekeepass.frontend.mui-components
  (:require-macros [onekeepass.frontend.mui-comp-classes
                    :refer  [declare-mui-classes]])
  (:require
   [reagent.core :as r]
   [reagent.impl.template :as rtpl]
   [goog.object :as gobj]
   ["@mui/material" :as mui]
   ["@mui/icons-material" :as mui-icons]
   ["@mui/material/colors" :as mui-colors]
   ["@mui/material/styles" :as mui-mat-styles]
   ["@mui/lab" :as mui-lab]
   ["@mui/x-date-pickers" :as mui-x-date-pickers]
   ["@mui/material/Autocomplete" :as mui-ac]
   ["react-split-pane" :as sp]
   ["@date-io/date-fns" :as DateAdapter]

   ["react-window" :as rw]
   ["react-virtualized-auto-sizer" :as vas]
   #_["react-idle-timer" :as react-idle-timer]
   [react]))

(set! *warn-on-infer* true)

(def react-use-state (.-useState ^js/React react))
(def react-use-effect (.-useEffect ^js/React react))

#_(def use-idle-timer (.-useIdleTimer ^js/ReactIdleTimer react-idle-timer))

;; https://github.com/dmtrKovalenko/date-io
;; DateAdapter is #js{:default #object[DateFnsUtils]}
;; (.-default DateAdapter) is #object[DateFnsUtils]
;; (type date-adapter)  is #object[Function]
(def date-adapter (.-default ^js/DateAdapter DateAdapter))

;; (Object.keys date-fns-utils) will give all available fuctions from this util
(def ^js/DateAdapter.Utils date-fns-utils (date-adapter.))

;; TODO: Need to replace split-spane use with our custom one or another actively maintained lib
;;s p is #js{:default #object[SplitPane], :Pane #object[Pane]}
(def split-pane
  "A reagent component formed from react componet found in default property of sp "
  (reagent.core/adapt-react-class (.-default ^js/SplitPane sp)))

;;rw is #js{:VariableSizeGrid #object[Grid], :VariableSizeList #object[List], :FixedSizeGrid #object[Grid], :FixedSizeList #object[List], :areEqual #object[areEqual], :shouldComponentUpdate #object[shouldComponentUpdate]}
(def fixed-size-list
  "A reagent component formed from react componet FixedSizeList"
  (reagent.core/adapt-react-class (.-FixedSizeList ^js/ReactWindow rw)))

;; vas is #js{:default #object[AutoSizer]}
(def auto-sizer
  "A reagent component formed from react componet AutoSizer"
  (reagent.core/adapt-react-class (.-default ^js/VirtualAutoSizer vas)))

(def auto-complete-filter
  "Autocomplete component exposes a factory to create a filter method 
   that can provided to the filterOptions prop. 
   This is used to change the default option filter behavior."
  (.-createFilterOptions ^js/Mui.Autocomplete mui-ac))

(def create-theme mui-mat-styles/createTheme)
(def color-grey (.-grey  ^js/Mui.Colors mui-colors))

;; Theme customization based on the following
;; https://mui.com/customization/theme-components/
;; https://mui.com/customization/default-theme/
;; We need to refresh manually the client when any changes done for the theme while using figwheel dev mode
(def custom-theme
  "Material UI v5 theme customization"
  (create-theme
   (clj->js {;; We can add custom properties in theme like status->danger 
             ;; Such custom theme values can be accessed and used in 'sx' as shown here 
             ;; {:sx {:bgcolor (fn [theme] (-> theme .-status .-danger))}
             :status {:danger "#e53e3e"}

             ;; Just by changing main color of primary and secondary all other colors are
             ;; automatically calculated and set in the theme by mui system
             ;; See the following for selecting suitable color combinations
             ;; https://mui.com/material-ui/customization/color/#picking-colors 
             ;; https://material.io/inline-tools/color/
             ;; https://bareynol.github.io/mui-theme-creator/ may be used to see some color combinations
             ;; Default primary #1976d2, secondary #9c27b0 (https://mui.com/material-ui/customization/palette/)
             :palette {:type "light"
                       :primary {:main  "#1976d2" #_"#6d4c41"}
                       :secondary {:main "#9c27b0" #_"#ffa000"}}
             :components
             {:MuiInput
              {:styleOverrides
               {:root {:border (str "1px solid" (gobj/get color-grey 500))
                       :outline "1px solid transparent"
                       ;;Also custom css "entry-cnt-text-field-edit-focused" from custom.css works 
                       ;;when passed throgh "classes"       
                       "&.Mui-focused" {:border "1px solid var(--color-primary-main)"
                                        :outline "1px solid var(--color-primary-main)"}}
                ;;This is not working
                ;; ".MuiInput-root.Mui-focused" {:border "1px solid var(--color-primary-main)"
                ;;                  :outline "1px solid var(--color-primary-main)"}
                }
               :defaultProps
               {:disableUnderline true}}
              :MuiButton
              {:defaultProps
               {:variant "contained"
                :color "secondary"
                :disableElevation true
                :disableRipple false
                :size "small"}}
              :MuiLink {:defaultProps {:color "inherit" :underline  "hover" :href "#"}}
              :MuiSvgIcon {:defaultProps {:fontSize "small"}}
              :MuiInputLabel {:defaultProps {:shrink true}}
              :MuiTooltip {:defaultProps {:arrow true}}}})))

;; We can change the theme type to 'light' or 'dark' by reseting this atom
(defonce custom-theme-atom (r/atom custom-theme))

(def color-primary-main (-> ^js/Mui.Theme @custom-theme-atom .-palette .-primary .-main))

(def color-secondary-main (-> ^js/Mui.Theme @custom-theme-atom .-palette .-secondary .-main))

(def color-grey-200 (gobj/get color-grey 200)) ;; replacement for var (--mui-color-grey-200)

#_(def color-primary-dark (-> ^js/Mui.Theme @custom-theme-atom .-palette .-primary .-dark))

#_(def color-secondary-dark (-> ^js/Mui.Theme @custom-theme-atom .-palette .-secondary .-dark))

;;;;;;;;;;;;;;;;;;;;;;;;;

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
 "mui-", "mui-lab/")

 ;; DateTimePicker and LocalizationProvider are moved from mui lab to mui-x date pickers
 ;; See https://mui.com/blog/lab-date-pickers-to-mui-x/
(declare-mui-classes
 [DateTimePicker
  DesktopDatePicker
  LocalizationProvider]
 "mui-", "mui-x-date-pickers/")

;;All material icons are defined here
(declare-mui-classes
 [Save
  SaveAs
  AccessTime
  ArrowDropDown
  ArrowRight
  AccountBalanceOutlined   ;; for bank
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
  Folder
  FolderOutlined
  FavoriteBorder
  FeedOutlined
  FlightTakeoffOutlined
  LoginOutlined
  LockOpenOutlined
  LockOutlined
  PostAddOutlined
  Search
  SettingsOutlined
  SecurityOutlined
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
  WifiOutlined] "mui-icon-" "mui-icons/")

;;;;;;;;;;;;;;;;
;;; Follwings are based on this example
;;; From https://github.com/reagent-project/reagent/blob/master/examples/material-ui/src/example/core.cljs
;;; https://github.com/reagent-project/reagent/blob/1.1/examples/material-ui/src/example/core.cljs 
(def ^:private input-component
  (r/reactify-component
   (fn [props]
     [:input (-> props
                 (assoc :ref (:inputRef props))
                 (dissoc :inputRef))])))

(def ^:private textarea-component
  (r/reactify-component
   (fn [props]
     [:textarea (-> props
                    (assoc :ref (:inputRef props))
                    (dissoc :inputRef))])))

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

(comment
  ;;To see all vars defined in this namespace
  (clojure.repl/dir onekeepass.frontend.mui-components))