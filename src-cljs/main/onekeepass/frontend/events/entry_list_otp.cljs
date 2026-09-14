(ns onekeepass.frontend.events.entry-list-otp
  "Current tokens for the rows of the entry list.

   Deliberately not the entry form's polling. That starts a backend task for one entry and
   sends an event every second; a list would start one per row. Here nothing is polled: the
   tokens of the rows on screen are fetched in one call, and the next fetch is scheduled for
   the moment the earliest of them expires. Since token boundaries are aligned to the clock,
   entries of the same period expire together and the usual case is a single call per
   period however many rows are showing.

   The tokens live in app-db under [(active-db-key) :entry-list-otp :tokens], as the custom
   icon data-urls do. Being per database means closing one leaves another's codes alone, and
   that ':close-kdbx-completed' and the external change reload take the codes with them when
   they drop the database's state. Locking keeps that state, so the lock paths in
   events.common drop the codes themselves."
  (:require [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.events.common :refer [active-db-key
                                                       assoc-in-key-db
                                                       check-error
                                                       get-in-key-db]]
            [re-frame.core :refer [dispatch reg-event-fx reg-fx reg-sub subscribe]]))

;; Rows ask for their token as they render, so the asks are collected and sent as one call
;; once the list has settled instead of one call per row
(def ^:private BATCH-DELAY-MS 50)

;; Tokens expiring within this of each other are refreshed together. Entries sharing a
;; period expire on the same instant, and this absorbs the small spread in when their
;; timers actually fire
(def ^:private EXPIRY-GRACE-MS 300)

;; What is held for an entry under [:entry-list-otp :tokens entry-uuid] is either
;;   {:token :period :otp-field-name :expires-at}  - a code to show
;;   {:no-code true}                               - asked, and it has none
;; The second is what stops a row without TOTP asking again on every render. The reply's
;; 'ttl' is turned into a wall clock 'expires-at' and is not itself kept: it is only true at
;; the instant of the fetch, and anything reading it later reads a stale number.

