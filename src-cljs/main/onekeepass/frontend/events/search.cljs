(ns onekeepass.frontend.events.search
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             active-db-key]]
   [onekeepass.frontend.background :as bg]))

(defn show-selected-entry-item [entry-uuid]
  (dispatch [:entry-list/entry-selected-in-search-result entry-uuid])
  (dispatch [:term-showing-in-form])
  (dispatch [:search-dialog-hide]))

(defn cancel-on-click []
  (dispatch [:search-dialog-close]))

(defn search-term-update [^js/Event e]
  (dispatch [:search-term-update (->  e .-target .-value)]))

(defn search-term-clear []
  (dispatch [:search-term-clear]))

(defn search-dialog-show []
  (dispatch [:search/dialog-show]))

(defn search-selected-entry-id-update [uuid]
  (dispatch [:search-selected-entry-id-update uuid]))

(defn search-result-entry-items
  "Returns an atom that has a list of all search term matched entry items "
  []
  (subscribe [:search-result-entry-items]))

(defn selected-entry-id
  []
  (subscribe [:search-selected-entry-id]))

(defn dialog-data []
  (subscribe [:search-dialog-data]))

(defn init-search-data [app-db]
  (-> app-db (assoc-in-key-db  [:search :not-matched] false)
      (assoc-in-key-db  [:search :result] nil)
      (assoc-in-key-db  [:search :error-text] nil)
      (assoc-in-key-db  [:search :term] nil)
      (assoc-in-key-db  [:search :term-showing-in-form] nil)
      (assoc-in-key-db  [:search :selected-entry-id] nil)
      (assoc-in-key-db [:search :dialog-show] false)))

(reg-event-fx
 :search/dialog-show
 (fn [{:keys [db]} [_event-id]]
   (let [previous-term (get-in-key-db db [:search :term-showing-in-form])]
     (if (nil? previous-term)
       {:db (-> db init-search-data
                (assoc-in-key-db [:search :dialog-show] true))}
       {:db (assoc-in-key-db db [:search :dialog-show] true)
        :fx [[:bg-start-term-search [(active-db-key db) previous-term]]]}))))

(reg-event-db
 :search-dialog-close
 (fn [db [_event-id]]
   (-> db init-search-data)))

(reg-event-db
 :term-showing-in-form
 (fn [db [_event-id]]
   ;; Copy the term to term-showing-in-form so that we can do search again when search-dialog shown 
   ;; after user sees the entry form data and comes back to see next match for the previously entered term
   (assoc-in-key-db db [:search :term-showing-in-form]
                    (get-in-key-db db [:search :term]))))

;; Just hides the search dialog retaining other values
(reg-event-db
 :search-dialog-hide
 (fn [db [_event-id]]
   (assoc-in-key-db db [:search :dialog-show] false)))

(reg-event-fx
 :search-term-update
 (fn [{:keys [db]} [_event-id term]]
   {:db (assoc-in-key-db db [:search :term] term)
    :fx [[:bg-start-term-search [(active-db-key db) term]]]}))


(defn- start-term-search [db-key term]
  (bg/search-term db-key term
                  (fn [api-response]
                    (when-let [result (check-error api-response #(dispatch [:search-error-text %]))]
                      (dispatch [:search-term-completed result])))))

;; Backend API call 
(reg-fx
 :bg-start-term-search
 (fn [[db-key term]] ;; fn in 'reg-fx' accepts single argument
   (start-term-search db-key term)))

;;
(reg-event-db
 :search-term-clear
 (fn [db [_event-id]]
   (-> db (assoc-in-key-db [:search :term] nil)
       (assoc-in-key-db  [:search :error-text] nil)
       (assoc-in-key-db [:search :selected-entry-id] nil)
       (assoc-in-key-db  [:search :result] nil))))

(reg-event-db
 :search-error-text
 (fn [db [_event-id error-text]] 
   (-> db (assoc-in-key-db  [:search :error-text] error-text)
       (assoc-in-key-db [:search :selected-entry-id] nil)
       (assoc-in-key-db  [:search :result] nil))))

(reg-event-db
 :search-selected-entry-id-update
 (fn [db [_event-id uuid]]
   (assoc-in-key-db db [:search :selected-entry-id] uuid)))

(reg-event-fx
 :search-term-completed
 (fn [{:keys [db]} [_event-id result]]
   ;; result is a map {:entry-items [map of entry summary]} as defined in struct EntrySearchResult
   (let [not-matched (empty? (:entry-items result))]
     {:db (-> db (assoc-in-key-db  [:search :result] result)
              (assoc-in-key-db [:search :selected-entry-id] nil)
              (assoc-in-key-db  [:search :error-text] nil)
              (assoc-in-key-db  [:search :not-matched] not-matched))})))

(reg-sub
 :search-result-entry-items
 (fn [db _query-vec]
   (get-in-key-db db [:search :result :entry-items])))

(reg-sub
 :search-selected-entry-id
 (fn [db _query-vec]
   (get-in-key-db db [:search :selected-entry-id])))

(reg-sub
 :search-dialog-data
 (fn [db _query-vec]
   (get-in-key-db db [:search])))
