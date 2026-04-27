(ns onekeepass.frontend.events.custom-icons
  "All events realted to the custom icons resource loading"
  (:require
   [re-frame.core :refer [reg-event-db 
                          reg-sub
                          dispatch
                          subscribe]]
   ;;[hickory.core :as hc]
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :as cmn-events]))



(defn custom-svg-icons-status []
  (subscribe [:custom-svg-icons-status]))

;; (defn to-svg [s]
;;   (map hc/as-hiccup (hc/parse-fragment s)))

(defonce custom-icons (atom {}))

(defn svg-icon-str [name]
  (get @custom-icons name))

(defn load-handle-icons-response [api-response]
  (reset! custom-icons (cmn-events/check-error api-response))
  (dispatch [:custom-icons-loading-done]))

;; Called just before rendering the main window
(reg-event-db 
 :custom-icons/load-custom-icons
 (fn [db [_event-id]]
   (bg/load-custom-svg-icons load-handle-icons-response)
   (assoc db :custom-svg-icons-status :loading)))

(reg-event-db 
 :custom-icons-loading-done
 (fn [db [_event-id]]
   (assoc db :custom-svg-icons-status :done)))

(reg-sub
 :custom-svg-icons-status
 (fn [db _query-vec]
   (:custom-svg-icons-status db)))