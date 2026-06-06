(ns onekeepass.frontend.events.custom-icons
  "Custom icon CRUD events and per-DB icon cache."
  (:require
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   ["@tauri-apps/plugin-dialog" :refer [open]]
   [re-frame.core :refer [reg-event-db reg-event-fx reg-fx reg-sub dispatch subscribe]]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.background-custom-icons :as bg-ci]
   [onekeepass.frontend.events.common :as cmn-events :refer [active-db-key
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             check-error
                                                             on-error]]
   [onekeepass.frontend.translation :as t]))

;; Per-DB cache (scoped under active-db-key via assoc-in-key-db / get-in-key-db):
;;   [:custom-icons]
;;     {:status     :loading | :done
;;      :icons      [{:uuid :name :last-modification-time}]
;;      :data-urls  {uuid "data:image/png;base64,…"}}
;;
;; UI dialog state (cross-DB, top level):
;;   [:custom-icons-ui]
;;     {:add-url-dialog {:show false :url "" :error nil}
;;      :manage-dialog  {:show false}}

;;; ── Public dispatch API ──────────────────────────────────────────────────────

(defn load-icons-for-db
  "Load (or refresh) the icon list for the active database."
  []
  (dispatch [:custom-icons/load]))

(defn refresh-icons-for-db
  "Reload icons after add/remove."
  []
  (dispatch [:custom-icons/refresh]))

(defn add-icon-from-url
  "Download a favicon from URL and store it as a custom icon.
   on-success-event (optional): a re-frame event vector. Once the icon is
   stored, it is dispatched with the newly-created icon's uuid appended as
   the last argument. Use this to assign the new icon to the form being
   edited (entry/group)."
  ([url] (dispatch [:custom-icons-add-from-url url nil]))
  ([url on-success-event]
   (dispatch [:custom-icons-add-from-url url on-success-event])))

(defn add-icon-from-file
  "Open file picker and store the chosen image as a custom icon.
   on-success-event (optional): see `add-icon-from-url`."
  ([] (dispatch [:custom-icons-add-from-file nil]))
  ([on-success-event]
   (dispatch [:custom-icons-add-from-file on-success-event])))

(defn remove-icon [uuid]
  (dispatch [:custom-icons-remove uuid]))

(defn show-manage-dialog []
  (dispatch [:custom-icons-show-manage-dialog]))

(defn close-manage-dialog []
  (dispatch [:custom-icons-close-manage-dialog]))

(defn show-add-url-dialog []
  (dispatch [:custom-icons-show-add-url-dialog]))

(defn close-add-url-dialog []
  (dispatch [:custom-icons-close-add-url-dialog]))

(defn set-entry-icon
  "Assign or clear a custom icon on an entry. Pass nil to clear."
  [entry-uuid custom-icon-uuid]
  (dispatch [:custom-icons-set-entry-icon entry-uuid custom-icon-uuid]))

(defn set-group-icon
  "Assign or clear a custom icon on a group. Pass nil to clear."
  [group-uuid custom-icon-uuid]
  (dispatch [:custom-icons-set-group-icon group-uuid custom-icon-uuid]))

;;; ── Subscriptions API ────────────────────────────────────────────────────────

(defn icons-list []
  (subscribe [:custom-icons-list]))

(defn icon-data-url [uuid]
  (subscribe [:custom-icons-data-url uuid]))

(defn manage-dialog-open? []
  (subscribe [:custom-icons-manage-dialog-open]))

(defn add-url-dialog-state []
  (subscribe [:custom-icons-add-url-dialog-state]))

(defn custom-svg-icons-status []
  (subscribe [:custom-svg-icons-status]))

;;; ── Legacy (SVG app-level icons, kept as-is for callers) ─────────────────────

(defonce custom-svg-icons (atom {}))

(defn svg-icon-str [name]
  (get @custom-svg-icons name))

(defn load-handle-icons-response [api-response]
  (reset! custom-svg-icons (check-error api-response))
  (dispatch [:custom-icons-loading-done]))

;;; ── Event handlers ───────────────────────────────────────────────────────────

