(ns onekeepass.frontend.events.password-generator
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error]]
   [onekeepass.frontend.background :as bg]))


(defn generator-dialog-data-update [kw value]
  (dispatch [:generator-dialog-data-update kw value]))

(defn password-options-update [kw value]
  (dispatch [:password-options-update kw value]))

(defn generator-dialog-data []
  (subscribe [:generator-dialog-data]))

;;;;
(def generator-dialog-init-data {:dialog-show false
                                 :password-visible false
                                 :text-copied false
                                 :password-options {;; All fields from struct PasswordGenerationOptions
                                                    :length 8
                                                    :numbers true
                                                    :lowercase-letters true
                                                    :uppercase-letters true
                                                    :symbols true
                                                    :spaces false
                                                    :exclude-similar-characters true
                                                    :strict true}
                                 ;;;
                                 :password-result {:password nil
                                                   :analyzed-password nil
                                                   :is-common false
                                                   :score {:name nil
                                                           :raw-value nil
                                                           :score-text nil}}})

(defn- init-dialog-data [app-db]
  (assoc-in app-db [:generator :dialog-data] generator-dialog-init-data))

;; Updates all top level fields of dialog-data
(defn- to-generator-dialog-data [db & {:as kws}]
  (let [data (get-in db [:generator :dialog-data])
        data (merge data kws)]
    (assoc-in db [:generator :dialog-data] data)))

;; Updates all :data fields
(defn- to-password-options-data [db & {:as kws}]
  (let [data (get-in db [:generator :dialog-data :password-options])
        data (merge data kws)]
    (assoc-in db [:generator :dialog-data :password-options] data)))

(reg-event-db
 :generator-dialog-data-update
 (fn [db [_event-id field-name-kw value]]
   (-> db
       (to-generator-dialog-data field-name-kw value))))

(reg-event-fx
 :password-options-update
 (fn [{:keys [db]} [_event-id field-name-kw value]]
   (let [db (-> db
                (to-password-options-data field-name-kw value)
                (to-generator-dialog-data :text-copied false))
         po (get-in db [:generator :dialog-data :password-options])]
     {:db db
      :fx [[:bg-analyzed-password po]]})))

(reg-event-fx
 :password-generator/start
 (fn [{:keys [db]} [_event-id]]
   (let [db (init-dialog-data db)
         po (get-in db [:generator :dialog-data :password-options])]
     {:db db
      :fx [[:bg-analyzed-password po]]})))

(reg-fx
 :bg-analyzed-password
 (fn [password-options]
   (bg/analyzed-password password-options
                         (fn [api-response]
                           (when-let [result (check-error api-response)]
                             (dispatch [:password-generation-complete result]))))))

(reg-event-db
 :password-generation-complete
 (fn [db [_event-id password-result]]
   (-> db
       (assoc-in  [:generator :dialog-data :password-result] password-result)
       (assoc-in  [:generator :dialog-data :dialog-show] true))))


(reg-sub
 :generator-dialog-data
 (fn [db [_query-id]]
   (get-in db [:generator :dialog-data])))

(reg-sub
 :generator-password-result
 (fn [db [_query-id]]
   (get-in db [:generator :dialog-data :password-result])))


(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db :generator)
  (get-in @re-frame.db/app-db [:generator :dialog-data :password-options])
  (def a (to-password-options-data @re-frame.db/app-db :length 11))
  (-> @re-frame.db/app-db :generator :dialog-data :password-result :score))

