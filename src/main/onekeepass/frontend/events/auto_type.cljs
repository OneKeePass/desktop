(ns onekeepass.frontend.events.auto-type
  (:require
   [re-frame.core :refer [reg-event-db
                          reg-event-fx
                          reg-fx reg-sub
                          dispatch
                          subscribe]]
   [clojure.string :as str]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             active-db-key]]
   [onekeepass.frontend.background :as bg]))

(def DEFAULT_SEQUENCE "{USERNAME}{TAB}{PASSWORD}{ENTER}")

(defn is-custom-sequence [sequence]
  (not= sequence DEFAULT_SEQUENCE)
  #_(not= (str/upper-case sequence) DEFAULT_SEQUENCE))

(defn get-sequence
  "Typically default sequence is nil indicating we need to use DEFAULT_SEQUENCE. 
   In that case, we use the standard one"
  [auto-type-m]
  (let [s (:default-sequence auto-type-m)]
    (if-not (nil? s)
      s
      DEFAULT_SEQUENCE)))

(defn perform-dialog-show-close []
  (dispatch [:perform-dialog-show-close]))

(defn send-auto-sequence []
  (dispatch [:send-auto-sequence]))

(defn auto-type-perform-dialog-data []
  (subscribe [:auto-type/perform-dialog]))

(reg-event-fx
 :perform-dialog-show-close
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in-key-db [:auto-type-perform-dialog :dialog-show] false)
            (assoc-in-key-db [:auto-type-perform-dialog :entry-uuid] nil)
            (assoc-in-key-db [:auto-type-perform-dialog :auto-type] {})
            (assoc-in-key-db [:auto-type-perform-dialog :window-info] {})
            (assoc-in-key-db [:auto-type-perform-dialog :api-error-text] nil)
            (assoc-in-key-db [:auto-type-perform-dialog :error-fields] nil))}))


#_(reg-event-fx
   :auto-type/perform-dialog-show
   (fn [{:keys [db]} [_event-id show? auto-type-m]]
     {:db (if show?
            (-> db
                (assoc-in-key-db [:auto-type-perform-dialog :dialog-show] true)
                (assoc-in-key-db [:auto-type-perform-dialog :auto-type] auto-type-m)
                (assoc-in-key-db [:auto-type-perform-dialog :api-error-text] nil)
                (assoc-in-key-db [:auto-type-perform-dialog :error-fields] nil))
            (-> db (assoc-in-key-db [:auto-type-perform-dialog :dialog-show] false)
                (assoc-in-key-db [:auto-type-perform-dialog :auto-type] {})
                (assoc-in-key-db [:auto-type-perform-dialog :api-error-text] nil)
                (assoc-in-key-db [:auto-type-perform-dialog :error-fields] nil)))}))

(reg-event-fx
 :perform-dialog-init
 (fn [{:keys [db]} [_event-id entry-uuid window-info auto-type-m]]
   {:db (-> db
            (assoc-in-key-db [:auto-type-perform-dialog :dialog-show] true)
            (assoc-in-key-db [:auto-type-perform-dialog :entry-uuid] entry-uuid)
            (assoc-in-key-db [:auto-type-perform-dialog :window-info] window-info)
            (assoc-in-key-db [:auto-type-perform-dialog :auto-type] auto-type-m)
            (assoc-in-key-db [:auto-type-perform-dialog :api-error-text] nil)
            (assoc-in-key-db [:auto-type-perform-dialog :error-fields] nil))}))

(reg-event-fx
 :send-auto-sequence
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [entry-uuid window-info auto-type]} (get-in-key-db db [:auto-type-perform-dialog])]
     {:fx [[:bg-send-sequence-to-winow [(active-db-key db) entry-uuid window-info (get-sequence auto-type)]]]})))

(reg-fx
 :bg-send-sequence-to-winow
 (fn [[db-key entry-uuid window-info sequence ]]
   (bg/send-sequence-to-winow db-key
                              entry-uuid
                              window-info
                              sequence
                              (fn [api-response]
                                (when-not (on-error api-response)
                                  (dispatch [:send-sequence-to-winow-completed]))))))

