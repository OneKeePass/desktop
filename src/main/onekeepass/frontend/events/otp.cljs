(ns onekeepass.frontend.events.otp
  (:require
   [clojure.string :as str]
   [cljs.core.async :refer [go timeout <!]]
   [re-frame.core :refer [reg-fx reg-event-db reg-event-fx reg-sub  dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             active-db-key
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             fix-tags-selection-prefix]]

   [onekeepass.frontend.utils :as u]
   [onekeepass.frontend.background :as bg]))


(reg-fx
 :otp/bg-entry-form-current-otp-data
 (fn [[db-key entry-uuid otp-field-name callback-fn]]
   (bg/entry-form-current-otp db-key entry-uuid otp-field-name callback-fn)))

(declare start-polling-otp-fields)

(reg-fx
 :otp/start-polling-otp-fields
 (fn [[db-key entry-uuid otp-field-m]]
   ;; otp-field-m is a map with otp field name as key and token data as its value
   ;; See 'start-polling-otp-fields' fn
   (start-polling-otp-fields db-key entry-uuid otp-field-m)))

(reg-event-fx
 :otp/generate-current-token
 (fn [{:keys [db]} [_event-id]]
   {}))

(def entry-form-otp-ttl-timers (atom {}))

(def entry-form-otp-period-timers (atom {}))


(defn start-polling-period-otp-data [db-key entry-uuid otp-field-name period]
  ;; Need to clear and remove any previous timer for 'entry-uuid otp-field-name'
  (let [timer-id (js/setInterval  (fn []
                                    (bg/entry-form-current-otp db-key entry-uuid otp-field-name #(println %)))
                                  (* 1000 period))]

    (swap! entry-form-otp-period-timers assoc-in [entry-uuid otp-field-name] timer-id)))

(defn stop-all-entry-polling
  [entry-uuid]
  (let [period-timer-ids (-> @entry-form-otp-period-timers (get entry-uuid) vals)
        ttl-timer-ids (-> @entry-form-otp-ttl-timers (get entry-uuid) vals)]
    ;; doseq loops and returns nil whereas 'for' can also be used but it will return a collection
    (doseq [timer-id period-timer-ids]
      (js/clearInterval timer-id)
      (swap! entry-form-otp-period-timers dissoc entry-uuid))

    (doseq [timer-id ttl-timer-ids]
      (js/clearTimeout timer-id)
      (swap! entry-form-otp-ttl-timers dissoc entry-uuid))))

(defn stop-polling-otp-data
  "Stops any prior timers set for this entry and otp-field-name"
  [entry-uuid otp-field-name]
  (let [timer-id-period (-> @entry-form-otp-period-timers (get-in [entry-uuid otp-field-name]))
        timer-id-ttl (-> @entry-form-otp-ttl-timers (get-in [entry-uuid otp-field-name]))]
    ;; timer-id-period, timer-id-ttl may be nil
    (js/clearInterval timer-id-period)
    (swap! entry-form-otp-period-timers dissoc entry-uuid)

    (js/clearTimeout timer-id-ttl)
    (swap! entry-form-otp-ttl-timers dissoc entry-uuid)))


(defn start-polling-ttl-otp-data
  [db-key entry-uuid otp-field-name ttl]
  (println "start-polling-ttl-otp-data is called with ttl " ttl)
  (let [timer-id (js/setTimeout
                  (fn []
                    (bg/entry-form-current-otp
                     db-key
                     entry-uuid
                     otp-field-name
                     (fn [api-reponse]
                       (println "After ttl time expiry " api-reponse)
                       (when-let [{:keys [period]} (check-error api-reponse #())]
                         (start-polling-period-otp-data db-key entry-uuid otp-field-name period)))))
                  (* 1000 ttl))]

    (swap! entry-form-otp-ttl-timers assoc-in [entry-uuid otp-field-name] timer-id)))

;; otp-field-m => {"otps" {:period 30, :token "138063", :ttl 23}}}
(defn start-polling-otp-fields 
  "Receives all otp field names with its 'current-opt-token' (inner map) in the map otp-field-m
  "
  [db-key entry-uuid otp-field-m]
  (doseq [[otp-field-name opt-data]  otp-field-m]
    (start-polling-ttl-otp-data db-key entry-uuid otp-field-name (:ttl opt-data))))

(defn test-call [db-key entry-uuid otp-field-name]
  ((bg/entry-form-current-otp
    db-key
    entry-uuid
    otp-field-name
    (fn [api-reponse]
      (when-let [{:keys [ttl]} (check-error api-reponse #())]
        (start-polling-ttl-otp-data db-key entry-uuid otp-field-name ttl))))))

(comment
  (-> @re-frame.db/app-db keys)
  (def db-key (:current-db-file-name @re-frame.db/app-db))
  (-> @re-frame.db/app-db (get db-key) keys)
  (def entry-uuid "991c0ddc-2531-4ec1-96e2-580687d376da")
  (def otp-field-name "otp")
  (test-call db-key entry-uuid otp-field-name)

  (bg/entry-form-current-otp db-key entry-uuid otp-field-name #(println %))
  (start-polling-ttl-otp-data db-key entry-uuid otp-field-name 5))