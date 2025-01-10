(ns onekeepass.frontend.background-password
  (:require
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]
   [onekeepass.frontend.background-common :as bg-cmn :refer [invoke-api]]))


(defn analyzed-password
  "Generates a password with the given options and returns the 
  generated password with its analysis"
  [password-options dispatch-fn]
  ;; passwordOptions should be in camelCase as it is the tauri command fn arg
  ;; However we use 'snake_case' to deserialize the struc 'PasswordGenerationOptions'
  (invoke-api "analyzed_password"
              (clj->js
               {:passwordOptions
                (->> password-options
                     (cske/transform-keys csk/->snake_case))})
              dispatch-fn
              :convert-request false))

(defn score-password [password dispatch-fn]
  (invoke-api "score_password" {:password password} dispatch-fn))

(defn generate-password-phrase [password-phrase-options dispatch-fn]
  (invoke-api "generate_password_phrase"
              (clj->js {:passwordPhraseOptions
                        (->> password-phrase-options
                             (cske/transform-keys csk/->snake_case))})
              dispatch-fn :convert-request false))


(comment
  (-> @re-frame.db/app-db keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)
  (-> (get @re-frame.db/app-db db-key) :entry-form-data)
  ;;{:name "EFFLarge"}
  (def a {:word-list-source {:name "EFFShort2"} :words 5 :separator "-" :capitalize-first {:type-name "Always"} :capitalize-words {:type-name "Never"}})
  )