(reg-event-fx
 :send-sequence-to-winow-completed
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:perform-dialog-show-close]]
         [:dispatch [:common/message-snackbar-open "Auto typing is completed"]]]}))

(reg-fx
 :auto-type/bg-active-window-to-auto-type
 (fn [[entry-uuid auto-type-m]]
   (bg/active-window-to-auto-type (fn [api-response]
                                    (when-let [window-info (check-error api-response)]
                                      (dispatch [:perform-dialog-init entry-uuid window-info auto-type-m]))))))

(reg-sub
 :auto-type/perform-dialog
 (fn [db _query-vec]
   (get-in-key-db db [:auto-type-perform-dialog])))


;;;;;;;;;;;;;;;   Auto type editing ;;;;;;;;;;;;;;;;;

(defn auto-type-edit-dialog-close []
  (dispatch [:auto-type-edit-dialog-close]))

(defn auto-type-edit-dialog-ok []
  (dispatch [:auto-type-edit-dialog-ok]))

(defn auto-type-edit-dialog-update [field-name-kw value]
  (dispatch [:auto-type-edit-dialog-update field-name-kw value]))

(defn auto-type-edit-dialog-data []
  (subscribe [:auto-type-edit-dialog-data]))

(defn auto-type-modified []
  (subscribe [:auto-type-modified]))

(defn- update-auto-type-data [db & {:as kws}]
  ;;(println "kws are " kws)
  (let [data (get-in-key-db db [:auto-type-edit-dialog :auto-type])
        data (merge data kws)]
    (assoc-in-key-db db [:auto-type-edit-dialog :auto-type] data)))

;; Hides the dialog and resets all field values
(reg-event-fx
 :auto-type-edit-dialog-close
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db [:auto-type-edit-dialog :dialog-show] false)
            (assoc-in-key-db [:auto-type-edit-dialog :entry-uuid] nil)
            (assoc-in-key-db [:auto-type-edit-dialog :auto-type] {})
            (assoc-in-key-db [:auto-type-edit-dialog :auto-type-undo] {})
            (assoc-in-key-db [:auto-type-edit-dialog :entry-form-fields] {})
            (assoc-in-key-db [:auto-type-edit-dialog :api-error-text] nil))}))

