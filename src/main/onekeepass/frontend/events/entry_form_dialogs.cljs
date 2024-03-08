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
   [onekeepass.frontend.utils :as u :refer [contains-val?]]
   [onekeepass.frontend.background :as bg]))


;;;;;;;;;;;;;;;;;;;;;  TOTP settings dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn otp-settings-dialog-show []
  (dispatch [:otp-settings-dialog-show true]))

(defn otp-settings-dialog-custom-settings-show []
  (dispatch [:otp-settings-dialog-custom-settings-show]))

(defn otp-settings-dialog-close []
  (dispatch [:otp-settings-dialog-show false]))

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
                                 :field-name "otp"
                                 :period 30
                                 :digits 6
                                 :show-custom-settings false
                                 :error-fields {}
                                 :data-type ONE_TIME_PASSWORD})

(defn- init-otp-settings-dialog-data [db]
  (assoc-in-key-db db [:otp-settings-dialog] otp-field-dialog-init-data))

(defn validate-fields [{:keys [secret-code field-name period digits]}]
  (cond-> {}
    (nil? secret-code)
    (assoc :secret-code "A valid value is required for secret code")

    (nil? field-name)
    (assoc :field-name "A valid field name is required ")

    (str/blank? period)
    (assoc :period "Period cannot be blank")))


(defn- to-otp-settings-data [db & {:as kws}]
  ;;(println "kws are " kws)
  (let [data (get-in-key-db db [:otp-settings-dialog])
        data (merge data kws)]
    (assoc-in-key-db db [:otp-settings-dialog] data)))


(reg-event-fx
 :otp-settings-dialog-update
 (fn [{:keys [db]} [_event-id m-kw-key m-value]]
   {:db (-> db
            (to-otp-settings-data m-kw-key m-value))}))

(reg-event-fx
 :otp-settings-dialog-show
 (fn [{:keys [db]} [_event-id show?]]
   {:db (-> db
            init-otp-settings-dialog-data
            (assoc-in-key-db [:otp-settings-dialog :dialog-show] show?))}))

(reg-event-fx
 :otp-settings-dialog-custom-settings-show
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db [:otp-settings-dialog  :show-custom-settings] true))}))


(reg-event-fx
 :otp-settings-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   (let [data (get-in-key-db db [:otp-settings-dialog])
         errors (validate-fields data)]

     (if (boolean (seq errors))
       {:db (-> db (assoc-in-key-db [:otp-settings-dialog :error-fields] errors))}

       {:db  (-> db (assoc-in-key-db [:otp-settings-dialog :dialog-show] false))}))))


(reg-sub
 :otp-settings-dialog-data
 (fn [db [_event-id]]
   (get-in-key-db db [:otp-settings-dialog])))