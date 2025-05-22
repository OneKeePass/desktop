(ns onekeepass.frontend.events.new-database
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [clojure.string :as str]
   [onekeepass.frontend.utils :as utils :refer [str->int]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error on-error]]
   [onekeepass.frontend.background :as bg]))


(def wizard-panels [:basic-info :credentials-info :file-info :security-info])

;; (defn open-file-explorer-on-click []
;;   (cmn-events/open-file-explorer-on-click :new-database-file-name-selected))

(defn save-as-file-explorer-on-click [database-name]
  (bg/save-file-dialog {:default-path (str database-name ".kdbx")}
                       (fn [file-anme]
                         ;; database-file-name is not updated if user cancels the 'Save As' file exploreer 
                         (when (not (str/blank? file-anme))
                           (dispatch [:new-database-field-update  :database-file-name file-anme])))))

(defn open-key-file-explorer-on-click []
  (cmn-events/open-file-explorer-on-click :new-database-key-file-name-selected))

(defn save-as-key-file-explorer-on-click [database-name]
  (bg/save-file-dialog {:default-path (str database-name ".keyx")}
                       (fn [key-file-name]
                         ;; key-file-name is not updated if user cancels the 'Save As' file exploreer 
                         (when (not (str/blank? key-file-name))
                           (bg/generate-key-file key-file-name  (fn [api-response]
                                                                  (when-not (on-error api-response)
                                                                    (dispatch [:new-database-field-update
                                                                               :key-file-name key-file-name]))))))))

(defn next-on-click []
  (dispatch [:new-database-next-panel]))

(defn previous-on-click []
  (dispatch [:new-database-previous-panel]))

(defn new-database-dialog-show []
  (dispatch [:new-database-dialog-show]))

(defn cancel-on-click []
  (dispatch [:new-database-dialog-close]))

(defn done-on-click []
  (dispatch [:new-database-create]))

(defn field-update-factory [kw-field-name]
  (fn [^js/Event e]
    (dispatch [:new-database-field-update kw-field-name (->  e .-target .-value)])))

(defn database-field-update [kw-field-name value]
  (dispatch [:new-database-field-update kw-field-name value]))

(defn new-database-kdf-algorithm-select [kdf-selection]
  (dispatch [:new-database-kdf-algorithm-select kdf-selection]))

(defn dialog-data []
  (subscribe [:new-database-dialog-data]))

(defn- find-next-panel [current]
  (if-let [idx (utils/find-index wizard-panels current)]
    (if (= idx 3)
      current
      (nth wizard-panels (inc idx)))
    current))

(defn- find-previous-panel [current]
  (if-let [idx (utils/find-index wizard-panels current)]
    (if (= idx 0)
      current
      (nth wizard-panels (dec idx)))
    current))

;; Even though this map has more fields to suppoort UI features than 'NewDatabase' struct and used in tauri invoke api
;; only fields fileds matching NewDatabase' struct are deserilaized and other extra fields are ignored
;; Because of this feature, we can see similar UI specific extra fields being used in 'entry-form', 'group-form' etc
;; and we need not remove before calling backend api. However the size json object passsed to tauri api will be large because of 
;; these extra fields
(def blank-new-db  {;;All fields matching 'NewDatabase' struct
                    :database-name nil
                    :database-description nil
                    :password nil
                    :database-file-name nil

                    :cipher-id "Aes256"
                    ;; algorithm and variant need to be set to these values so that 
                    ;; kdf map is serialized to enum KdfAlgorithm::Argon2d
                    :kdf {:algorithm "Argon2d" :iterations 10 :memory 64 :parallelism 2 :variant 0}

                    :key-file-name nil

                    ;; Extra UI related fields
                    ;; These fields will be ignored by serde while doing json deserializing to NewDatabase struct
                    :dialog-show false
                    :show-additional-protection false
                    :password-visible false
                    :password-confirm nil
                    :api-error-text nil
                    :db-file-file-exists false
                    :error-fields {} ;; a map e.g {:id1 "some error text" :id2 "some error text" }
                    :panel :basic-info
                    :call-to-create-status nil

                    ;; Indicates that new database will be created with imported data
                    :imported-data false})

(defn- init-new-database-data [app-db]
  (assoc app-db :new-database blank-new-db))

(reg-event-db
 :new-database-key-file-name-selected
 (fn [db [_event-id key-file-name]]
   (assoc-in db [:new-database :key-file-name] key-file-name)))

(reg-event-fx
 :new-database-dialog-close
 (fn [{:keys [db]} [_event-id]]
   (let [import-data? (get-in db [:new-database :imported-data])]
     {:db (-> db
              (assoc-in [:new-database] {})
              (assoc-in [:new-database :dialog-show] false))
      :fx [(when import-data?
             [:dispatch [:import-csv/clear]])]})))

(defn- validate-security-fields
  [app-db]
  (let [{:keys [iterations memory parallelism]} (-> app-db :new-database :kdf)
        [iterations memory parallelism] (mapv str->int [iterations memory parallelism])
        errors (if (or (nil? iterations) (< iterations 5) (> iterations 100))
                 {:iterations "Valid values should be in the range 5 - 100"} {})
        errors (merge errors
                      (if (or (nil? memory) (< memory 1) (> memory 1000))
                        {:memory "Valid values should be in the range 1 - 1000"} {}))
        errors (merge errors
                      (if (or (nil? parallelism) (< parallelism 1) (> parallelism 100))
                        {:parallelism "Valid values should be in the range 1 - 100"} {}))]

    errors))