;; 
(reg-event-fx
 :auto-type-edit-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [auto-type entry-form-fields]} (get-in-key-db db [:auto-type-edit-dialog])
         sequence-val (:default-sequence auto-type)]
     (if-not (empty? (str/trim (str sequence-val)))
       {:fx [[:bg-parse-auto-type-sequence [(:default-sequence auto-type) entry-form-fields]]]}
       {:fx [[:dispatch [:auto-type-edit-complete]]]}
       #_{:db (-> db (assoc-in-key-db [:auto-type-edit-dialog :api-error-text] "Valid sequence is required"))}))))

(reg-fx
 :bg-parse-auto-type-sequence
 (fn [[sequence entry-form-fields]]
   ;; Check sequence parsing error. If no error call entry form events to save 
   (bg/parse-auto-type-sequence sequence entry-form-fields
                                (fn [api-response]
                                  (when-not (on-error api-response
                                                      (fn [e]
                                                        (dispatch [:parse-sequence-error e])))
                                    (dispatch [:auto-type-edit-complete]))))))

(reg-event-fx
 :parse-sequence-error
 (fn [{:keys [db]} [_event-id error]]
   {:db (-> db
            (assoc-in-key-db [:auto-type-edit-dialog :api-error-text] error))}))

;; If the sequence is DEFAULT_SEQUENCE, do not call save ?
(reg-event-fx
 :auto-type-edit-complete
 (fn [{:keys [db]} [_event-id]]
   (let [auto-type (get-in-key-db db [:auto-type-edit-dialog :auto-type])
         sequence-val (:default-sequence auto-type)]
     (if (is-custom-sequence sequence-val)
       {:fx [;; dispatch to  entry form update first
             [:dispatch [:entry-form/auto-type-updated auto-type]]
             ;; Close the dialog
             [:dispatch [:auto-type-edit-dialog-close]]]}
       {:fx [[:dispatch [:auto-type-edit-dialog-close]]]}))))

;; Called to start the auto type add/modify dialog
(reg-event-fx
 :auto-type/edit-init
 (fn [{:keys [db]} [_event-id entry-uuid auto-type-m entry-form-fields]]
   (let [sequence1 (get-sequence auto-type-m)
         modified-auto-type (merge auto-type-m {:default-sequence sequence1})]
     {:db (-> db
              (assoc-in-key-db [:auto-type-edit-dialog :dialog-show] true)
              (assoc-in-key-db [:auto-type-edit-dialog :entry-uuid] entry-uuid)
              (assoc-in-key-db [:auto-type-edit-dialog :auto-type]  modified-auto-type)
              ;; Keep a copy of existing value of DefaultSequence to monitor changes
              (assoc-in-key-db [:auto-type-edit-dialog :auto-type-undo] modified-auto-type)
              (assoc-in-key-db [:auto-type-edit-dialog :entry-form-fields] entry-form-fields)
              (assoc-in-key-db [:auto-type-edit-dialog :api-error-text] nil))})))

;; Called to update to a field in the dialog
(reg-event-db
 :auto-type-edit-dialog-update
 (fn [db [_event-id field-name-kw value]]
   (-> db
       (update-auto-type-data field-name-kw value))))

(reg-sub
 :auto-type-edit-dialog-data
 (fn [db _query-vec]
   (get-in-key-db db [:auto-type-edit-dialog])))

(reg-sub
 :auto-type-modified
 (fn [db _query-vec]
   (let [undo-data (get-in-key-db db [:auto-type-edit-dialog :auto-type-undo])
         data (get-in-key-db db [:auto-type-edit-dialog :auto-type])]
     (if (and (seq undo-data) (not= undo-data data))
       true
       false))))


(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> (get @re-frame.db/app-db db-key) :auto-type-edit-dialog))


#_(reg-sub
   :auto-type-modified
   (fn [db _query-vec]
     (let [sequence-undo-val (get-in-key-db db [:auto-type-edit-dialog  :sequence-undo])
           sequence-val  (get-in-key-db db [:auto-type-edit-dialog :auto-type :default-sequence])]
       (if (or (and (nil? sequence-val) (= sequence-undo-val DEFAULT_SEQUENCE))
               (= sequence-val sequence-undo-val))
         false
         true))))

#_(reg-event-fx
   :auto-type/edit-init
   (fn [{:keys [db]} [_event-id entry-uuid auto-type-m entry-form-fields]]
     {:db (-> db
              (assoc-in-key-db [:auto-type-edit-dialog :dialog-show] true)
              (assoc-in-key-db [:auto-type-edit-dialog :entry-uuid] entry-uuid)
              (assoc-in-key-db [:auto-type-edit-dialog :auto-type]  auto-type-m)
            ;; Keep a copy of existing value of DefaultSequence to monitor changes
              (assoc-in-key-db [:auto-type-edit-dialog :sequence-undo] (get-sequence auto-type-m))
              (assoc-in-key-db [:auto-type-edit-dialog :entry-form-fields] entry-form-fields)
              (assoc-in-key-db [:auto-type-edit-dialog :api-error-text] nil))}))


#_(reg-sub
   :auto-type-modified
   (fn [db _query-vec]
     (let [sequence-undo-val (get-in-key-db db [:auto-type-edit-dialog  :sequence-undo])
           sequence-val  (get-in-key-db db [:auto-type-edit-dialog :auto-type :default-sequence])]
       (if (or (and (nil? sequence-val) (= sequence-undo-val DEFAULT_SEQUENCE))
               (= sequence-val sequence-undo-val))
         false
         true))))

