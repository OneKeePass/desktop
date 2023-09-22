(ns onekeepass.frontend.events.auto-type
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]] 
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             active-db-key]]
   [onekeepass.frontend.background :as bg]))



(defn cancel-on-click []
  (dispatch [:auto-type/perform-dialog-show false {}]))

(defn auto-type-perform-dialog-data []
  (subscribe [:auto-type/perform-dialog]))

(reg-event-fx
 :auto-type/perform-dialog-show
 (fn [{:keys [db]} [_event-id show? auto-type-m]] 
   {:db (if show?
          (-> db
              (assoc-in-key-db [:auto-type-perform-dialog :dialog-show] true)
              (assoc-in-key-db [:auto-type-perform-dialog :data] auto-type-m)
              (assoc-in-key-db [:auto-type-perform-dialog :api-error-text] nil)
              (assoc-in-key-db [:auto-type-perform-dialog :error-fields] nil))
          (-> db (assoc-in-key-db [:auto-type-perform-dialog :dialog-show] false)
              (assoc-in-key-db [:auto-type-perform-dialog :data] {})
              (assoc-in-key-db [:auto-type-perform-dialog :api-error-text] nil)
              (assoc-in-key-db [:auto-type-perform-dialog :error-fields] nil)))}))

#_(reg-fx
 :auto-type/bg-active-window 
 (fn [[]]
   (bg/test-call (fn [api-response] 
                   
                   ))
   )
 )

#_(reg-fx
 :bg-insert-or-update-custom-entry-type
 (fn [[db-key entry-type-form-data]]
   (bg/insert-or-update-custom-entry-type
    db-key
    entry-type-form-data
    (fn [api-response]
      #_(println "bg/insert-or-update-custom-entry-type api-response " api-response)
      (when-let [entry-type-uuid (check-error api-response)]
        (dispatch [:create-custom-entry-type-completed entry-type-uuid]))))))

(reg-sub
 :auto-type/perform-dialog
 (fn [db _query-vec]
   (get-in-key-db db [:auto-type-perform-dialog])))