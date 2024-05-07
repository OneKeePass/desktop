(ns onekeepass.frontend.translation
  (:require ["i18next" :as i18n]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.events.common :as cmn-events :refer [check-error]]))

(set! *warn-on-infer* true)

(def ^:private i18n-obj ^js/i18nObj i18n)

;; TODO: Remove this ?
;; Need a flag to show that all tranlations json file data are loaded 
;; and available to use
(defonce ^:private translations-loaded-and-init-done (atom false))

;; It appears that start page lstr calls are made before translations data are loaded because of async call nature
;; The "getStarted" key is called only once. Other keys are called second time and gets the translation data
;; Not sure why this is happening
;; Need to add some subscriber in the start page and use that to trigger to reload the texts after 
;; translation data are loaded 
;; This should be a problem for other pages
(def trans-defaults {"titles.getStarted" "Get Started"})

(def i18n-instance (atom nil))

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
  [txt-key interpolation-args]
  (-> (str "dialog.titles." (convert  txt-key)) (lstr interpolation-args)))

(defn lstr-l-cv
  "Adds 'labels' prefix to the key and gets the traslated text. 
   This is similar to the macro tr-l-cv. This fn needs to be used if we want to evaluate a expression 
   and then call translate
   "
  [txt-key]
  (-> (str "labels." (convert txt-key)) lstr))

(defonce ^:private translations-data (atom {}))

(defonce ^:private current-locale-language (atom nil))

(declare ^:private  setup-i18n-with-backend)

(declare ^:private create-back-end)

(defn- translations-loaded
  "Handles response on successful loading of all tranalations json files found in app resource dir
   Resource root dir: _up_/resources/public/translations
  The arg language determines the default language to use in i18n option
  valid values are one of :use-current-locale-language or 'en', 'fr', 'ea'....
  "
  [language api-response]
  ;; api-response's ok value found in :result is not transformed to clj 
  ;; That means the serialized data from 'TranslationResource' struct is not tranformed
  ;; See the use of :strs and snake_case
  (let [{:strs [current_locale_language prefered_language translations] :as res} (check-error api-response)
        ;;_ (println "language current_locale_language prefered_language are " language current_locale_language prefered_language)
        ;; translations is a map where key is the language id and value is a json string and 
        ;; the json string needs to be parsed 
        ;; TODO: use a try block to parse and handle error if any
        parsed-translations (reduce (fn [m [k v]] (assoc m k (.parse js/JSON v)))  {} translations)
        lng (condp = language
              :use-prefered-language
              prefered_language

              :use-current-locale-language
              current_locale_language

              (if (nil? language) "en" language))
        ;;lng (if (= language :use-current-locale-language) current_locale language)
        ]
    ;;(println "language(arg) current_locale_language prefered_language lng " language current_locale_language prefered_language lng )
    (when-not (empty? res)
      (reset! current-locale-language current_locale_language)
      (reset! translations-data parsed-translations)
      (setup-i18n-with-backend lng (create-back-end parsed-translations))
      #_(setup-i18n-with-backend (-> lng (str/split #"-") first)
                                 (create-back-end parsed-translations)))))

(defn load-language-translation
  "Needs to be called on app loading in the very begining to load locale language and 'en' 
   tranalations json files found in app resource dir"
  []
  (bg/load-language-translations [] (partial translations-loaded :use-prefered-language)))

(defn load-locale-translation
  "Needs to be called on app loading in the very begining to load locale language and 'en' 
   tranalations json files found in app resource dir"
  []
  (bg/load-language-translations [] (partial translations-loaded :use-current-locale-language)))

(defn- load-translations
  "The arg is used as the default language in i18n's option
   The arg language-ids is vec of languages to load. 
   Typically it will be having two languages. One is the the 'language' and another
   is the 'fallbackLng'
   e.g language language-ids - 'fr' ['fr' 'en'] 
  "
  [language language-ids]
  (bg/load-language-translations language-ids (partial translations-loaded language)))

(defn- create-i18n-init
  "The init call on an instance of 'i18n' returns a promise and we need to r
   esolve here before using any fns from 'i18n'"
  [^js/i18nObj instance options]
  (go
    (try
      (let [_f (<p! (.init instance (clj->js options)))]
        (reset! translations-loaded-and-init-done true)
        (reset! i18n-instance instance)
        (js/console.log  "i18n init is done successfully")
        ;; Need to dispatch on successful loading of data
        (cmn-events/load-language-translation-completed))
      ;; Error should not happen as we have already loaded a valid translations data before calling init 
      ;; Still what to do if there is any error in initializing 'i18n'? 
      (catch js/Error err
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
            :debug true}
        ^js/i18nObj instance (.createInstance i18n-obj)]
    (.use instance (clj->js back-end))
    (create-i18n-init instance m)))

(comment
  cljs꞉onekeepass.frontend.translation꞉> 
  (Object.keys i18n)
  #js["observers" "options" "services"
      "logger" "modules" "constructor" "init"
      "loadResources" "reloadResources" "use"
      "setResolvedLanguage" "changeLanguage"
      "getFixedT" "t" "exists"
      "setDefaultNamespace" "hasLoadedNamespace"
      "loadNamespaces" "loadLanguages" "dir" "cloneInstance" "toJSON" "createInstance"])