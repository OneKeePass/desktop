(ns onekeepass.frontend.events.entry-form-dialogs
  (:require-macros [onekeepass.frontend.okp-macros :refer [as-map]])
  (:require [clojure.string :as str]
            [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.constants :refer [ONE_TIME_PASSWORD_TYPE OTP
                                                   OTP_URL_PREFIX]]
            [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                                      assoc-in-key-db
                                                                      check-error
                                                                      get-in-key-db]]
            [onekeepass.frontend.events.entry-form-common :refer [add-section-field
                                                                  entry-form-key
                                                                  is-field-exist]]
            [onekeepass.frontend.translation :refer [lstr-sm]]
            [onekeepass.frontend.utils :as u :refer [str->int]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub
                                   subscribe]]))


;;;;;;;;;;;;;;;;;;;;;  TOTP settings dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn otp-settings-dialog-show [section-name standard-field?]
  ;; First need to ensure that some required fields are valid
  (dispatch [:entry-form/validate-form-fields
             (fn []
               (dispatch [:otp-settings-dialog-show true section-name (if (nil? standard-field?) false standard-field?)]))]))

(defn otp-settings-dialog-custom-settings-show []
  (dispatch [:otp-settings-dialog-custom-settings-show]))

(defn otp-settings-dialog-close []
  (dispatch [:otp-settings-dialog-show false nil true]))

(defn otp-settings-dialog-update [kw value]
  (dispatch [:otp-settings-dialog-update kw value]))

(defn otp-settings-dialog-ok []
  (dispatch [:otp-settings-dialog-ok]))

(defn otp-settings-dialog-data []
  (subscribe [:otp-settings-dialog-data]))

(def otp-field-dialog-init-data {:dialog-show false
                                 :hash-algorithm "SHA1"
                                 :section-name nil
                                 :secret-code nil
                                 :otp-uri-used false
                                 :standard-field true ;; field-name should be "otp"
                                 :field-name OTP
                                 :data-type ONE_TIME_PASSWORD_TYPE
                                 :protected true
                                 :required false
                                 :period 30
                                 :digits 6
                                 :show-custom-settings false
                                 :error-fields {}
                                 :api-error-text nil})

(defn- init-otp-settings-dialog-data [db]
  (assoc-in-key-db db [:otp-settings-dialog] otp-field-dialog-init-data))

(defn- convert-value
  "Called to convert string value to int
   This is required as type of value comes as string eventhough number is entered 
   "
  [kw value]
  (cond
    (or (= kw :period) (= kw :digits))
    (str->int value)

    :else
    value))

(defn validate-fields
  "Validates each field and accumulates the errors"
  [db]
  (let [{:keys [secret-code standard-field field-name period digits]} (get-in-key-db db [:otp-settings-dialog])]
    (cond-> {}
      (nil? secret-code)
      (assoc :secret-code "A valid value is required for secret code")

      (and (not standard-field) (nil? field-name))
      (assoc :field-name "A valid field name is required ")

      (and (not standard-field) (is-field-exist db field-name) #_(= OTP field-name))
      (assoc :field-name "Please provide another name for the field")

      (or (nil? period) (or (< period 1) (> period 60)))
      (assoc :period "Valid values should be in the range 1 - 60")

      (or (nil? digits) (or (< digits 6) (> digits 10)))
      (assoc :digits "Valid values should be in the range 6 - 10"))))

(defn set-error-fields
  "Setts the error filed values if any 
   Returns the updated app-db
  "
  [db]
  (let [errors (validate-fields db)]
    (assoc-in-key-db db [:otp-settings-dialog :error-fields] errors)))

#_(defn validate-fields
    "Validates each field and accumulates the errors"
    [{:keys [secret-code standard-field field-name period digits]}]
    (cond-> {}
      (nil? secret-code)
      (assoc :secret-code "A valid value is required for secret code")

      (and (not standard-field) (nil? field-name))
      (assoc :field-name "A valid field name is required ")

      (and (not standard-field) (= OTP field-name))
      (assoc :field-name "Please provide another name for the field")

      (or (nil? period) (or (< period 1) (> period 60)))
      (assoc :period "Valid values should be in the range 1 - 60")

      (or (nil? digits) (or (< digits 6) (> digits 10)))
      (assoc :digits "Valid values should be in the range 6 - 10")))

#_(defn validate
    "Returns the error map"
    [db]
    (let [data (get-in-key-db db [:otp-settings-dialog])
          errors (validate-fields data)]
      errors))

