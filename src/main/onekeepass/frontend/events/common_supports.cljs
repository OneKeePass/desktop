(ns onekeepass.frontend.events.common-supports
  (:require
   [re-frame.core :refer [dispatch]]))

;; Introduced onekeepass.frontend.events.common-supports here 
;; to avoid circular dependency issue while using 'translation' in onekeepass.frontend.events.common
;; onekeepass.frontend.events.common -> onekeepass.frontend.translation -> 
;; onekeepass.frontend.events.translation -> onekeepass.frontend.events.common


;;TODO: 
;; Need to add somethings similar to the following so that translations fns can be called here 
;; This is required to avoid the above said circular references
;; set-translator should be called in onekeepass.frontend.translation ??
;; Till that time, we can't use any translation fns from ns onekeepass.frontend.translation here

#_(def ^:private tr-service (atom {}))
#_(defn set-translator [tr-fns-m]
    (reset! tr-service tr-fns-m))

(defn check-error
  "Receives a map with keys result and error or either one.
   Returns the value of result in case there is no error. If there is 
   an error a nil value is returned and calls the supplied error fn or default error fn
  "
  ([{:keys [result error]} error-fn]
   (if-not (nil? error)
     (do
       (if (nil? error-fn)
         (dispatch [:common/message-snackbar-error-open error])
         (error-fn error))
       nil)
     result))
  ([api-response]
   (check-error api-response nil)))

(defn on-error
  "A common error handler for the background API call.
  logs the error and returns true in case of error or false
  "
  ([{:keys [error]} error-fn]
   (if-not (nil? error)
     (do
       (if  (nil? error-fn)
         #_(println "API returned error: " error)
         ;; Should we use a generic error dialog instead of snackbar ?
         (dispatch [:common/message-snackbar-error-open error])
         (error-fn error))
       true)
     false))
  ([api-response]
   (on-error api-response nil)))