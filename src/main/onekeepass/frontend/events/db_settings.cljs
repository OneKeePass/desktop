(ns onekeepass.frontend.events.db-settings
  "All database settings events for the selected database"
  (:require
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [clojure.string :as str]
   [onekeepass.frontend.utils :as utils :refer [str->int contains-val?]]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             active-db-key]]
   [onekeepass.frontend.background :as bg]))



(def panels [:general-info :credentials-info  :security-info])

(defn open-key-file-explorer-on-click []
  (cmn-events/open-file-explorer-on-click :db-settings-key-file-name-selected))

(defn read-db-settings []
  (dispatch [:db-settings-read-start]))

(defn field-update-factory [kw-field-name]
  (fn [^js/Event e] 
    (dispatch [:db-settings-field-update kw-field-name (->  e .-target .-value)])))

(defn database-field-update [kw-field-name value]
  (dispatch [:db-settings-field-update kw-field-name value]))

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
  (let [{:keys [iterations memory parallelism]} (get-in-key-db app-db [:db-settings :data :kdf :Argon2])
        ;; Need to convert incoming str values to the proper int values
        ;; [iterations memory parallelism] (mapv str->int [iterations memory parallelism])

        errors (if (or (nil? iterations) (or (< iterations 5) (> iterations 100)))
                 {:iterations "Valid values should be in the range 5 - 100"} {})
        errors (merge errors
                      (if (or (nil? memory) (or (< memory 1) (> memory 1000)))
                        {:memory "Valid values should be in the range 1 - 1000"} {}))
        errors (merge errors
                      (if (or (nil? parallelism) (or (< parallelism 1) (> parallelism 100)))
                        {:parallelism "Valid values should be in the range 1 - 100"} {}))]

    errors))

(def field-not-empty? (comp not empty?))

(defn- validate-credential-fields [db]
  (let [{:keys [password password-used key-file-used]} (get-in-key-db db [:db-settings :data])
        cp (get-in-key-db db [:db-settings :password-confirm])
        visible  (get-in-key-db db [:db-settings :password-visible])]

    (cond
      (and (field-not-empty? password) (not visible) (not= password cp))
      {:password-confirm "Password and Confirm password are not matching"}

      (and (not key-file-used) (not password-used))
      {:no-credential-set 
       "Database needs to be protected using a master key formed with a password or a key file or both"})))

(defn- validate-required-fields
  [db panel]
  (cond
    (= panel :general-info)
    (when (str/blank? (get-in-key-db db [:db-settings :data :meta :database-name]))
      {:database-name "A valid database name is required"})

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
                       #_(update-in [:kdf :Argon2 :iterations] str->int)
                       #_(update-in [:kdf :Argon2 :parallelism] str->int)
                       #_(update-in [:kdf :Argon2 :memory] str->int)
                       (update-in [:kdf :Argon2 :memory] * 1048576)
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

(reg-event-db
 :db-settings-read-completed
 (fn [db [_event-id {:keys [key-file-name] :as settings}]]
   ;; Need to convert memory value from bytes to MB. 
   ;; And we need to convert back bytes before saving back
   ;; See event ':bg-set-db-settings'
   (let [data (-> settings
                  (update-in [:kdf :Argon2 :memory] #(Math/floor (/ % 1048576)))
                  (assoc-in [:org-key-file-name] key-file-name))]
     ;;(println "Data is " data)
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
  (cond (or (= ks [:db-settings :data :kdf :Argon2 :iterations])
            (= ks [:db-settings :data :kdf :Argon2 :parallelism])
            (= ks [:db-settings :data :kdf :Argon2 :memory]))
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
 :db-settings-field-update
 ;; kw-field-name is single kw or a vec of kws  
 (fn [db [_event-id kw-field-name value]]
   (let [;;ks will be a vector. Eg [:db-settings :data :kdf ...]
         ks (into [:db-settings] (if (vector? kw-field-name)
                                   kw-field-name
                                   [kw-field-name]))
         val (convert-value ks value)
         password-val? (contains-val? ks :password)
         key-file-name-val? (contains-val? ks :key-file-name) 
         db (assoc-in-key-db db ks val)] 
     (cond
       (and password-val? (field-not-empty? val))
       (-> db (assoc-in-key-db [:db-settings :data :password-used] true)
           (assoc-in-key-db [:db-settings :error-fields :no-credential-set] nil)
           (assoc-in-key-db [:db-settings :data :password-changed] true))

       key-file-name-val?
       (if (field-not-empty? val)
         (-> db (assoc-in-key-db [:db-settings :data :key-file-used] true)
             (assoc-in-key-db [:db-settings :data :key-file-changed] true)
             (assoc-in-key-db [:db-settings :error-fields :no-credential-set] nil))
         (-> db (assoc-in-key-db [:db-settings :data :key-file-used] false)
             (assoc-in-key-db [:db-settings :data :key-file-changed] true)
             (assoc-in-key-db [:db-settings :data :key-file-name] nil)))
       :else
       db))))

(reg-event-db
 :db-settings-password-change-action
 (fn [db [_event-id kw-action-name]]
   (cond
     (= kw-action-name :change)
     (-> db (assoc-in-key-db [:db-settings :password-field-show] true))

     (= kw-action-name :add)
     (-> db (assoc-in-key-db [:db-settings :password-field-show] true)
         (assoc-in-key-db [:db-settings :error-fields :no-credential-set] nil))

     (= kw-action-name :remove)
     (-> db (assoc-in-key-db [:db-settings :password-use-removed] true)
         (assoc-in-key-db [:db-settings :data :password-used] false)
         (assoc-in-key-db [:db-settings :data :password] nil)
         (assoc-in-key-db [:db-settings :data :password-changed] true)))))

(reg-event-db
 :db-settings-key-file-change-action
 (fn [db [_event-id kw-action-name]]
   (cond
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
         (assoc-in-key-db [:db-settings :data :key-file-name] nil)))))

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
  (def a1 (-> (get @re-frame.db/app-db db-key) :db-settings :data))
  (def a2 (-> (get @re-frame.db/app-db db-key) :db-settings :undo-data))
  (-> a1 :kdf)
  (-> a2 :kdf)
  (= a1 a2)
  (-> (get @re-frame.db/app-db db-key) :db-settings))