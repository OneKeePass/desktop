(ns onekeepass.frontend.events.entry-form-ex
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.constants :as const :refer [PASSWORD]]
   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             assoc-in-key-db
                                                             check-error
                                                             fix-tags-selection-prefix
                                                             get-in-key-db
                                                             on-error]]
   ;; Do not remove. Required to register otp evets
   [onekeepass.frontend.events.entry-form-otp]
   [onekeepass.frontend.events.entry-form-common :as ef-events-cmn :refer [add-section-field
                                                                           entry-form-key
                                                                           extract-form-field-names-values
                                                                           extract-form-otp-fields
                                                                           get-form-data
                                                                           is-field-exist
                                                                           merge-section-key-value]]
   ;; Need to be called here so that events are registered 
   [onekeepass.frontend.events.entry-form-auto-open :as ao-events]
   [onekeepass.frontend.translation :refer [lstr-sm]]
   [onekeepass.frontend.utils :as u :refer [contains-val?]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub
                          subscribe]]))

(def Favorites "Favorites")

(def entry-form-open-url ao-events/entry-form-open-url)

(def place-holder-resolved-value ef-events-cmn/place-holder-resolved-value)

;;;;;;;;;;;;;;;;;;;;;;   Support functions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- validate-entry-form-data
  "Verifies that the user has entered valid values in some of the required fields of the entry form
  Returns a map of fileds with errors and error-fields will be {} in case no error is found
  "
  [{:keys [group-uuid title]}]
  ;;(println "group-uuid title are " group-uuid title)
  (let [error-fields (cond-> {}
                       (u/uuid-nil-or-default? group-uuid)
                       (assoc :group-selection "Please select a group ")

                       (str/blank? title)
                       (assoc :title "Please enter a title for this form"))]
    error-fields))

(defn- validate-required-fields [error-fields _kvsd]
  error-fields
  #_(loop [{:keys [key value required] :as m} (first kvsd)
           rest-kvsd (next kvsd)
           acc error-fields]
      (if (nil? m) acc
          (let [acc (if (and required (str/blank? value))
                      (assoc acc key "Please enter a valid value for this required field")
                      acc)]
            (recur (first rest-kvsd) (next rest-kvsd) acc)))))

(defn- validate-all
  "Validates all required fields including title, parent group etc
  Returns the error-fields map or an empty map if all required values are present
   "
  [form-data]
  (let [error-fields (validate-entry-form-data form-data)
        ;; We get all fields across all sections
        ;; Need to make a flattened sequence of all KV maps
        kvds (flatten (vals (:section-fields form-data)))
        error-fields (validate-required-fields error-fields kvds)]
    error-fields))

(defn- init-expiry-duration-selection
  "Iniatializes the expiry related data in entry-form top level field. 
  Returns the updated app-db"
  [app-db entry-form-data]
  (if (:expires entry-form-data)
    (assoc-in-key-db app-db [entry-form-key :expiry-duration-selection] "custom-date")
    (assoc-in-key-db app-db [entry-form-key :expiry-duration-selection] "no-expiry")))

;; TODO: Need to replace with generic fns from common
(defn- has-password-field
  "Checks whether the arg 'field-maps-vec' has a map with entry form entry field map  
  (struct KeyValueData) that has a 'key' for password
  Reurns true if the passed vec of maps has a map with :key = Password
  "
  [field-maps-vec]
  (->> field-maps-vec (filter
                       (fn [m] (= (:key m) PASSWORD))) empty? boolean not))

(defn- section-with-password-score
  "Returns a vector of two elements [section-name [{:key..} {:key ..}]] or empty vector
  if no PASSWORD field is found
  "
  [db]
  (let [section-fields  (get-in-key-db db [entry-form-key :data :section-fields])
        ;; filter returns a seq with 0 or 1 member vec [k v]
        section-name-with-kvs (->> section-fields
                                   (filterv (fn [[_section-name field-maps-vec]]
                                              (has-password-field field-maps-vec))) first)]
    section-name-with-kvs))

(defn- adjust-expiration-time
  "Returns the updated app-db"
  [app-db selection expiry-time]
  (println "adjust-expiration-time in " expiry-time)
  (if (= selection "no-expiry")
    (assoc-in-key-db app-db [entry-form-key :data :expires] false)
    ;; value is js Date utc string of format "2022-05-13T04:02:44.481Z"
    ;; The backend rust deserialization fails with this value
    ;; Removing the '.481Z' and using just "2022-05-13T04:02:44" works for now
    ;; Need to fix the backend to accept the date string ending in Z without this hack
    (-> app-db (assoc-in-key-db [entry-form-key :data :expires] true)
        (assoc-in-key-db [entry-form-key :data :expiry-time] (u/strip-utc-tz expiry-time)  #_(first (str/split expiry-time #"\."))))))

(defn expiry-date-on-change-factory
  "Creates an event handler to handle an event when the date is changed in the date time picker"
  []
  ;; Returns a function that acceps two arguments 'value: TValue, keyboardInputValue: string'
  ;; See @mui/x-date-pickers-pro/DateTimePicker onChange prop
  (fn [v kb]
    ;;(println "kb is " kb)
    ;; (println " exp v str is " (str v) " v as date " v )

    ;; Following is not used as kb will be in the format of '09/29/2022 08:53 pm' and this is not in the 
    ;; format as expected by backend api and call will fail 
    #_(let [d (cond
                (= (str v) "Invalid Date")
                ;; kb is nil if v is a valid date; 
                kb

                (instance? js/Date v)
                (u/to-UTC-ISO-string v)

                :else
                v)]
        (dispatch [:entry-form-update-section [:main {:fields [:expiry :value]
                                                      :value d}]])
        (dispatch [:entry-form-update-section [:main {:fields [:expiry :expiry-duration-selection]
                                                      :value "custom-date"}]]))

    ;; If we edit the field directly, we get #inst "0NaN-NaN-NaNTNaN:NaN:NaN.NaN-00:00" in v
    ;; This causes 'Compile Exception: Unrecognized date/time syntax: 0NaN-NaN-NaNTNaN:NaN:NaN.NaN-00:00'
    ;; For now we ignore any attempt changes in the input and allow the date change by datetime picker
    ;; by using the following check. (str v) returns the string "Invalid Date" and kb will have keyboard input 
    ;; in the format '09/29/2022 08:53 pm'.
    (when-not (= (str v) "Invalid Date")
      (let [d (if (instance? js/Date v) (u/to-UTC-ISO-string v) v)]
        ;; Need to strip 'Z' indicating UTC from datetime str 
        (dispatch [:entry-form-data-update-field-value :expiry-time (u/strip-utc-tz d)])
        (dispatch [:entry-form-data-update-field-value :expires true])
        (dispatch [:entry-form-all-update-field-value :expiry-duration-selection "custom-date"])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  

(defn password-generator-show []
  (dispatch [:password-generator/start
             ;; A call back fn that is to be called with the new generated password and score
             (fn [password score]
               (dispatch [:entry-form-update-generated-password password score]))]))

(defn update-section-value-on-change
  "Updates a section's KeyValue map with the given key and value"
  [section key value]
  (dispatch [:entry-form-update-section-value section key value]))

(defn entry-form-data-update-field-value
  "Update a field found in :data"
  [field-name-kw value]
  (dispatch [:entry-form-data-update-field-value field-name-kw value]))

(defn edit-mode-menu-clicked []
  (dispatch [:entry-form-ex/edit true]))

(defn favorite-menu-checked
  "Called when an entry is marked as favorite or not"
  [yes?]
  (dispatch [:entry-is-favorite-ex yes?]))

(defn close-on-click
  "Called to close the form"
  [_e]
  (dispatch [:close-form-ex]))

(defn ok-edit-on-click
  "Called to save the changes"
  [_e]
  (dispatch [:ok-entry-edit-ex]))

(defn entry-update-cancel-on-click
  "Called to undo any updates done on the form and cancel button is clicked"
  [_e]
  (dispatch [:cancel-entry-edit-ex]))

(defn new-entry-cancel-on-click
  "Called when user cancels the new entry without applying the changes"
  [_e]
  (dispatch [:entry-form-ex/show-welcome]))

(defn ok-new-entry-add []
  (dispatch [:ok-new-entry-add-ex]))

(defn on-tags-selection
  "Called on selecting one or more tags on the entry form"
  [_e tags]
  (dispatch [:entry-form-tags-selected-ex (fix-tags-selection-prefix tags)]))

#_(defn section-date-field-on-change-factory
    "Creates an event handler to handle an event when the date is changed in the date picker"
    [section key]
    (fn [date-val kb]
      ;;(println "date-val is " date-val " and type is " (type date-val))
      (when-not (= (str date-val) "Invalid Date")
        (let [date-val-str (if (instance? js/Date date-val) (.toLocaleDateString date-val) date-val)]
          (dispatch [:entry-form-update-section-value section key date-val-str])))))

(defn expiry-duration-selection-on-change [value]
  (dispatch [:expiry-duration-selection-ex value]))

#_(defn entry-type-name-on-change
    "Called whenever an entry type name is selected in the New Entry Form"
    [entry-type-name]
    (dispatch [:entry-form-entry-type-name-selected-ex entry-type-name]))

;;:
(defn entry-type-uuid-on-change [entry-type-uuid]
  (dispatch [:entry-form-entry-type-uuid-selected-ex entry-type-uuid]))

(defn on-group-selection
  "Called on selecting a group for the entry in the new entry form view
  The option selected in autocomplete component is passed as 'group-info' - a javacript object
  "
  [_e group-info]
  (dispatch [:entry-form-group-selected-ex (js->clj group-info :keywordize-keys true)]))

(defn entry-form-all
  "Returns an atom for the whole entry-form map
   (keys @(entry-form-all)) are :showing :group-selection-info :otp-fields :edit :undo-data :data  :error-field ..
  "
  []
  (subscribe [:entry-form-all]))

(defn entry-form-data
  "Returns an atom that has the map of entry-form's :data "
  []
  (subscribe [:entry-form-data-ex]))

(defn entry-form-section-data [section]
  (subscribe [:entry-form-section-data section]))

(defn form-edit-mode
  "Returns an atom to indiacate editing is true or not"
  []
  (subscribe [:entry-form-edit-ex]))

(defn form-showing-status []
  (subscribe [:entry-form-showing-ex]))

(defn entry-form-data-fields
  " 
  Called to get value of one more of form 'data' level fields. 
  The arg is a single field name or  fields in a vector of two more field 
  names (Keywords) like [:title :icon-id]
  Returns an atom which resolves to a single value  or a map when derefenced
  e.g {:title 'value'} or {:tags 'value} or {:title 'value' :icon-id 'value}
   "
  [fields]
  (subscribe [:entry-form-data-fields fields]))

(defn entry-form-field
  "Gets the value of any field at the top level in entry-form itself. See other subs to get the field
  values from :data or [:data :section-fields] 
  "
  [file-name-kw]
  (subscribe [:entry-form-field file-name-kw]))

(defn modified
  "An atom that has a true value when entry form is modified"
  []
  (subscribe [:modified-ex]))

(defn favorites? []
  (subscribe [:entry-form-ex/favorites-status]))

(defn groups-listing
  "Returns an atom of vector with all group summary info to use in group selection field"
  []
  ;;Delegates to a subcriber in group tree event
  (subscribe [:group-tree-content/groups-listing]))

(defn is-entry-parent-group-deleted
  [group-uuid]
  (subscribe [:group-tree-content/group-in-recycle-bin group-uuid]))

;;;;;;;;;;;;;;;;;;;;;;;  Form Events      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(reg-event-fx
 :entry-form-ex/find-entry-by-id
 (fn [{:keys [db]} [_event-id  entry-id]]
   ;; (println "Called entry-form-ex/find-entry-by-id for entry-id " entry-id)
   (bg/get-entry-form-data-by-id (active-db-key db) entry-id
                                 (fn [api-response]
                                   (when-let [entry (check-error
                                                     api-response
                                                     #(dispatch [:entry-form-data-load-error %]))]
                                     (dispatch [:entry-form-data-load-completed-ok entry]))))
   {}))


(reg-event-fx
 :entry-form-data-load-completed-ok
 (fn [{:keys [db]} [_event-id  entry-data]]
   (let [otp-fields (extract-form-otp-fields entry-data)]
     {:db (-> db
              (assoc-in-key-db [entry-form-key :data] entry-data)
              (assoc-in-key-db [entry-form-key :edit] false)
              (init-expiry-duration-selection entry-data)
              (assoc-in-key-db [entry-form-key :showing] :selected)
              (assoc-in-key-db [entry-form-key :visibility-list] nil)
              ;; otp-fields is map with otp field name as key and token info (map) as value
              ;; This map is updated periodically when polling is started
              (assoc-in-key-db [entry-form-key :otp-fields] otp-fields))})))

(reg-event-db
 :entry-form-data-load-error
 (fn [db [_event-id error]]
   (-> db (assoc-in-key-db [entry-form-key :api-error-text] error))))

;; Rename :entry-form-data-load-completed-error
;; and remove ok part
#_(reg-event-db
   :entry-form-data-load-completed
   (fn [db [_event-id status result]] ;;result is )
     (if (= status :ok)
       (-> db
           #_(assoc-in-key-db [entry-form-key] {})
           (assoc-in-key-db [entry-form-key :data] result)
           (assoc-in-key-db [entry-form-key :edit] false)
           (init-expiry-duration-selection result)
           (assoc-in-key-db [entry-form-key :showing] :selected))

       (-> db (assoc-in-key-db [entry-form-key :api-error-text] result)))))

;; "Updates a section's KeyValue map with the given key and value"
(reg-event-fx
 :entry-form-update-section-value
 (fn [{:keys [db]} [_event-id section key value]]
   (let [section-kvs (merge-section-key-value db section key value)]

     (if-not (= key PASSWORD)
       {:db (assoc-in-key-db db [entry-form-key :data :section-fields section] section-kvs)}
       ;; Recalculate the password score for its strength
       {:db (assoc-in-key-db db [entry-form-key :data :section-fields section] section-kvs)
        :fx [[:bg-score-password [value]]]}))))

(reg-fx
 :bg-score-password
 (fn [[password]]
   (bg/score-password password
                      (fn [api-response]
                        (when-let [password-score (check-error api-response)]
                          ;; password-score is a map from struct 'PasswordScore'
                          (dispatch [:entry-form-update-section-password-score password-score]))))))

;; Updates the new score 
(reg-event-db
 :entry-form-update-section-password-score
 (fn [db [_event-id password-score]]
   ;; password-score is a map from struct 'PasswordScore'
   (let [[section-name section-kvs] (section-with-password-score db)
         section-kvs (mapv (fn [m] (if (= (:key m) PASSWORD)
                                     (assoc m :password-score password-score) m)) section-kvs)]

     ;; section-name should not be nil as we expect entry-form-update-section-password-score called only for passwords 
     (if-not (nil? section-name)
       (assoc-in-key-db db [entry-form-key :data :section-fields section-name] section-kvs)
       db))))

;; Called whenever a new password is generated using a password generator dialog
(reg-event-db
 :entry-form-update-generated-password
 (fn [db [_event-id password password-score]]
   (let [[section-name section-kvs] (section-with-password-score db)
         section-kvs (mapv (fn [m] (if (= (:key m) PASSWORD)
                                     (assoc m :value password :password-score password-score) m)) section-kvs)]
     (assoc-in-key-db db [entry-form-key :data :section-fields section-name] section-kvs))))

;; Update a field found in :data
(reg-event-db
 :entry-form-data-update-field-value
 (fn [db [_event-id field-name-kw value]]
   (assoc-in-key-db db [entry-form-key :data field-name-kw] value)))

;; Update a field found in the top level :entry-form itself
(reg-event-db
 :entry-form-all-update-field-value
 (fn [db [_event-id field-name-kw value]]
   (assoc-in-key-db db [entry-form-key field-name-kw] value)))

(defn entry-form-field-visibility-toggle [key]
  (dispatch [:entry-form-field-visibility-toggle key]))

(defn visible? [key]
  (subscribe [:entry-form-field-in-visibile-list key]))

;; Toggles the a field's membership in a list of visibility fields
(reg-event-db
 :entry-form-field-visibility-toggle
 (fn [db [_event-id key]]
   (let [vl (get-in-key-db db [entry-form-key :visibility-list])]
     (if (contains-val? vl key)
       (assoc-in-key-db db [entry-form-key :visibility-list] (filterv #(not= % key) vl))
       (assoc-in-key-db db [entry-form-key :visibility-list] (conj vl key))))))

(reg-event-db
 :entry-form-tags-selected-ex
 (fn [db [_event-id tags]]
   ;;(println "Calling with entry-form-tags-selected in event " tags)
   (assoc-in-key-db db [entry-form-key :data :tags] tags)))

(reg-event-fx
 :entry-form-ex/show-welcome
 (fn [{:keys [db]} [_event-id text-to-show]]
   ;; Sometime this event may be called twice in succession 
   ;; For example, load-category-entry-items and in turn :entry-list/load-entry-items
   ;; may trigger this. This check prevents successive calls and thus backend api 
   ;; call to stop polling is called only once
   (if (= :welcome (get-in-key-db db [entry-form-key :showing]))
     {}
     {:db (-> db (assoc-in-key-db [entry-form-key :data] {})
              (assoc-in-key-db [entry-form-key :undo-data] {})
              (assoc-in-key-db [entry-form-key :otp-fields] {})
              (assoc-in-key-db [entry-form-key :showing] :welcome)
              (assoc-in-key-db [entry-form-key :welcome-text-to-show] text-to-show)
              (assoc-in-key-db [entry-form-key :edit] false)
              (assoc-in-key-db [entry-form-key :error-fields] {})
              (assoc-in-key-db [entry-form-key :group-selection-info] nil))})))

(reg-event-fx
 :close-form-ex
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:entry-form-ex/show-welcome]]
         [:dispatch [:entry-list/update-selected-entry-id nil]]]}))

(reg-event-fx
 :entry-form-ex/edit
 (fn [{:keys [db]} [_event-id edit?]]
   (if edit?
     {:db (-> db
              (assoc-in-key-db [entry-form-key :undo-data] (get-form-data db))
              (assoc-in-key-db [entry-form-key :edit] edit?))}
     {:db (assoc-in-key-db db [entry-form-key :edit] edit?)})))

(reg-event-fx
 :cancel-entry-edit-ex
 (fn [{:keys [db]} [_event-id]]
   (let [undo-data (get-in-key-db db [entry-form-key :undo-data])
         data (get-form-data db)]
     {:db (if (and (seq undo-data) (not= undo-data data))
            (-> db (assoc-in-key-db [entry-form-key :data] undo-data)
                (assoc-in-key-db [entry-form-key :undo-data] {})
                (assoc-in-key-db [entry-form-key :edit] false)
                (assoc-in-key-db [entry-form-key :error-fields] {}))
            (-> db (assoc-in-key-db  [entry-form-key :edit] false)
                (assoc-in-key-db [entry-form-key :undo-data] {})
                (assoc-in-key-db [entry-form-key :error-fields] {})))
      :fx []})))

#_(reg-event-db
   :cancel-entry-edit-ex
   (fn [db [_event-id]]
     (let [undo-data (get-in-key-db db [entry-form-key :undo-data])
           data (get-in-key-db db [entry-form-key :data])]
       (if (and (seq undo-data) (not= undo-data data))
         (-> db (assoc-in-key-db [entry-form-key :data] undo-data)
             (assoc-in-key-db [entry-form-key :undo-data] {})
             (assoc-in-key-db [entry-form-key :edit] false)
             (assoc-in-key-db [entry-form-key :error-fields] {}))
         (-> db (assoc-in-key-db  [entry-form-key :edit] false)
             (assoc-in-key-db [entry-form-key :undo-data] {})
             (assoc-in-key-db [entry-form-key :error-fields] {}))))))

(defn- update-entry [db dispatch-fn]
  (let [form-data (get-form-data db)]
    (bg/update-entry (active-db-key db) form-data dispatch-fn)))

;; Edit is accepted and calls the backend API to update the Db
(reg-event-fx
 :ok-entry-edit-ex
 (fn [{:keys [db]} [_event-id]]
   (let [form-data (get-form-data db)
         error-fields (validate-all form-data)]
     ;;(println "auto type is " (:auto-type form-data))
     (if (boolean (seq error-fields))
       {:db (assoc-in-key-db db [entry-form-key :error-fields] error-fields)}
       ;; clear any previous errors as 'error-fields' will be empty at this time
       {:db (assoc-in-key-db db [entry-form-key :error-fields] error-fields)
        :fx [[:bg-update-entry [(active-db-key db) form-data]]]}))))

;; Called to validate required fields are valid or not
;; e.g Title, parent group and type
;; Calls the 'callback-fn' if there is no error
(reg-event-fx
 :entry-form/validate-form-fields
 (fn [{:keys [db]} [_event-id callback-fn]]
   (let [form-data (get-form-data db)
         error-fields (validate-all form-data)]
     (if (boolean (seq error-fields))
       {:db (assoc-in-key-db db [entry-form-key :error-fields] error-fields)}
       (do
         (callback-fn)
         {:db (assoc-in-key-db db [entry-form-key :error-fields] {})})))))

(reg-fx
 :bg-update-entry
 (fn [[db-key form-data]]
   ;;(println "bg-update-entry section fileds " (-> form-data :section-fields (get "Login Details")))
   (bg/update-entry db-key form-data (fn [api-response]
                                       (when-not (on-error api-response)
                                         (dispatch [:entry-update-complete-ex]))))))

(reg-event-fx
 :entry-form/auto-type-updated
 (fn [{:keys [db]} [_event-id auto-type-m]]
   {;; First set the changed incoming auto-type map to entry form data
    :db (-> db (assoc-in-key-db [entry-form-key :data :auto-type] auto-type-m))
    ;; For now, the 'ok-entry-edit-ex' event is reused for this save. It is assumed there will not 
    ;; be any validation error!
    :fx [[:dispatch [:ok-entry-edit-ex]]]}))

;; :entry-form/find-entry-by-id is called indirectly 
(reg-event-fx
 :entry-update-complete-ex
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:common/message-snackbar-open (lstr-sm 'entryUpdated)]]
         ;; We need not call any explicit resetting of Edit mode to false, as the call 
         ;; to :entry-list/entry-updated -> :entry-form/find-entry-by-id will put the form in read only mode
         #_[:dispatch [:entry-form-edit-ex false]]
         ;; Call entry list update so that any 'primary title' (Title) value or
         ;; the 'secondary title' (User Name) is changed in edit mode of entry form  
         ;; This also in turn calls ':entry-form/find-entry-by-id' if the current entry form is  
         ;; matches selected entry uuid in entry list and this updates the entry form to 
         ;; sync with the backend update - This also ensures that we have correct history entries
         [:dispatch [:entry-list/entry-updated]]
         ;; In case of this entry's marked as Favorites by adding a tag,  
         ;; we need to refresh the entry-category view also
         [:dispatch [:entry-category/reload-category-data]]]}))

(reg-event-fx
 :entry-is-favorite-ex
 (fn fn [{:keys [db]} [_event-id yes?]]
   (let [tags (get-in-key-db db [entry-form-key :data :tags])
         tags (if yes?
                (into [Favorites] tags)
                (filterv #(not= Favorites %) tags))]
     {:db (assoc-in-key-db db [entry-form-key :data :tags] tags)
      :fx [[:dispatch [:ok-entry-edit-ex]]]})))


(reg-event-db
 :expiry-duration-selection-ex
 (fn [db [_event-id v]]
   (let [expiry-time (get-in-key-db db [entry-form-key :data :expiry-time])
         d (condp = v
             "no-expiry"
             nil

             "three-months"
             (u/add-months-today 3)

             "six-months"
             (u/add-months-today 6)

             "one-year"
             (u/add-months-today 12)

             "custom-date"
             expiry-time)
         d (if (instance? js/Date d) (u/to-UTC-ISO-string d) d)]
     (-> db (assoc-in-key-db [entry-form-key :expiry-duration-selection] v)
         (adjust-expiration-time v d)))))

(reg-sub
 :entry-form-edit-ex ;; same id as in above 
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key :edit])))

(reg-sub
 :entry-form-all
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key])))

(reg-sub
 :entry-form-data-ex
 (fn [db _query-vec]
   (get-form-data db)))

;; Gets the only section data
(reg-sub
 :entry-form-section-data
 :<- [:entry-form-data-ex]
 (fn [data [_query-id section]]
   (get-in data [:section-fields section])))

;; Gets a :data level field value
;; Arg 'fields' may be a vector if we need to get values for more than one field
;; Returns a single value or a map
(reg-sub
 :entry-form-data-fields
 :<- [:entry-form-data-ex]
 (fn [data [_query-id fields]]
   (if-not (vector? fields)
     ;; fields is a single field name
     (get data fields)
     ;; a vector field names
     (select-keys data fields))))

;; Gets the value of a field at top level 'entry-form' itself
(reg-sub
 :entry-form-field
 :<- [:entry-form-all]
 (fn [form-db [_query-id field]]
   ;;(println "form-db called... " form-db)
   (get form-db field)))

;; Replace this with the above generic sub 'entry-form-field'
(reg-sub
 :entry-form-showing-ex
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key :showing])))

(reg-sub
 :entry-form-field-in-visibile-list
 (fn [db [_query-id key]]
   (contains-val? (get-in-key-db db [entry-form-key :visibility-list]) key)))

(reg-sub
 :modified-ex
 (fn [db _query-vec]
   (let [undo-data (get-in-key-db db [entry-form-key :undo-data])
         data (get-form-data db)]
     (if (and (seq undo-data) (not= undo-data data))
       true
       false))))

(reg-sub
 :entry-form-ex/favorites-status
 :<- [:entry-form-data-ex]
 (fn [data _query-vec]
   (contains-val? (:tags data) Favorites)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Attachments ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn view-attachment
  "Calls the back end to save the attachment as temp file and lauch the system viewer
   The args name is the attachment name and attachment-hash is the hash of the atatchment content as string
  "
  [name attachment-hash]
  (dispatch [:attachment-view-start name attachment-hash]))

(defn attachment-name-changed [attachment-hash name]
  (dispatch [:attachment-name-changed attachment-hash name]))

(defn save-attachment [file-name attachment-hash]
  (dispatch [:common/save-file-dialog file-name
             (fn [full-file-name]
               (when-not (nil? full-file-name)
                 (dispatch [:save-attachment-file-selected full-file-name attachment-hash])))]))

(defn upload-attachment-start []
  (cmn-events/open-file-explorer-on-click :attachment-upload-start))

(defn attachment-delete-confirm-dialog-open [attachment-hash]
  (dispatch [:attachment-delete-confirm-dialog-open attachment-hash]))

(defn attachment-delete-dialog-ok []
  (dispatch [:attachment-delete-dialog-ok]))

(defn attachment-delete-dialog-close []
  (dispatch [:attachment-delete-dialog-close]))

(defn attachments
  "Gets an atom which on deref gives a map as in BinaryKeyValue struct"
  []
  (subscribe [:attachments]))

(defn attachment-delete-dialog-data []
  (subscribe [:attachment-delete-dialog-data]))

(reg-event-fx
 :save-attachment-file-selected
 (fn [{:keys [db]} [_event-id full-file-name attachment-hash]]
   ;; Side effect call
   (bg/save-attachment-as (active-db-key db)
                          full-file-name
                          attachment-hash
                          (fn [api-response]
                            (when-not (on-error api-response)
                              (dispatch [:common/message-snackbar-open "Attachment saved to the selected file"]))))
   {}))

(reg-event-fx
 :attachment-view-start
 (fn [{:keys [db]} [_event-id name attachment-hash]]
   {:fx [[:bg-save-attachment-as-temp-file [(active-db-key db) name attachment-hash]]]}))

(reg-fx
 :bg-save-attachment-as-temp-file
 (fn [[db-key name attachment-hash]]
   (bg/save-attachment-as-temp-file db-key name attachment-hash
                                    (fn [api-response]
                                      (when-let [temp-file-name (check-error api-response)]
                                        (dispatch [:common/message-snackbar-open "Lauching the system viewer"])
                                        (bg/open-file temp-file-name
                                                      (fn [api-response]
                                                        (on-error api-response))))))))

;; Called with the user selected full file name
(reg-event-fx
 :attachment-upload-start
 (fn [{:keys [db]} [_event-id full-file-name]]
   ;; Side effect
   (bg/upload-attachment (active-db-key db) full-file-name
                         (fn [api-reponse]
                           ;;Response has AttachmentUploadInfo on success 
                           (when-let [attachment-upload-info (check-error api-reponse)]
                             (dispatch [:attachment-uploaded attachment-upload-info]))))
   {}))

;; Handle the attachment upload
;; This will attach the uploaded file content with the currrent entry form
#_(reg-event-fx
   :attachment-uploaded
   (fn [{:keys [db]} [_event-id {:keys [data-hash data-size name]}]]
     (let [attachments (get-in-key-db db [entry-form-key :data :binary-key-values])
           bkv {:data-hash data-hash :data-size data-size :key name :value "" :index_ref 0}
           updated (conj attachments bkv)]
       {:db (-> db (assoc-in-key-db [entry-form-key :data :binary-key-values] updated))})))

(reg-event-fx
 :attachment-uploaded
 (fn [{:keys [db]} [_event-id {:keys [data-hash data-size name]}]]
   (let [attachments (get-in-key-db db [entry-form-key :data :binary-key-values])
         bkv {:data-hash data-hash :data-size data-size :key name :value "" :index_ref 0}
         existing (filterv (fn [{:keys [data-hash key]}]
                             (if (and (= data-hash data-hash) (= key name)) true false)) attachments)
         existing (boolean (seq existing))]
     (if existing
       {:fx [[:dispatch [:common/message-box-show "Upload status" "The uploaded attachment is the same as the existing one"]]]}
       {:db (-> db (assoc-in-key-db [entry-form-key :data :binary-key-values] (conj attachments bkv)))}))))

(reg-event-fx
 :attachment-name-changed
 (fn [{:keys [db]} [_event-id attachment-hash name]]
   (let [attachments (get-in-key-db db [entry-form-key :data :binary-key-values])
         updated (mapv (fn [{:keys [data-hash] :as m}]
                         (if (= attachment-hash data-hash)
                           (assoc m :key name) m)) attachments)]
     {:db (-> db (assoc-in-key-db [entry-form-key :data :binary-key-values] updated))})))

(reg-event-db
 :attachment-delete-confirm-dialog-open
 (fn [db [_event-id attachment-hash]]
   (-> db (assoc-in-key-db [entry-form-key :attachment-delete-dialog]
                           {:dialog-show true
                            :attachment-hash attachment-hash}))))

(reg-event-db
 :attachment-delete-dialog-close
 (fn [db [_event-id]]
   (-> db (assoc-in-key-db [entry-form-key :attachment-delete-dialog] {:dialog-show false}))))

(reg-event-fx
 :attachment-delete-dialog-ok
 (fn [{:keys [db]} [_event-id]]
   (let [attachment-hash (get-in-key-db db [entry-form-key :attachment-delete-dialog :attachment-hash])
         attachments (get-in-key-db db [entry-form-key :data :binary-key-values])
         updated (filterv (fn [{:keys [data-hash] :as m}]
                            (if (= attachment-hash data-hash)
                              false true)) attachments)]
     {:db (-> db (assoc-in-key-db [entry-form-key :data :binary-key-values] updated))
      :fx [[:dispatch [:attachment-delete-dialog-close]]]})))

(reg-sub
 :attachment-delete-dialog-data
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key :attachment-delete-dialog])))

(reg-sub
 :attachments
 :<- [:entry-form-data-ex]
 (fn [data _query-vec]
   ;;binary-key-values is a vec of map of type 
   ;; {:data-hash "4952851536318644461" 
   ;;  :data-size 500 
   ;;  :index-ref 0 
   ;;  :key "Welcome Scan.jpg" :value ""}
   ;; In case of attachment the 'key' has the file name and the 'value' field is empty
   ;; But to reuse the custom "text-field" which expects the lable name in 'key' and field value in 'value'
   ;; we need to form a new map for that
   (mapv (fn [{:keys [key data-size] :as m}]
           (assoc m :key "Name" :value key :data-size data-size #_(if (= 1 data-size) 0 data-size))) (:binary-key-values data))
   #_(mapv (fn [{:keys [key]}] {:key "Name" :value key}) (:binary-key-values data))))

;;;;;;;;;;;;;;;;;;;;;;; Section name add/modify ;;;;;;;;;;;;;;;;;;;;;;;;

;;;; 
;; Note: popper-anchor-el was used for mui-popper in 'add-modify-section-field-popper'
;; add-modify-section-field-popper use is replaced with 'add-modify-section-field-dialog' 
;; popper-anchor-el is not used to anchor dialog and nil can be passed

(defn open-section-name-dialog [popper-anchor-el]
  (dispatch [:section-name-dialog-open  popper-anchor-el]))

(defn open-section-name-modify-dialog [section-name popper-anchor-el]
  ;;(println "open-section-name-modify-dialog called with " section-name popper-anchor-el)
  (dispatch [:section-name-modify-dialog-open section-name popper-anchor-el]))

(defn section-name-dialog-update [kw value]
  (dispatch [:section-name-dialog-update kw value]))

(defn section-name-add-modify [dialog-data]
  (dispatch [:section-name-add-modify dialog-data]))

(defn section-name-dialog-data []
  (subscribe [:section-name-dialog-data]))

(def section-name-dialog-init-data {:dialog-show false
                                    :popper-anchor-el nil
                                    :section-name nil
                                    :error-fields {}  ;; key is the section-name  
                                    :mode :add ;; or :modify
                                    :current-section-name nil})

(defn- to-section-name-dialog-data [db & {:as kws}]
  ;;(println "kws are " kws)
  (let [data (get-in-key-db db [entry-form-key :section-name-dialog-data])
        data (merge data kws)]
    (assoc-in-key-db db [entry-form-key :section-name-dialog-data] data)))

(defn- init-section-name-dialog-data [db]
  (assoc-in-key-db db [entry-form-key :section-name-dialog-data] section-name-dialog-init-data))

(reg-event-db
 :section-name-dialog-open
 (fn [db [_event-id  popper-anchor-el]]
   ;;(println "Event section-field-dialog-open called with popper-anchor-el "  popper-anchor-el )
   (-> db
       (init-section-name-dialog-data)
       (to-section-name-dialog-data :dialog-show true
                                    ;;:section-name section-name
                                    :popper-anchor-el popper-anchor-el))))

(reg-event-db
 :section-name-modify-dialog-open
 (fn [db [_event-id section-name popper-anchor-el]]
   (-> db
       (init-section-name-dialog-data)
       (to-section-name-dialog-data :dialog-show true
                                    :section-name section-name
                                    :mode :modify
                                    :popper-anchor-el popper-anchor-el
                                    :current-section-name section-name))))

(reg-event-db
 :section-name-dialog-update
 (fn [db [_event-id section-kw value]]
   (-> db
       (to-section-name-dialog-data section-kw value))))

(defn- modify-section-name [app-db {:keys [current-section-name section-name]}]
  (let [section-names (get-in-key-db app-db [entry-form-key :data :section-names])
        section-names (mapv
                       (fn [n]
                         (if (= n current-section-name) section-name n))
                       section-names)
        section-fields (get-in-key-db
                        app-db
                        [entry-form-key :data :section-fields]) ;; This is a map

        section-fields  (into {}
                              (map
                               (fn [[k v]]
                                 (if (= k current-section-name) [section-name v] [k v]))
                               section-fields))]

    (-> app-db
        (assoc-in-key-db [entry-form-key :data :section-names] section-names)
        (assoc-in-key-db [entry-form-key :data :section-fields] section-fields))))

;; Called for both add or modify
(reg-event-fx
 :section-name-add-modify
 (fn [{:keys [db]} [_event-id {:keys [section-name current-section-name mode] :as m}]]
   (if (or (str/blank? section-name) (and (= section-name current-section-name) (= mode :modify)))
     {:db (-> db (to-section-name-dialog-data :dialog-show false))}
     (let [section-names (get-in-key-db db [entry-form-key :data :section-names])
           ;; Need to ensure that section names are unique as case insensitive manner
           section-names-upper (map (fn [sn] (str/upper-case sn)) section-names)
           found (contains-val? section-names-upper (str/upper-case section-name))]
       (if found
         {:db (-> db (to-section-name-dialog-data
                      :error-fields
                      {:section-name "The name is not unique in this form"}
                      :section-name section-name))}
         (if (= mode :add)
           {:db (-> db
                    (assoc-in-key-db  [entry-form-key :data :section-names] (conj section-names section-name))
                    (to-section-name-dialog-data :dialog-show false))}
           {:db (-> db (modify-section-name m)
                    (to-section-name-dialog-data :dialog-show false))}))))))

(reg-sub
 :section-name-dialog-data
 (fn [db [_query-id]]
   (get-in-key-db db [entry-form-key :section-name-dialog-data])))

;;;;;;;;;;;;;;;;;;;;;    Section delete   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

;;  Here we are removing/deleting a custom section and its fields during edit on confirming


(defn section-delete [section-name]
  (dispatch [:section-delete section-name]))

(defn section-delete-confirm [yes?]
  (dispatch [:section-delete-confirm yes?]))

(defn section-delete-dialog-data []
  (subscribe [:section-delete-dialog-data]))

(reg-event-db
 :section-delete
 (fn [db [_event-id section-name]]
   (assoc-in-key-db db [entry-form-key :section-delete-dialog-data]
                    {:dialog-show true
                     :section-name section-name})))


(defn- delete-section [app-db section-name]
  (let [section-fields (get-in-key-db app-db [entry-form-key :data :section-fields])
        section-fields (into {} (filter #(not= (key %) section-name) section-fields))
        section-names (get-in-key-db app-db [entry-form-key :data :section-names])
        section-names (filterv #(not= % section-name) section-names)]
    (-> app-db (assoc-in-key-db [entry-form-key :data :section-fields] section-fields)
        (assoc-in-key-db [entry-form-key :data :section-names] section-names))))

(reg-event-db
 :section-delete-confirm
 (fn [db [_event-id yes?]]
   (if yes?
     (let [section-name (get-in-key-db db [entry-form-key :section-delete-dialog-data :section-name])]
       (-> db (delete-section section-name)
           (assoc-in-key-db [entry-form-key :section-delete-dialog-data]
                            {:dialog-show false :section-name nil})))

     (assoc-in-key-db db [entry-form-key :section-delete-dialog-data]
                      {:dialog-show false :section-name nil}))))

(reg-sub
 :section-delete-dialog-data
 (fn [db [_query-id]]
   (get-in-key-db db [entry-form-key :section-delete-dialog-data])))

;;;;;;;;;;;;;;;;;;;;;    Section Field Add/Modify    ;;;;;;;;;;;;;;;;;;;; 

(def field-edit-dialog-key :section-field-dialog-data)

(defn open-section-field-dialog [section-name popper-anchor-el]
  (dispatch [:section-field-dialog-open section-name popper-anchor-el]))

(defn open-section-field-modify-dialog
  "kv is a map with the existing field data"
  [kv]
  (dispatch [:section-field-modify-dialog-open kv]))

(defn close-section-field-dialog []
  (dispatch [:section-field-dialog-update :dialog-show false]))

(defn section-field-add
  "Receives a map and dispatches that to the add event"
  [field-data-m]
  (dispatch [:section-field-add field-data-m]))

(defn section-field-modify
  "Receives a map and dispatches that to the modify event"
  [field-data-m]
  (dispatch [:section-field-modify field-data-m]))

(defn section-field-dialog-update [field-name-kw value]
  (dispatch [:section-field-dialog-update field-name-kw value]))

(defn section-field-dialog-data []
  (subscribe [:section-field-dialog-data]))

(def section-field-dialog-init-data {:dialog-show false
                                     :popper-anchor-el nil
                                     :section-name nil
                                     :field-name nil
                                     :field-value nil
                                     :protected false
                                     :required false
                                     :error-fields {}  ;; key is the field-name 
                                     :add-more false
                                     :mode :add ;; or :modify
                                     :current-field-name nil
                                     :data-type "Text"})

#_(defn- to-section-field-data [db kw value]
    (assoc-in-key-db db [entry-form-key field-edit-dialog-key kw] value))

(defn- to-section-field-data [db & {:as kws}]
  ;;(println "kws are " kws)
  (let [data (get-in-key-db db [entry-form-key field-edit-dialog-key])
        data (merge data kws)]
    (assoc-in-key-db db [entry-form-key field-edit-dialog-key] data)))

(defn- init-section-field-dialog-data [db]
  (assoc-in-key-db db [entry-form-key field-edit-dialog-key] section-field-dialog-init-data))

(defn- modify-section-field [app-db {:keys [section-name
                                            current-field-name
                                            field-name
                                            required
                                            protected]}]
  (let [section-fields-m (get-in-key-db
                          app-db
                          [entry-form-key :data :section-fields]) ;; This is a map
        ;; fields is vector of KVs
        fields (get section-fields-m section-name)
        fields (mapv (fn [m] (if (= (:key m) current-field-name) (assoc m
                                                                        :key field-name
                                                                        :protected protected
                                                                        :required required) m)) fields)
        section-fields-m (assoc section-fields-m section-name fields)]
    (assoc-in-key-db app-db [entry-form-key :data :section-fields] section-fields-m)))

(reg-event-db
 :section-field-dialog-open
 (fn [db [_event-id section-name popper-anchor-el]]
   ;;(println "Event section-field-dialog-open called with popper-anchor-el "  popper-anchor-el )
   (-> db
       (init-section-field-dialog-data)
       (to-section-field-data :dialog-show true
                              :section-name section-name
                              :popper-anchor-el popper-anchor-el)
       ;;  (to-section-field-data :dialog-show true)
       ;;  (to-section-field-data :section-name section-name)
       ;;  (to-section-field-data :popper-anchor-el popper-anchor-el)
       )))

(reg-event-db
 :section-field-modify-dialog-open
 (fn [db [_event-id {:keys [key section-name protected required popper-anchor-el]}]]
   (-> db
       (init-section-field-dialog-data)
       (to-section-field-data :popper-anchor-el popper-anchor-el
                              :section-name section-name
                              :mode :modify
                              :field-name key
                              :current-field-name key
                              :protected protected
                              :required required
                              :dialog-show true)
       ;;  (to-section-field-data :popper-anchor-el popper-anchor-el)
       ;;  (to-section-field-data :section-name section-name)
       ;;  (to-section-field-data :mode :modify)
       ;;  (to-section-field-data :field-name key)
       ;;  (to-section-field-data :current-field-name key)
       ;;  (to-section-field-data :protected protected)
       ;;  (to-section-field-data :dialog-show true)
       )))

(reg-event-db
 :section-field-dialog-update
 (fn [db [_event-id field-name-kw value]]
   (if (and (= field-name-kw :dialog-show) (not value))
     (init-section-field-dialog-data db)
     (-> db
         (to-section-field-data field-name-kw value)))))

(reg-event-fx
 :section-field-add
 (fn [{:keys [db]} [_event-id {:keys [section-name field-name] :as m}]] ;; field-value protected
   (if-not (str/blank? field-name)
     (if (is-field-exist db field-name)
       {:db (to-section-field-data db :error-fields {field-name (str "Field with name " field-name " already exists in this form")})}
       {:db (-> db (add-section-field  m)
                (init-section-field-dialog-data)
                (to-section-field-data :section-name section-name)
                (to-section-field-data :dialog-show true) ;; continue to show dialog 
                (to-section-field-data :add-more true))})
     {:db (to-section-field-data db :dialog-show false)})))

(reg-event-fx
 :section-field-modify
 (fn [{:keys [db]} [_event-id {:keys [current-field-name field-name] :as m}]] ;; field-value protected
   (if-not (str/blank? field-name)
     (if (and (not= current-field-name field-name) (is-field-exist db field-name))
       {:db (to-section-field-data db :error-fields {field-name (str "Field with name " field-name " already exists in this form")})}
       {:db (-> db (modify-section-field m)
                (to-section-field-data :dialog-show false))})
     {:db (to-section-field-data db :dialog-show false)})))

(reg-sub
 :section-field-dialog-data
 (fn [db [_query-id]]
   (get-in-key-db db [entry-form-key field-edit-dialog-key])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Section field delete ;;;;;;;;;;;;;;;;;;;;;;;;

(defn field-delete [section-name field-name-kw]
  (dispatch [:field-delete section-name field-name-kw]))

(defn field-delete-confirm [yes?]
  (dispatch [:field-delete-confirm yes?]))

(defn field-delete-dialog-data []
  (subscribe [:field-delete-dialog-data]))

(reg-event-db
 :field-delete
 (fn [db [_event-id section-name field-name-kw]]
   (assoc-in-key-db db [entry-form-key :field-delete-dialog-data] {:dialog-show true
                                                                   :section-name section-name
                                                                   :field-name field-name-kw})))

(defn- delete-section-field
  "Deletes a field in a section. 
  Returns the updated app-db
  "
  [app-db section-name field-name]
  (let [section-fields (get-in-key-db
                        app-db
                        [entry-form-key :data :section-fields])
        ;;kvs (get section-fields section-name)
        ;;kvs (filterv (fn[m] (not= field-name (:key m))) kvs)
        kvs (->> (get section-fields section-name)
                 (filterv (fn [m] (not= field-name (:key m)))))
        section-fields (assoc section-fields section-name kvs)]
    (assoc-in-key-db app-db [entry-form-key :data :section-fields] section-fields)))

(reg-event-db
 :field-delete-confirm
 (fn [db [_event-id yes?]]
   (if yes?
     (let [section-name (get-in-key-db db [entry-form-key :field-delete-dialog-data :section-name])
           field-name (get-in-key-db db [entry-form-key :field-delete-dialog-data :field-name])]
       (-> db (delete-section-field section-name field-name)
           (assoc-in-key-db [entry-form-key :field-delete-dialog-data]
                            {:dialog-show false :section-name nil :field-name nil})))

     (assoc-in-key-db db [entry-form-key :field-delete-dialog-data]
                      {:dialog-show false :section-name nil :field-name nil}))))

(reg-sub
 :field-delete-dialog-data
 (fn [db [_query-id]]
   (get-in-key-db db [entry-form-key :field-delete-dialog-data])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;    Custom Entry Type ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn new-custom-entry-type
  "Called to create a blank entry type form for the user to create a custom type"
  []
  (dispatch [:new-custom-entry-type]))

(defn cancel-new-custom-entry-type []
  (dispatch [:cancel-new-custom-entry-type]))

;; A map to match the struct EntryTypeFormData
(def entry-type-data {:entry-type-name nil
                      :entry-type-icon-name nil
                      :section-fields {}
                      :section-names []})

(defn create-custom-entry-type
  []
  (dispatch [:create-custom-entry-type]))

(defn delete-custom-entry-type [entry-type-uuid]
  (dispatch [:entry-form-ex/delete-custom-entry-type entry-type-uuid]))

;; This event will create a blank entry-type-data and is used in the 
;; entry form's data field itself
(reg-event-fx
 :new-custom-entry-type
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db
            (assoc-in-key-db [entry-form-key :undo-data]
                             (get-form-data db))
            (assoc-in-key-db [entry-form-key :data] entry-type-data)
            (assoc-in-key-db [entry-form-key :error-fields] {})
            (assoc-in-key-db [entry-form-key :showing] :custom-entry-type-new))}))

(reg-event-db
 :cancel-new-custom-entry-type
 (fn [db [_event-id]]
   (-> db
       (assoc-in-key-db  [entry-form-key :data]
                         (get-in-key-db db [entry-form-key :undo-data]))
       (assoc-in-key-db [entry-form-key :showing] :new))))

(defn validate-entry-type-form-data
  "Verifies that the user has entered valid values in custom entry type form
  Returns a map of fileds with errors and error-fields will be {} in case no error is found
  "
  [{:keys [entry-type-name section-names section-fields]}]
  (let [error-fields (cond-> {}
                       (empty? section-names)
                       (assoc :general "At least one section is required")

                       (and (boolean (seq section-names)) (or (empty? section-fields) (some #(empty? %) (vals section-fields))))
                       #_(and (boolean (seq section-names)) (some #(empty? %) (vals section-fields)))
                       (assoc :general "At least one field is required in each section")

                       (str/blank? entry-type-name)
                       (assoc :entry-type-name "Please enter a valid entry type name"))]
    error-fields))

(reg-event-fx
 :create-custom-entry-type
 (fn [{:keys [db]} [_event-id]]
   (let [entry-type-form-data (get-form-data db)
         error-fields (validate-entry-type-form-data entry-type-form-data)]
     (if (boolean (seq error-fields))
       {:db (assoc-in-key-db db [entry-form-key :error-fields] error-fields)}
       {:fx [[:bg-insert-or-update-custom-entry-type [(active-db-key db) entry-type-form-data]]]}))))

(reg-fx
 :bg-insert-or-update-custom-entry-type
 (fn [[db-key entry-type-form-data]]
   (bg/insert-or-update-custom-entry-type
    db-key
    entry-type-form-data
    (fn [api-response]
      #_(println "bg/insert-or-update-custom-entry-type api-response " api-response)
      (when-let [entry-type-uuid (check-error api-response)]
        (dispatch [:create-custom-entry-type-completed entry-type-uuid]))))))

(reg-event-fx
 :create-custom-entry-type-completed
 (fn [{:keys [db]} [_event-id entry-type-uuid]]
   ;; Need to first refresh entry type names list and we create a new form
   ;; again with the new custom entry type as selected
   {:fx [[:dispatch [:common/load-entry-type-headers]]
         ;; A New Entry form with the newly created entry name is shown 
         [:dispatch [:entry-form-entry-type-uuid-selected-ex entry-type-uuid]]
         ;; Need to refresh entry-category as we have added a new custom entry type
         [:dispatch [:entry-category/reload-category-data]]
         [:dispatch [:common/message-snackbar-open "New custom entry type is created"]]]}))


;; Called to delete a custom entry type from entry category view
(reg-event-fx
 :entry-form-ex/delete-custom-entry-type
 (fn [{:keys [db]} [_event-id entry-type-uuid]]
   ;; Call the backend api to delete this custom entry type permanently
   (bg/delete-custom-entry-type (active-db-key db) entry-type-uuid
                                (fn [api-response]
                                  ;; deleted-entry-type-header is a map from EntryTypeHeader
                                  (when-let [deleted-entry-type-header
                                             (check-error api-response
                                                          #(dispatch [:delete-custom-entry-type-error %]))]
                                    (dispatch [:delete-custom-entry-type-completed (:name deleted-entry-type-header)]))))
   {}))

(reg-event-fx
 :delete-custom-entry-type-completed
 (fn [{:keys [db]} [_event-id entry-type-name]]
   {:fx [[:dispatch [:common/message-snackbar-open
                     (str "Custom entry type " entry-type-name " is deleted")]]
         [:dispatch [:common/load-entry-type-headers]]
         #_[:dispatch [:common/load-entry-type-names]]
         [:dispatch [:entry-category/entry-type-deleted]]]}))

;; "There are some entries found either in trash or in "
(reg-event-fx
 :delete-custom-entry-type-error
 (fn [{:keys [_db]} [_event-id api-error]]
   ;;(println "delete-custom-entry-type-error is " api-error)
   (let [api-error-text  (if (= api-error "CustomEntryTypeInUse")
                           "Please first delete all entries that of this type and then deleting permanently from trash"
                           "Custom entry type delete failed")]
     {:fx [[:dispatch [:common/message-snackbar-error-open api-error-text]]]})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   New Entry ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Creates a blank New Entry Form with the given type and group are preselected 
(reg-event-fx
 :entry-form-ex/add-new-entry
 (fn [{:keys [db]} [_event-id group-info entry-type-uuid]]
   ;;(println "group-info entry-type-name " group-info entry-type-name)
   {:fx [[:dispatch [:entry-list/update-selected-entry-id nil]]
         [:bg-new-entry-form-data [(active-db-key db) group-info entry-type-uuid]]]}))

;; Backend API call 
(reg-fx
 :bg-new-entry-form-data
 ;; fn in 'reg-fx' accepts single argument - vector arg typically 
 ;; used so that we can pass more than one input
 (fn [[db-key group-info entry-type-uuid]]
   (bg/new-entry-form-data db-key entry-type-uuid (fn [api-response]
                                                    (when-let [form-data (check-error api-response)]
                                                      (dispatch [:new-blank-entry-created-ex form-data group-info]))))))

;; Called with the result from the background API call which returns
;; a blank entry map that can be used to create a new Entry 
(reg-event-db
 :new-blank-entry-created-ex
 (fn [db [_ form-data group-info]]
   (let [form-data (assoc form-data :group-uuid (:uuid group-info)) ;; set the group uuid 
         ]
     (-> db (assoc-in-key-db [entry-form-key :data] form-data)
         (init-expiry-duration-selection form-data)
         (assoc-in-key-db [entry-form-key :showing] :new)
         #_(assoc-in-key-db [entry-form-key :entry-type-name-selection] (:entry-type-name form-data))
         (assoc-in-key-db [entry-form-key :group-selection-info] group-info)
         (assoc-in-key-db [entry-form-key :edit] true)
         (assoc-in-key-db [entry-form-key :error-fields] {})))))

;; This event is dispatched when a new group option is selected in the new entry form
(reg-event-db
 :entry-form-group-selected-ex
 (fn [db [_event-id group-info]]
   ;;(println "Calling with group in event " group-info)
   (-> db (assoc-in-key-db  [entry-form-key :group-selection-info] group-info)
       ;; IMPORTANT: Set the entry form data's group uuid. 
       (assoc-in-key-db  [entry-form-key :data :group-uuid] (:uuid group-info)))))

;; Shows a blank New Entry Form with the selected entry name
#_(reg-event-fx
   :entry-form-entry-type-name-selected-ex
   (fn [{:keys [db]} [_event-id entry-type-name]]
     (let [group-info (get-in-key-db db [entry-form-key :group-selection-info])]
       {:fx [[:dispatch [:entry-form-ex/add-new-entry group-info entry-type-name]]
             #_[:dispatch [:entry-category/new-entry-form-entry-type-selected entry-type-name]]]}) ;;entry-form-ex/add-new-entry
     #_{:db (assoc-in-key-db db [entry-form-key :entry-type-name-selection] name)}))

(reg-event-fx
 :entry-form-entry-type-uuid-selected-ex
 (fn [{:keys [db]} [_event-id entry-type-uuid]]
   (let [group-info (get-in-key-db db [entry-form-key :group-selection-info])]
     {:fx [[:dispatch [:entry-form-ex/add-new-entry group-info entry-type-uuid]]]})))

(reg-event-fx
 :ok-new-entry-add-ex
 (fn [{:keys [db]} [_event-id]]
   (let [form-data (get-form-data db)
         error-fields (validate-all form-data)
         errors-found (boolean (seq error-fields))]
     (if errors-found
       {:db (assoc-in-key-db db [entry-form-key :error-fields] error-fields)}
       {:fx [[:bg-insert-entry [(active-db-key db) form-data]]]}))))

(reg-fx
 :bg-insert-entry
 (fn [[db-key {:keys [uuid group-uuid entry-type-uuid entry-type-name]
               :as new-entry-form-data}]]
   (bg/insert-entry db-key
                    new-entry-form-data
                    (fn [api-response]
                      (when-not (on-error api-response)
                        (dispatch [:insert-entry-form-data-complete uuid group-uuid entry-type-uuid entry-type-name]))))))

(reg-event-fx
 :insert-entry-form-data-complete
 (fn [{:keys [_db]} [_event-id entry-uuid group-uuid entry-type-uuid entry-type-name]]
   ;;(println "insert-entry-form-data-complete called")
   {:fx [[:dispatch [:entry-form-ex/show-welcome]]
         ;; Furthur refreshing view event are delegated to entry-category/entry-inserted 
         ;; which in turn calls entry-list/entry-inserted, group-tree-content/entry-inserted
         [:dispatch [:entry-category/entry-inserted entry-uuid group-uuid entry-type-uuid entry-type-name]]
         [:dispatch [:common/message-snackbar-open (lstr-sm 'entryCreated)]]]}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  Entry History  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-history-index-selection [entry-uuid idx]
  (dispatch [:update-history-index-selection-ex entry-uuid idx]))

(defn load-history-entries-summary [entry-uuid]
  (dispatch [:load-history-entries-summary entry-uuid]))

(defn history-content-close [entry-uuid]
  (dispatch [:history-content-close entry-uuid]))

(defn close-restore-confirm-dialog []
  (dispatch [:update-restore-confirm-open-ex false]))

(defn show-restore-confirm-dialog []
  (dispatch [:update-restore-confirm-open-ex true]))

(defn show-delete-confirm-dialog []
  (dispatch [:delete-confirm-open-ex true]))

(defn close-delete-confirm-dialog []
  (dispatch [:delete-confirm-open-ex false]))

(defn restore-entry-from-history
  []
  (dispatch [:restore-entry-from-history-ex]))

(defn delete-history-entry-by-index [entry-uuid idx]
  (dispatch [:delete-history-entry-by-index entry-uuid idx]))

(defn delete-all-history-entries [entry-uuid]
  (dispatch [:delete-all-history-entries entry-uuid]))

(defn show-delete-all-confirm-dialog []
  (dispatch [:delete-all-confirm-open-ex true]))

(defn close-delete-all-confirm-dialog []
  (dispatch [:delete-all-confirm-open-ex false]))

(defn history-available []
  (subscribe [:history-available-ex]))

(defn selected-history-index
  "The index of history entry user clicked"
  []
  (subscribe [:selected-history-index-ex]))

(defn history-summary-list
  "Gets history entries list data"
  []
  (subscribe [:history-entries-summary-list]))

(defn restore-flag []
  (subscribe [:restore-flag]))

(defn delete-flag []
  (subscribe [:delete-flag]))

(defn delete-all-flag []
  (subscribe [:delete-all-flag]))

(defn loaded-history-entry-uuid []
  (subscribe [:loaded-history-entry-uuid]))

(defn history-entry-form-showing
  "Returns true if the entry form is showing a history content"
  []
  (subscribe [:history-entry-form-showing]))

(reg-event-fx
 :load-history-entries-summary
 (fn [{:keys [db]} [_event-id entry-uuid]]
   (bg/history-entries-summary (active-db-key db)
                               entry-uuid
                               (fn [api-response]
                                 (when-let [summary-list (check-error api-response)]
                                   (dispatch [:load-history-entries-summary-complete summary-list]))))
   {}))

(reg-event-fx
 :load-history-entries-summary-complete
 (fn [{:keys [db]} [_event-id summary-list]]

   {:db (-> db
            (assoc-in-key-db [entry-form-key :entry-history-form] {})
            (assoc-in-key-db [entry-form-key :entry-history-form :entries-summary-list] summary-list))
    :fx [[:dispatch [:common/show-content :entry-history]]
         [:dispatch [:entry-form-ex/show-welcome "Showing history entries. Select one to see details"]]]}))

(reg-event-fx
 :update-history-index-selection-ex
 (fn [{:keys [db]} [_event-id entry-uuid idx]]
   ;;(println "idx is " idx)
   {:db (-> db
            (assoc-in-key-db  [entry-form-key :entry-history-form :entry-selected] true)
            (assoc-in-key-db  [entry-form-key :entry-history-form :selected-index] idx))

    :fx [[:dispatch [:find-history-entry-by-index entry-uuid idx]]]}))

(reg-event-fx
 :history-content-close
 (fn [{:keys [_db]} [_event-id entry-uuid]]
   {:fx [[:dispatch [:entry-form-ex/show-welcome]]
         [:dispatch [:common/show-content :group-entry]]
         [:dispatch [:entry-form-ex/find-entry-by-id entry-uuid]]]}))

(reg-event-fx
 :find-history-entry-by-index
 (fn [{:keys [db]} [_event-id entry-id index]]
   (bg/history-entry-by-index (active-db-key db) entry-id index
                              (fn [api-response]
                                (when-let [entry (check-error
                                                  api-response
                                                  #(dispatch [:history-entry-form-data-load-completed :error %]))]
                                  (dispatch [:history-entry-form-data-load-completed :ok entry]))))
   {}))

(reg-event-db
 :history-entry-form-data-load-completed
 (fn [db [_event-id status result]] ;;result is 
   ;;(println "history-entry-form load result is " result)
   (if (= status :ok)
     (-> db
         (assoc-in-key-db [entry-form-key :data] result)
         (init-expiry-duration-selection result)
         (assoc-in-key-db [entry-form-key :edit] false)
         (assoc-in-key-db [entry-form-key :error-fields] {})
         (assoc-in-key-db [entry-form-key :showing] :history-entry-selected)
         (assoc-in-key-db [entry-form-key :api-error-text] nil))
     (-> db (assoc-in-key-db [entry-form-key :api-error-text] result)))))

(reg-event-fx
 :delete-history-entry-by-index
 (fn [{:keys [db]} [_event-id entry-id index]]
   (bg/delete-history-entry-by-index (active-db-key db) entry-id index
                                     (fn [api-response]
                                       (when-not (on-error api-response)
                                         ;; Reload summary list again
                                         (dispatch [:load-history-entries-summary entry-id]))))
   {}))

(reg-event-fx
 :delete-all-history-entries
 (fn [{:keys [db]} [_event-id entry-id]]
   (bg/delete-history-entries (active-db-key db) entry-id
                              (fn [api-response]
                                (when-not (on-error api-response)
                                  (dispatch [:history-content-close entry-id]))))
   {}))

(reg-event-db
 :update-restore-confirm-open-ex
 (fn [db [_ open?]]
   (assoc-in-key-db db  [entry-form-key :entry-history-form :restore-flag] open?)))

(reg-event-fx
 :restore-entry-from-history-ex
 (fn [{:keys [db]} [_event-id]]
   ;; Update the entry with the selected version of history
   (update-entry db (fn [api-response]
                      (when-not (on-error api-response)
                        (dispatch [:restore-entry-from-histore-complete]))))
   {}))

(reg-event-db
 :delete-confirm-open-ex
 (fn [db [_ open?]]
   (assoc-in-key-db db  [entry-form-key :entry-history-form :delete-flag] open?)))

(reg-event-db
 :delete-all-confirm-open-ex
 (fn [db [_ open?]]
   (assoc-in-key-db db  [entry-form-key :entry-history-form :delete-all-flag] open?)))

(reg-event-fx
 :restore-entry-from-histore-complete
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:update-restore-confirm-open-ex false]]
         [:dispatch [:common/show-content :group-entry]]
         ;; Need to reload the newly upated entry
         ;; entry-list and entry-category selection remains the same
         [:dispatch [:entry-form-ex/find-entry-by-id
                     (get-in-key-db db [entry-form-key :data :uuid])]]]}))

(reg-sub
 :entry-history-form
 (fn [db _query-vec]
   (get-in-key-db db [entry-form-key :entry-history-form])))

(reg-sub
 :history-entries-summary-list
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:entries-summary-list history-form)))

(reg-sub
 :loaded-history-entry-uuid
 :<- [:history-entries-summary-list]
 (fn [list _query-vec]
   (:uuid (first list))))

(reg-sub
 :selected-history-index-ex
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:selected-index history-form)
   #_(get-in-key-db db [entry-form-key :entry-history-form :selected-index])))

(reg-sub
 :restore-flag
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:restore-flag history-form)))

(reg-sub
 :delete-flag
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:delete-flag history-form)))

(reg-sub
 :delete-all-flag
 :<- [:entry-history-form]
 (fn [history-form _query-vec]
   (:delete-all-flag history-form)))

(reg-sub
 :history-available-ex
 :<- [:entry-form-data-ex]
 (fn [data _query-vec]
   (> (:history-count data) 0)))

(reg-sub
 :history-entry-form-showing
 (fn [db _query_vec]
   (= :history-entry-selected (get-in-key-db db [entry-form-key :showing]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Entry delete ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;   
(defn deleted-category-showing []
  (subscribe [:entry-category/deleted-category-showing]))

(defn recycle-group-selected? []
  (subscribe [:group-tree-content/recycle-group-selected]))

(defn selected-group-in-recycle-bin? []
  (subscribe [:group-tree-content/selected-group-in-recycle-bin]))

(defn entry-delete-start [entry-id]
  (dispatch [:entry-delete-start entry-id]))

(defn entry-delete-info-dialog-close []
  (dispatch [:entry-delete-dialog-hide]))

(defn entry-delete-dialog-data []
  (subscribe [:entry-delete]))

(reg-event-db
 :entry-delete-dialog-hide
 (fn [db [_event-id]]
   (-> db (assoc-in-key-db [:entry-delete :dialog-show] false))))

(reg-event-fx
 :entry-delete-start
 (fn [{:keys [db]} [_event-id  entry-id]]
   {:db (-> db (assoc-in-key-db [:entry-delete :dialog-show] true)
            (assoc-in-key-db [:entry-delete :status] :in-progress))
    :fx [[:bg-move-entry-to-recycle-bin [(active-db-key db) entry-id]]]}))

(defn- on-entry-delete [api-response]
  (when (not (on-error api-response (fn [e]
                                      (dispatch [:entry-delete-error e]))))
    (dispatch [:entry-delete-completed])))

(reg-fx
 :bg-move-entry-to-recycle-bin
 (fn [[db-key entry-id]]
   (bg/move-entry-to-recycle_bin db-key entry-id on-entry-delete)))

(reg-event-db
 :entry-delete-error
 (fn [db [_event-id error-text]]
   (-> db (assoc-in-key-db [:entry-delete :api-error-text] error-text)
       (assoc-in-key-db [:entry-delete :status] :completed))))

(reg-event-fx
 :entry-delete-completed
 (fn [{:keys [db]} [_event-id]]
   {:db (-> db (assoc-in-key-db [:entry-delete :dialog-show] false)
            (assoc-in-key-db  [:entry-delete :status] :completed))
    ;; calls to refresh entry list and category  
    :fx [[:dispatch [:common/refresh-forms]]
         [:dispatch [:common/message-snackbar-open "Entry is deleted"]]]}))

(reg-sub
 :entry-delete
 (fn [db _query-vec]
   (get-in-key-db db [:entry-delete])))

;;;;;;;;;;;;;;; Auto type ;;;;;;;;;;;;;;;;;;;;;;

(defn perform-auto-type-start []
  (dispatch [:entry-perform-auto-type]))

(defn entry-auto-type-edit []
  (dispatch [:entry-auto-type-edit]))

(defn auto-type-perform-dialog-data []
  (subscribe [:auto-type/perform-dialog]))

;; auto-type is map from struct AutoType of EntryFormData as wells as Entry (for tag AutoType)
;; Sometime we use 'auto-type-m' for 'auto-type'
(reg-event-fx
 :entry-perform-auto-type
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [uuid auto-type]} (get-form-data db)]
     ;; Backend call to get the active to which we will be sending the sequence
     {:fx [[:auto-type/bg-active-window-to-auto-type [uuid auto-type]]]})))

(reg-event-fx
 :entry-auto-type-edit
 (fn [{:keys [db]} [_event-id]]
   (let [{:keys [uuid auto-type] :as form-data} (get-form-data db)
         entry-form-fields (extract-form-field-names-values form-data)]
     {:fx [[:dispatch [:auto-type/edit-init uuid auto-type entry-form-fields]]]})))

;;;;;;;;;;;;;;;;;;;;; Otp (TOPT) related ;;;;;;;;;;;

;; All entry-form otp related events are moved to the 
;; namespace onekeepass.frontend.events.entry-form-otp 
;; TODO: Need to do similar moving of other events - auto type, section adding, custom field adding etc  
;; from here to a separate namespace


(defn entry-form-delete-otp-field [section otp-field-name]
  (dispatch [:entry-form-delete-otp-field section otp-field-name]))

(defn entry-form-otp-start-polling []
  (dispatch [:entry-form-otp-start-polling]))

(defn entry-form-otp-stop-polling [entry-uuid]
  (dispatch [:entry-form-otp-stop-polling entry-uuid]))

(defn otp-currrent-token [opt-field-name]
  (subscribe [:otp-currrent-token opt-field-name]))

(comment
  (keys @re-frame.db/app-db)

  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)

  (-> (get @re-frame.db/app-db db-key) :entry-form-data) ;; for now

  ;; All entry map keys
  (-> (get @re-frame.db/app-db db-key) :entry-form-data keys)
  ;; => (:showing :group-selection-info :edit :expiry-duration-selection :welcome-text-to-show 
  ;;     :error-fields :entry-history-form :api-error-text :undo-data :data)

  ;; All entry form data keys
  (-> (get @re-frame.db/app-db db-key) :entry-form-data :data keys)
  ;; => :tags :icon-id :parsed-fields :binary-key-values :section-fields :title :expiry-time :history-count
  ;;    :expires :standard-section-names :last-modification-time :entry-type-name :auto-type
  ;;    :notes :section-names :entry-type-icon-name :last-access-time :uuid :entry-type-uuid :group-uuid :creation-time
  )

;;;;;;;;;;;;;;;;;;;;;    Custom Field Add Dialog     ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn open-custom-field-dialog []
;;   (dispatch [:custom-field-dialog-open]))

;; (defn oepn-custom-field-modify-dialog [kv]
;;   (dispatch [:custom-field-modify-dialog-open kv]))

;; (defn close-custom-field-dialog []
;;   (dispatch [:custom-field-dialog-update :dialog-show false]))

;; (defn custom-field-add
;;   "Receives a map and dispatches that to the add event"
;;   [field-data-m]
;;   (dispatch [:custom-field-add field-data-m]))

;; (defn custom-field-modify
;;   "Receives a map and dispatches that to the modify event"
;;   [field-data-m]
;;   (dispatch [:custom-field-modify field-data-m]))

;; (defn update-custom-field-dialog-data [field-name-kw value]
;;   (dispatch [:custom-field-dialog-update field-name-kw value]))

;; (defn custom-field-dialog-data []
;;   (subscribe [:custom-field-dialog-data]))

#_(defn- is-field-exist
    "Checks that a given field name exists in the entry form or not "
    [app-db field-name]
    (let [all-section-fields (-> (get-in-key-db
                                  app-db
                                  [entry-form-key :data :section-fields])
                                 vals flatten) ;;all-section-fields is a list of maps for all sections
          ;; _ (println "all-section-fields are " all-section-fields)
          ;;found  (filter (fn [m] (= field-name (:key m))) all-section-fields)
          ]
      (or (contains-val? standard-kv-fields field-name)
          (-> (filter (fn [m] (= field-name (:key m))) all-section-fields) seq boolean))))

;; (defn append-custom-to-section-names
;;   "Adds the custom field section name to the :section-names vec if required
;;   Returns the updated app-db
;;   "
;;   [app-db]
;;   (let [section-names (get-in-key-db app-db [entry-form-key :data :section-names])]
;;     (if (contains-val? section-names section-name-custom-fields)
;;       app-db
;;       (assoc-in-key-db app-db [entry-form-key :data :section-names]
;;                        (conj section-names section-name-custom-fields)))))

;; (defn- add-custom-field
;;   "Creates a new KV for the added custom field and updates the Custom Fields section
;;   Returns the updated app-db
;;   "
;;   [app-db {:keys [field-name field-value protected data-type]}]
;;   (println "data-type is " data-type)
;;   (let [section-fields (get-in-key-db
;;                         app-db
;;                         [entry-form-key :data :section-fields])
;;         custom-fields (-> section-fields (get section-name-custom-fields []))
;;         custom-fields (conj custom-fields {:key field-name
;;                                            :value field-value
;;                                            :protected protected
;;                                            :data-type data-type})]
;;     (assoc-in-key-db app-db [entry-form-key :data :section-fields] (assoc section-fields section-name-custom-fields custom-fields))))

;; (defn- modify-custom-field [app-db {:keys [current-field-name field-name  protected]}]
;;   (let [section-fields (get-in-key-db
;;                         app-db
;;                         [entry-form-key :data :section-fields])
;;         custom-fields (get section-fields section-name-custom-fields)
;;         custom-fields (mapv (fn [m] (if (= (:key m) current-field-name) (assoc m :key field-name :protected protected) m)) custom-fields)
;;         section-fields (assoc section-fields section-name-custom-fields custom-fields)]
;;     (assoc-in-key-db app-db [entry-form-key :data :section-fields] section-fields)))

;; (defn- delete-section-field
;;   "Deletes a field in a section. 
;;   Returns the updated app-db
;;   "
;;   [app-db section-name field-name]
;;   (let [section-fields (get-in-key-db
;;                         app-db
;;                         [entry-form-key :data :section-fields])
;;         ;;kvs (get section-fields section-name)
;;         ;;kvs (filterv (fn[m] (not= field-name (:key m))) kvs)
;;         kvs (->> (get section-fields section-name)
;;                  (filterv (fn [m] (not= field-name (:key m)))))
;;         section-fields (assoc section-fields section-name kvs)]
;;     (assoc-in-key-db app-db [entry-form-key :data :section-fields] section-fields)))

;; (defn- to-cust-data [db kw value]
;;   (assoc-in-key-db db [entry-form-key :custom-field-dialog-data  kw] value))

;; (def custom-field-dialog-init-data {:dialog-show false
;;                                     :field-name nil
;;                                     :field-value nil
;;                                     :protected false
;;                                     :error-fields {}  ;; key is the field-name 
;;                                     :add-more false
;;                                     :mode :add ;; or :modify
;;                                     :current-field-name nil
;;                                     :data-type "Text"})

;; (defn- init-custom-field-dialog-data [db]
;;   (assoc-in-key-db db [entry-form-key :custom-field-dialog-data] custom-field-dialog-init-data))

;; (reg-event-db
;;  :custom-field-dialog-open
;;  (fn [db [_event-id]]
;;    (-> db
;;        (init-custom-field-dialog-data)
;;        (to-cust-data :dialog-show true))))

;; (reg-event-db
;;  :custom-field-modify-dialog-open
;;  (fn [db [_event-id {:keys [key protected]}]]
;;    (-> db
;;        (init-custom-field-dialog-data)
;;        (to-cust-data :mode :modify)
;;        (to-cust-data :field-name key)
;;        (to-cust-data :current-field-name key)
;;        (to-cust-data :protected protected)
;;        (to-cust-data :dialog-show true))))

;; (reg-event-db
;;  :custom-field-dialog-update
;;  (fn [db [_event-id field-name-kw value]]
;;    (-> db
;;        (to-cust-data field-name-kw value))))

;; (reg-event-fx
;;  :custom-field-add
;;  (fn [{:keys [db]} [_event-id {:keys [field-name] :as m}]] ;; field-value protected
;;    (if (is-field-exist db field-name)
;;      {:db (to-cust-data db :error-fields {field-name (str "Field with name " field-name " already exists in this form")})}
;;      {:db (-> db (add-custom-field m)
;;               (append-custom-to-section-names) ;; Need to include 'Custom Fields' in section-names
;;               (init-custom-field-dialog-data)
;;               (to-cust-data :dialog-show true) ;; continue to show dialog
;;               (to-cust-data :add-more true))})))

;; (reg-event-fx
;;  :custom-field-modify
;;  (fn [{:keys [db]} [_event-id {:keys [current-field-name field-name] :as m}]] ;; field-value protected
;;    ;;(println "current-field-name field-name are " current-field-name field-name (not= current-field-name field-name))
;;    (if (and (not= current-field-name field-name) (is-field-exist db field-name))
;;      {:db (to-cust-data db :error-fields {field-name (str "Field with name " field-name " already exists in this form")})}
;;      {:db (-> db (modify-custom-field m)
;;               (to-cust-data :dialog-show false))})))

;; (defn field-delete [section-name field-name-kw]
;;   (dispatch [:field-delete section-name field-name-kw]))

;; (defn field-delete-confirm [yes?]
;;   (dispatch [:field-delete-confirm yes?]))

;; (defn field-delete-dialog-data []
;;   (subscribe [:field-delete-dialog-data]))

;; (reg-event-db
;;  :field-delete
;;  (fn [db [_event-id section-name field-name-kw]]
;;    (assoc-in-key-db db [entry-form-key :field-delete-dialog-data] {:dialog-show true
;;                                                                    :section-name section-name
;;                                                                    :field-name field-name-kw})))

;; (reg-event-db
;;  :field-delete-confirm
;;  (fn [db [_event-id yes?]]
;;    (if yes?
;;      (let [section-name (get-in-key-db db [entry-form-key :field-delete-dialog-data :section-name])
;;            field-name (get-in-key-db db [entry-form-key :field-delete-dialog-data :field-name])]
;;        (-> db (delete-section-field section-name field-name)
;;            (assoc-in-key-db [entry-form-key :field-delete-dialog-data]
;;                             {:dialog-show false :section-name nil :field-name nil})))

;;      (assoc-in-key-db db [entry-form-key :field-delete-dialog-data]
;;                       {:dialog-show false :section-name nil :field-name nil}))))

;; (reg-sub
;;  :field-delete-dialog-data
;;  (fn [db [_query-id]]
;;    (get-in-key-db db [entry-form-key :field-delete-dialog-data])))

;; (reg-sub
;;  :custom-field-dialog-data
;;  (fn [db [_query-id]]
;;    (get-in-key-db db [entry-form-key :custom-field-dialog-data])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;  Section name  old ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (defn section-add-name-update [section-name]
;;   (dispatch [:section-add-name-update section-name]))

;; (defn section-add-done []
;;   (dispatch [:section-add-done]))

;; (defn section-add-data []
;;   (subscribe [:section-add-data]))

;; (reg-event-db
;;  :section-add-name-update
;;  (fn [db [_event-id name]]
;;    (let [found (contains-val? (get-in-key-db db [entry-form-key :data :section-names]) name)]
;;      (if found
;;        (-> db
;;            (assoc-in-key-db [entry-form-key :section-add :section-name] name)
;;            (assoc-in-key-db  [entry-form-key :section-add :error-fields] {:section-name "The name is not unique in this form"}))
;;        (-> db
;;            (assoc-in-key-db [entry-form-key :section-add :section-name] name)
;;            (assoc-in-key-db  [entry-form-key :section-add :error-fields] {}))))))

;; (reg-event-fx
;;  :section-add-done
;;  (fn [{:keys [db]} [_query-id]]
;;    (let [section-names (get-in-key-db db [entry-form-key :data :section-names])
;;          section-name (get-in-key-db db [entry-form-key :section-add :section-name])]
;;      (if (str/blank? section-name)
;;        {}
;;        {:db (-> db
;;                 (assoc-in-key-db  [entry-form-key :data :section-names] (conj section-names section-name))
;;                 (assoc-in-key-db [entry-form-key :section-add] {}))}))))

;; (reg-sub
;;  :section-add-data
;;  (fn [db _query-vec]
;;    (get-in-key-db db [entry-form-key :section-add])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

