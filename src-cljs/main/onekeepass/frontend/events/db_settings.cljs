(ns onekeepass.frontend.events.db-settings
  "All database settings events for the selected database"
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [clojure.string :as str]
   [onekeepass.frontend.translation  :refer-macros [tr-m]]
   [onekeepass.frontend.utils :as utils :refer [str->int contains-val?]]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             active-db-key]]
   [onekeepass.frontend.background :as bg]))

(def panels [:general-info :credentials-info  :security-info])

(defn app-settings-dialog-read-start []
  (dispatch [:app-settings/read-start])
  (dispatch [:db-settings-dialog-show false]))

(defn open-key-file-explorer-on-click []
  (cmn-events/open-file-explorer-on-click :db-settings-key-file-name-selected))

(defn read-db-settings []
  (dispatch [:db-settings-read-start]))

(defn field-update-factory [kw-field-name]
  (fn [^js/Event e]
    (dispatch [:db-settings-field-update kw-field-name (->  e .-target .-value)])))

(defn database-field-update [kw-field-name value]
  (dispatch [:db-settings-field-update kw-field-name value]))

(defn db-settings-kdf-algorithm-select [kdf-selection]
  (dispatch [:db-settings-kdf-algorithm-select kdf-selection]))

(defn password-change-action [kw-action-name]
  (dispatch [:db-settings-password-change-action kw-action-name]))

(defn key-file-change-action [kw-action-name]
  (dispatch [:db-settings-key-file-change-action kw-action-name]))

(defn db-settings-panel-select [kw-panel]
  (dispatch [:db-settings-panel-select kw-panel]))

(defn cancel-on-click []
  (dispatch [:db-settings-dialog-show false]))

(defn ok-on-click []
  (dispatch [:db-settings-write-start]))

(defn generate-key-file []
  (dispatch [:db-settings-generate-key-file]))

(defn dialog-data []
  (subscribe [:db-settings]))

(defn key-file-name-change-kind []
  (subscribe [:key-file-name-change-kind]))

(defn password-changed? []
  (subscribe [:password-changed]))

(defn db-settings-modified []
  (subscribe [:db-settings-modified]))

