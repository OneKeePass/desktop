(ns onekeepass.frontend.events.app-settings
  (:require-macros [onekeepass.frontend.okp-macros :refer [as-map]]
                   [onekeepass.frontend.translation :refer [tr-m]])
  (:require [clojure.string :as str]
            [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.events.common :as cmn-events :refer [on-error]]
            [onekeepass.frontend.utils :refer [str->int]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

(def panels [:general-info  :security-info])

(defn field-update-factory [kw-field-name]
  (fn [^js/Event e]
    (dispatch [:app-settings-field-update kw-field-name (->  e .-target .-value)])))

#_(defn app-settings-dialog-read-start []
    (dispatch [:app-settings/read-start]))

(defn app-settings-dialog-close []
  (dispatch [:app-settings-dialog-close]))

(defn app-settings-panel-select [kw-panel]
  (dispatch [:app-settings-panel-select kw-panel]))

(defn app-settings-save []
  (dispatch [:app-settings-save]))

(defn app-settings-dialog-data []
  (subscribe [:app-settings-dialog-data]))

#_(def field-not-empty? (comp not empty?))

;; Note ks includes :app-settings
(defn- convert-value
  "(->  e .-target .-value) returns a string value 
  and to make comparision with undo-data, we need to make sure both values 
  are 'int' type
  "
  [ks value]
  (cond (or (= ks [:app-settings :preference-data :clipboard-timeout])
            (= ks [:app-settings :preference-data :session-timeout]))
        (str->int value)

        :else
        value))

(defn- validate-security-fields
  [app-db]
  (let [{:keys [clipboard-timeout session-timeout]} (get-in app-db [:app-settings :preference-data])
        ;; Need to convert incoming str values to the proper int values 
        errors (cond-> {}
                 (or (nil? session-timeout) (or (< session-timeout 1) (> session-timeout 1440)))
                 (assoc :session-timeout (tr-m  appSettings "sessionValidVal"))

                 (or (nil? clipboard-timeout) (or (< clipboard-timeout 10) (> clipboard-timeout 300)))
                 (assoc :clipboard-timeout (tr-m  appSettings "clipboardValidVal")))]

    errors))


(defn- validate-required-fields
  [db panel]
  (cond
    (= panel :security-info)
    (validate-security-fields db)

    :else
    {}))

#_(defn- validate-all-panels [db]
  (reduce (fn [v panel]
            (let [errors (validate-required-fields db panel)]
              (if (boolean (seq errors))
                ;; early return when the first panel has some errors
                (reduced [panel errors])
                v))) [] panels))

(def settings-default-data {:dialog-show false
                            :panel :general-info
                            :api-error-text nil
                            :error-fields nil
                            :preference-data {}})

#_(defn init-dialog-data [app-db])

(reg-event-fx
 :app-settings/read-start
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in [:app-settings] settings-default-data)
            (assoc-in  [:app-settings :dialog-show] true)
            (assoc-in  [:app-settings :preference-data] (:app-preference db)))}))

(reg-event-fx
 :app-settings-dialog-close
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in db [:app-settings] settings-default-data)}))

(reg-event-db
 :app-settings-panel-select
 (fn [db [_event-id kw-panel]]
   (let [current (get-in db [:app-settings :panel])
         errors  (validate-required-fields db current)]
     (if (boolean (seq errors))
       (-> db (assoc-in [:app-settings :error-fields] errors))
       (-> db (assoc-in [:app-settings :panel] kw-panel)
           (assoc-in [:app-settings :error-fields] nil))))))

(reg-event-db
 :app-settings-field-update
 (fn [db [_event-id kw-field-name value]]
   (let [ks (into [:app-settings] (if (vector? kw-field-name)
                                    kw-field-name
                                    [kw-field-name]))

         current-panel (get-in db [:app-settings :panel])
         val (convert-value ks value)
         ;; Set the updated value 
         db (assoc-in db ks val)
         errors  (validate-required-fields db current-panel)
         db (-> db (assoc-in [:app-settings :error-fields] errors))]
     db)))

(reg-event-fx
 :app-settings-save
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [theme
                 language
                 session-timeout
                 clipboard-timeout
                 default-entry-category-groupings]} (-> db :app-settings :preference-data)]
     {:fx [[:bg-update-preference (as-map [theme
                                           language
                                           session-timeout
                                           clipboard-timeout
                                           default-entry-category-groupings])]]})))

(reg-fx
 :bg-update-preference
 (fn [preference-data]
   (bg/update-preference preference-data
                         (fn [api-reponse]
                           (when-not (on-error api-reponse)
                             (dispatch [:common/load-app-preference])
                             (dispatch [:app-settings-dialog-close]))))))

(reg-sub
 :app-settings-dialog-data
 (fn [db _query-vec]
   (get-in db [:app-settings])))


(comment
  (-> @re-frame.db/app-db keys))