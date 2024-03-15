(ns onekeepass.frontend.events.entry-form-dialogs
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-fx reg-event-db reg-event-fx reg-sub  dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             active-db-key
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             fix-tags-selection-prefix]]
   [onekeepass.frontend.constants :refer [PASSWORD ONE_TIME_PASSWORD]]
   [onekeepass.frontend.utils :as u :refer [str->int contains-val?]]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.constants :as const]))


;;;;;;;;;;;;;;;;;;;;;  TOTP settings dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn otp-settings-dialog-show [section-name]
  ;; First need to ensure that some required fields are valid
  (dispatch [:entry-form/validate-form-fields
             (fn []
               (dispatch [:otp-settings-dialog-show true section-name]))]))

(defn otp-settings-dialog-custom-settings-show []
  (dispatch [:otp-settings-dialog-custom-settings-show]))

(defn otp-settings-dialog-close []
  (dispatch [:otp-settings-dialog-show false nil]))

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
                                 :field-name "otp"
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
  [{:keys [secret-code field-name period digits]}]
  (cond-> {}
    (nil? secret-code)
    (assoc :secret-code "A valid value is required for secret code")

    (nil? field-name)
    (assoc :field-name "A valid field name is required ")

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

(defn set-error-fields
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
   (let [val (convert-value m-kw-key m-value)
         db (cond
              (and (= m-kw-key :secret-code) (str/starts-with? val const/OTP_URL_PREFIX))
              (-> db
                  (to-otp-settings-data m-kw-key val :otp-uri-used true :show-custom-settings false))

              (= m-kw-key :secret-code)
              (-> db
                  (to-otp-settings-data m-kw-key val :otp-uri-used false))
              :else
              (-> db
                  (to-otp-settings-data m-kw-key val)))
         db (set-error-fields db)
         db (assoc-in-key-db db [:otp-settings-dialog :api-error-text] nil)]
     {:db db})))

(reg-event-fx
 :otp-settings-dialog-show
 (fn [{:keys [db]} [_event-id show? section-name]]
   {:db (-> db
            init-otp-settings-dialog-data
            (assoc-in-key-db [:otp-settings-dialog :dialog-show] show?)
            (assoc-in-key-db [:otp-settings-dialog :section-name] section-name))}))

(reg-event-fx
 :otp-settings-dialog-custom-settings-show
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db [:otp-settings-dialog  :show-custom-settings] true))}))


(reg-event-fx
 :otp-settings-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [secret-code period digits hash-algorithm] :as data} (get-in-key-db db [:otp-settings-dialog])
         errors (validate-fields data)]
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
   {:db (-> db (assoc-in-key-db [:otp-settings-dialog :api-error-text] error))}))

(reg-event-fx
 :otp-settings-form-url-success
 (fn [{:keys [db]} [_event-id otp-url]]
   (let [{:keys [section-name field-name]} (get-in-key-db db [:otp-settings-dialog])]
     {:db  (-> db (assoc-in-key-db [:otp-settings-dialog :dialog-show] false))
      :fx [[:dispatch [:entry-form/otp-url-formed section-name field-name otp-url]]]})))


(reg-sub
 :otp-settings-dialog-data
 (fn [db [_event-id]]
   (get-in-key-db db [:otp-settings-dialog])))


(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys))