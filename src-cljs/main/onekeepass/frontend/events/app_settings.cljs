(ns onekeepass.frontend.events.app-settings
  (:require-macros [onekeepass.frontend.okp-macros :refer [as-map]]
                   [onekeepass.frontend.translation :refer [tr-m]])
  (:require [clojure.string :as str]
            [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.events.common :as cmn-events :refer [check-error on-error]]
            [onekeepass.frontend.utils :refer [str->int]]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx
                                   reg-sub subscribe]]))

(def panels [:general-info :security-info :file-management :browser-integration])

(defn field-update-factory
  "Creates a function that can be used as on-change handler for text fields
  The kw-field-name can be a single keyword or a vector of keywords
  depending on where the field value needs to be updated in the app-settings data
  For example, to update the 'theme' field in preference-data map, we need to
  pass [:preference-data :theme] as the kw-field-name argument
  "
  [kw-field-name]
  (fn [^js/Event e]
    (dispatch [:app-settings-field-update kw-field-name (->  e .-target .-value)])))

(defn field-update [kw-field-name value]
  ;; (println "field-update: " kw-field-name value)
  (dispatch [:app-settings-field-update kw-field-name value]))

#_(defn app-settings-dialog-read-start []
    (dispatch [:app-settings/read-start]))

(defn app-settings-dialog-close []
  (dispatch [:app-settings-dialog-close]))

(defn app-settings-panel-select [kw-panel]
  (dispatch [:app-settings-panel-select kw-panel]))

(defn app-settings-save []
  (dispatch [:app-settings-save]))

(defn open-backup-dir-dialog []
  (dispatch [:app-settings/open-backup-dir-dialog]))

(defn app-settings-dialog-data []
  (subscribe [:app-settings-dialog-data]))

(defn app-settings-modified []
  (subscribe [:app-settings-modified]))

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
                 (or (nil? session-timeout) (< session-timeout 1)  (> session-timeout 1440))
                 (assoc :session-timeout (tr-m  appSettings "sessionValidVal"))

                 (or (nil? clipboard-timeout) (< clipboard-timeout 10)  (> clipboard-timeout 300) #_(or (< clipboard-timeout 10) (> clipboard-timeout 300)))
                 (assoc :clipboard-timeout (tr-m  appSettings "clipboardValidVal")))]

    errors))

(defn- validate-file-management-fields
  [app-db]
  (let [{:keys [backup]} (get-in app-db [:app-settings :preference-data])
        {:keys [enabled dir]} backup]
    (cond-> {}
      (and enabled (str/blank? dir))
      (assoc :backup-dir "Backup directory is required when backup is enabled"))))


(defn- validate-required-fields
  [db panel]
  (cond
    (= panel :security-info)
    (validate-security-fields db)

    (= panel :file-management)
    (validate-file-management-fields db)

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
                            :undo-data nil
                            :preference-data {}})

#_(defn init-dialog-data [app-db])

(reg-event-fx
 :app-settings/read-start
 (fn [{:keys [db]} [_event-id]]
   (let [pd (:app-preference db)]
     {:db (-> db (assoc-in [:app-settings] settings-default-data)
              (assoc-in  [:app-settings :dialog-show] true)
              (assoc-in [:app-settings :undo-data]
                        (select-keys pd [:theme
                                         :language
                                         :session-timeout
                                         :clipboard-timeout
                                         :backup
                                         :browser-ext-support
                                         :default-entry-category-groupings]))
              (assoc-in  [:app-settings :preference-data] pd))})))

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
                 backup
                 browser-ext-support
                 default-entry-category-groupings]} (-> db :app-settings :preference-data)]
     {:fx [[:bg-update-preference (as-map [theme
                                           language
                                           session-timeout
                                           clipboard-timeout
                                           backup
                                           browser-ext-support
                                           default-entry-category-groupings])]]})))

(reg-event-fx
 :app-settings/open-backup-dir-dialog
 (fn [{:keys [db]} [_event-id]]
   (let [default-path (get-in db [:app-settings :preference-data :backup :dir])]
     (bg/open-directory-dialog
      (cond-> {}
        (not (str/blank? default-path))
        (assoc :default-path default-path))
      (fn [api-response]
        (when-let [selected-dir (check-error api-response)]
          (dispatch [:app-settings/backup-dir-selected selected-dir]))))
     {})))

(reg-event-db
 :app-settings/backup-dir-selected
 (fn [db [_event-id selected-dir]]
   (let [db (assoc-in db [:app-settings :preference-data :backup :dir] selected-dir)
         errors (validate-required-fields db (get-in db [:app-settings :panel]))]
     (assoc-in db [:app-settings :error-fields] errors))))

(reg-fx
 :bg-update-preference
 (fn [preference-data]
   (bg/update-preference preference-data
                         (fn [api-response]
                           (let [error (:error api-response)]
                             (if (and error (str/starts-with? error "BrowserManifestNeedsUserGrant:"))
                               ;; MAS sandbox: manifest directory needs a one-time NSOpenPanel grant.
                               ;; Payload format: "BrowserManifestNeedsUserGrant:<browser-id>|||<actual-dir>"
                               (let [payload  (subs error (count "BrowserManifestNeedsUserGrant:"))
                                     parts    (str/split payload #"\|\|\|" 2)
                                     browser-id (str/trim (first parts))
                                     actual-dir (some-> (second parts) str/trim not-empty)]
                                 (dispatch [:browser-integration/needs-user-grant browser-id actual-dir]))
                               (when-not (on-error api-response)
                                 (dispatch [:common/load-app-preference])
                                 (dispatch [:app-settings-dialog-close]))))))))

(reg-sub
 :app-settings-dialog-data
 (fn [db _query-vec]
   (get-in db [:app-settings])))

(reg-sub
 :app-settings-modified
 (fn [db _query-vec]
   (let [undo-data (get-in db [:app-settings :undo-data])
         current-data (get-in db [:app-settings :preference-data])]
     (not= undo-data (select-keys current-data (keys undo-data))))))

(comment
  (-> @re-frame.db/app-db keys))
