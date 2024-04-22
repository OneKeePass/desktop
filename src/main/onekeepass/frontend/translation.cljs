(ns onekeepass.frontend.translation
  (:require ["i18next" :as i18n]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as str]
            [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.events.common :as cmn-events :refer [check-error]]))

;;(set! *warn-on-infer* true)

(def i18n-obj ^js/i18nObj i18n)

;; Need a flag to show that all tranlations json file data are loaded 
;; and available to use
(defonce ^:private translations-loaded-and-init-done (atom false))

;; It appears that start page lstr calls are made before translations data are loaded because of async call nature
;; The "getStarted" key is called only once. Other keys are called second time and gets the translation data
;; Not sure why this is happening
;; Need to add some subscriber in the start page and use that to trigger to reload the texts after 
;; translation data are loaded 
;; This should be a problem for other pages
(def trans-defaults {"getStarted" "Get Started"})

(def i18n-instance (atom nil))

(defn lstr [txt-key]
  ;;(println "lstr is called for key " txt-key)
  (if @translations-loaded-and-init-done
    #_(.t i18n-obj txt-key)
    (.t @i18n-instance txt-key)
    (get trans-defaults txt-key txt-key)
    #_(do
        (println "translations-loaded-and-init-done is false and use defaults for key " txt-key)
        (get trans-defaults txt-key txt-key))))

(defonce ^:private translations-data (atom {}))

(defonce ^:private current-locale (atom nil))

#_(defn language-translation [language-id]
    (get @translations-data name))

(declare ^:private  setup-i18n-with-backend)

(declare ^:private create-back-end)

