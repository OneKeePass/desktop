(ns onekeepass.frontend.events.remote-storage
  "Desktop remote-storage dialog flow. A single multi-step MUI dialog drives
   the user from picking a connection (or entering ad-hoc credentials) to
   browsing a remote SFTP/WebDAV server and picking a kdbx file to open or
   a folder to drop a new kdbx into.

   Desktop is kdbx-only: no blob secure store, no private-key file storage,
   no delete-config path. Connections are sourced from REMOTE_CONNECTION_SFTP
   / _WEBDAV entries across the currently open kdbx databases.

   re-frame state shape under :remote-storage:
   {:dialog-show       boolean
    :mode              :open | :create
    :step              :source-pick | :form | :browse
    :current-type      :sftp | :webdav
    :kdbx-source       {:sftp [{summary} ...] :webdav [...]}
    :form-data         {:sftp {..} :webdav {..}}      ;; ad-hoc form
    :form-errors       {:sftp {..} :webdav {..}}
    :listing           {:type kw :connection-id uuid
                        :stack [{:parent-dir .. :sub-dirs [..] :files [..]} ...]}
    :api-error-text    string-or-nil
    :status            :idle | :in-progress}"
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.background-remote-storage :as bg-rs]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error]]
   [onekeepass.frontend.translation :refer [lstr-m]]
   [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx reg-sub
                          subscribe]]))

;; ---- helpers ----

(defn- form-db-key
  [kw-type connection-id parent-dir file-name]
  (let [tag (case kw-type :sftp const/V-SFTP :webdav const/V-WEBDAV)
        file-path (if (= parent-dir "/")
                    (str "/" file-name)
                    (str parent-dir "/" file-name))]
    (str tag "-" connection-id "-" file-path)))

(defn- sftp-init-data []
  {:name nil :host nil :port 22 :user-name nil :password nil :start-dir "/"})

(defn- webdav-init-data []
  {:name nil :root-url nil :user-name nil :password nil
   :allow-untrusted-cert false :start-dir "/"})

(defn- init-for-type [kw-type]
  (case kw-type :sftp (sftp-init-data) :webdav (webdav-init-data)))

(defn- connection-entry-type->kw-type [entry-type]
  (condp = entry-type
    const/REMOTE_CONNECTION_SFTP_TYPE_NAME :sftp
    const/REMOTE_CONNECTION_WEBDAV_TYPE_NAME :webdav
    nil))

(def ^:private blank-state
  {:dialog-show false
   :mode :open
   :step :source-pick
   :current-type :sftp
   :kdbx-source {:sftp [] :webdav []}
   :form-data {:sftp (sftp-init-data) :webdav (webdav-init-data)}
   :form-errors {:sftp {} :webdav {}}
   :listing nil
   :new-db-file-name "new-db.kdbx"
   :api-error-text nil
   :status :idle
   :direct-open? false})

;; ---- public accessor API (UI uses these, never re-frame directly) ----

(defn show-for-open
  "Open the remote-storage dialog in 'open existing db' mode."
  []
  (dispatch [::dialog-show :open]))

(defn show-for-create
  "Open the remote-storage dialog in 'create new db' mode."
  []
  (dispatch [::dialog-show :create]))

(defn cancel-dialog []
  (dispatch [::dialog-hide]))

(defn back-step []
  (dispatch [::back-step]))

(defn dialog-data [] (subscribe [::dialog-data]))

(defn type-selected [kw-type]
  (dispatch [::type-selected kw-type]))

(defn current-type [] (subscribe [::current-type]))

(defn kdbx-source-connections [kw-type]
  (subscribe [::kdbx-source-connections kw-type]))

(defn grouped-kdbx-source-connections [kw-type]
  (subscribe [::grouped-kdbx-source-connections kw-type]))

(defn connect-by-id-start [connection-id]
  (dispatch [::connect-by-id-start connection-id]))

(defn open-entry-remote [entry-type connection-id]
  (println "open-entry-remote entry-type connection-id" entry-type connection-id)
  (dispatch [::open-entry-remote entry-type connection-id]))

(defn enter-ad-hoc-form []
  (dispatch [::enter-ad-hoc-form]))

(defn connect-ad-hoc-start []
  (dispatch [::connect-ad-hoc-start]))

(defn form-data [kw-type] (subscribe [::form-data kw-type]))

(defn form-data-update [field value]
  (dispatch [::form-data-update field value]))

