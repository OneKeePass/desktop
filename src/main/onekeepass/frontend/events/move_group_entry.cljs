(ns onekeepass.frontend.events.move-group-entry
  "All group and entry related move events. When a group/entry is deleted it is moved to
  the recycle bin group. When a group/entry is putback, again it is move event
   "
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx
                          reg-sub dispatch subscribe]]
   [onekeepass.frontend.events.common  :refer [active-db-key
                                               get-in-key-db
                                               assoc-in-key-db
                                               on-error]]
   [onekeepass.frontend.background :as bg]))


(defn move-group-entry-dialog-show [kind-kw show?] ;; show or hide
  (dispatch [:move-group-entry-dialog-show kind-kw show?]))

(defn move-group-entry-ok [kind-kw id]
  ;;(println "entry-put-back-ok called " kind-kw id)
  (dispatch [:move-group-entry-start kind-kw id]))

(defn move-group-entry-group-selected-factory
  "A factory fn that returns a fn with two args"
  [kind-kw] 
  ;; Called on selecting a group in auto complete box
  ;; The option selected in autocomplete component is passed as 'group-info' - a javacript object 
  (fn [_e group-info] 
    (dispatch [:move-group-entry-group-selected kind-kw (js->clj group-info :keywordize-keys true)])))

(defn move-group-entry-dialog-data [kind-kw]
  (subscribe [:move-group-entry kind-kw]))

;; kind-kw :entry or :group
(reg-event-fx
 :move-group-entry-start
 (fn [{:keys [db]} [_query-id kind-kw id]]
   (let [g (get-in-key-db db [:move-group-entry kind-kw :group-selection-info])] 
     (if (empty? g)
       {:db (-> db (assoc-in-key-db [:move-group-entry kind-kw :field-error] true))}
       {:db (-> db (assoc-in-key-db [:move-group-entry kind-kw :field-error] false)
                (assoc-in-key-db [:move-group-entry kind-kw :status] :in-progress))
        :fx [[:bg-move [kind-kw (active-db-key db) id (:uuid g)]]]}))))

(defn- on-move-complete [kind-kw api-response]
  (when (not (on-error api-response #(dispatch [:move-group-entry-error kind-kw %])))
    (dispatch [:move-group-entry-completed kind-kw])))

(reg-fx
 :bg-move
 (fn [[kind-kw db-key id parent-group-uuid]]
   (if (= kind-kw :group)
     (bg/move-group db-key id parent-group-uuid #(on-move-complete kind-kw %))
     (bg/move-entry db-key id parent-group-uuid #(on-move-complete kind-kw %)))))

(reg-event-db
 :move-group-entry-dialog-show
 (fn [db [_query-id kind-kw show?]]
   (if show?
     (-> db (assoc-in-key-db [:move-group-entry kind-kw :dialog-show] true)
         (assoc-in-key-db [:move-group-entry kind-kw :api-error-text] nil)
         (assoc-in-key-db [:move-group-entry kind-kw :group-selection-info] nil)
         (assoc-in-key-db [:move-group-entry kind-kw :field-error] false))
     (-> db (assoc-in-key-db [:move-group-entry kind-kw :dialog-show] false)))))

(reg-event-db
 :move-group-entry-error
 (fn [db [_query-id kind-kw error]]
   (-> db (assoc-in-key-db [:move-group-entry kind-kw :api-error-text] error)
       (assoc-in-key-db [:move-group-entry kind-kw :status] :completed)
       )))

(reg-event-fx
 :move-group-entry-completed
 (fn [{:keys [db]} [_event-id kind-kw]]
   {:db (-> db
            (assoc-in-key-db [:move-group-entry kind-kw :dialog-show] false)
            (assoc-in-key-db [:move-group-entry kind-kw :status] :completed)
            (assoc-in-key-db [:move-group-entry kind-kw :api-error-text] nil)
            (assoc-in-key-db [:move-group-entry kind-kw :field-error] false))

    :fx [[:dispatch [:common/message-snackbar-open 
                     (str (if (= kind-kw :group) "Group" "Entry") " is moved")]]
         [:dispatch [:common/refresh-forms]]] }))


(reg-event-db
 :move-group-entry-group-selected
 (fn [db [_event-id kind-kw group-info]]
   ;;(println "Calling with group in event " group-info " kind-kw " kind-kw)
   (-> db (assoc-in-key-db [:move-group-entry kind-kw :group-selection-info] group-info))))

(reg-sub
 :move-group-entry
 (fn [db [_event-id kind-kw]]
   (get-in-key-db db [:move-group-entry kind-kw])))


;;;;;;;;;;;;;; Permanent delete ;;;;;;;;;;;;;;

(defn delete-permanent-group-entry-dialog-data [kind-kw]
  (subscribe [:delete-permanent-group-entry kind-kw]))

(defn delete-permanent-group-entry-dialog-show [kind-kw show?] ;; show or hide
  (dispatch [:delete-permanent-group-entry-dialog-show kind-kw show?]))

(defn delete-permanent-group-entry-ok [kind-kw id]
  (dispatch [:delete-permanent-group-entry-start kind-kw id]))

(reg-event-db
 :delete-permanent-group-entry-dialog-show
 (fn [db [_query-id kind-kw show?]]
   (if show?
     (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :dialog-show] true)
         (assoc-in-key-db [:delete-permanent-group-entry kind-kw :api-error-text] nil))
     (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :dialog-show] false)))))

(reg-event-fx
 :delete-permanent-group-entry-start
 (fn [{:keys [db]} [_query-id kind-kw id]]
   {:db (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :status] :in-progress))
    :fx [[:bg-permanent-delete [(active-db-key db) kind-kw id]]]}))


(defn- on-delete-permanent-complete [kind-kw api-response]
  (when (not (on-error api-response #(dispatch [:delete-permanent-group-entry-error kind-kw %])))
    (dispatch [:delete-permanent-group-entry-completed kind-kw])))

(reg-fx
 :bg-permanent-delete
 (fn [[db-key kind-kw id]]
   (if (= kind-kw :group) 
     (bg/remove-group-permanently db-key id #(on-delete-permanent-complete kind-kw %))
     (bg/remove-entry-permanently db-key id #(on-delete-permanent-complete kind-kw %)))))


(reg-event-fx
 :delete-permanent-group-entry-completed
 (fn [{:keys [db]} [_event-id kind-kw]]
   {:db (-> db
            (assoc-in-key-db [:delete-permanent-group-entry kind-kw :dialog-show] false)
            (assoc-in-key-db [:delete-permanent-group-entry kind-kw :status] :completed)
            (assoc-in-key-db [:delete-permanent-group-entry kind-kw :api-error-text] nil))
    :fx [[:dispatch [:common/message-snackbar-open
                     (str (if (= kind-kw :group) "Group" "Entry") " is permanently deleted")]]
         [:dispatch [:common/refresh-forms]]]}))

(reg-event-db
 :delete-permanent-group-entry-error
 (fn [db [_query-id kind-kw error]]
   (-> db (assoc-in-key-db [:delete-permanent-group-entry kind-kw :api-error-text] error)
       (assoc-in-key-db [:delete-permanent-group-entry kind-kw :status] :completed))))

(reg-sub
 :delete-permanent-group-entry
 (fn [db [_event-id kind-kw]]
   (get-in-key-db db [:delete-permanent-group-entry kind-kw])))