(reg-event-fx
 :custom-icons/load
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in-key-db db [:custom-icons]
                         {:status :loading :icons [] :data-urls {}})
    :fx [[:bg-list-custom-icons (active-db-key db)]]}))

(reg-event-fx
 :custom-icons/refresh
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc-in-key-db db [:custom-icons :status] :loading)
    :fx [[:bg-list-custom-icons (active-db-key db)]]}))

(reg-event-fx
 :custom-icons-loaded
 (fn [{:keys [db]} [_event-id icons]]
   (let [db-key (active-db-key db)
         existing (or (get-in-key-db db [:custom-icons :data-urls]) {})
         to-fetch (remove #(contains? existing %) (map :uuid icons))]
     {:db (-> db
              (assoc-in-key-db [:custom-icons :icons] icons)
              (assoc-in-key-db [:custom-icons :status] :done))
      :fx (mapv (fn [uuid] [:bg-get-custom-icon-data [db-key uuid]]) to-fetch)})))

(reg-event-db
 :custom-icons-data-url-ready
 (fn [db [_event-id uuid data-url]]
   (assoc-in-key-db db [:custom-icons :data-urls uuid] data-url)))

(reg-event-fx
 :custom-icons-add-from-url
 (fn [{:keys [db]} [_event-id url on-success-event]]
   {:db (assoc-in db [:custom-icons-ui :add-url-dialog :url] url)
    :fx [[:dispatch [:common/progress-message-box-show
                     (t/lstr-dlg-title 'addCustomIcon)
                     (t/lstr-dlg-text 'downloadingFavicon)]]
         [:bg-add-custom-icon-from-url
          [(active-db-key db) url on-success-event]]]}))

(reg-event-fx
 :custom-icons-add-from-file
 (fn [{:keys [db]} [_event-id on-success-event]]
   {:fx [[:bg-add-custom-icon-from-file
          [(active-db-key db) on-success-event]]]}))

(reg-event-fx
 :custom-icons-remove
 (fn [{:keys [db]} [_event-id uuid]]
   {:fx [[:bg-remove-custom-icon [(active-db-key db) uuid]]]}))

(reg-event-fx
 :custom-icons-set-entry-icon
 (fn [{:keys [db]} [_event-id entry-uuid custom-icon-uuid]]
   {:fx [[:bg-set-entry-custom-icon [(active-db-key db) entry-uuid custom-icon-uuid]]]}))

(reg-event-fx
 :custom-icons-set-group-icon
 (fn [{:keys [db]} [_event-id group-uuid custom-icon-uuid]]
   {:fx [[:bg-set-group-custom-icon [(active-db-key db) group-uuid custom-icon-uuid]]]}))

(reg-event-db
 :custom-icons-show-manage-dialog
 (fn [db [_event-id]]
   (assoc-in db [:custom-icons-ui :manage-dialog :show] true)))

(reg-event-db
 :custom-icons-close-manage-dialog
 (fn [db [_event-id]]
   (assoc-in db [:custom-icons-ui :manage-dialog :show] false)))

(reg-event-db
 :custom-icons-show-add-url-dialog
 (fn [db [_event-id]]
   (assoc-in db [:custom-icons-ui :add-url-dialog] {:show true :url "" :error nil})))

(reg-event-db
 :custom-icons-close-add-url-dialog
 (fn [db [_event-id]]
   (assoc-in db [:custom-icons-ui :add-url-dialog :show] false)))

;;; ── Legacy SVG event handler ────────────────────────────────────────────────

(reg-event-fx
 :custom-icons/load-custom-icons
 (fn [{:keys [db]} [_event-id]]
   {:db (assoc db :custom-svg-icons-status :loading)
    :fx [[:bg-load-custom-svg-icons]]}))

(reg-event-db
 :custom-icons-loading-done
 (fn [db [_event-id]]
   (assoc db :custom-svg-icons-status :done)))

;;; ── Backend effect handlers (reg-fx) ─────────────────────────────────────────

(reg-fx
 :bg-list-custom-icons
 (fn [db-key]
   (bg-ci/list-custom-icons db-key
                         (fn [response]
                           (when-let [icons (check-error response)]
                             (dispatch [:custom-icons-loaded icons]))))))

(reg-fx
 :bg-get-custom-icon-data
 (fn [[db-key uuid]]
   (bg-ci/get-custom-icon-data db-key uuid
                            (fn [response]
                              (when-let [b64 (check-error response)]
                                (dispatch [:custom-icons-data-url-ready uuid
                                           (str "data:image/png;base64," b64)]))))))

(defn- on-icon-added
  "Common handler invoked after an add-custom-icon backend call returns.
   Hides the progress dialog (shown by the add-from-url / add-from-file
   triggers), refreshes the icon list, and — if on-success-event is provided
   — dispatches it with the new icon's uuid appended.

   Note: check-error / on-error already auto-hide the progress dialog on
   the error path; we hide explicitly here on the success path."
  [response on-success-event]
  (when-let [{:keys [uuid]} (check-error response)]
    (dispatch [:common/progress-message-box-hide])
    (dispatch [:custom-icons/refresh])
    (when on-success-event
      (dispatch (conj (vec on-success-event) uuid)))))

(reg-fx
 :bg-add-custom-icon-from-url
 (fn [[db-key url on-success-event]]
   (bg-ci/add-custom-icon-from-url db-key url
                                #(on-icon-added % on-success-event))))

(reg-fx
 :bg-add-custom-icon-from-file
 (fn [[db-key on-success-event]]
   (go
     (try
       (let [path (<p! (open (clj->js {:multiple false
                                       :filters [{:name "Images"
                                                  :extensions ["png" "jpg" "jpeg" "ico"]}]})))]
         (when path
           (dispatch [:common/progress-message-box-show
                      (t/lstr-dlg-title 'addCustomIcon)
                      (t/lstr-dlg-text 'addingCustomIcon)])
           (bg-ci/add-custom-icon-from-file db-key path
                                         #(on-icon-added % on-success-event))))
       (catch js/Error _err nil)))))

(reg-fx
 :bg-remove-custom-icon
 (fn [[db-key uuid]]
   (bg-ci/remove-custom-icon db-key uuid
                          (fn [response]
                            (when-not (on-error response)
                              ;; Backend cleared :custom-icon-uuid from every
                              ;; entry and group that referenced this icon.
                              ;; The cljs-side summaries / form / tree are now
                              ;; stale — refresh them all so the UI flips
                              ;; affected items back to their built-in icons.
                              (dispatch [:custom-icons/refresh])
                              (dispatch [:group-tree-content/load-groups])
                              (dispatch [:entry-category/reload-category-data])
                              (dispatch [:entry-list/entry-updated]))))))

(reg-fx
 :bg-set-entry-custom-icon
 (fn [[db-key entry-uuid custom-icon-uuid]]
   (bg-ci/set-entry-custom-icon db-key entry-uuid custom-icon-uuid
                             #(check-error %))))

(reg-fx
 :bg-set-group-custom-icon
 (fn [[db-key group-uuid custom-icon-uuid]]
   (bg-ci/set-group-custom-icon db-key group-uuid custom-icon-uuid
                             #(check-error %))))

(reg-fx
 :bg-load-custom-svg-icons
 (fn [_]
   (bg/load-custom-svg-icons load-handle-icons-response)))


;;; ── Subscriptions ────────────────────────────────────────────────────────────

(reg-sub
 :custom-icons-list
 (fn [db _query-vec]
   (or (get-in-key-db db [:custom-icons :icons]) [])))

(reg-sub
 :custom-icons-data-url
 (fn [db [_query-id uuid]]
   (get-in-key-db db [:custom-icons :data-urls uuid])))

(reg-sub
 :custom-icons-manage-dialog-open
 (fn [db _query-vec]
   (get-in db [:custom-icons-ui :manage-dialog :show] false)))

(reg-sub
 :custom-icons-add-url-dialog-state
 (fn [db _query-vec]
   (get-in db [:custom-icons-ui :add-url-dialog] {:show false :url "" :error nil})))

(reg-sub
 :custom-svg-icons-status
 (fn [db _query-vec]
   (:custom-svg-icons-status db)))