#_(defn set-error-fields
    "Setts the error filed values if any 
   Returns the updated app-db
  "
    [db]
    (let [data (get-in-key-db db [:otp-settings-dialog])
          errors (validate-fields data)]
      (assoc-in-key-db db [:otp-settings-dialog :error-fields] errors)))

(defn- to-otp-settings-data [db & {:as kws}]
  (let [data (get-in-key-db db [:otp-settings-dialog])
        data (merge data kws)]
    (assoc-in-key-db db [:otp-settings-dialog] data)))

(reg-event-fx
 :otp-settings-dialog-update
 (fn [{:keys [db]} [_event-id m-kw-key m-value]]
   (let [standard-field? (get-in-key-db db [:otp-settings-dialog :standard-field])
         val (convert-value m-kw-key m-value)
         db (cond
              (and (= m-kw-key :secret-code) (str/starts-with? val OTP_URL_PREFIX))
              (-> db
                  (to-otp-settings-data m-kw-key val :otp-uri-used true :show-custom-settings false))

              (= m-kw-key :secret-code)
              (-> db
                  (to-otp-settings-data m-kw-key val :otp-uri-used false))
              :else
              (-> db
                  (to-otp-settings-data m-kw-key val)))
         ;; check error only for standard field during field entry. Otherwise the validation of fields are 
         ;; done on ok
         db (if-not standard-field? db (set-error-fields db))
         db (assoc-in-key-db db [:otp-settings-dialog :api-error-text] nil)]
     {:db db})))

(reg-event-fx
 :otp-settings-dialog-show
 (fn [{:keys [db]} [_event-id show? section-name standard-field?]]
   (let [standard-field  (if (nil? standard-field?) false standard-field?)
         field-name (if standard-field OTP nil)]
     {:db (-> db
              init-otp-settings-dialog-data
              (assoc-in-key-db [:otp-settings-dialog :standard-field] standard-field)
              (assoc-in-key-db [:otp-settings-dialog :field-name] field-name)
              (assoc-in-key-db [:otp-settings-dialog :dialog-show] show?)
              (assoc-in-key-db [:otp-settings-dialog :section-name] section-name))})))

(reg-event-fx
 :otp-settings-dialog-custom-settings-show
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db [:otp-settings-dialog  :show-custom-settings] true))}))

(reg-event-fx
 :otp-settings-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [secret-code period digits hash-algorithm] :as data} (get-in-key-db db [:otp-settings-dialog])
         errors (validate-fields db)]
     (if (boolean (seq errors))
       {:db (-> db (assoc-in-key-db [:otp-settings-dialog :error-fields] errors))}

       {:db (-> db (assoc-in-key-db [:otp-settings-dialog :error-fields] nil))
        :fx [[:bg-form-otp-url [{:secret-or-url secret-code
                                 :period period
                                 :digits digits
                                 :hash-algorithm hash-algorithm}]]]}))))

