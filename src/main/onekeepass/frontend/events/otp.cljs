(ns onekeepass.frontend.events.otp
  (:require
   [re-frame.core :refer [reg-fx  dispatch]]
   [onekeepass.frontend.events.common :as cmn-events :refer [check-error]]

   [onekeepass.frontend.background :as bg]))

(def entry-form-otp-field-timers (atom {}))

(defn start-polling-entry-form-otp-field
  "Starts the timer for a given otp field token generation and indicator update"
  [db-key entry-uuid otp-field-name {:keys [period ttl]}]
  (let [remaining (atom ttl)
        ;; Timer that polls every second and makes call to the backgend generate token api
        ;; when the ttl expires
        timer-id (js/setInterval
                  ;; This callbasck is called every sec
                  (fn []
                    (let [rem (dec @remaining)]
                      (if (= rem 0)
                        (do
                          (reset! remaining period)
                          ;; Backend call
                          (bg/entry-form-current-otp
                           db-key
                           entry-uuid
                           otp-field-name
                           ;; Backend call result handler fn 
                           (fn [api-response]
                             (when-let [current-opt-token
                                        (check-error api-response #(println "Error in getting currrent otp token" %))]
                               #_(dispatch [:entry-form/update-opt-ttl-indicator otp-field-name @remaining])
                               (dispatch [:entry-form/update-otp-token entry-uuid otp-field-name current-opt-token])))))
                        (do
                          (reset! remaining rem)
                          (dispatch [:entry-form/update-opt-ttl-indicator otp-field-name rem]))))) 1000)]
    ;; Timer id is stored to clear the time later
    (swap! entry-form-otp-field-timers assoc-in [entry-uuid otp-field-name] timer-id)))

(defn start-polling-entry-form-otp-fields
  "Receives all otp field names with its 'current-opt-token' (inner map) in the map otp-field-m
  "
  [db-key entry-uuid otp-field-m]
  (doseq [[otp-field-name opt-data] otp-field-m]
    (start-polling-entry-form-otp-field db-key entry-uuid otp-field-name opt-data)))

(defn stop-polling-entry-form-otp-fields
  [entry-uuid]
  (let [timer-ids (-> @entry-form-otp-field-timers (get entry-uuid) vals)]
    ;; doseq loop is used which returns nil whereas 'for' can also be used but it will return a collection
    (doseq [timer-id timer-ids]
      (js/clearInterval timer-id)
      (swap! entry-form-otp-field-timers dissoc entry-uuid))))

(reg-fx
 :otp/start-polling-otp-fields
 (fn [[db-key previous-entry-uuid entry-uuid otp-field-m]]
   (when-not (nil? previous-entry-uuid)
     (stop-polling-entry-form-otp-fields previous-entry-uuid))
   
   ;; Any previous polling for 'entry-uuid' needs to be stopped
   (stop-polling-entry-form-otp-fields entry-uuid)
   
   ;; otp-field-m is a map with otp field name as key and token data as its value
   ;; See 'start-polling-otp-fields' fn
   (let [fields-m (into {} (filter (fn [[_k v]] (not (nil? v))) otp-field-m))]
     (when (boolean (seq fields-m))
       (start-polling-entry-form-otp-fields db-key entry-uuid fields-m)))))

(reg-fx
 :otp/stop-all-entry-form-polling
 (fn []
   (doseq [uuid (keys @entry-form-otp-field-timers)]
     (stop-polling-entry-form-otp-fields uuid))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; (def entry-form-otp-ttl-timers (atom {}))

;; (def entry-form-otp-period-timers (atom {}))

;; (def entry-form-ttl-indicator-timers (atom {}))

;; (declare start-polling-otp-fields)

;; (declare stop-all-entry-polling)

;; (declare stop-all-entry-form-polling)

;; (reg-fx
;;  :otp/start-polling-otp-fields
;;  (fn [[db-key previous-entry-uuid entry-uuid otp-field-m]]
;;    (when-not (nil? previous-entry-uuid)
;;      (stop-all-entry-polling previous-entry-uuid))
;;    ;; otp-field-m is a map with otp field name as key and token data as its value
;;    ;; See 'start-polling-otp-fields' fn
;;    (start-polling-otp-fields db-key entry-uuid otp-field-m)))

;; (reg-fx
;;  :otp/stop-polling-otp-fields
;;  (fn [[entry-uuid]]
;;    (when-not (nil? entry-uuid)
;;      (stop-all-entry-polling entry-uuid))))

;; (reg-fx
;;  :otp/stop-all-entry-form-polling
;;  (fn []
;;    (stop-all-entry-form-polling)))

;; (defn start-polling-ttl-indicator [entry-uuid otp-field-name period ttl]
;;   (let [remaining (atom ttl)
;;         timer-id (js/setInterval (fn []
;;                                    (let [rem (dec @remaining)
;;                                          rem (if (= rem 0) period rem)]
;;                                      (reset! remaining rem)
;;                                      (dispatch [:entry-form/update-opt-ttl-indicator otp-field-name rem]))) 1000)]
;;     (swap! entry-form-ttl-indicator-timers assoc-in [entry-uuid otp-field-name] timer-id)))

;; (defn start-polling-period-otp-data [db-key entry-uuid otp-field-name period]
;;   ;; Need to clear and remove any previous timer for 'entry-uuid otp-field-name' ?
;;   (let [timer-id (js/setInterval  (fn []
;;                                     (bg/entry-form-current-otp
;;                                      db-key
;;                                      entry-uuid
;;                                      otp-field-name
;;                                      (fn [api-response]
;;                                        (when-let [current-opt-token (check-error
;;                                                                      api-response #(println "Error in getting currrent otp token" %))]

;;                                          (dispatch [:entry-form/update-otp-token entry-uuid otp-field-name current-opt-token])))))
;;                                   (* 1000 period))]

;;     (swap! entry-form-otp-period-timers assoc-in [entry-uuid otp-field-name] timer-id)))

;; (defn stop-all-entry-polling
;;   [entry-uuid]
;;   (let [period-timer-ids (-> @entry-form-otp-period-timers (get entry-uuid) vals)
;;         ttl-timer-ids (-> @entry-form-otp-ttl-timers (get entry-uuid) vals)
;;         ttl-indicator-timer-ids (-> @entry-form-ttl-indicator-timers (get entry-uuid) vals)]
;;     ;; doseq loop is used which returns nil whereas 'for' can also be used but it will return a collection
;;     (doseq [timer-id period-timer-ids]
;;       (js/clearInterval timer-id)
;;       (swap! entry-form-otp-period-timers dissoc entry-uuid))

;;     (doseq [timer-id ttl-timer-ids]
;;       (js/clearTimeout timer-id)
;;       (swap! entry-form-otp-ttl-timers dissoc entry-uuid))

;;     (doseq [timer-id ttl-indicator-timer-ids]
;;       (js/clearInterval timer-id)
;;       (swap! entry-form-ttl-indicator-timers dissoc entry-uuid))))

;; (defn stop-all-entry-form-polling []
;;   (doseq [uuid (distinct (concat (keys @entry-form-otp-period-timers) (keys @entry-form-otp-ttl-timers)))]
;;     (stop-all-entry-polling uuid)))

;; (defn stop-polling-otp-data
;;   "Stops any prior timers set for this entry and otp-field-name"
;;   [entry-uuid otp-field-name]
;;   (let [timer-id-period (-> @entry-form-otp-period-timers (get-in [entry-uuid otp-field-name]))
;;         timer-id-ttl (-> @entry-form-otp-ttl-timers (get-in [entry-uuid otp-field-name]))]
;;     ;; timer-id-period, timer-id-ttl may be nil
;;     (js/clearInterval timer-id-period)
;;     (swap! entry-form-otp-period-timers dissoc entry-uuid)

;;     (js/clearTimeout timer-id-ttl)
;;     (swap! entry-form-otp-ttl-timers dissoc entry-uuid)))


;; (defn start-polling-ttl-otp-data
;;   [db-key entry-uuid otp-field-name ttl]
;;   (println "start-polling-ttl-otp-data is called with ttl " ttl)
;;   (let [timer-id (js/setTimeout
;;                   (fn []
;;                     (bg/entry-form-current-otp
;;                      db-key
;;                      entry-uuid
;;                      otp-field-name
;;                      (fn [api-reponse]
;;                        (println "After ttl time expiry " api-reponse)
;;                        (when-let [{:keys [period] :as current-opt-token} (check-error api-reponse #())]
;;                          (dispatch [:entry-form/update-otp-token entry-uuid otp-field-name current-opt-token])
;;                          (start-polling-period-otp-data db-key entry-uuid otp-field-name period)))))
;;                   (* 1000 ttl))]

;;     (swap! entry-form-otp-ttl-timers assoc-in [entry-uuid otp-field-name] timer-id)))

;; ;; otp-field-m => {"otps" {:period 30, :token "138063", :ttl 23}}}
;; (defn start-polling-otp-fields
;;   "Receives all otp field names with its 'current-opt-token' (inner map) in the map otp-field-m
;;   "
;;   [db-key entry-uuid otp-field-m]
;;   (doseq [[otp-field-name opt-data] otp-field-m]
;;     (start-polling-ttl-indicator entry-uuid otp-field-name (:period opt-data) (:ttl opt-data))
;;     (start-polling-ttl-otp-data db-key entry-uuid otp-field-name (:ttl opt-data))))

;; (defn test-call [db-key entry-uuid otp-field-name]
;;   ((bg/entry-form-current-otp
;;     db-key
;;     entry-uuid
;;     otp-field-name
;;     (fn [api-reponse]
;;       (when-let [{:keys [ttl]} (check-error api-reponse #())]
;;         (start-polling-ttl-otp-data db-key entry-uuid otp-field-name ttl))))))

(comment
  (-> @re-frame.db/app-db keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)
  (def entry-uuid "991c0ddc-2531-4ec1-96e2-580687d376da")
  (def otp-field-name "otp")
  (test-call db-key entry-uuid otp-field-name)

  (bg/entry-form-current-otp db-key entry-uuid otp-field-name #(println %))
  (start-polling-ttl-otp-data db-key entry-uuid otp-field-name 5))