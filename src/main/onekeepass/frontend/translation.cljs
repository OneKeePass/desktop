(ns onekeepass.frontend.translation
  (:require ["i18next" :as i18n]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [onekeepass.frontend.events.translation :as tr-events]))

(set! *warn-on-infer* true)

(def ^:private i18n-obj ^js/i18nObj i18n)

;; It appears that start page lstr calls are made before translations data are loaded because of async call nature
;; The "getStarted" key is called only once. Other keys are called second time and gets the translation data
;; Not sure why this is happening
;; Need to add some subscriber in the start page and use that to trigger to reload the texts after 
;; translation data are loaded 
;; This should be a problem for other pages
(def trans-defaults {"titles.getStarted" "Get Started"})

(def i18n-instance (atom nil))

;; https://www.i18next.com/translation-function/plurals#singular-plural

;; See interpolation options here
;;https://www.i18next.com/translation-function/interpolation
(defn lstr
  "Gets the translation text for the given key and applying the interpolation if passed
  Arg interpolation-args is a map that provides value for any variable names used in text
  "
  ([txt-key interpolation-args]
   (let [;; NOTE: transform-keys will be called recursively though here interpolation-args 
         ;; will not have any inner map
         args (when-not (empty? interpolation-args)
                (->> interpolation-args (cske/transform-keys csk/->camelCaseString) clj->js))]

     (if (not (nil? @i18n-instance))
       (.t ^js/i18nObj @i18n-instance txt-key args)
       (get trans-defaults txt-key txt-key))))
  ([txt-key]
   (lstr txt-key nil)))

(defn- convert
  "Converts the case of the key string to the camelCase key as used in translation.json 
  IMPORTANT: 
   camel-snake-kebab.core/->camelCase expects a non nil value;Otherwise an error 
   will be thrown resulting UI not showing!
  "
  [txt-key]
  (csk/->camelCase
   (if (string? txt-key) txt-key "")))

(defn lstr-dlg-title
  "Adds prefix 'dialog.titles' to the key before getting the translation"
  ([txt-key interpolation-args]
   (-> (str "dialog.titles." (convert  txt-key)) (lstr interpolation-args)))
  ([txt-key]
   (lstr-dlg-title txt-key nil)))

(defn lstr-l-cv
  "Adds 'labels' prefix to the key and gets the traslated text. 
   This is similar to the macro tr-l-cv. This fn needs to be used if we want to evaluate a expression 
   and then call translate
   "
  [txt-key]
  (-> (str "labels." (convert txt-key)) lstr))

(defn lstr-l
  "Adds prefix 'labels' to the key before getting the translation
 The arg 'txt-key' can be a quoted symbol or a string
  "
  ([txt-key interpolation-args]
   (-> (str "labels." txt-key) (lstr interpolation-args)))
  ([txt-key]
   (lstr-l txt-key nil)))

(defn lstr-ml
  "Adds 'menuLabels' prefix to the key and gets the translated text."
  [txt-key]
  (-> (str "menuLabels." (convert txt-key)) lstr))

(defn lstr-sm
  "Adds prefix 'snackbarMessages' to the key before getting the translation
  The arg 'txt-key' are expected to be a symbol as passed in events call ':common/message-snackbar-open' 
   "
  ([txt-key interpolation-args]
     ;; If string value is used, that means such text is 
     ;; already translated or in some cases yet to be translated    
   (if (symbol? txt-key)
     (lstr (str "snackbarMessages." txt-key) interpolation-args)
     txt-key))
  ([txt-key]
   (lstr-sm txt-key nil)))