(defn pick-private-key
  "Opens the native file picker and stores the chosen path on the SFTP form
   under :private-key-full-file-name. Used by the ad-hoc SFTP form."
  []
  (dispatch [::pick-private-key]))

(defn clear-private-key []
  (dispatch [::form-data-update :private-key-full-file-name nil]))

(defn form-errors [kw-type] (subscribe [::form-errors kw-type]))

(defn listing [] (subscribe [::listing]))

(defn list-sub-dir [parent-dir sub-dir]
  (dispatch [::list-sub-dir parent-dir sub-dir]))

(defn listing-previous []
  (dispatch [::listing-previous]))

(defn file-picked-for-open [parent-dir file-name]
  (dispatch [::file-picked-for-open parent-dir file-name]))

(defn folder-picked-for-new-db [parent-dir new-db-file-name]
  (dispatch [::folder-picked-for-new-db parent-dir new-db-file-name]))

(defn new-db-file-name [] (subscribe [::new-db-file-name]))

(defn new-db-file-name-update [value]
  (dispatch [::new-db-file-name-update value]))

(defn save-remote-kdbx [db-key overwrite]
  (dispatch [::save-remote-kdbx db-key overwrite]))

;; ---- subscriptions ----

(reg-sub
 ::dialog-data
 (fn [db _] (:remote-storage db)))

(reg-sub
 ::current-type
 (fn [db _] (get-in db [:remote-storage :current-type])))

(reg-sub
 ::kdbx-source-connections
 (fn [db [_ kw-type]] (get-in db [:remote-storage :kdbx-source kw-type])))

;; Groups the flat kdbx-source summaries by their parent kdbx db-key and
;; joins each group with the database-name from :opened-db-list, so the
;; source-pick step can render per-database subheaders.
(reg-sub
 ::grouped-kdbx-source-connections
 (fn [[_ kw-type] _]
   [(subscribe [::kdbx-source-connections kw-type])
    (subscribe [:opened-db-list])])
 (fn [[summaries opened-list] _]
   (let [name-by-key (into {} (map (juxt :db-key :database-name)) opened-list)]
     (->> summaries
          (group-by :db-key)
          (map (fn [[db-key items]]
                 {:db-key db-key
                  :db-name (or (name-by-key db-key) db-key)
                  :summaries items}))
          (sort-by :db-name)
          vec))))

(reg-sub
 ::form-data
 (fn [db [_ kw-type]] (get-in db [:remote-storage :form-data kw-type])))

(reg-sub
 ::form-errors
 (fn [db [_ kw-type]] (get-in db [:remote-storage :form-errors kw-type])))

(reg-sub
 ::listing
 (fn [db _] (get-in db [:remote-storage :listing])))

(reg-sub
 ::new-db-file-name
 (fn [db _] (get-in db [:remote-storage :new-db-file-name])))

;; ---- events ----

(reg-event-fx
 ::dialog-show
 (fn [{:keys [db]} [_ mode]]
   (let [kw-type (or (get-in db [:remote-storage :current-type]) :sftp)]
     {:db (-> db
              (assoc :remote-storage (-> blank-state
                                         (assoc :dialog-show true)
                                         (assoc :mode mode)
                                         (assoc :current-type kw-type))))
      :fx [[:dispatch [::load-kdbx-source-connections kw-type]]]})))

(reg-event-db
 ::dialog-hide
 (fn [db _]
   (assoc db :remote-storage (assoc blank-state :dialog-show false))))

;; Public event mirroring (show-for-create) — exposed so other flows can open
;; the remote-storage create dialog from re-frame :fx vectors.
(reg-event-fx
 :remote-storage/show-for-create
 (fn [_ _]
   (println "remote-storage/show-for-create is called...")
   {:fx [[:dispatch [::dialog-show :create]]]}))

;; Called from the new-db wizard's 'Save Remote' branch. Stashes the prepared
;; new-db payload and opens the remote-storage create dialog. When the user
;; picks a remote folder, ::folder-picked-for-new-db sees the pending payload
;; and dispatches create-kdbx directly instead of re-entering the new-db
;; wizard.
(reg-event-fx
 :remote-storage/begin-create-with-payload
 (fn [{:keys [db]} [_ new-db-payload]]
   (let [kw-type (or (get-in db [:remote-storage :current-type]) :sftp)
         db-name (:database-name new-db-payload)
         default-file (if (str/blank? db-name)
                        "new-db.kdbx"
                        (str db-name ".kdbx"))]
     {:db (-> db
              (assoc :remote-storage (-> blank-state
                                         (assoc :dialog-show true)
                                         (assoc :mode :create)
                                         (assoc :current-type kw-type)
                                         (assoc :new-db-file-name default-file)
                                         (assoc :pending-new-db new-db-payload))))
      :fx [[:dispatch [::load-kdbx-source-connections kw-type]]]})))