(defn- validate-security-fields
  [app-db]
  (let [{:keys [iterations memory parallelism]} (get-in-key-db app-db [:db-settings :data :kdf])
        ;; Need to convert incoming str values to the proper int values
        ;; [iterations memory parallelism] (mapv str->int [iterations memory parallelism])

        errors (if (or (nil? iterations) (< iterations 5) (> iterations 100))
                 {:iterations (tr-m databaseSettings iterations)} {})
        errors (merge errors
                      (if (or (nil? memory) (< memory 1)  (> memory 1000) #_(or (< memory 1) (> memory 1000)))
                        {:memory (tr-m databaseSettings memory)} {}))
        errors (merge errors
                      (if (or (nil? parallelism) (< parallelism 1) (> parallelism 100) #_(or (< parallelism 1) (> parallelism 100)))
                        {:parallelism (tr-m databaseSettings parallelism)} {}))]

    errors))

(def field-not-empty? (comp not empty?))

(defn- validate-credential-fields [db]
  (let [{:keys [password password-used key-file-used]} (get-in-key-db db [:db-settings :data])
        cp (get-in-key-db db [:db-settings :password-confirm])
        visible  (get-in-key-db db [:db-settings :password-visible])]

    (cond
      (and (field-not-empty? password) (not visible) (not= password cp))
      {:password-confirm (tr-m databaseSettings passwordConfirm)}

      (and (not key-file-used) (not password-used))
      {:no-credential-set (tr-m databaseSettings noCredentialSet)})))

(defn- validate-required-fields
  [db panel]
  (cond
    (= panel :general-info)
    (when (str/blank? (get-in-key-db db [:db-settings :data :meta :database-name]))
      {:database-name (tr-m databaseSettings databaseName)})

    (= panel :credentials-info)
    (validate-credential-fields db)

    (= panel :security-info)
    (validate-security-fields db)

    ;;(= panel :file-info)
    ))

(defn- validate-all-panels [db]
  (reduce (fn [v panel]
            (let [errors (validate-required-fields db panel)]
              (if (boolean (seq errors))
                ;; early return when the first panel has some errors
                (reduced [panel errors])
                v))) [] panels))

;; Called on when user picks a key file
(reg-event-fx
 :db-settings-key-file-name-selected
 (fn [{:keys [_db]} [_event-id key-file-name]]
   {:fx [[:dispatch [:db-settings-field-update  [:data :key-file-name] key-file-name]]]}))

(reg-event-fx
 :db-settings-write-start
 (fn [{:keys [db]} [_event-id]]
   (let [[panel errors] (validate-all-panels db)]
     (if (not (boolean (seq errors)))
       {:db (-> db (assoc-in-key-db [:db-settings :status] :in-progress))
        :fx [[:bg-set-db-settings [(active-db-key db) (get-in-key-db db [:db-settings :data])]]]}
       {:db (-> db
                (assoc-in-key-db [:db-settings :panel] panel)
                (assoc-in-key-db [:db-settings :error-fields] errors))}))))

(reg-fx
 :bg-set-db-settings
 (fn [[db-key settings]]
   ;; settings is from [:db-settings :data]
   ;; Need to do some str to int and blank str handling
   (let [settings  (-> settings
                       (update-in [:kdf :memory] * 1048576)
                       ;; Allow space only password
                       ;; (str/blank? "  ") true but (empty? "  ") is false
                       ;; (str/blank? "") true but (empty? "") is true
                       (update-in [:password] #(if (empty? %) nil %))
                       (update-in [:key-file-name] #(if (str/blank? %) nil %)))]
     (bg/set-db-settings db-key settings
                         (fn [api-response]
                           (when-not (on-error api-response #(dispatch [:db-settings-write-error %]))
                             (dispatch [:db-settings-write-completed])))))))

(reg-event-db
 :db-settings-write-completed
 (fn [db [_event-id]]
   (assoc-in-key-db db [:db-settings :dialog-show] false)))

(reg-event-db
 :db-settings-write-error
 (fn [db [_event-id api-error-text]]
   (-> db
       (assoc-in-key-db [:db-settings :api-error-text] api-error-text)
       (assoc-in-key-db [:db-settings :status] :completed))))

(reg-event-fx
 :db-settings/notify-screen-locked
 (fn [{:keys [db]} [_event-id]]
   (let [locked? (get-in-key-db db [:locked])]
     (if locked?
       {:fx [[:dispatch [:db-settings-dialog-show false]]]}
       {}))))

(reg-event-fx
 :db-settings-read-start
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db [:db-settings :data :password] nil)
            (assoc-in-key-db [:db-settings :data :key-file-name] nil)
            (assoc-in-key-db [:db-settings :data :org-key-file-name] nil)

            (assoc-in-key-db [:db-settings :dialog-show] true)

            (assoc-in-key-db [:db-settings :password-confirm] nil)
            (assoc-in-key-db [:db-settings :password-visible] false)
            ;; Toggles password field vs buttons
            (assoc-in-key-db [:db-settings :password-field-show] false)
            (assoc-in-key-db [:db-settings :password-use-added] false) ;; Add button?
            ;; Toggles showing Add/Remove Password button 
            (assoc-in-key-db [:db-settings :password-use-removed] false)

            (assoc-in-key-db [:db-settings :key-file-field-show] false)
            ;; Toggles showing Add/Remove Key file button
            (assoc-in-key-db [:db-settings :key-file-use-removed] false)

            (assoc-in-key-db [:db-settings :panel] :general-info)
            (assoc-in-key-db [:db-settings :status] :in-progress)
            (assoc-in-key-db [:db-settings :api-error-text] nil))
    :fx [[:bg-get-db-settings [(active-db-key db)]]]}))

(reg-fx
 :bg-get-db-settings
 ;; fn in 'reg-fx' accepts single argument
 ;; A vector arg typically used so that we can pass more than one input
 (fn [[db-key]]
   (bg/get-db-settings db-key (fn [api-response]
                                (when-let [settings (check-error api-response
                                                                 #(dispatch [:db-settings-read-error %]))]
                                  (dispatch [:db-settings-read-completed settings]))))))

;; Need to convert memory value from bytes to MB. 
;; And we need to convert back bytes before saving to the backend
;; See event ':bg-set-db-settings'
(defn- kdf-adjustment-in-settings [settings]
  ;; Currently only kdf algorithms supported are Argon2d and Argon2id
  ;; See enum KdfAlgorithm
  (-> settings (update-in [:kdf :memory] #(Math/floor (/ % 1048576)))))

(reg-event-db
 :db-settings-read-completed
 (fn [db [_event-id {:keys [key-file-name] :as settings}]]

   (let [data (-> settings
                  kdf-adjustment-in-settings
                  (assoc-in [:org-key-file-name] key-file-name))]

     (-> db (assoc-in-key-db  [:db-settings :data] data)
         (assoc-in-key-db  [:db-settings :undo-data] data)
         (assoc-in-key-db [:db-settings :status] :completed)
         (assoc-in-key-db [:db-settings :panel] :general-info)))))

(reg-event-db
 :db-settings-read-error
 (fn [db [_event-id error]]
   (-> db (assoc-in-key-db [:db-settings :api-error-text] error)
       (assoc-in-key-db [:db-settings :status] :completed))))

(reg-event-fx
 :db-settings-generate-key-file
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:bg-db-settings-generate-key-file [(get-in-key-db db [:db-settings :data :meta :database-name])]]]}))

(reg-fx
 :bg-db-settings-generate-key-file
 (fn [[database-name]]
   (bg/save-file-dialog {:default-path (str database-name ".keyx")
                         :title "Save Key File"}
                        (fn [key-file-name]
                          ;; key-file-name is not updated if user cancels the 'Save As' file exploreer 
                          (when (not (str/blank? key-file-name))
                            (bg/generate-key-file key-file-name
                                                  (fn [api-response]
                                                    (when-not (on-error api-response)
                                                      (dispatch [:db-settings-field-update
                                                                 [:data :key-file-name] key-file-name])))))))))

(defn- convert-value
  "(->  e .-target .-value) returns a string value 
  and to make comparision with undo-data, we need to make sure both values 
  are 'int' type
  "
  [ks value]
  (cond (or (= ks [:db-settings :data :kdf :iterations])
            (= ks [:db-settings :data :kdf :parallelism])
            (= ks [:db-settings :data :kdf :memory]))
        (str->int value)

        ;;
        (and (= ks [:db-settings :data :password]) (empty? value))
        nil

        (and (= ks [:db-settings :data :key-file-name]) (str/blank? value))
        nil
        ;; (and (str/blank? value) (or (= ks [:db-settings :data :password]) (= ks [:db-settings :data :key-file-name])))
        ;; nil

        :else
        value))

(reg-event-db
 :db-settings-kdf-algorithm-select
 (fn [db [_event-id kdf-selection]]
   ;; Fields algorithm and variant need to be set to these values so that 
   ;; kdf map is serialized to enum KdfAlgorithm::Argon2d or  KdfAlgorithm::Argon2id
   ;; Also see events in new-database.cljs
   (-> db (assoc-in-key-db [:db-settings :data :kdf :algorithm] kdf-selection)
       (assoc-in-key-db [:db-settings :data :kdf :variant] (if (= kdf-selection "Argon2d") 0 2)))))

;; A common event that handles most of the update of fields in the map found in ':db-settings'
;; May need to refactor to panel specific events (like crypto, kdf ,crdentials etc to its own events)
(reg-event-db
 :db-settings-field-update
 ;; The incoming kw-field-name is single kw or a vec of kws  
 (fn [db [_event-id kw-field-name value]]
   (let [;; The final ks will be a vector. Eg [:db-settings :data :kdf ...]
         ks (into [:db-settings] (if (vector? kw-field-name)
                                   kw-field-name
                                   [kw-field-name]))

         val (convert-value ks value)

         password-val? (contains-val? ks :password)
         key-file-name-val? (contains-val? ks :key-file-name)

         ;; Determines the panel to show with any errors 
         current-panel (get-in-key-db db [:db-settings :panel])

         ;; Set the updated value 
         db (assoc-in-key-db db ks val)

         ;; Need to set additional flags for the password/key file case
         db (cond
              ;; password-val? indicates password field is updated
              (and password-val? (field-not-empty? val))
              (-> db (assoc-in-key-db [:db-settings :data :password-used] true)
                  (assoc-in-key-db [:db-settings :data :password-changed] true))

              ;; key-file-name-val? indicates key file name field is updated
              key-file-name-val?
              (if (field-not-empty? val)
                (-> db (assoc-in-key-db [:db-settings :data :key-file-used] true)
                    (assoc-in-key-db [:db-settings :data :key-file-changed] true))
                (-> db (assoc-in-key-db [:db-settings :data :key-file-used] false)
                    (assoc-in-key-db [:db-settings :data :key-file-changed] true)
                    (assoc-in-key-db [:db-settings :data :key-file-name] nil)))
              :else
              db)
         ;; Determine any errors in the current panel 
         errors  (validate-required-fields db current-panel)
         db (-> db
                (assoc-in-key-db [:db-settings :error-fields] errors))]
     db)))

(reg-event-db
 :db-settings-password-change-action
 (fn [db [_event-id kw-action-name]]
   (let [db (cond
              (= kw-action-name :change)
              (-> db (assoc-in-key-db [:db-settings :password-field-show] true))

              (= kw-action-name :add)
              (-> db (assoc-in-key-db [:db-settings :password-field-show] true)
                  (assoc-in-key-db [:db-settings :password-use-added] true)
                  (assoc-in-key-db [:db-settings :error-fields :no-credential-set] nil))

              (= kw-action-name :remove)
              (-> db (assoc-in-key-db [:db-settings :password-use-removed] true)
                  (assoc-in-key-db [:db-settings :data :password-used] false)
                  (assoc-in-key-db [:db-settings :data :password] nil)
                  (assoc-in-key-db [:db-settings :data :password-changed] true)))
         errors (validate-credential-fields db)
         db (-> db (assoc-in-key-db [:db-settings :error-fields] errors))]
     db)))

(reg-event-fx
 :db-settings-key-file-change-action
 (fn [{:keys [db]} [_event-id kw-action-name]]
   (let [db (cond
              (= kw-action-name :change)
              (-> db (assoc-in-key-db [:db-settings :key-file-field-show] true))

              ;; Add may be called after Remove call in the same screen
              (= kw-action-name :add)
              (let [existing-key-file-name (get-in-key-db db [:db-settings :undo-data :key-file-name])
                    k-used (if (field-not-empty? existing-key-file-name) true false)]
                (-> db (assoc-in-key-db [:db-settings :key-file-field-show] true)
                    (assoc-in-key-db [:db-settings :data :key-file-name] existing-key-file-name)
                    (assoc-in-key-db [:db-settings :data :key-file-used] k-used)
                    (assoc-in-key-db [:db-settings :data :key-file-changed] false)
                    (assoc-in-key-db [:db-settings :error-fields :no-credential-set] nil)))


              (= kw-action-name :remove)
              (-> db (assoc-in-key-db [:db-settings :key-file-use-removed] true)
                  (assoc-in-key-db [:db-settings :data :key-file-used] false)
                  (assoc-in-key-db [:db-settings :data :key-file-changed] true)
                  (assoc-in-key-db [:db-settings :data :key-file-name] nil)))
         errors (validate-credential-fields db)
         db (-> db (assoc-in-key-db [:db-settings :error-fields] errors))]
     {:db db})))

(reg-event-db
 :db-settings-panel-select
 (fn [db [_event-id kw-panel]]
   (let [current (get-in-key-db db [:db-settings :panel])
         errors  (validate-required-fields db current)]
     (if (boolean (seq errors))
       (-> db (assoc-in-key-db [:db-settings :error-fields] errors))
       (-> db (assoc-in-key-db [:db-settings :panel] kw-panel)
           (assoc-in-key-db [:db-settings :error-fields] nil))))))

(reg-event-fx
 :db-settings-dialog-show
 (fn [{:keys [db]} [_event-id show?]]
   {:db (if show?
          (-> db
              (assoc-in-key-db [:db-settings :dialog-show] true)
              (assoc-in-key-db [:db-settings :api-error-text] nil)
              (assoc-in-key-db [:db-settings :error-fields] nil))
          (-> db (assoc-in-key-db [:db-settings :dialog-show] false)
              (assoc-in-key-db [:db-settings :data] nil)
              (assoc-in-key-db [:db-settings :undo-data] nil)
              (assoc-in-key-db [:db-settings :api-error-text] nil)
              (assoc-in-key-db [:db-settings :error-fields] nil)))}))

(reg-sub
 :db-settings
 (fn [db _query-vec]
   (get-in-key-db db [:db-settings])))

(reg-sub
 :key-file-name-change-kind
 :<- [:db-settings]
 (fn [{{:keys [key-file-name org-key-file-name]} :data} _query-vec]
   (cond
     (and (nil? org-key-file-name) (not (nil? key-file-name)))
     :none-to-some

     (and (not (nil? org-key-file-name)) (nil? key-file-name))
     :some-to-none

     (and (not (nil? org-key-file-name)) (not (nil? key-file-name)) (not= org-key-file-name key-file-name))
     :some-to-some)))

(reg-sub
 :password-changed
 :<- [:db-settings]
 (fn [{{:keys [password]} :data} _query-vec]
   (field-not-empty? password)
   #_(not (str/blank? password))))

(reg-sub
 :db-settings-modified
 (fn [db _query-vec]
   (let [undo-data (get-in-key-db db [:db-settings :undo-data])
         data (get-in-key-db db [:db-settings :data])]
     (if (and (seq undo-data) (not= undo-data data))
       true
       false))))

(comment

  (def db-key (:current-db-file-name @re-frame.db/app-db))

  (-> (get @re-frame.db/app-db db-key)
      :db-settings
      :data (select-keys
             [:password-used :password-changed :password
              :key-file-used :key-file-changed :key-file-name]))
  (-> (get @re-frame.db/app-db db-key) :db-settings))