(defn lstr-field-name
  "Adds 'entryFieldNames' prefix to the key and gets the translated text of standard entry fields"
  [txt-key]
  (lstr (str "entryFieldNames." (convert txt-key))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare ^:private  setup-i18n-with-backend)

(declare ^:private create-back-end)

(defn parse-json [str-value]
  (try
    (.parse js/JSON str-value)
    (catch js/Error err
      (js/console.log (ex-cause err))
      #js {})))

(defn- translations-loaded-callback [ok-response]
  ;; ok-response may be nil on error. Then no translation will be available  
  ;; (println "ok-response is " ok-response)

  ;; api-response's ok value found in :result is not transformed to clj 
  ;; That means the serialized data from 'TranslationResource' struct is not tranformed
  ;; See the use of :strs and snake_case
  (let [{:strs [_current_locale_language prefered_language translations]} ok-response
        ;; translations is a map where key is the language id and value is a json string and 
        ;; the json string needs to be parsed. After parsing the string in 'v', the type 
        ;; of the parsed value is a js object - #object[Object]
        parsed-translations (reduce (fn [m [k v]] (assoc m k (parse-json v)))  {} translations)]
    ;; (println "res is  " res)
    #_(println "current_locale_language prefered_language are " current_locale_language prefered_language)

    ;; Type of 'parsed-translations' is  cljs.core/PersistentArrayMap
    #_(println "Type of 'parsed-translations' is " (type parsed-translations))

    ;; Type of translations for en is  #object[Object]
    #_(println "Type of value for en key in 'parsed-translations' is " (type (:en parsed-translations)))

    (setup-i18n-with-backend prefered_language (create-back-end parsed-translations))))

;; This loads the translation files for the selected language ( done in Settings screen)
;; We need to have the corresponding translation json files in the dir resources/public/translations
;; See load_language_translations fn of src-tauri/src/translation.rs

(defn load-language-translation
  "Needs to be called on app loading in the very begining to load locale language (or prefered_language) and 'en' 
   tranalations json files found in app resource dir"
  ([]
   (tr-events/load-language-translation [] translations-loaded-callback))
  ;; Not used at this time
  ([language-ids]
   ;; language-ids is a vec of two charater language ids
   ;; e.g ["en" "fr"]
   (tr-events/load-language-translation language-ids translations-loaded-callback)))

#_(defn reload-language-translation
    "Called after language selection is changed"
    []
    (tr-events/reload-language-data [] translations-loaded-callback))

(defn- create-i18n-init
  "The init call on an instance of 'i18n' returns a promise and we need to r
   esolve here before using any fns from 'i18n'"
  [^js/i18nObj instance options]
  (go
    (try
      (let [_f (<p! (.init instance (clj->js options)))]
        (reset! i18n-instance instance)
        (js/console.log  "i18n init is done successfully")
        ;; Need to dispatch on successful loading of data 
        (tr-events/load-language-data-complete))
      ;; Error should not happen as we have already loaded a valid translations data before calling init 
      ;; Still what to do if there is any error in initializing 'i18n'? 
      (catch js/Error err
        (tr-events/load-language-data-complete)
        (js/console.log (ex-cause err))))))

;;https://www.i18next.com/misc/creating-own-plugins#backend

(defn- create-back-end [translations]
  {:type "backend"

   :init (fn [_services _backendOptions _i18nextOptions]
           ;;(println "services:  " services) 
           ;; (println "backendOptions: "  backendOptions) 
           ;;(println "i18nextOptions: " i18nextOptions)
           )

   ;; Typically read woul have been called when we call use fn
   ;; The translations data for the main language and fallback language will be 
   ;; called through callback and i18n retains internally
   :read (fn [language _namespace callback]
           ;;(println "create-back-end language namespace callback " language namespace callback)
           ;;(println "data  is... " (clj->js (get @translations-data language)))
           (callback nil (clj->js (get translations language))))})

(defn- setup-i18n-with-backend [language back-end]
  (let [m  {:lng language
            :fallbackLng "en"
            :compatibilityJSON "v4"
            :debug false}
        ^js/i18nObj instance (.createInstance i18n-obj)]
    (.use instance (clj->js back-end))
    (create-i18n-init instance m)))

(comment
  (in-ns 'onekeepass.frontend.translation)  

  ;; Need to import macros to use like
  (require '[onekeepass.frontend.translation :refer [tr-l tr-ml tr-dlg-title]])
  (Object.keys i18n)
  #js["observers" "options" "services"
      "logger" "modules" "constructor" "init"
      "loadResources" "reloadResources" "use"
      "setResolvedLanguage" "changeLanguage"
      "getFixedT" "t" "exists"
      "setDefaultNamespace" "hasLoadedNamespace"
      "loadNamespaces" "loadLanguages" "dir" "cloneInstance" "toJSON" "createInstance"])