(reg-event-fx
 ::back-step
 (fn [{:keys [db]} _]
   (let [step (get-in db [:remote-storage :step])]
     (case step
       :browse {:db (assoc-in db [:remote-storage :step] :source-pick)}
       :form   {:db (assoc-in db [:remote-storage :step] :source-pick)}
       {:fx [[:dispatch [::dialog-hide]]]}))))

(reg-event-fx
 ::type-selected
 (fn [{:keys [db]} [_ kw-type]]
   {:db (-> db
            (assoc-in [:remote-storage :current-type] kw-type)
            (update-in [:remote-storage :form-data kw-type] #(or % (init-for-type kw-type)))
            (update-in [:remote-storage :form-errors kw-type] #(or % {})))
    :fx [[:dispatch [::load-kdbx-source-connections kw-type]]]}))

(reg-event-fx
 ::load-kdbx-source-connections
 (fn [_ [_ kw-type]]
   {:fx [[::bg-list-kdbx-source-connections [kw-type]]]}))

(reg-fx
 ::bg-list-kdbx-source-connections
 (fn [[kw-type]]
   (bg-rs/list-kdbx-source-connections
    kw-type
    (fn [api-response]
      (when-some [summaries (check-error api-response)]
        (dispatch [::kdbx-source-connections-loaded kw-type summaries]))))))

(reg-event-db
 ::kdbx-source-connections-loaded
 (fn [db [_ kw-type summaries]]
   (assoc-in db [:remote-storage :kdbx-source kw-type] (or summaries []))))

(reg-event-db
 ::enter-ad-hoc-form
 (fn [db _]
   (-> db
       (assoc-in [:remote-storage :step] :form)
       (assoc-in [:remote-storage :api-error-text] nil))))

(reg-event-db
 ::new-db-file-name-update
 (fn [db [_ value]]
   (-> db
       (assoc-in [:remote-storage :new-db-file-name] value)
       (assoc-in [:remote-storage :api-error-text] nil))))

(reg-event-db
 ::form-data-update
 (fn [db [_ field value]]
   (let [kw-type (get-in db [:remote-storage :current-type])]
     (assoc-in db [:remote-storage :form-data kw-type field] value))))

(reg-event-fx
 ::pick-private-key
 (fn [_ _]
   {:fx [[::bg-open-private-key-dialog]]}))

(reg-fx
 ::bg-open-private-key-dialog
 (fn [_]
   (bg/open-file-dialog
    (fn [api-response]
      (when-some [picked (check-error api-response)]
        (dispatch [::form-data-update :private-key-full-file-name picked]))))))

(reg-event-fx
 ::connect-by-id-start
 (fn [{:keys [db]} [_ connection-id]]
   (let [kw-type (get-in db [:remote-storage :current-type])]
     {:db (-> db
              (assoc-in [:remote-storage :status] :in-progress)
              (assoc-in [:remote-storage :api-error-text] nil))
      :fx [[::bg-connect-by-id [kw-type connection-id]]]})))

(reg-event-fx
 ::open-entry-remote
 (fn [_ [_ entry-type connection-id]]
   (if-let [kw-type (connection-entry-type->kw-type entry-type)]
     {:fx [[:dispatch [:common/progress-message-box-show "Connecting" "Please wait..."]]
           [::bg-open-entry-remote [kw-type connection-id]]]}
     {:fx [[:dispatch [:common/message-snackbar-error-open "Unsupported remote connection type"]]]})))

(reg-fx
 ::bg-connect-by-id
 (fn [[kw-type connection-id]]
   (bg-rs/connect-by-id-and-retrieve-root-dir
    kw-type connection-id
    (fn [api-response]
      (when-some [connect-status (check-error
                                  api-response
                                  #(dispatch [::connect-failed %]))]
        (dispatch [::connect-complete kw-type connection-id connect-status]))))))

(reg-fx
 ::bg-open-entry-remote
 (fn [[kw-type connection-id]]
   (bg-rs/connect-by-id-and-retrieve-root-dir
    kw-type connection-id
    (fn [api-response]
      (dispatch [:common/progress-message-box-hide])
      (when-some [connect-status (check-error
                                  api-response
                                  #(dispatch [:common/message-snackbar-error-open %]))]
        (dispatch [::open-entry-remote-complete
                   kw-type connection-id connect-status]))))))

(reg-event-fx
 ::connect-ad-hoc-start
 (fn [{:keys [db]} _]
   (let [kw-type (get-in db [:remote-storage :current-type])
         raw (get-in db [:remote-storage :form-data kw-type])
         ;; <input type="number"> hands us a string; the Rust SftpConnectionConfig.port is u16.
         info (cond-> raw
                (= kw-type :sftp)
                (update :port #(if (string? %) (js/parseInt % 10) %)))]
     {:db (-> db
              (assoc-in [:remote-storage :status] :in-progress)
              (assoc-in [:remote-storage :api-error-text] nil))
      :fx [[::bg-connect-ad-hoc [kw-type info]]]})))

(reg-fx
 ::bg-connect-ad-hoc
 (fn [[kw-type info]]
   (bg-rs/connect-and-retrieve-root-dir
    kw-type info
    (fn [api-response]
      (when-some [connect-status (check-error
                                  api-response
                                  #(dispatch [::connect-failed %]))]
        (let [cid (:connection-id connect-status)]
          (dispatch [::connect-complete kw-type cid connect-status])))))))

(reg-event-db
 ::connect-failed
 (fn [db [_ error]]
   (-> db
       (assoc-in [:remote-storage :status] :idle)
       (assoc-in [:remote-storage :api-error-text] error))))

(reg-event-fx
 ::connect-complete
 (fn [{:keys [db]} [_ kw-type connection-id {:keys [dir-entries]}]]
   (let [{:keys [parent-dir sub-dirs files]} dir-entries]
     {:db (-> db
              (assoc-in [:remote-storage :status] :idle)
              (assoc-in [:remote-storage :step] :browse)
              (assoc-in [:remote-storage :listing]
                        {:type kw-type
                         :connection-id connection-id
                         :stack [{:parent-dir (or parent-dir "/")
                                  :sub-dirs (or sub-dirs [])
                                  :files (or files [])}]}))})))

(reg-event-db
 ::open-entry-remote-complete
 (fn [db [_ kw-type connection-id {:keys [dir-entries]}]]
   (let [{:keys [parent-dir sub-dirs files]} dir-entries]
     (assoc db :remote-storage
            (-> blank-state
                (assoc :dialog-show true)
                (assoc :mode :open)
                (assoc :step :browse)
                (assoc :current-type kw-type)
                (assoc :status :idle)
                (assoc :direct-open? true)
                (assoc :listing
                       {:type kw-type
                        :connection-id connection-id
                        :stack [{:parent-dir (or parent-dir "/")
                                 :sub-dirs (or sub-dirs [])
                                 :files (or files [])}]}))))))

(reg-event-fx
 ::list-sub-dir
 (fn [{:keys [db]} [_ parent-dir sub-dir]]
   (let [{:keys [type connection-id]} (get-in db [:remote-storage :listing])]
     {:fx [[::bg-list-sub-dir [type connection-id parent-dir sub-dir]]]})))

(reg-fx
 ::bg-list-sub-dir
 (fn [[kw-type connection-id parent-dir sub-dir]]
   (bg-rs/list-sub-dir
    kw-type connection-id parent-dir sub-dir
    (fn [api-response]
      (when-some [dir-entries (check-error api-response)]
        (dispatch [::sub-dir-listed dir-entries]))))))

(reg-event-db
 ::sub-dir-listed
 (fn [db [_ {:keys [parent-dir sub-dirs files]}]]
   (update-in db [:remote-storage :listing :stack]
              conj {:parent-dir (or parent-dir "/")
                    :sub-dirs (or sub-dirs [])
                    :files (or files [])})))

(reg-event-db
 ::listing-previous
 (fn [db _]
   (update-in db [:remote-storage :listing :stack]
              (fn [stack]
                (if (> (count stack) 1) (vec (butlast stack)) stack)))))

;; -- handing off the picked file/folder to the rest of the app --

(reg-event-fx
 ::file-picked-for-open
 (fn [{:keys [db]} [_ parent-dir file-name]]
   (let [{:keys [type connection-id]} (get-in db [:remote-storage :listing])
         db-file-name (form-db-key type connection-id parent-dir file-name)]
     {:fx [[:dispatch [::dialog-hide]]
           [:dispatch [:open-db-form/remote-open-show db-file-name file-name]]]})))

(reg-event-fx
 ::folder-picked-for-new-db
 (fn [{:keys [db]} [_ parent-dir file-name]]
   (let [{:keys [type connection-id]} (get-in db [:remote-storage :listing])
         db-file-name (form-db-key type connection-id parent-dir file-name)
         pending (get-in db [:remote-storage :pending-new-db])]
     (if pending
       ;; save-remote? path — wizard data already collected; create directly.
       ;; Keep the rs dialog open during create so a 'file already exists'
       ;; error can be shown inline and the user can edit the filename and
       ;; retry without losing their place.
       {:db (-> db
                (assoc-in [:remote-storage :status] :in-progress)
                (assoc-in [:remote-storage :api-error-text] nil))
        :fx [[:dispatch [:remote-storage/create-kdbx
                         (assoc pending :database-file-name db-file-name)]]]}
       ;; legacy path — bring user into the new-db wizard pre-filled.
       {:fx [[:dispatch [::dialog-hide]]
             [:dispatch [:new-database/remote-target-selected db-file-name file-name]]]}))))

;; -- read / save / create remote kdbx (called by open-db / new-db flows) --

(reg-event-fx
 :remote-storage/read-kdbx
 (fn [_ [_ db-file-name password key-file-name]]
   {:fx [[:dispatch [:common/progress-message-box-show "Opening" "Please wait..."]]
         [::bg-read-kdbx [db-file-name password key-file-name]]]}))

;; Backend marker (variant Display) returned when the remote connection can't
;; be resolved — happens when an entry-backed remote is opened from a recent
;; entry but the parent kdbx (which holds the SFTP/WebDAV connection entry)
;; isn't open yet.
(def ^:private no-remote-storage-connection "NoRemoteStorageConnection")

(reg-fx
 ::bg-read-kdbx
 (fn [[db-file-name password key-file-name]]
   (bg-rs/read-kdbx
    db-file-name password key-file-name
    (fn [api-response]
      (dispatch [:common/progress-message-box-hide])
      (when-some [kdbx-loaded (check-error
                               api-response
                               (fn [err]
                                 (let [msg (if (= err no-remote-storage-connection)
                                             (lstr-m "openDbPage" "parentDbNotOpen")
                                             err)]
                                   (dispatch [:open-db-error msg]))))]
        ;; Re-use the local path's completion event so the open-db dialog is
        ;; reset and hidden in addition to loading the kdbx into the UI.
        (dispatch [:open-db-file-loading-done kdbx-loaded]))))))

(reg-event-fx
 ::save-remote-kdbx
 (fn [_ [_ db-key overwrite]]
   {:fx [[::bg-save-kdbx [db-key overwrite]]]}))

(reg-fx
 ::bg-save-kdbx
 (fn [[db-key overwrite]]
   (bg-rs/save-kdbx
    db-key overwrite
    (fn [api-response]
      (when-some [kdbx-saved (check-error api-response)]
        (dispatch [:common/kdbx-database-saved kdbx-saved]))))))

(reg-event-fx
 :remote-storage/create-kdbx
 (fn [_ [_ new-db]]
   {:fx [[:dispatch [:common/progress-message-box-show "Creating" "Please wait..."]]
         [::bg-create-kdbx [new-db]]]}))

(reg-event-db
 ::create-error
 (fn [db [_ error]]
   (-> db
       (assoc-in [:remote-storage :status] :idle)
       (assoc-in [:remote-storage :api-error-text] error))))

(reg-event-fx
 ::create-success
 (fn [_ [_ kdbx-loaded]]
   {:fx [[:dispatch [::dialog-hide]]
         [:dispatch [:common/kdbx-database-opened kdbx-loaded]]]}))

(reg-fx
 ::bg-create-kdbx
 (fn [[new-db]]
   (bg-rs/create-kdbx
    new-db
    (fn [api-response]
      (dispatch [:common/progress-message-box-hide])
      (when-some [kdbx-loaded (check-error
                               api-response
                               #(dispatch [::create-error %]))]
        (dispatch [::create-success kdbx-loaded]))))))
