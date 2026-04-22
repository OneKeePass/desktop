(ns onekeepass.frontend.events.common-supports
  (:require
   [re-frame.core :refer [dispatch]]))

;; Introduced onekeepass.frontend.events.common-supports here 
;; to avoid circular dependency issue while using 'translation' in onekeepass.frontend.events.common
;; onekeepass.frontend.events.common -> onekeepass.frontend.translation -> 
;; onekeepass.frontend.events.translation -> onekeepass.frontend.events.common


;; Following is done so that translations fns can be called here 
;; This is required to avoid the above said circular references
;; set-translator should be called in onekeepass.frontend.translation ??
;; Without this tr fns mapping, we can't use any translation fns from ns onekeepass.frontend.translation here

(def ^:private tr-service (atom {}))

(defn set-translator [tr-fns-m]
  (reset! tr-service tr-fns-m))

(defn- lstr-sm [txt-key]
  ((:lstr-sm @tr-service) txt-key))

(defn- lstr-error-sm [txt-key]
  ((:lstr-error-sm @tr-service) txt-key))

;; An example to show when the backend core returns error from
;; Error::DataError ("ErrorEntryUuidExistsInTarget")
(defn error-to-msg-text [error]
  (cond
    (= error "ErrorEntryUuidExistsInTarget")
    (lstr-error-sm 'entryUuidExistsInTarget)
    ;;"An entry with the same uuid already exists in the target database"

    (= error "ErrorGroupUuidExistsInTarget")
    (lstr-error-sm 'groupUuidExistsInTarget)
    ;;"A group with the same uuid already exists in the target database"

    :else
    error))

(defn check-error
  "Receives a map with keys :result and :error or either one.
   If the map has a valid value in :result, then value in :error is nil
   If the map has some error value in :error, then value in :result is nil
   
   Returns the value of result in case there is no error. 
   If there is an error, a nil value is returned and calls the supplied 
   error fn or default error fn
  "
  ([{:keys [result error]} error-fn]
   (if-not (nil? error)
     (do
       (if (nil? error-fn)
         (do
           (dispatch [:common/message-snackbar-error-open (error-to-msg-text error)])
           (dispatch [:common/progress-message-box-hide]))
         (do
           (error-fn error)
           (dispatch [:common/progress-message-box-hide])))
       nil)
     result))
  ([api-response]
   (check-error api-response nil)))

(defn on-error
  "Receives a map with keys :result and :error or either one.
   Returns true in case of error and calls an 'error-fn' with the error 
   Returns false when error is nil
  "
  ([{:keys [error]} error-fn]
   (if-not (nil? error)
     (do
       ;; Ensure that we hide this message box 
       (dispatch [:common/progress-message-box-hide])
       (if  (nil? error-fn)
         #_(println "API returned error: " error)
         ;; Should we use a generic error dialog instead of snackbar ?
         (dispatch [:common/message-snackbar-error-open error])
         (error-fn error))
       true)
     false))
  ([api-response]
   (on-error api-response nil)))