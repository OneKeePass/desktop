(ns onekeepass.frontend.translation
  (:require [clojure.string :as str]
            [camel-snake-kebab.core :as csk]))

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
  "Prefixes 'button.labels' to the keys"
  [& keys]
  `(tr-with-prefix "button" "labels" ~@keys))

(defmacro tr-ml 
  "Prefixes 'menu.labels' to the keys"
  [& keys]
  `(tr-with-prefix "menu" "labels" ~@keys))

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
     (onekeepass.frontend.translation/lstr (str/join "." (conj v# "entryType.titles")))))

(defmacro tr-entry-field-name-cv
  "Uses values found in the symbols passed as keys and adds the prefix before calling lstr"
  [& keys]
  `(let [v# (to-vals ~@keys)] 
     (onekeepass.frontend.translation/lstr (str/join "." (conj v# "entry.fields")))))

(defmacro tr-entry-section-name-cv
  "Uses values found in the symbols or string values passed as keys and adds the prefix before calling lstr"
  [& keys]
  `(let [v# (to-vals ~@keys)]
     (onekeepass.frontend.translation/lstr (str/join "." (conj v# "entry.sections")))))

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; Few experimental code ....

;; All tr macros treat 'keys' as sequence of symbols and do try evaluate values of those symbols and then use
;; Tried various ways to evaluete values passed in 'keys' instead of symbols. But not statified with the following solution
;; Finally the workable solution is using the macro 'to-vals' instead of using a fn to get values
;; from symbols. This is achieved by creating anonymous function and use it in another macro

#_(defn to-cc 
  [& keys]
  ;;keys
  (map (fn [v] (csk/->camelCase v)) keys))

;; Calling site needs define something similar 'to-cc' and pass that to this macro
;; ~to-cc used to evalute the arg 'to-cc' to a fn
;; Need to use gensym based v1 and v2 to be clear from any conflicts with variables in the calling site
#_(defmacro tr-l-cv1 [to-cc & keys]
  (let [v1 (gensym)
        v2 (gensym)]
    `(let [~v1  (~to-cc ~@keys)
           ~v2 (str/join "." (conj  ~v1 "labels"))]
       ;;(onekeepass.frontend.translation/lstr ~v2)
       ~v2
       #_(str/join "." (conj  ~v1 "labels")))))

#_(defmacro tr-l-cv2 [to-cc & keys]
  `(let [v1#  (~to-cc ~@keys)
         v2# (str/join "." (conj  v1# "labels"))]
         ;;(onekeepass.frontend.translation/lstr ~v2)
     v2#
     #_(str/join "." (conj  ~v1 "labels"))))

#_(defmacro mac1 [& keys ]
  (list str/join "." (list 'map (fn [x] (csk/->camelCase x)) 
                           (list reverse (-> keys vec (conj "labels"))))))

#_(defmacro mac2 [& keys]
  (list 'map (fn [x] (csk/->camelCase x))
        (list reverse (-> keys vec (conj "labels"))))
  )

#_(defmacro mac4 [& keys]
  (list 'map (fn [x] (csk/->camelCase x)) (vec keys)
        #_(list reverse (-> keys vec (conj "labels")))))

#_(defmacro mac3 [& keys]
  `(let [v# (mac4 ~@keys)]
     (str/join "." (conj v# "labels"))
     #_(onekeepass.frontend.translation/lstr v#)))

#_(defmacro tr-l-cv [& keys]
    (let [v1 (gensym)
          v2 (gensym)
          my-fn  (fn [& keys]
                 ;;keys
                   (map (fn [v] v #_(csk/->camelCase v)) keys))]

      `(let [~v1  (~my-fn ~@keys)
             ~v2 (str/join "." (conj  ~v1 "labels"))]
         ~v2
         #_(str/join "." (conj  ~v1 "labels")))))

#_(defmacro tr-l-cv [& keys]
    (let [v1 (gensym)
          v2 (gensym)]
      `(let [~v1 (my-fn1 ~@keys)
             ~v2 (str/join "." (conj  ~v1 "labels"))]
         ~v2
         #_(str/join "." (conj  ~v1 "labels")))))

#_(defmacro tr-l-ck
  "Converts each key to a camelCase word before forming combined key and Adds prefix 'labels'"
  [& keys]
  `(tr-with-prefix "labels" ~@(map (fn [k] (csk/->camelCase k)) keys)))