(defn- validate-required-fields
  [panel db]
  (cond
    (= panel :basic-info)
    (when (str/blank? (get-in db [:new-database :database-name]))
      {:database-name "A valid dispaly name is required"})

    (= panel :credentials-info)
    (let [p (get-in db [:new-database :password])
          cp (get-in db [:new-database :password-confirm])
          visible  (get-in db [:new-database :password-visible])]
      (when (and (not visible) (not= p cp))
        {:password-confirm "Password and Confirm password are not matching"}))

    ;; (= panel :security-info)
    ;; (validate-security-fields db)

    ;;(= panel :file-info)
    ))

(defn- default-db-file-name
  "Default db file name based on database name"
  [app-db next-panel]
  (let [existing-val (get-in app-db [:new-database :database-file-name])]
    (if (and (= next-panel :file-info) (str/blank? existing-val))
      (cmn-events/new-db-full-file-name app-db (get-in app-db [:new-database :database-name]))
      nil)))

(reg-event-fx
 :new-database-next-panel
 (fn [{:keys [db]} [_event-id]]
   (let [current (get-in db [:new-database :panel])
         errors  (validate-required-fields current db)
         next-panel (find-next-panel current)]
     (if (not (nil? errors))
       {:db (-> db (assoc-in [:new-database :error-fields] errors))}
       {:db (-> db
                (assoc-in [:new-database :panel] next-panel)
                (assoc-in [:new-database :error-fields] {}))
        :fx (if-let [name (default-db-file-name db next-panel)]
              [[:dispatch [:new-database-field-update :database-file-name name]]]
              [])}))))

(reg-event-db
 :new-database-previous-panel
 (fn [db [_event-id]]
   (let [current (get-in db [:new-database :panel])]
     (assoc-in db [:new-database :panel] (find-previous-panel current)))))

(defn- check-file-exists [file-name]
  (bg/is-file-exists file-name (fn [api-response]
                                 (if-let [result (check-error api-response)]
                                   (dispatch [:new-database-field-update :db-file-file-exists result])
                                   (dispatch [:new-database-field-update :db-file-file-exists false])))))

;; A common field update event except for kdf selection
(reg-event-db
 :new-database-field-update
 ;; kw-field-name is single kw or a vec of kws
 (fn [db [_event-id kw-field-name value]]
   (when (= kw-field-name :database-file-name)
     (check-file-exists value))
   (-> db
       (assoc-in (into [:new-database] (if (vector? kw-field-name)
                                         kw-field-name
                                         [kw-field-name])) value)
       ;; Hide any previous api-error-text
       (assoc-in [:new-database :api-error-text] nil))))

(reg-event-db
 :new-database-kdf-algorithm-select
 (fn [db [_event-id kdf-selection]]
   ;; Fields algorithm and variant need to be set to these values so that 
   ;; kdf map is serialized to enum KdfAlgorithm::Argon2d or  KdfAlgorithm::Argon2id
   ;; Also see in db-settings 
   (-> db (assoc-in [:new-database :kdf :algorithm] kdf-selection)
       (assoc-in [:new-database :kdf :variant] (if (= kdf-selection "Argon2d") 0 2)))))


(defn- on-database-creation-completed [api-response]
  (when-let [kdbx-loaded (check-error api-response (fn [error]
                                                     (dispatch [:new-database-create-kdbx-error error])))]
    (dispatch [:new-database-created kdbx-loaded])))

(defn- convert-kdf-value [new-db]
  (-> new-db
      (update-in [:kdf :iterations] str->int)
      (update-in [:kdf :parallelism] str->int)
      (update-in [:kdf :memory] str->int)
      ;; Need to make sure memory value is in MB 
      (update-in [:kdf :memory] * 1048576)))

;; Called when 'Done' is clicked
(reg-event-fx
 :new-database-create
 (fn [{:keys [db]} [_event-id]]
   ;;(println "event new-database-create called") 
   (let [errors (validate-security-fields db)
         imported-data? (get-in db [:new-database :imported-data])]
     (if (boolean (seq errors))
       {:db (assoc-in db [:new-database :error-fields] errors)}
       {:db (-> db (assoc-in [:new-database :call-to-create-status] :in-progress)
                (assoc-in [:new-database :api-error-text] nil))
        :fx [(if-not imported-data?
               [:bg-create-kdbx (:new-database db)]
               [:dispatch [:import-file/new-database (convert-kdf-value (:new-database db)) on-database-creation-completed]])]}))))
(reg-fx
 :bg-create-kdbx
 (fn [new-db]
   (bg/create-kdbx (convert-kdf-value new-db)  on-database-creation-completed)))

(reg-event-fx
 :new-database-created
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db (assoc-in [:new-database :api-error-text] nil)
            (assoc-in [:new-database :call-to-create-status] :completed))
    :fx [[:dispatch [:new-database-dialog-close]]
         [:dispatch [:common/kdbx-database-opened kdbx-loaded]]
         #_[:dispatch [:import-csv/clear]]]}))

(reg-event-db
 :new-database-create-kdbx-error
 (fn [db [_event-id error]]
   (let [db (-> db (assoc-in [:new-database :call-to-create-status] :completed)
                (assoc-in  [:new-database :api-error-text] error))]
     (if (str/starts-with? error "The key file")
       (-> db (assoc-in [:new-database :panel] :credentials-info))
       db))))

(reg-event-db
 :new-database-dialog-show
 (fn [db [_event-id]]
   (-> db init-new-database-data (assoc-in  [:new-database :dialog-show] true))))

(reg-event-db
 :new-database/dialog-show
 (fn [db [_event-id imported-data?]]
   ;; (println "new-database/dialog-show is called ")
   (-> db init-new-database-data (assoc-in [:new-database :dialog-show] true)
       (assoc-in [:new-database :imported-data] imported-data?))))

(reg-sub
 :new-database-dialog-data
 (fn [db _query-vec]
   (get-in db [:new-database])))

(comment
  @re-frame.db/app-db)
