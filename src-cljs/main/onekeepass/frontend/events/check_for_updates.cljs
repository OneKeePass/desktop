(ns onekeepass.frontend.events.check-for-updates
  (:require
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :refer [check-error]]
   [re-frame.core :refer [dispatch reg-event-fx]]))

(reg-event-fx
 :check-for-updates/start
 (fn [{:keys [_db]} [_event-id {:keys [silent?]}]]
   (bg/check-for-updates
    (fn [api-response]
      (when-let [result (check-error
                         api-response
                         (fn [error]
                           (if silent?
                             (js/console.warn "Silent update check failed:" error)
                             (dispatch [:common/message-snackbar-error-open error]))))]
        (dispatch [:check-for-updates-success {:silent? silent? :result result}]))))
   {}))

(reg-event-fx
 :check-for-updates-success
 (fn [{:keys [_db]} [_event-id {:keys [silent? result]}]]
   ;; The Rust side returns `updateAvailable` (camelCase). invoke-api
   ;; kebab-cases it to :update-available — note: no `?` suffix.
   (let [{:keys [update-available
                 current-version
                 latest-version
                 release-notes
                 download-url]} result]
     (cond
       update-available
       {:fx [[:dispatch
              [:generic-dialog-show-with-state :check-for-updates-dialog
               {:data {:update-available? true
                       :current-version current-version
                       :latest-version latest-version
                       :release-notes release-notes
                       :download-url download-url
                       :silent? silent?}}]]]}

       silent?
       {}

       :else
       {:fx [[:dispatch
              [:generic-dialog-show-with-state :check-for-updates-dialog
               {:data {:update-available? false
                       :current-version current-version
                       :latest-version latest-version
                       :silent? silent?}}]]]}))))

(defn start-silent-check
  "Triggers a background update check that only surfaces UI when a newer
   release exists. Intended for app startup."
  []
  (dispatch [:check-for-updates/start {:silent? true}]))
