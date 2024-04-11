(ns onekeepass.frontend.events.otp
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-fx]]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error]]
   [onekeepass.frontend.background :as bg]))

(reg-fx
 :otp/start-polling-otp-fields
 (fn [[db-key previous-entry-uuid entry-uuid otp-field-m]]
   ;;(println "Empty :otp/start-polling-otp-fields")
   
   ;;(println "previous-entry-uuid entry-uuid otp-field-m are " previous-entry-uuid entry-uuid otp-field-m)
   ;; otp-field-m is a map with otp field name as key and token data as its value
   ;; See 'start-polling-otp-fields' fn
   #_(let [fields-m (into {} (filter (fn [[_k v]] (not (nil? v))) otp-field-m))]
       (if (boolean (seq fields-m))
         (bg/start-polling-entry-otp-fields
          db-key previous-entry-uuid
          entry-uuid fields-m #(on-error %))
         (bg/stop-polling-all-entries-otp-fields
          db-key
          #(on-error %))))
   
   ))

;; Stops all otp polling of an entry form
;; db-key should not be nil 
;; Api call will fail if db-key is nil
(reg-fx
 :otp/stop-all-entry-form-polling
 (fn [[db-key dispatch-fn]]
   ;;(println "Empty :otp/stop-all-entry-form-polling")
   ;;(println "Stopping form polling for db-key " (last (str/split db-key "/")))
   #_(bg/stop-polling-all-entries-otp-fields db-key (if-not (nil? dispatch-fn) dispatch-fn #(on-error %)))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (def entry-form-otp-field-timers (atom {}))

;; (defn start-polling-entry-form-otp-field
;;   "Starts the timer for a given otp field token generation and indicator update"
;;   [db-key entry-uuid otp-field-name {:keys [period ttl]}]
;;   (let [remaining (atom ttl)
;;         ;; Timer that polls every second and makes call to the backgend generate token api
;;         ;; when the ttl expires
;;         timer-id (js/setInterval
;;                   ;; This callbasck is called every sec
;;                   (fn []
;;                     (let [rem (dec @remaining)]
;;                       (if (= rem 0)
;;                         (do
;;                           (reset! remaining period)
;;                           ;; Backend call
;;                           (bg/entry-form-current-otp
;;                            db-key
;;                            entry-uuid
;;                            otp-field-name
;;                            ;; Backend call result handler fn 
;;                            (fn [api-response]
;;                              (when-let [current-opt-token
;;                                         (check-error api-response #(println "Error in getting currrent otp token" %))]
;;                                #_(dispatch [:entry-form/update-opt-ttl-indicator otp-field-name @remaining])
;;                                (dispatch [:entry-form/update-otp-token entry-uuid otp-field-name current-opt-token])))))
;;                         (do
;;                           (reset! remaining rem)
;;                           (dispatch [:entry-form/update-opt-ttl-indicator otp-field-name rem]))))) 1000)]
;;     ;; Timer id is stored to clear the time later
;;     (swap! entry-form-otp-field-timers assoc-in [entry-uuid otp-field-name] timer-id)))

;; (defn start-polling-entry-form-otp-fields
;;   "Receives all otp field names with its 'current-opt-token' (inner map) in the map otp-field-m
;;   "
;;   [db-key entry-uuid otp-field-m]
;;   (doseq [[otp-field-name opt-data] otp-field-m]
;;     (start-polling-entry-form-otp-field db-key entry-uuid otp-field-name opt-data)))

;; (defn stop-polling-entry-form-otp-fields
;;   [entry-uuid]
;;   (let [timer-ids (-> @entry-form-otp-field-timers (get entry-uuid) vals)]
;;     ;; doseq loop is used which returns nil whereas 'for' can also be used but it will return a collection
;;     (doseq [timer-id timer-ids]
;;       (js/clearInterval timer-id)
;;       (swap! entry-form-otp-field-timers dissoc entry-uuid))))

;; (reg-fx
;;  :otp/start-polling-otp-fields
;;  (fn [[db-key previous-entry-uuid entry-uuid otp-field-m]]
;;    (when-not (nil? previous-entry-uuid)
;;      (stop-polling-entry-form-otp-fields previous-entry-uuid))

;;    ;; Any previous polling for 'entry-uuid' needs to be stopped
;;    (stop-polling-entry-form-otp-fields entry-uuid)

;;    ;; otp-field-m is a map with otp field name as key and token data as its value
;;    ;; See 'start-polling-otp-fields' fn
;;    (let [fields-m (into {} (filter (fn [[_k v]] (not (nil? v))) otp-field-m))]
;;      (when (boolean (seq fields-m))
;;        (start-polling-entry-form-otp-fields db-key entry-uuid fields-m)))))

;; (reg-fx
;;  :otp/stop-all-entry-form-polling
;;  (fn []
;;    (doseq [uuid (keys @entry-form-otp-field-timers)]
;;      (stop-polling-entry-form-otp-fields uuid))))

;; (reg-fx
;;  :otp/stop-entry-form-polling
;;  (fn [[entry-uuid]]
;;    (stop-polling-entry-form-otp-fields entry-uuid)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (-> @re-frame.db/app-db keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)
  (def entry-uuid "991c0ddc-2531-4ec1-96e2-580687d376da")
  (def otp-field-name "otp")
  (test-call db-key entry-uuid otp-field-name)

  (bg/entry-form-current-otp db-key entry-uuid otp-field-name #(println %))
  (start-polling-ttl-otp-data db-key entry-uuid otp-field-name 5))