(reg-fx
 :bg-form-otp-url
 (fn [[otp-settings]]
   (bg/form-otp-url otp-settings (fn [api-response]
                                   (when-let [opt-url (check-error
                                                       api-response
                                                       #(dispatch [:otp-settings-form-url-error %]))]
                                     (dispatch [:otp-settings-form-url-success opt-url]))))))

;; Called when api call returns any error
(reg-event-fx
 :otp-settings-form-url-error
 (fn [{:keys [db]} [_event-id error]]
   (let [secret-code-field-error (if (str/starts-with? error "OtpKeyDecodeError")
                                   (assoc {} :secret-code "Valid encoded key or full TOTPAuth URL is required") {})]
     {:db (-> db
              (assoc-in-key-db [:otp-settings-dialog :error-fields] secret-code-field-error)
              (assoc-in-key-db [:otp-settings-dialog :api-error-text] error))})))

(reg-event-fx
 :otp-settings-form-url-success
 (fn [{:keys [db]} [_event-id otp-url]]
   (let [{:keys [section-name field-name standard-field] :as data} (get-in-key-db db [:otp-settings-dialog])
         ;; Need to add the field to the section if it is not standard field
         db (if standard-field  db (add-section-field db (select-keys data [:section-name :field-name :protected :required :data-type])))]
     ;;(println ":otp-settings-form-url-success section-name field-name otp-url " section-name field-name otp-url)
     {:db  (-> db (assoc-in-key-db [:otp-settings-dialog :dialog-show] false))
      :fx [[:dispatch [:entry-form/otp-url-formed section-name field-name otp-url]]]})))

(reg-sub
 :otp-settings-dialog-data
 (fn [db [_event-id]]
   (get-in-key-db db [:otp-settings-dialog])))

;;;;;;;;;;;;;;;;;;;;;;;; Clone entry options dialog ;;;;;;;;;;;;;;;

(defn clone-entry-options-dialog-show [entry-uuid]
  (dispatch [:clone-entry-options-dialog-show entry-uuid]))

(defn clone-entry-options-dialog-update [kw value]
  (dispatch [:clone-entry-options-dialog-update kw value]))

(defn clone-entry-options-dialog-ok []
  (dispatch [:clone-entry-options-dialog-ok]))

(defn clone-entry-options-dialog-close []
  (dispatch [:clone-entry-options-dialog-close]))

(defn selected-group-info [group-uuid]
  (subscribe [:group-tree-content/group-summary-info-by-id group-uuid]))

(defn clone-entry-options-dialog-data []
  (subscribe [:clone-entry-options-dialog-data]))

(def ^:private clone-entry-options-dialog-init-data {:dialog-show false
                                                     :error-fields {}
                                                     :api-error-text nil
                                                     :new-title nil
                                                     :parent-group-uuid nil
                                                     :entry-uuid nil
                                                     :keep-histories false
                                                     :link-by-reference false})

(defn- init-clone-entry-options-dialog-data [db]
  (assoc-in-key-db db [:clone-entry-options-dialog] clone-entry-options-dialog-init-data))

(defn- to-clone-entry-options-data [db & {:as kws}]
  (let [data (get-in-key-db db [:clone-entry-options-dialog])
        data (merge data kws)]
    (assoc-in-key-db db [:clone-entry-options-dialog] data)))

(reg-event-fx
 :clone-entry-options-dialog-show
 (fn [{:keys [db]} [_event-id entry-uuid]]
   (let [{:keys [uuid group-uuid title]} (get-in-key-db db [entry-form-key :data])
         new-title (if (= uuid entry-uuid) (str title " Copy") nil)]
     {:db (-> db
              init-clone-entry-options-dialog-data
              (assoc-in-key-db [:clone-entry-options-dialog :dialog-show] true)
              (assoc-in-key-db [:clone-entry-options-dialog :new-title] new-title)
              (assoc-in-key-db [:clone-entry-options-dialog :parent-group-uuid] group-uuid)
              (assoc-in-key-db [:clone-entry-options-dialog :entry-uuid] entry-uuid))})))

(reg-event-fx
 :clone-entry-options-dialog-update
 (fn [{:keys [db]} [_event-id m-kw-key m-value]]
   {:db (-> db (to-clone-entry-options-data m-kw-key m-value))}))

(reg-event-fx
 :clone-entry-options-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   (let [db-key (active-db-key db)
         {:keys [entry-uuid new-title parent-group-uuid keep-histories link-by-reference]}
         (get-in-key-db db [:clone-entry-options-dialog])]
     {:fx [[:bg-clone-entry [db-key
                             entry-uuid
                             (as-map [new-title parent-group-uuid keep-histories link-by-reference])]]]})))

(reg-fx
 :bg-clone-entry
 (fn [[db-key entry-uuid entry-clone-option]]
   (bg/clone-entry db-key
                   entry-uuid
                   entry-clone-option
                   (fn [api-response]
                     (when-let  [cloned-entry-uuid (check-error api-response)]
                       (dispatch [:entry-clone-complte cloned-entry-uuid]))))))

(reg-event-fx
 :entry-clone-complte
 (fn [{:keys [_db]} [_event-id cloned-entry-uuid]]
   {:fx [;; This refresh calls uses the original entry-uuid
         [:dispatch [:common/refresh-forms]]

         ;; Following two events selects the cloned entry in entry list and loads the entry form with 
         ;; the cloned entry data
         [:dispatch [:entry-list/update-selected-entry-id cloned-entry-uuid]]
         [:dispatch [:entry-form-ex/find-entry-by-id cloned-entry-uuid]]

         [:dispatch [:clone-entry-options-dialog-close]]
         [:dispatch [:common/message-snackbar-open (lstr-sm 'entryCloned)]]]}))

(reg-event-fx
 :clone-entry-options-dialog-close
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            init-clone-entry-options-dialog-data)}))

(reg-sub
 :clone-entry-options-dialog-data
 (fn [db [_event-id]]
   (get-in-key-db db [:clone-entry-options-dialog])))

#_(reg-sub
   :clone-entry-parent-group-info
   (fn [[_query-id group-uuid]]
     (subscribe [:group-tree-content/group-summary-info-by-id group-uuid]))
   (fn [group-info]))


(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys))