(ns onekeepass.frontend.background
  "All backend api calls"
  (:require
   [re-frame.core :refer [dispatch]]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]
   [onekeepass.frontend.utils :refer [contains-val?]]
   
   ["@tauri-apps/api/dialog" :refer (open,save)]
   ["@tauri-apps/api/tauri" :refer (invoke)]
   ["@tauri-apps/api/clipboard" :refer [writeText readText]]
   ["@tauri-apps/api/event" :as tauri-event]
   
   #_["@tauri-apps/api/path" :as tauri-path]
   #_["@tauri-apps/api/event" :as tauri-event :refer [listen]]
   #_["@tauri-apps/api/shell" :as tauri-shell]
   #_["@tauri-apps/api/app"   :refer (getName getVersion)]
   #_["@tauri-apps/api/window" :refer (getCurrent)]))

(set! *warn-on-infer* true)

(defn invoke-api
  "Invokes the backend command API calls using the tauri's 'invoke' command.
   Args 
    'name' is the tauri command name
    'api-args' is the argument map that is passed to the tauri command. The args must be serializable by the tauri API.
    'dispatch-fn' is a function that will be called when the tauri command call's promise is resolved or in error. 
     The call back function 'distach-fn' should accept a map (keys are  :result, :error) as input arg  

    IMPORTANT: If the returned value is a string instead of a map or any other type 
    and we want the string as {:result \"some string value\"}, then we need to pass  :convert-response false
  "
  [name api-args dispatch-fn &
   {:keys [convert-request convert-response convert-response-fn]
    :or {convert-request true convert-response true}}]
  (go
    (try
      (let [;; when convert-request is false, the api-args is assumed to be a js object 
            ;; that can deserialized to Rust names and types as expected by the 'command' api
            ;; When convert-request is true, the api args are converted to 'camelCaseString' as expected by tauri command fns 
            ;; so that args can be deserialized to tauri types
            ;; When convert-request is true and api-args is a js object, (cske/transform-keys csk/->camelCaseString) 
            ;; does not make any changes as expeted to be in a proper deserilaizable format
            args (if convert-request
                   ;; changes all keys to camelCase (e.g db-key -> dbKey)
                   ;; Tauri expects all API arguments names passed in JS api to be in camelCase which 
                   ;; are in turn deserialized as snake_case to match rust argument names used in 
                   ;; tauri commands.  
                   ;; Note
                   ;; Only the api argument names are expected to be in camelCase. The keys of value passed are not changed to cameCase 
                   ;; and they deserialized by the the corresponding struct serde definition. As result, mostly  convert-request = false
                   (->> api-args (cske/transform-keys csk/->camelCaseString) clj->js)
                   api-args)
            r (<p! (invoke name args))]
        ;; Call the dispatch-fn with the resolved value 'r'
        ;;(println "r is " r)
        (dispatch-fn {:result (cond

                                (not (nil? convert-response-fn))
                                (-> r js->clj convert-response-fn) ;; custom transformer of response

                                (and convert-response (string? r))
                                (csk/->kebab-case-keyword r)

                                convert-response
                                (->> r js->clj (cske/transform-keys csk/->kebab-case-keyword))

                                ;; No conversion is done and just js->clj
                                :else
                                (js->clj r))})
        ;; Just to track db modifications if any
        (dispatch [:common/db-api-call-completed name]))
      (catch js/Error err
        (do
          ;;Call the dispatch-fn with any error returned by the back end API
          (dispatch-fn {:error (ex-cause err)})
          (js/console.log (ex-cause err)))))))

(def ^:private tauri-event-listeners
  "This is a map where keys are the event name (kw) values tauri listeners" (atom {}))

(defn register-event-listener
  "Called to register an event handler to listen for a named event 
   message emitted in the backend service 
   event-name is the event name kw 
   event-handler-fn is the handler function
   "
  ([caller-name event-name event-handler-fn]
   (let [el (get @tauri-event-listeners [caller-name event-name])]
    ;;Register the event handler function only if the '[caller-name event-name]' is not already registered
     (when (nil? el)
       (go
         (try
           (let [event-name-str  event-name #_(-> event-name csk/->snake_case name) ;; :group-update => "group_update"
                 unlisten-fn (<p! (tauri-event/listen event-name-str event-handler-fn))]
            ;;unlisten-fn is the resolved value of the Promise returned by fn tauri-event/listen
             (swap! tauri-event-listeners assoc [caller-name event-name] unlisten-fn))
           (catch js/Error err
             (do
               (println "error is " (ex-cause err))
               (js/console.log (ex-cause err))))))
      ;;To log the following messsage use 'if' instead of 'when' form
       #_(println "Tauri event listener for " event-name " is already registered"))))
  ([event-name event-handler-fn]
   (register-event-listener :common event-name event-handler-fn)))

(defn open-file-dialog
  "Calls the tauri's 'open' command so that native file explorerer dialog is opened
   The 'dispatch-fn' will receive a result map with the file slected by the user or nil 
   if the user selects 'Cancel' button in the dialog"
  [dispatch-fn]
  (go
    (try
      (let [f (<p! (open))]
        (dispatch-fn {:result f}))
      (catch js/Error err
        (dispatch-fn {:error (ex-cause err)})
        (js/console.log (ex-cause err))))))

(defn save-file-dialog
  "Calls the tauri's 'save' command so that native file explorerer dialog is opened
   The 'dispatch-fn' will receive the full file path when user clicks 'Save'
   or nil if the user selects 'Cancel' button in the dialog.

  The api-args is a map for Save options.
  e.g 
    {:default-path \"/full/path/to/an/existingdir/myfile.txt\" 
    :title         \"Save Attachment\"
    }
    This will open a save dialog with the file name 'myfile.txt' to save in the dir
    '/full/path/to/an/existingdir/'
  "
  [api-args dispatch-fn]
  (go
    (try
      (let [f (<p! (save (->> api-args
                              (cske/transform-keys csk/->camelCaseString)
                              clj->js)))]
        (dispatch-fn f))
      ;;TODO Add returning error to dispatch-fn
      (catch js/Error err (js/console.log (ex-cause err))))))

(defn write-to-clipboard
  "Copies given data to the clipboard - equivalent to Cmd + C"
  [data]
  (go
    (try
      (let [_r (<p! (writeText data))]
        (println "Data is copied to clipboard" _r))
      (catch js/Error err (js/console.log "Error: " (ex-cause err))))))

(defn read-from-clipboard
  "Gets any text copied previously to clipboard and 'callback-fn' is called with that data - equivalent to Cmd + P
  The arg 'callback-fn' should accept one argument
  "
  [callback-fn]
  (go
    (try
      (let [r (<p! (readText))]
        (callback-fn r)
        (js/console.log "Data read " r))
      (catch js/Error err (js/console.log "Error: " (ex-cause err))))))

(defn load-kdbx
  "Calls the API to read and parse the selected db file.
   Calls the dispatch-fn with the received map of type 'KdbxLoaded' 
  "
  [file-name password key-file-name dispatch-fn]
  (invoke-api "load_kdbx" {:db-file-name file-name
                           :password password
                           :key-file-name key-file-name} dispatch-fn))

(defn unlock-kdbx
  "Calls the API to unlock the previously opened db file.
   Calls the dispatch-fn with the received map of type 'KdbxLoaded' 
  "
  [db-key password key-file-name dispatch-fn]
  (invoke-api "unlock_kdbx" {:db-key db-key
                             :password password
                             :key-file-name key-file-name} dispatch-fn))

(defn groups-summary-data
  "Gets all groups and subgroups for a given db-key"
  [db-key dispatch-fn]
  ;;As uuid strings are used to get values of a group in the response map
  ;;We do not want to convert any string keys of the map to keword key. 
  (invoke-api "groups_summary_data" {:db-key db-key} dispatch-fn :convert-response false))

(defn entry-summary-data
  "Gets the list of entry summary data for a given entry category as defined in EnteryCategory in 'db_service.rs'. 
  The args the db-key and the entry-category 
  (AllEntries, Favorites,Deleted, {:group \"some valid group uuid\"}  {:entrytype \"Login\"}).
  The entry-category should have a value that can be converted to the enum  EnteryCategory
  The serilaization expect the enum name as camelCase 
  For now the valid enums are 
  `allEntries`, `favourites`, `deleted`,   
  `{:group \"valid uuid\"}`, 
  `{:entrytype \"Login\"}`,   - should be deprecated
  `{:entry-type-uuid \"9e644c27-d00b-4aca-8355-5078c5a4fb44\"}`
  "
  [db-key entry-category dispatch-fn]
  ;;(println "entry-category " entry-category)
  (invoke-api "entry_summary_data" {:db-key db-key :entry-category
                                    (if (map? entry-category) entry-category (csk/->camelCaseString entry-category))} dispatch-fn :convert-response true))

(defn history-entries-summary [db-key entry-uuid dispatch-fn]
  (invoke-api "history_entries_summary" {:db-key db-key :entry-uuid entry-uuid} dispatch-fn))

(defn- transform-response-entry-keys
  "All keys in the incoming raw entry map from backend will be transformed
  using custom key transformer
   "
  [entry]
  (let [keys_exclude (->  entry (get "section_fields") keys vec)
        t-fn (fn [k]
               (if (contains-val? keys_exclude k)
                 k
                 (csk/->kebab-case-keyword k)))]
    (cske/transform-keys t-fn entry)))

(defn get-entry-form-data-by-id
  "Gets an entry details for a give given db-key and the entry uuid. "
  [db-key entry-uuid dispatch-fn]
  (invoke-api "get_entry_form_data_by_id" {:db-key db-key :entry-uuid entry-uuid}
              dispatch-fn
              :convert-response-fn transform-response-entry-keys))

(defn history-entry-by-index [db-key entry-uuid index dispatch-fn]
  (invoke-api "history_entry_by_index" {:db-key db-key :entry-uuid entry-uuid :index index}
              dispatch-fn
              :convert-response-fn transform-response-entry-keys))

(defn delete-history-entry-by-index [db-key entry-uuid index dispatch-fn]
  (invoke-api "delete_history_entry_by_index" {:db-key db-key :entry-uuid entry-uuid :index index} dispatch-fn))

(defn delete-history-entries [db-key entry-uuid dispatch-fn]
  (invoke-api "delete_history_entries" {:db-key db-key :entry-uuid entry-uuid} dispatch-fn))

(defn new-entry-form-data [db-key entry-type-uuid dispatch-fn]
  (invoke-api "new_entry_form_data"
              {:db-key db-key
               :entry-type-uuid entry-type-uuid :parent-group-uuid nil} dispatch-fn
              :convert-response-fn transform-response-entry-keys))

(defn- transform-resquest-entry-form-data
  "All keys in the incoming entry map from UI will be transformed
  using custom key transformer
   "
  [entry-form-data]
  (let [keys_exclude (->  entry-form-data :section-fields keys vec)
       ;; _ (println "keys_exclude are " keys_exclude)
        t-fn (fn [k]
               (if (contains-val? keys_exclude k)
                 k
                 (csk/->snake_case k)))]
    (cske/transform-keys t-fn entry-form-data)))

(defn update-entry
  "Called to update changes to an entry form data (EntryFormData struct) to the backend storage"
  [db-key entry-form-data dispatch-fn]
  ;; Call to 'update_entry_from_form_data' 
  ;; need to be renamed  after back end api 'update_entry' is refactored to use EntryFormData struct
  (invoke-api "update_entry_from_form_data"
              (clj->js
               {:dbKey db-key
                :formData (transform-resquest-entry-form-data entry-form-data)})
              dispatch-fn :convert-request false))

(defn insert-entry
  "Called to insert a new entry form data (EntryFormData struct) to the backend storage as Entry struct"
  [db-key entry-form-data dispatch-fn]
  (invoke-api "insert_entry_from_form_data"
              (clj->js
               {:dbKey db-key
                :formData (transform-resquest-entry-form-data entry-form-data)})
              dispatch-fn :convert-request false))

(defn get-group-by-id
  "Gets an group details for a give given db-key and the group uuid. "
  [db-key group-uuid dispatch-fn & opts]
  (apply invoke-api "get_group_by_id" {:db-key db-key :group-uuid group-uuid} dispatch-fn opts))

(defn update-group
  "Called to update the group data in the backend storage"
  [db-key group dispatch-fn]
  ;;(println "Going to update group..." group)
  (let [args (clj->js {:dbKey db-key
                       :group (->> group (cske/transform-keys csk/->snake_case))})]
    (invoke-api "update_group" args dispatch-fn :convert-request false)))

(defn insert-group [db-key group dispatch-fn]
  (let [args (clj->js {:dbKey db-key
                       :group (->> group (cske/transform-keys csk/->snake_case))})]
    (invoke-api "insert_group" args dispatch-fn :convert-request false)))

(defn move-entry-to-recycle_bin [db-key entry-uuid dispatch-fn]
  (invoke-api "move_entry_to_recycle_bin" {:db-key db-key :entry-uuid entry-uuid} dispatch-fn))

(defn move-entry
  [db-key entry-uuid new-parent-id dispatch-fn]
  (invoke-api "move_entry" {:db-key db-key :entry-uuid entry-uuid :new-parent-id new-parent-id} dispatch-fn))

(defn move-group-to-recycle_bin [db-key group-uuid dispatch-fn]
  (invoke-api "move_group_to_recycle_bin" {:db-key db-key :group-uuid group-uuid} dispatch-fn))

(defn move-group
  [db-key group-uuid new-parent-id dispatch-fn]
  (invoke-api "move_group" {:db-key db-key :group-uuid group-uuid :new-parent-id new-parent-id} dispatch-fn))

(defn remove-entry-permanently [db-key entry-uuid dispatch-fn]
  (invoke-api "remove_entry_permanently" {:db-key db-key :entry-uuid entry-uuid} dispatch-fn))

(defn remove-group-permanently [db-key group-uuid dispatch-fn]
  (invoke-api "remove_group_permanently" {:db-key db-key :group-uuid group-uuid} dispatch-fn))

(defn upload-attachment
  [db-key file-name dispatch-fn]
  (invoke-api "upload_entry_attachment" {:db-key db-key :file-name file-name} dispatch-fn))

(defn save-as-kdbx
  "Saves the db using a new file name and returns the KdbxLoaded"
  [db-key db-file-name dispatch-fn]
  (invoke-api "save_as_kdbx" {:db-key db-key :db-file-name db-file-name} dispatch-fn))

(defn save-kdbx
  "Saves the opened kdbx file.
  The backend api returns KdbxSaved struct with the same db key on successfull saving with database-name.
  "
  [db-key dispatch-fn]
  (invoke-api "save_kdbx" {:db-key db-key} dispatch-fn))

(defn save-all-modified-dbs
  [db-keys dispatch-fn]
  (invoke-api "save_all_modified_dbs" {:db-keys db-keys} dispatch-fn))

(defn close-kdbx
  "Called to close a previously opened db. 
  This call removes the content of the db from cache in the backend
  "
  [db-key dispatch-fn]
  (invoke-api "close_kdbx" {:db-key db-key} dispatch-fn))

(defn- request-argon2key-transformer
  "A custom transformer that transforms a map that has ':Argon2' key "
  [new-db]
  (let [t (fn [k] (if (= k :Argon2)
                    ;;retains the key as :Argon2 instead of :argon-2
                    :Argon2
                    (csk/->snake_case k)))]
    (cske/transform-keys t new-db)))

(defn create-kdbx
  "Called to create new database.
  The arg new-db is deserializable as json as expected by NewDatabase struct
  "
  [new-db dispatch-fn]
  ;; Tauri api expects 'camelCase' 'newDb' and deserializes that to the argument 'new_db' in 'create_kdbx' command
  ;; and we do not want to do any conversion in invoke fn as we have already done it here 
  (invoke-api "create_kdbx" (clj->js {:newDb (request-argon2key-transformer new-db)}) dispatch-fn :convert-request false))

(defn get-categories-to-show
  [db-key dispatch-fn & opts]
  (apply invoke-api "get_categories_to_show" {:db-key db-key} dispatch-fn opts))

(defn mark-group-as-category
  [db-key group-uuid dispatch-fn & opts]
  ;;TODO: Rename group_id to group_uuid in backend API and then change here the key group-id to group-uuid
  (apply invoke-api "mark_group_as_category" {:db-key db-key :group-id group-uuid} dispatch-fn opts))

(defn search-term
  [db-key term dispatch-fn]
  (invoke-api "search_term" {:db-key db-key :term term} dispatch-fn))

(defn new-blank-group [mark-as-category dispatch-fn]
  (invoke-api "new_blank_group" {:mark-as-category mark-as-category} dispatch-fn))

(defn load-custom-svg-icons
  [dispatch-fn]
  (invoke-api "load_custom_svg_icons" {} dispatch-fn :convert-response false))

(defn svg-file [name dispatch-fn]
  (invoke-api "svg_file" {:name name} dispatch-fn :convert-response false))

(defn read-app-preference [dispatch-fn]
  (invoke-api "read_app_preference" {} dispatch-fn))

(defn system-info-with-preference [dispatch-fn]
  (invoke-api "system_info_with_preference" {} dispatch-fn))

(defn- handle-argon2-renaming
  "A custom transform fuction to make sure Argon2 is converted to :Argon2 not converted to :argon-2 so that 
  we can keep same as generated by serde 
  "
  [data]
  (let [t (fn [k] (if (= k "Argon2")
                    :Argon2
                    (csk/->kebab-case-keyword k)))]
    ;; transform-keys walks through the keys of a map using the transforming fn passed as first arg 
    ;; see https://github.com/clj-commons/camel-snake-kebab/blob/version-0.4.3/src/camel_snake_kebab/extras.cljc
    (cske/transform-keys t data)))

(defn get-db-settings [db-key dispatch-fn]
  ;; Note: With regard to reading and writing of DbSettings, we use custom conversion from json to clojure data and back
  ;; It is possible to use just custom converter only in 'set-db-settings' by using the default response converted :argon-2 in clojure code
  ;; Then convert only :argon-2 to :Argon2 before calling 'set-db-settings'
  (invoke-api "get_db_settings" {:db-key db-key} dispatch-fn {:convert-response-fn handle-argon2-renaming}))

(defn set-db-settings [db-key db-settings dispatch-fn]
  ;; We keep Argon2 as expected by tauri api serde conversion for DbSettings struct (serde expects 'snake_case' fields of DbSettings)
  ;; The default csk/->snake_case converts Argon2 to argon_2 
  (let [t (fn [k] (if (= k :Argon2) k (csk/->snake_case k)))
        converted  (cske/transform-keys t db-settings)]
    ;; Tauri api keys - dbKey and dbSettings -  are to be in 'camelCase'  
    (invoke-api "set_db_settings" (clj->js {:dbKey db-key :dbSettings converted}) dispatch-fn {:convert-request false})))

(defn menu-action-requested
  "The args menu-id and action should match MenuActionRequest and action should match enum 'MenuAction'"
  [menu-id action dispatch-fn]
  ;; We need to form api-args as expected tauri backend API
  ;; The api 'menu_action_requested' expects one arument 'request' of type MenuActionRequest
  (let [api-args (clj->js {:request {:menu_id menu-id :menu_action action}})]
    (invoke-api "menu_action_requested" api-args dispatch-fn :convert-request false)))

(defn is-file-exists [file-name dispatch-fn]
  (invoke-api "is_path_exists" {:in-path file-name} dispatch-fn))

(defn collect-entry-group-tags [db-key dispatch-fn]
  (invoke-api  "collect_entry_group_tags" {:db-key db-key} dispatch-fn))

(defn entry-type-headers
  "Gets all entry types header information that are avaiable. 
   Returns a map that has standard and custom entry type names separately. 
   See EntryTypeHeasders struct
  "
  [db-key dispatch-fn]
  (invoke-api "entry_type_headers" {:db-key db-key} dispatch-fn))

(defn insert-or-update-custom-entry-type
  "Called to insert a new Custom Entry type or update an existing custom type"
  [db-key entry-type-form-data dispatch-fn]
  (invoke-api "insert_or_update_custom_entry_type"
              (clj->js {:dbKey db-key
                        :entryTypeFormData (transform-resquest-entry-form-data entry-type-form-data)})
              dispatch-fn
              :convert-request false :convert-response false))

(defn delete-custom-entry-type
  "Deletes a valid custom entry type and api-response is the deleted EntryTypeHeader"
  [db-key entry-type-uuid dispatch-fn]
  (invoke-api "delete_custom_entry_type" {:db-key db-key :entry-type-uuid entry-type-uuid} dispatch-fn))

(defn analyzed-password
  "Generates a password with the given options and returns the 
  generated password with its analysis"
  [password-options dispatch-fn]
  (invoke-api "analyzed_password"
              (clj->js
               {:passwordOptions
                (->> password-options
                     (cske/transform-keys csk/->snake_case))})
              dispatch-fn
              :convert-request false))

(defn export-main-content-as-xml [db-key xml-file-name]
  (invoke-api "export_main_content_as_xml"  {:db-key db-key :xml-file-name xml-file-name} #(println %)))

(defn export-as-xml [db-key xml-file-name]
  (invoke-api "export_as_xml"  {:db-key db-key :xml-file-name xml-file-name} #(println %)))

(comment
  (def db-key (:current-db-file-name @re-frame.db/app-db)))

#_(defn unregister-event-listener
    "Unregisters the previously registered event handler"
    ([caller-name event-name]
     (let [unlisten-fn (get @tauri-event-listeners [caller-name event-name])]
       (if (nil? unlisten-fn)
         (println "No existing listener found for the event name " event-name " and unlisten is not called")
         (do
           (unlisten-fn)
           (swap! tauri-event-listeners assoc [caller-name event-name] nil)))))
    ([event-name]
     (unregister-event-listener :common event-name)))

#_(defn open-file
    "Opens a file passed as 'file-name' from the local file system with the system's default app
   The arg 'file-name' expected to be the complete path.
   Any error in opening is passed as {:error msg} to the 'dispatch-fn'
  "
    [file-name dispatch-fn]
    (go
      (try
        (let [f (<p! (tauri-shell/open file-name))]
          (dispatch-fn {:result f}))
      ;;TODO Add returning error to dispatch-fn
        (catch js/Error err
          (dispatch-fn {:error (ex-cause err)})
          (js/console.log (ex-cause err))))))

#_(defn get-standard-path
  "Called to get the standard platform specific dir.
   The arg kw-name identifies what backend api to call and used as key to indentify the dir"
  [kw-name dispatch-fn]
  (let [call-fn (cond
                  (= :document-dir kw-name)
                  tauri-path/documentDir

                  (= :home-dir kw-name)
                  tauri-path/homeDir

                  :else
                  :unknown-name)]
    (if (= call-fn :unknown-name)
      (dispatch-fn {:error "Unknown standard path requested"})
      (go
        (try
          (let [dir-name (<p! (call-fn))]
            (dispatch-fn {:result {kw-name dir-name}}))
          (catch js/Error err
            (dispatch-fn {:error (ex-cause err)})
            (js/console.log (ex-cause err))))))))

#_(defn standard-paths [dispatch-fn]
    (invoke-api "standard_paths" {} dispatch-fn))
