(ns onekeepass.frontend.events.translation
  (:require [onekeepass.frontend.background :as bg]
            [onekeepass.frontend.events.common-supports
             :refer [check-error]]
            [re-frame.core :refer [dispatch
                                   reg-event-fx reg-fx]]))

;; Need to use onekeepass.frontend.events.common-supports instead of onekeepass.frontend.events.common here 
;; to avoid circular dependency issue
;; onekeepass.frontend.events.common -> onekeepass.frontend.translation -> 
;; onekeepass.frontend.events.translation -> onekeepass.frontend.events.common

(defn load-language-translation [language-ids callback-on-tr-load]
  (dispatch [:load-language-data-start language-ids callback-on-tr-load]))

(defn load-language-data-complete []
  (dispatch [:load-language-data-complete]))

#_(defn reload-language-data [language-ids callback-on-tr-load]
    (dispatch [:reload-language-data language-ids callback-on-tr-load]))

;; To avoid circular refrence between this ns onekeepass.frontend.events.translation and 
;; onekeepass.frontend.translation, we need to use this atom to call fns defined in 
;; onekeepass.frontend.events.translation ns
(def ^:private tr-service (atom {}))

(defn set-translator [tr-fns-m]
  (reset! tr-service tr-fns-m))

;; Called when the application starts
(reg-event-fx
 :load-language-data-start
 (fn [{:keys [_db]} [_event-id language-ids-vec callback-on-tr-load]]
   {:fx [;; Set the load status to false particularly during dev time 
         [:dispatch [:common/reset-load-language-translation-status]]
         [:bg-load-language-translations [language-ids-vec callback-on-tr-load]]]}))

;; Called after user changes the language selection
;; Not yet ued in desktop
#_(reg-event-fx
   :reload-language-data
   (fn [{:keys [_db]} [_event-id language-ids-vec callback-on-tr-load]]
     {:fx [[:dispatch [:common/reset-load-language-translation-status]]
           [:bg-load-language-translations [language-ids-vec callback-on-tr-load]]]}))

(reg-fx
 :bg-load-language-translations
 (fn [[language-ids-vec callback-on-tr-load]]
   (bg/load-language-translations language-ids-vec
                                  (fn [api-response]
                                    (let [r (check-error api-response)]
                                      (callback-on-tr-load r))))))

;; Called to indicate the translation data are loaded and vailable for using
(reg-event-fx
 :load-language-data-complete
 (fn [{:keys [db]} [_event-id]]
   {:fx [[:dispatch [:common/load-language-translation-complete]]]}))

(comment
  (in-ns 'onekeepass.frontend.events.translation)

  ;;@re-frame.db/app-db
  )