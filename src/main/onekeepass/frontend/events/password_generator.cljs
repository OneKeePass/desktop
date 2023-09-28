(ns onekeepass.frontend.events.password-generator
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [clojure.string :as str]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error]]
   [onekeepass.frontend.background :as bg]))


(defn generator-dialog-data-update [kw value]
  (dispatch [:generator-dialog-data-update kw value]))

(defn password-options-update [kw value]
  (dispatch [:password-options-update kw value]))

(defn generator-password-copied []
  (dispatch [:generator-password-copied]))

(defn generator-dialog-data []
  (subscribe [:generator-dialog-data]))

;;;;
(def generator-dialog-init-data {:dialog-show false
                                 :password-visible false
                                 :text-copied false
                                 :callback-on-copy-fn nil
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
         {:keys [length] :as po} (get-in db [:generator :dialog-data :password-options])] 
     (if (empty? (str/trim (str length)))
       {:db db
        :fx [[:dispatch [:common/message-snackbar-error-open "A valid length number is required and it should be 8 or above"]]]}
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

