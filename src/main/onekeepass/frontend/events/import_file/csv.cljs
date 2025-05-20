(ns onekeepass.frontend.events.import-file.csv
  (:require-macros [onekeepass.frontend.okp-macros
                    :refer  [as-map]])
  (:require
   [clojure.set :as cset]
   [onekeepass.frontend.background.csv :as bg-csv]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error
                                                             current-opened-db
                                                             locked?]] 
   [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

;; Called from csv-imoprt-start-dialog
(defn open-file-explorer-on-click
  "Opens the os specific file picking window dialog so that user can pick a csv file"
  []
  (cmn-events/open-file-explorer-on-click :import-csv-file-picked))

;; (defn import-cvs-file []
;;   (dispatch [:import/import-csv-file-start]))

(defn import-csv-mapped [mapping-options]
  (dispatch [:import-csv-mapped mapping-options]))

(defn import-csv-map-custom-field [checked?]
  (dispatch [:import-csv-map-custom-field checked?]))

(defn import-csv-new-database []
  (dispatch [:import-csv-new-database]))

(defn import-csv-mapping-result []
  (subscribe [:import-csv-mapping-result]))

;; Called from system nenu
(reg-event-fx
 :import/import-csv-file-start
 (fn [{:keys [_db]} [_event-id]]
   {:fx [[:dispatch [:generic-dialog-show :csv-imoprt-start-dialog]]]}))

(reg-event-fx
 :import-csv-file-picked
 (fn [{:keys [_db]} [_event-id picked-file-full-name]]
   (if-not (nil? picked-file-full-name)
     {:fx [[:bg-import-cvs-file [picked-file-full-name]]]}
     {})))

(reg-fx
 :bg-import-cvs-file
 (fn [[file-full-path]]
   (bg-csv/import-cvs-file file-full-path
                           (fn [api-response]
                             ;; CvsHeaderInfo
                             (when-some [header-info (check-error api-response)]
                               (dispatch [:import-csv-loaded header-info]))))))

(def NOT_PRESENT "Not Available")

;; These are some standard keepass login entry type fields that we support in csv import
(def okp-filed-to-csv-header-mapping [{:field-name const/GROUP :mapped-name NOT_PRESENT}
                                      {:field-name const/TITLE :mapped-name NOT_PRESENT}
                                      {:field-name const/USERNAME :mapped-name NOT_PRESENT}
                                      {:field-name const/PASSWORD :mapped-name NOT_PRESENT}
                                      {:field-name const/URL :mapped-name NOT_PRESENT}

                                      {:field-name const/NOTES :mapped-name NOT_PRESENT} 
                                      {:field-name const/OTP :mapped-name NOT_PRESENT}
                                      {:field-name const/TAGS :mapped-name NOT_PRESENT}
                                      #_{:field-name const/MODIFIED_TIME :mapped-name NOT_PRESENT}
                                      #_{:field-name const/CREATED_TIME :mapped-name NOT_PRESENT}])

(reg-event-fx
 :import-csv-loaded
 (fn [{:keys [db]} [_event-id {:keys [headers]}]]
   {:db (-> db (assoc-in [:import-csv :headers] headers))
    :fx [;; Show the dialog csv-columns-mapping-dialog 
         [:dispatch [:generic-dialog-show-with-state :csv-columns-mapping-dialog
                     ;; Add NOT_PRESENT as first element to vec headers
                     {:data {:csv-headers (-> NOT_PRESENT (cons headers) vec)
                             :mapping-options okp-filed-to-csv-header-mapping}}]]]}))

(reg-event-fx
 :import-csv-mapped
 (fn [{:keys [db]} [_event-id mapping-options]]
   (let [headers (get-in db [:import-csv :headers])

         ;; The standard fields mapped to headers
         mapped-fields (filter (fn [{:keys [mapped-name] :as m}] (when (not= mapped-name NOT_PRESENT) m)) mapping-options)
         matched-count (count mapped-fields)

         ;; Unique headers considered for mapping
         mapped-headers (->> mapped-fields (map (fn [m] (:mapped-name m))) set)

         ;; Unique headers not yet considered for mapping
         not-mapped-headers (cset/difference (set headers) mapped-headers)

         ;; Title should be mapped
         title-matched (filter (fn [{:keys [field-name mapped-name] :as m}] (when (and (= field-name const/TITLE) (not= mapped-name NOT_PRESENT)) m)) mapping-options)

         {:keys [db-key file-name]} (current-opened-db db)
         db-locked? (locked? db-key)]
     ;; (println "Counts : mapped-headers not-mapped-headers " (count mapped-headers) (count not-mapped-headers))
     ;; (println "mapped-headers not-mapped-headers " (vec mapped-headers) not-mapped-headers)
     ;;(println "matched-count db-key db-locked? title-matched" matched-count db-key db-locked? title-matched)
     (cond

       (= matched-count 0)
       {:fx [[:dispatch [:generic-dialog-set-api-error :csv-columns-mapping-dialog "No mapping is done"]]]}

       (empty? title-matched)
       {:fx [[:dispatch [:generic-dialog-set-api-error :csv-columns-mapping-dialog "Title mapping is not done"]]]}

       :else
       {:db (-> db (assoc-in [:import-csv :mapping-result] {:mapped-fields mapped-fields
                                                            
                                                            :mapped-headers (vec mapped-headers)
                                                            :not-mapped-headers (vec not-mapped-headers)
                                                            :db-key db-key 
                                                            :unmapped-custom-field false
                                                            :current-db-file-name file-name
                                                            :db-locked db-locked?}))
        :fx [[:dispatch [:generic-dialog-close :csv-columns-mapping-dialog]]
             [:dispatch [:generic-dialog-show :csv-mapping-completed-dialog]]]}))))

(reg-event-fx
 :import-csv-map-custom-field
 (fn [{:keys [db]} [_event-id checked?]]
   {:db (-> db (assoc-in [:import-csv :mapping-result :unmapped-custom-field] checked?))}))


(reg-event-fx
 :import-csv-new-database
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:generic-dialog-close :csv-mapping-completed-dialog]]
         [:dispatch [:new-database/dialog-show true]]]}))

(reg-event-fx
 :import-file/new-database
 (fn [{:keys [db]} [_event-id new-db on-database-creation-completed]]
   (println "import-file/new-database is called with " new-db)

   (let [headers (get-in db [:import-csv :headers])
         {:keys [mapped-fields not-mapped-headers unmapped-custom-field]} (get-in db [:import-csv :mapping-result])
         mapping (as-map [headers mapped-fields not-mapped-headers unmapped-custom-field])]

     {:fx [[:bg-create-new-db-with-imported-csv [new-db mapping on-database-creation-completed]]]})))

(reg-fx
 :bg-create-new-db-with-imported-csv
 (fn [[new-db mapping on-database-creation-completed]]
   (bg-csv/create-new-db-with-imported-csv new-db mapping on-database-creation-completed)))

;; (reg-event-fx
;;  :imports-csv-new-db-created
;;  (fn [{:keys [db]} [_event-id kdbx-loaded]]
;;    {}))

;; (reg-event-fx
;;  :imports-csv-new-db-create-failed
;;  (fn [{:keys [db]} [_event-id error]]
;;    {}))

(reg-sub
 :import-csv-mapping-result
 (fn [db _query-vec]
   (get-in db [:import-csv :mapping-result])))

(comment
  (-> @re-frame.db/app-db keys))

;;not-matched (filter (fn [{:keys [mapped-name] :as m}] (when (= mapped-name NOT_PRESENT) m)) mapped)