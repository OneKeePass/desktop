(ns onekeepass.frontend.events.password-generator
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.background-password :as bg-pwd]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             on-error]]
   [onekeepass.frontend.translation :refer-macros [tr-m]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub
                          subscribe]]))


(defn generator-dialog-data-update [kw value]
  (dispatch [:generator-dialog-data-update kw value]))

(defn password-options-update [kw value]
  (dispatch [:password-options-update kw value]))

(defn generator-password-copied
  "Generated password is copied to the password field or to clipboard"
  []
  (dispatch [:generator-password-copied]))

(defn set-active-password-generator-panel [kw-panel-id]
  (dispatch [:set-active-password-generator-panel kw-panel-id]))

(defn pass-phrase-options-update
  "Called to update pass phrase option fields that are not enum fields"
  [kw value]
  (dispatch [:pass-phrase-options-update kw value]))

(defn pass-phrase-options-select-field-update
  "Called to update pass phrase option fields that are formed from enums WordListSource and ProbabilityOption "
  [field-name-kw value]
  (dispatch [:pass-phrase-options-select-field-update field-name-kw value]))

(defn generator-dialog-data []
  (subscribe [:generator-dialog-data]))

#_(defn generator-panel-selected []
  (subscribe [:generator-panel-selected]))

;;;;
(def generator-dialog-init-data
  {:dialog-show false
   :password-visible true
   :text-copied false
   :callback-on-copy-fn nil
   ;; :password or :pass-phrase
   :panel-shown :password
   ;; All fields from struct PasswordGenerationOptions
   :password-options {:length 8
                      :numbers true
                      :lowercase-letters true
                      :uppercase-letters true
                      :symbols true
                      :spaces false
                      :exclude-similar-characters true
                      :strict true}
   ;; All fields from struct PassphraseGenerationOptions and intialized from app-preference
   :phrase-generator-options {}
    ;; some fields from struct 'AnalyzedPassword'  form the map in :password-result                             
   :password-result {:password nil
                     :analyzed-password nil
                     :is-common false
                     ;; value of :score is a map from enum PasswordScore
                     ;; note the use of #[serde(tag = "name")] in PasswordScore
                     :score {:name nil
                             :raw-value nil
                             :score-text nil}}})

(defn- init-dialog-data [app-db]
  (let [phrase-generator-options (cmn-events/app-preference-phrase-generator-options app-db)]
    (-> app-db (assoc-in [:generator :dialog-data] generator-dialog-init-data)
        (assoc-in [:generator :dialog-data :phrase-generator-options] phrase-generator-options))))

;; Updates all top level fields of dialog-data
(defn- to-generator-dialog-data [db & {:as kws}]
  (let [data (get-in db [:generator :dialog-data])
        data (merge data kws)]
    (assoc-in db [:generator :dialog-data] data)))