#_(defn- translations-loaded
    "Handles response on successful loading of all tranalations json files found in app resource dir
   Resource root dir: _up_/resources/public/translations
  "
    [api-response]
  ;; api-response's ok value found in :result is not transformed to clj 
  ;; That means the serialized data from 'TranslationResource' struct is not tranformed
  ;; See the use of :strs and snake_case
    (let [{:strs [current_locale translations] :as res} (check-error api-response)
        ;; translations is a map where key is the language id and value is a json string and 
        ;; the json string needs to be parsed 
        ;; TODO: use a try block to parse and handle error if any
          parsed-translations (reduce (fn [m [k v]] (assoc m k (.parse js/JSON v)))  {} translations)]
      (when-not (empty? res)
        (reset! current-locale current_locale)
        (reset! translations-data parsed-translations)
        (setup-i18n-with-backend (-> current_locale (str/split #"-") first)
                                 (create-back-end parsed-translations)))))

(defn- translations-loaded
  "Handles response on successful loading of all tranalations json files found in app resource dir
   Resource root dir: _up_/resources/public/translations
  The arg language determines the default language to use in i18n option
  valid values are one of :use-current-locale or 'en', 'fr', 'ea'....
  "
  [language api-response]
  ;; api-response's ok value found in :result is not transformed to clj 
  ;; That means the serialized data from 'TranslationResource' struct is not tranformed
  ;; See the use of :strs and snake_case
  (let [{:strs [current_locale translations] :as res} (check-error api-response)
        ;; translations is a map where key is the language id and value is a json string and 
        ;; the json string needs to be parsed 
        ;; TODO: use a try block to parse and handle error if any
        parsed-translations (reduce (fn [m [k v]] (assoc m k (.parse js/JSON v)))  {} translations)
        lng (if (= language :use-current-locale) current_locale language)]
    (when-not (empty? res)
      (reset! current-locale current_locale)
      (reset! translations-data parsed-translations)
      (setup-i18n-with-backend (-> lng (str/split #"-") first)
                               (create-back-end parsed-translations)))))

(defn load-locale-translation
  "Needs to be called on app loading in the very begining to load locale language and 'en' 
   tranalations json files found in app resource dir"
  []
  (bg/load-language-translations [] (partial translations-loaded :use-current-locale)))

(defn- load-translations [language language-ids]
  (bg/load-language-translations language-ids (partial translations-loaded language)))

(defn- create-i18n-init
  "The init call on an instance of 'i18n' returns a promise and we need to r
   esolve here before using any fns from 'i18n'"
  [instance options]
  (go
    (try
      (let [_f (<p! (.init instance (clj->js options)))]
        (reset! translations-loaded-and-init-done true)
        (reset! i18n-instance instance)
        (js/console.log  "i18n init is done successfully"))
      ;; Error should not happen as we have already loaded a valid translations data before calling init 
      ;; Still what to do if there is any error in initializing 'i18n'? 
      (catch js/Error err
        (js/console.log (ex-cause err))))))

;; This uses i18n default instance
#_(defn- init-i18n
    "The init call on 'i18n' returns a promise and we need to resolve here before using any fns from 'i18n'"
    [options]
    (go
      (try
        (let [_f (<p! (.init i18n-obj (clj->js options)))]
          (reset! translations-loaded-and-init-done true)
          (js/console.log  "i18n init is done successfully"))
      ;; Error should not happen as we have already loaded a valid translations data before calling init 
      ;; Still what to do if there is any error in initializing 'i18n'? 
        (catch js/Error err
          (js/console.log (ex-cause err))))))

;; This uses i18n default instance and uses passed i18n-obj-in
#_(defn- init-i18n
    "The init call on 'i18n' returns a promise and we need to resolve here before using any fns from 'i18n'"
    [i18n-obj-in options]
    (go
      (try
        (let [_f (<p! (.init i18n-obj-in (clj->js options)))]
          (reset! translations-loaded-and-init-done true)
          (js/console.log  "i18n init is done successfully"))
      ;; Error should not happen as we have already loaded a valid translations data before calling init 
      ;; Still what to do if there is any error in initializing 'i18n'? 
        (catch js/Error err
          (js/console.log (ex-cause err))))))

;;https://www.i18next.com/misc/creating-own-plugins#backend

#_(def temp-services (atom {}))

(defn- create-back-end [translations]
  {:type "backend"

   :init (fn [services, backendOptions, i18nextOptions]
           ;;(println "services:  " services) (println "backendOptions: "  backendOptions) (println "i18nextOptions: " i18nextOptions)
           #_(reset! temp-services services))

   ;; Typically read woul have been called when we call use fn
   ;; The translations data for the main language and fallback language will be 
   ;; called through callback and i18n retains internally
   :read (fn [language _namespace callback]
           (println "create-back-end language namespace callback " language namespace callback)
           ;;(println "data  is... " (clj->js (get @translations-data language)))
           (callback nil (clj->js
                          ;; (get @translations-data language)
                          (get translations language))))})

(defn- setup-i18n-with-backend [language back-end]
  (let [m  {:lng language
            :fallbackLng "en"
            :compatibilityJSON "v4"
            :debug true}
        instance (.createInstance i18n-obj)]
    (.use instance (clj->js back-end))
    (create-i18n-init instance m)))

;; Used with default prebuilt 'i18n' instance
#_(defn- setup-i18n-with-backend [language back-end]
    (let [m  {:lng language
              :fallbackLng "en"
              :compatibilityJSON "v4"
              :debug true}]
      (.use i18n-obj (clj->js back-end))
      (init-i18n m)
      #_(init-i18n (.use i18n-obj (clj->js back-end)) m)))

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


#_(defn setup-i18n []
    (let [m  {:lng "en"
              :debug true
              :resources {:en {:translation {"key" "hello world"}}}}]
      (init-i18n m)))

#_(def back-end {:type "backend"
                 :read (fn [language namespace callback]
                         (println "language namespace callback " language namespace callback)
                         (callback nil (clj->js {"key" "hello world...."})))

                 :create (fn [languages, namespace, key, fallbackValue]
                           (println "languages namespace key, fallbackValue " languages namespace key fallbackValue))})


#_(defn setup-i18n-with-backend []
    (let [m  {:lng "en"
              :debug true}]
      (.use i18n (clj->js back-end))
      (init-i18n m)))
