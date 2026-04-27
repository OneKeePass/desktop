(ns onekeepass.frontend.mui-comp-classes
  (:require [clojure.string]
            [clojure.pprint]
            [camel-snake-kebab.core]))

(defmacro def-reagent-classes
  "See below"
  [args def-prefix ns-prefix]
  (list 'map '(fn [x] (list 'def
                            (symbol (str def-prefix (camel-snake-kebab.core/->kebab-case x)))
                            (list 'reagent.core/adapt-react-class (symbol (str ns-prefix x))))) args))

(defmacro declare-mui-classes
  "Defines 'def's for all symbols passed in the args vector
   in the namespace where this macro is called
   e.g (def mui-icon-button (reagent.core/adapt-react-class mui/IconButton))
   args is a vector of material ui component names
   def-prefix is the prefix to use for the var name
   ns-prefix is the namespace to prefix to the 'imported' material ui component
   "
  [args def-prefix ns-prefix]
  `(do
     ~@(def-reagent-classes args def-prefix ns-prefix)))


(comment

  (macroexpand-1 '(declare-mui-classes ["TextFieldItem" "Button" "IconButton"] "mui-" "mui/"))
  ;;Will geneate the following
  (do
    (def mui-text-field-item (reagent.core/adapt-react-class mui/TextFieldItem))
    (def mui-button (reagent.core/adapt-react-class mui/Button))
    (def mui-icon-button (reagent.core/adapt-react-class mui/IconButton)))

  (macroexpand-1 '(declare-mui-classes [] "mui-" "mui/"))

  (macroexpand-1 '(declare-mui-icons [MuiIconVpnKeyOutlined]))
  ;; will generate =>
  (do
    (def mui-icon-vpn-key-outlined MuiIconVpnKeyOutlined)))
