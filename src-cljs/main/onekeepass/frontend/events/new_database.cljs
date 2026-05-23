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

(declare create-kdbx-fx)

(defn save-as-file-explorer-on-click
  ([database-name default-path]
   (save-as-file-explorer-on-click database-name default-path nil))
  ([database-name default-path after-selected-fx]
  (bg/save-file-dialog {:default-path (if (str/blank? default-path)
                                        (str database-name ".kdbx")
                                        default-path)}
                       (fn [file-anme]
                         ;; database-file-name is not updated if user cancels the 'Save As' file exploreer 
                         (when (not (str/blank? file-anme))
                           (dispatch [:new-database-file-selected file-anme after-selected-fx]))))))

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
  (dispatch [:new-database/dialog-close]))

(defn done-on-click []
  (dispatch [:new-database-create]))

(defn field-update-factory [kw-field-name]
  (fn [^js/Event e]
    (dispatch [:new-database-field-update kw-field-name (->  e .-target .-value)])))

(defn database-field-update [kw-field-name value]
  (dispatch [:new-database-field-update kw-field-name value]))

(defn save-remote-toggle [checked?]
  (dispatch [:new-database-field-update :save-remote? (boolean checked?)]))

(defn new-database-kdf-algorithm-select [kdf-selection]
  (dispatch [:new-database-kdf-algorithm-select kdf-selection]))

(defn dialog-data []
  (subscribe [:new-database-dialog-data]))

(defn- skip-panel?
  [panel db]
  (and (= panel :file-info)
       (get-in db [:new-database :save-remote?])))

(defn- find-next-panel [current db]
  (loop [idx (utils/find-index wizard-panels current)]
    (cond
      (nil? idx) current
      (= idx 3) (nth wizard-panels idx)
      :else
      (let [nxt (nth wizard-panels (inc idx))]
        (if (skip-panel? nxt db)
          (recur (inc idx))
          nxt)))))

(defn- find-previous-panel [current db]
  (loop [idx (utils/find-index wizard-panels current)]
    (cond
      (nil? idx) current
      (= idx 0) (nth wizard-panels 0)
      :else
      (let [prv (nth wizard-panels (dec idx))]
        (if (skip-panel? prv db)
          (recur (dec idx))
          prv)))))

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
                    :remote? false
                    ;; User intent set on the first wizard panel: skip the local 'Save As' panel
                    ;; and route 'Done' into the remote-storage create dialog.
                    :save-remote? false
                    :show-additional-protection false
                    :password-visible false
                    :password-confirm nil
                    :api-error-text nil
                    :db-file-file-exists false
                    :database-file-user-selected false
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
 :new-database/dialog-close
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
         next-panel (find-next-panel current db)]
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
     (assoc-in db [:new-database :panel] (find-previous-panel current db)))))

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
       (cond-> (= kw-field-name :database-file-name)
         (assoc-in [:new-database :database-file-user-selected] false))
       ;; Hide any previous api-error-text
       (assoc-in [:new-database :api-error-text] nil))))

(reg-event-fx
 :new-database-file-selected
 (fn [{:keys [db]} [_event-id file-name after-selected-fx]]
   (check-file-exists file-name)
   (let [db (-> db
                (assoc-in [:new-database :database-file-name] file-name)
                (assoc-in [:new-database :database-file-user-selected] true)
                (assoc-in [:new-database :api-error-text] nil))]
     (if after-selected-fx
       (after-selected-fx db)
       {:db db}))))

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

(defn- create-kdbx-fx [db]
  (let [remote? (get-in db [:new-database :remote?])
        new-db (:new-database db)
        prepared (convert-kdf-value new-db)]
    {:db (-> db (assoc-in [:new-database :call-to-create-status] :in-progress)
             (assoc-in [:new-database :api-error-text] nil))
     :fx [(if remote?
            [:dispatch [:remote-storage/create-kdbx prepared]]
            [:bg-create-kdbx new-db])]}))

(defn- save-as-then-create-fx [db]
  {:db (-> db
           (assoc-in [:new-database :panel] :file-info)
           (assoc-in [:new-database :api-error-text] nil))
   :fx [[:new-database-save-as-then-create (:new-database db)]]})

;; Called when 'Done' is clicked
(reg-event-fx
 :new-database-create
 (fn [{:keys [db]} [_event-id]]
   ;;(println "event new-database-create called")
   (let [errors (validate-security-fields db)
         save-remote? (get-in db [:new-database :save-remote?])
         imported-data? (get-in db [:new-database :imported-data])
         mas-build? (:mas-build db)
         database-file-user-selected? (get-in db [:new-database :database-file-user-selected])]
     (cond
       (boolean (seq errors))
       {:db (assoc-in db [:new-database :error-fields] errors)}

       save-remote?
       ;; Hand the prepared wizard payload to remote-storage, then close the
       ;; new-db dialog (matches local-create behavior: dialog closes before
       ;; the create proceeds). When the user picks a remote folder, the
       ;; remote-storage flow uses the stashed payload to call create-kdbx
       ;; directly — no further new-db dialog re-entry.
       (let [payload (convert-kdf-value (:new-database db))]
         {:fx [[:dispatch [:remote-storage/begin-create-with-payload payload]]
               [:dispatch [:new-database/dialog-close]]]})

       (and mas-build? (not imported-data?) (not database-file-user-selected?))
       (save-as-then-create-fx db)

       (not imported-data?)
       (create-kdbx-fx db)

       :else
       {:db (-> db (assoc-in [:new-database :call-to-create-status] :in-progress)
                (assoc-in [:new-database :api-error-text] nil))
        :fx [[:dispatch [:import-file/new-database (convert-kdf-value (:new-database db)) on-database-creation-completed]]]}))))

(reg-fx
 :new-database-save-as-then-create
 (fn [{:keys [database-name database-file-name]}]
   (save-as-file-explorer-on-click database-name database-file-name create-kdbx-fx)))
(reg-fx
 :bg-create-kdbx
 (fn [new-db]
   (bg/create-kdbx (convert-kdf-value new-db)  on-database-creation-completed)))

(reg-event-fx
 :new-database-created
 (fn [{:keys [db]} [_event-id kdbx-loaded]]
   {:db (-> db (assoc-in [:new-database :api-error-text] nil)
            (assoc-in [:new-database :call-to-create-status] :completed))
    :fx [[:dispatch [:new-database/dialog-close]]
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

;; Called from the remote-storage browse dialog after the user picks a remote
;; folder + file name for a new db when the remote-storage flow was entered
;; WITHOUT a new-db wizard in flight (e.g. a future entry point that opens the
;; rs create dialog directly). The save-remote? checkbox path doesn't go
;; through here — that path stashes the prepared payload in remote-storage
;; and calls create-kdbx directly.
;; db-file-name is the prefixed remote db_key
;; ("Sftp-<uuid>-/path/file.kdbx" / "Webdav-..."), file-name is the bare base
;; file name.
(reg-event-db
 :new-database/remote-target-selected
 (fn [db [_event-id db-file-name file-name]]
   (let [display-name (str/replace (or file-name "") #"(?i)\.kdbx$" "")]
     (-> db
         init-new-database-data
         (assoc-in [:new-database :dialog-show] true)
         (assoc-in [:new-database :remote?] true)
         (assoc-in [:new-database :database-file-user-selected] true)
         (assoc-in [:new-database :database-file-name] db-file-name)
         (assoc-in [:new-database :database-name] display-name)))))

(reg-sub
 :new-database-dialog-data
 (fn [db _query-vec]
   (get-in db [:new-database])))

(comment
  @re-frame.db/app-db)
