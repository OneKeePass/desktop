(ns onekeepass.frontend.translation
  (:require [clojure.string :as str]
            [camel-snake-kebab.core ]))

;; TODO: Replace these macro uses with functions as done in mobile cljs files. 
;; Functions will be defined in ns onekeepass.frontend.translation (translation.cljs file)
;; and will be used intead of calling the macros defined here

(defmacro tr [& keys]
  "Joins all symbols or strings passed in keys and calls lstr"
  `(onekeepass.frontend.translation/lstr ~(str/join "." keys)))

(defmacro tr-with-prefix [prefix & keys]
  `(onekeepass.frontend.translation/lstr ~(str/join "." (conj keys prefix))))

(defmacro tr-l 
   "Prefixes 'labels' to the keys"
  [& keys]
  `(tr-with-prefix "labels" ~@keys))

(defmacro tr-bl 
  "Prefixes 'buttonLabels' to the keys"
  [& keys]
  `(tr-with-prefix "buttonLabels"  ~@keys))

(defmacro tr-ml 
  "Prefixes 'menuLabels' to the keys"
  [& keys]
  `(tr-with-prefix "menuLabels"  ~@keys))

(defmacro tr-t 
  "Prefixes 'titles' to the keys"
  [& keys]
  `(tr-with-prefix "titles" ~@keys))

(defmacro tr-h
  "Adds prefix 'helperTexts'"
  [& keys]
  `(tr-with-prefix "helperTexts" ~@keys))

(defmacro tr-m 
  "Adds prefix 'messages'"
  [& keys]
  `(tr-with-prefix "messages" ~@keys))

(defmacro tr-dlg-title 
  "Prefixes 'dialog.titles' to the keys"
  [& keys]
  `(tr-with-prefix "dialog" "titles" ~@keys))

(defmacro tr-dlg-text 
   "Prefixes 'dialog.texts' to the keys"
  [& keys]
  `(tr-with-prefix "dialog" "texts" ~@keys))

;; An internal utility macro used in '*-cv' macros
;; This uses map to get values from the symbols passed as keys
;; Basically this is used in a 'let' block in the calling site
;; See tr-l-cv,tr-entry-type-title-cv ... for the use
(defmacro to-vals 
  "Extracts values found in the symbols passed as keys in the caller space"
  [& keys]
  (list 'map '(fn [x] (camel-snake-kebab.core/->camelCase 
                       (if (string? x) x ""))) (vec keys)))

(defmacro tr-l-cv 
  "Uses values found in the symbols passed as keys and adds the prefix before calling lstr"
  [& keys]
  `(let [v# (to-vals ~@keys)]
     #_(str/join "." (conj v# "labels"))
     (onekeepass.frontend.translation/lstr (str/join "." (conj v# "labels")))))

(defmacro tr-entry-type-title-cv
  "Uses values found in the symbols passed as keys and adds the prefix before calling lstr"
  [& keys]
  `(let [v# (to-vals ~@keys)]
     #_(str/join "." (conj v# "labels"))
     (onekeepass.frontend.translation/lstr (str/join "." (conj v# "entryTypeTitles")))))

;; lstr-field-name is an equivalent impl using fn
(defmacro tr-entry-field-name-cv
  "Uses values found in the symbols passed as keys and adds the prefix before calling lstr"
  [& keys]
  `(let [v# (to-vals ~@keys)] 
     (onekeepass.frontend.translation/lstr (str/join "." (conj v# "entryFieldNames")))))

(defmacro tr-entry-section-name-cv
  "Uses values found in the symbols or string values passed as keys and adds the prefix before calling lstr"
  [& keys]
  `(let [v# (to-vals ~@keys)]
     (onekeepass.frontend.translation/lstr (str/join "." (conj v# "entrySectionNames")))))

(comment

  (macroexpand-1 '(tr-l Browse))
  ;;  =>  (onekeepass.frontend.okp-macros/tr-with-prefix "labels" Browse)
  
  (macroexpand-1 '(tr-with-prefix labels name db))
  ;;  =>  (onekeepass.frontend.translation/lstr "labels.name.db")
  
  (macroexpand '(tr getStarted))
  ;;  =>  (onekeepass.frontend.translation/lstr "getStarted")
  
  (macroexpand '(tr-l name db))
  ;;  =>  (onekeepass.frontend.translation/lstr "labels.name.db")
  
  (macroexpand '(tr-t unlockDatabase))
  ;;  =>  (onekeepass.frontend.translation/lstr "titles.unlockDatabase")
  
  ;; firstName secondName should have some string values in the calling site
  (macroexpand-1 '(to-vals firstName secondName))
  ;;  =>  (map (fn [x] (camel-snake-kebab.core/->camelCase (if (string? x) x ""))) [firstName secondName])
  
  (macroexpand-1 '(tr-l-cv firstName secondName))
  ;;  =>   (clojure.core/let [v__31370__auto__ (onekeepass.frontend.translation/to-vals firstName secondName)]
  ;;          (onekeepass.frontend.translation/lstr (clojure.string/join "." (clojure.core/conj v__31370__auto__ "labels"))))
  

  ;; Here is an example where we can pass string instead of symbol as 
  ;; compared to (macroexpand-1 '(tr-l-cv firstName secondName))
  (macroexpand-1 '(tr-entry-section-name-cv "Attachments"))
  ;; (clojure.core/let [v__31808__auto__ (onekeepass.frontend.translation/to-vals "Attachments")] 
  ;;   (onekeepass.frontend.translation/lstr (clojure.string/join "." (clojure.core/conj v__31808__auto__ "entry.sections"))))
  
  (macroexpand-1 '(to-vals "Attachments"))
  ;; => (map (fn [x] (camel-snake-kebab.core/->camelCase (if (string? x) x ""))) ["Attachments"])
  
  ;; (lstr "entry.sections.attachments")
  )