;; Timers, and the record of what is already on its way. These stay out of app-db
;; deliberately - they are machinery for a moment rather than state, they mean nothing after
;; a reload, and holding them there would cost a dispatch per row per render
(defonce ^:private pending (atom #{}))

(defonce ^:private in-flight (atom #{}))

(defonce ^:private batch-timer (atom nil))

(defonce ^:private refresh-timer (atom nil))

(defn otp-token-data
  "What is held for an entry - a token to show, or a marker saying it has none.

   The badge renders nothing without a token, so the marker needs no handling there, and
   passing the same value back to 'ensure-otp-token' lets that tell 'never asked' from
   'asked, and it has none' without reaching into app-db from a render."
  [entry-uuid]
  (subscribe [:entry-list-otp-token entry-uuid]))

(reg-sub
 :entry-list-otp-token
 (fn [db [_query-id entry-uuid]]
   (get-in-key-db db [:entry-list-otp :tokens entry-uuid])))

(defn- cancel-timer [timer-atom]
  (when-let [id @timer-atom]
    (js/clearTimeout id))
  (reset! timer-atom nil))

(defn- schedule-refresh
  "Sets one timer for the earliest expiry held, replacing any timer already set"
  [held]
  (cancel-timer refresh-timer)
  (let [expiry-times (keep :expires-at (vals held))]
    (when (seq expiry-times)
      (let [wait (- (apply min expiry-times) (js/Date.now))]
        (reset! refresh-timer
                (js/setTimeout #(do (reset! refresh-timer nil)
                                    (dispatch [:entry-list-otp-refresh-expired]))
                               (max 0 wait)))))))

(reg-fx
 :entry-list-otp-schedule-refresh
 (fn [held]
   (schedule-refresh held)))

(defn- fetch-tokens [db-key entry-uuids]
  (when (and db-key (seq entry-uuids))
    (swap! in-flight into entry-uuids)
    (bg/entry-list-current-otps
     db-key
     entry-uuids
     (fn [api-response]
       (swap! in-flight #(apply disj % entry-uuids))
       ;; A row quietly showing no code is the right outcome of a failure here. The default
       ;; handler would raise an error snackbar over whatever the user is doing, for
       ;; something they never asked for
       (when-let [items (check-error api-response
                                     (fn [error]
                                       (js/console.warn "Could not get the entry list otp tokens:" error)))]
         (dispatch [:entry-list-otp-tokens-loaded db-key entry-uuids items]))))))

(reg-fx
 :entry-list-otp-bg-current-otps
 (fn [[db-key entry-uuids]]
   (fetch-tokens db-key entry-uuids)))

;; 'requested' is needed as well as the reply because an entry with no otp field, or one
;; whose field has since gone, is simply absent from the reply. Recording those as
;; ':no-code' both drops any stale token and stops the row asking again
(reg-event-fx
 :entry-list-otp-tokens-loaded
 (fn [{:keys [db]} [_event-id db-key requested items]]
   (if (or (not= db-key (active-db-key db))
           (get-in db [db-key :locked]))
     ;; The database was closed, locked or switched while the fetch was on its way. Its
     ;; codes are no longer wanted, and storing them now would either outlive the lock or
     ;; put them under whichever database happens to be active instead
     {}
     (let [now (js/Date.now)
           received (reduce (fn [m {:keys [entry-uuid otp-field-name token ttl period]}]
                              (assoc m entry-uuid {:token token
                                                   :period period
                                                   :otp-field-name otp-field-name
                                                   :expires-at (+ now (* 1000 ttl))}))
                            {} items)
           held (reduce (fn [m entry-uuid]
                          (assoc m entry-uuid (get received entry-uuid {:no-code true})))
                        (or (get-in-key-db db [:entry-list-otp :tokens]) {})
                        requested)]
       {:db (assoc-in-key-db db [:entry-list-otp :tokens] held)
        :fx [[:entry-list-otp-schedule-refresh held]]}))))

(reg-event-fx
 :entry-list-otp-refresh-expired
 (fn [{:keys [db]} [_event-id]]
   (let [held (get-in-key-db db [:entry-list-otp :tokens])
         cutoff (+ (js/Date.now) EXPIRY-GRACE-MS)
         expired (->> held
                      (filter (fn [[_uuid {:keys [expires-at]}]]
                                (and expires-at (<= expires-at cutoff))))
                      (mapv key))]
     (if (seq expired)
       {:fx [[:entry-list-otp-bg-current-otps [(active-db-key db) expired]]]}
       ;; Nothing is due yet - the timer fired early, or the database it was set for has
       ;; since been switched away from. Set it again against what is held now
       {:fx [[:entry-list-otp-schedule-refresh held]]}))))

;; Called when entries of the active database may have changed - an entry saved, a merge,
;; custom icons updated - all of which reload the entry list.
;; The ':no-code' markers are dropped, so a row whose entry has just been given a TOTP asks
;; again as it renders, and every code held is fetched again, so one whose otp url was
;; changed or removed does not linger until it expires
(reg-event-fx
 :entry-list-otp/entries-changed
 (fn [{:keys [db]} [_event-id]]
   (let [held (get-in-key-db db [:entry-list-otp :tokens])
         with-code (into {} (remove (fn [[_uuid v]] (:no-code v))) held)]
     (if (seq held)
       {:db (assoc-in-key-db db [:entry-list-otp :tokens] with-code)
        :fx [(when (seq with-code)
               [:entry-list-otp-bg-current-otps [(active-db-key db) (vec (keys with-code))]])]}
       {}))))

(defn- schedule-batch []
  (when (nil? @batch-timer)
    (reset! batch-timer
            (js/setTimeout #(do (reset! batch-timer nil)
                                (dispatch [:entry-list-otp-fetch-pending]))
                           BATCH-DELAY-MS))))

;; The fetch goes through an event only so that it can read the active db key from app-db
(reg-event-fx
 :entry-list-otp-fetch-pending
 (fn [{:keys [db]} [_event-id]]
   (let [uuids (vec @pending)]
     (reset! pending #{})
     {:fx [[:entry-list-otp-bg-current-otps [(active-db-key db) uuids]]]})))

(defn- needs-fetch? [entry-uuid held]
  (cond
    ;; A fetch for it is already on its way
    (or (contains? @pending entry-uuid) (contains? @in-flight entry-uuid))
    false

    ;; Never asked about this entry
    (nil? held)
    true

    ;; Asked, and it turned out to have no code. Nothing further to do
    (:no-code held)
    false

    ;; Holding a code whose life has run out without the refresh timer having fired - the
    ;; computer was asleep, or the timer was set against another database that was active
    ;; at the time
    :else
    (<= (:expires-at held) (js/Date.now))))

(defn ensure-otp-token
  "Asks for an entry's token when what is held for it is missing or has expired.

   'held' is what 'otp-token-data' gave the caller, so the decision is made from the value
   the row already has. Safe to call from a render fn, as the custom icon fetch is - a row
   asking again for a token that is present and still live does nothing."
  [entry-uuid held]
  (when (and entry-uuid (needs-fetch? entry-uuid held))
    (swap! pending conj entry-uuid)
    (schedule-batch)))
