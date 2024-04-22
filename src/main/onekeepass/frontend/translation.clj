(ns onekeepass.frontend.translation
  (:require [clojure.string :as str]))

(defmacro tr [& keys]
  `(onekeepass.frontend.translation/lstr ~(str/join "." keys))
  #_(str/join "." keys))

(defmacro tr-with-prefix [prefix & keys]
  `(onekeepass.frontend.translation/lstr ~(str/join "." (conj keys prefix)))
  #_(str/join "." (conj keys prefix)))

(defmacro tr-l [& keys]
  `(tr-with-prefix "labels" ~@keys)
  #_(str/join "." (conj keys "labels")))

(defmacro tr-t [& keys]
  `(tr-with-prefix "titles" ~@keys)
  #_(str/join "." (conj keys "titles")))

(defmacro tr-h [& keys]
  `(tr-with-prefix "helperTexts" ~@keys))

(defmacro tr-m [& keys]
  `(tr-with-prefix "messages" ~@keys))


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
  )