;; Updates all :data fields
(defn- to-password-options-data [db kw-option-key & {:as kws}]
  (let [data (get-in db [:generator :dialog-data kw-option-key #_:password-options])
        data (merge data kws)]
    (assoc-in db [:generator :dialog-data kw-option-key #_:password-options] data)))

(reg-event-db
 :generator-dialog-data-update
 (fn [db [_event-id field-name-kw value]]
   (-> db
       (to-generator-dialog-data field-name-kw value))))

(reg-event-fx
 :password-options-update
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   (let [db (-> db
                (to-password-options-data :password-options field-name-kw value)
                (to-generator-dialog-data :text-copied false))
         {:keys [length] :as po} (get-in db [:generator :dialog-data :password-options])]
     (if (empty? (str/trim (str length)))
       {:db db
        :fx [[:dispatch [:common/message-snackbar-error-open (tr-m passwordGenerator above8)]]]}
       {:db db
        :fx [[:dispatch [:common/message-snackbar-error-close]]
             [:bg-analyzed-password po]]}))))

(reg-event-fx
 :password-generator/start
 (fn [{:keys [db]} [_event-id callback-on-copy-fn]]
   (let [db (-> db init-dialog-data
                (assoc-in [:generator :dialog-data :callback-on-copy-fn] callback-on-copy-fn))
         po (get-in db [:generator :dialog-data :password-options])]
     {:db db
      :fx [[:bg-analyzed-password po]]})))

(defn- handle-error
  "Receives the error part of the api response"
  [error-text]
  (let [txt (condp = error-text
              "You need to enable at least one kind of characters."
              (tr-m passwordGenerator oneKindCharaterRequired)

              "The length of passwords cannot be 0."
              (tr-m passwordGenerator length0)

              error-text)]
    (dispatch [:common/message-snackbar-error-open txt])))

(reg-fx
 :bg-analyzed-password
 (fn [password-options]
   (bg/analyzed-password password-options
                         (fn [api-response]
                           (when-let [result (check-error api-response handle-error)]
                             (dispatch [:password-generation-complete result]))))))

(reg-event-db
 :password-generation-complete
 (fn [db [_event-id password-result]]
   ;;(println ":password-generation-complete called "password-result)
   (-> db
       (assoc-in  [:generator :dialog-data :password-result] password-result)
       (assoc-in  [:generator :dialog-data :dialog-show] true))))

(reg-event-fx
 :generator-password-copied
 (fn [{:keys [db]} [_event-id]]
   (let [data (get-in db [:generator :dialog-data])
         callback-on-copy-fn (:callback-on-copy-fn data)
         password (-> data :password-result :password)
         score (-> data :password-result :score)]
     (if (nil? callback-on-copy-fn)
       (do
         (bg/write-to-clipboard password) ;; side effect!
         {:fx [[:dispatch [:generator-dialog-data-update :text-copied true]]]})
       (do
         (callback-on-copy-fn password score) ;; side effect!
         {:fx [[:dispatch [:generator-dialog-data-update :dialog-show false]]]})))))


(reg-sub
 :generator-dialog-data
 (fn [db [_query-id]]
   (get-in db [:generator :dialog-data])))

#_(reg-sub
 :generator-password-result
 (fn [db [_query-id]]
   (get-in db [:generator :dialog-data :password-result])))

#_(reg-sub
 :generator-panel-selected
 (fn [db [_query-id]]
   (get-in db [:generator :dialog-data :panel-shown])))


;;;;;;;;;;;;;;;;;;;;;;;; Pass phrase ;;;;;;;;;;;;;;;;;;;;;;
(reg-event-fx
 :set-active-password-generator-panel
 (fn [{:keys [db]} [_event-id  kw-panel-id]]
   (let [po (get-in db [:generator :dialog-data :password-options])
         ppo (get-in db [:generator :dialog-data :phrase-generator-options])]
     {:db (assoc-in db [:generator :dialog-data :panel-shown] kw-panel-id)
      :fx [(if (= kw-panel-id :password)
             [:bg-analyzed-password po] [:bg-generate-password-phrase ppo])]})))

(reg-event-fx
 :pass-phrase-options-update
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   (let [db (-> db
                (to-password-options-data :phrase-generator-options field-name-kw value)
                (to-generator-dialog-data :text-copied false))
         {:keys [words] :as po} (get-in db [:generator :dialog-data :phrase-generator-options])]
     (if (empty? (str/trim (str words)))
       {:db db
        :fx [[:dispatch [:common/message-snackbar-error-open (tr-m passwordGenerator "atLeastOneWord")]]]}
       {:db db
        :fx [[:dispatch [:common/message-snackbar-error-close]]
             [:bg-generate-password-phrase po]]}))))


(reg-event-fx
 :pass-phrase-options-select-field-update
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   (let [db (-> db (assoc-in [:generator :dialog-data :phrase-generator-options field-name-kw :type-name] value)
                (to-generator-dialog-data :text-copied false))
         ;; For now, we set probability value of 50% as content. That is half the time
         db (if (or (= field-name-kw :capitalize-first) (= field-name-kw :capitalize-words))
              (if (= value "Sometimes")
                (assoc-in db [:generator :dialog-data :phrase-generator-options field-name-kw :content] 0.5)
                (assoc-in db [:generator :dialog-data :phrase-generator-options field-name-kw :content] nil))
              db)

         po (get-in db [:generator :dialog-data :phrase-generator-options])]

     {:db db
      :fx [[:bg-generate-password-phrase po]]})))

(reg-fx
 :bg-generate-password-phrase
 (fn [pass-phrase-options]
   (bg-pwd/generate-password-phrase
    pass-phrase-options
    (fn [api-response]
      (when-let [result (check-error api-response #(dispatch [:common/message-snackbar-error-open %]))]
        (dispatch [:pass-phrase-generation-complete result]))))))

;; map generated-pass-phrase-m is from struct GeneratedPassPhrase
(reg-event-fx
 :pass-phrase-generation-complete
 (fn [{:keys [db]} [_event-id generated-pass-phrase-m]]
   ;; We check whether the user changed any pass pgrase generation option and we call 
   ;; the preference update accordingly
   (let [pref-data (cmn-events/app-preference-phrase-generator-options db)
         pp-data (get-in db [:generator :dialog-data :phrase-generator-options])
         modified (not= pref-data pp-data) #_(and (seq pref-data) (not= pref-data pp-data))]

     {:fx [[:dispatch [:password-generation-complete generated-pass-phrase-m]]
           (when modified
             [:bg-update-pass-gen-preference {:pass-phrase-options pp-data}])]})))


;; This is similar to the app preference update call in
;; src/main/onekeepass/frontend/events/app_settings.cljs
;; Here we are using 'bg/update-preference' to update the PasswordGeneratorPreference only
(reg-fx
 :bg-update-pass-gen-preference
 (fn [preference-data]
   (bg/update-preference preference-data
                         (fn [api-reponse]
                           (when-not (on-error api-reponse)
                             ;; Reloads the whole app preference 
                             ;; This ensures the pass phrase option in preference data is the updated one 
                             (dispatch [:common/load-app-preference]))))))


(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db :generator)
  (get-in @re-frame.db/app-db [:generator :dialog-data :password-options])
  (def a (to-password-options-data :password-options @re-frame.db/app-db :length 11))
  (-> @re-frame.db/app-db :generator :dialog-data :password-result :score))

