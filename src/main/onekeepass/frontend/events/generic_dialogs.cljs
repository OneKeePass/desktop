(ns onekeepass.frontend.events.generic-dialogs
  "All common dialog events that are used across many pages"
  (:require-macros [onekeepass.frontend.okp-macros
                    :refer  [defn-generic-dialog-disp-events
                             defn-generic-dialog-subs-events]])
  (:require [re-frame.core :refer [reg-event-fx reg-sub dispatch subscribe]]
            [onekeepass.frontend.utils :as u]))


(def GENERIC-DIALOGS :generic-dialogs)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Copied from the impl used in mobile specific cljs file 'onekeepass/mobile/events/dialogs.cljs'
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  merge-result-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; dialog-identifier-kw :merge-result-dialog

(defn-generic-dialog-disp-events merge-result-dialog  [;; The arg 'close' means there is a re-frame event ending in 'close'
                                                       ;; The second arg is passed to that event
                                                       [close nil]
                                                       [show-with-state state-m]
                                                       #_[init state-m]
                                                       #_[update-and-show state-m]
                                                       #_[show nil]])

;; a subscribe event wrapper
(defn-generic-dialog-subs-events merge-result-dialog [[data nil]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  move-group-or-entry-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; dialog-identifier-kw :move-group-or-entry-dialog

(defn-generic-dialog-disp-events move-group-or-entry-dialog  [[close nil]
                                                              [show-with-state state-m]
                                                              [update-with-map state-m]
                                                              #_[init state-m]
                                                              #_[update-and-show state-m]
                                                              #_[show nil]])

;; a subscribe event wrapper
(defn-generic-dialog-subs-events move-group-or-entry-dialog [[data nil]])



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  csv-columns-mapping-dialog   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;
; dialog-identifier-kw :csv-columns-mapping-dialog

(defn-generic-dialog-disp-events :csv-columns-mapping-dialog  [[close nil]
                                                               [set-api-error error]
                                                               [show-with-state state-m]
                                                               [update-with-map state-m]])


(defn-generic-dialog-subs-events :csv-columns-mapping-dialog [[data nil]])

;;;;;;

(defn-generic-dialog-disp-events :csv-imoprt-start-dialog [[close nil]])


(defn-generic-dialog-subs-events :csv-imoprt-start-dialog [[data nil]])


;;;;;;

(defn-generic-dialog-disp-events :csv-mapping-completed-dialog [[close nil]])


(defn-generic-dialog-subs-events :csv-mapping-completed-dialog [[data nil]])



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- init-dialog-map
  "Called to initialize fields of a dialog identified by 'dialog-identifier-kw' 
   Returns a map which is set as intial values of these fieldds"
  []
  (-> {}
      (assoc-in [:dialog-show] false)
      (assoc-in [:title] nil)
      (assoc-in [:confirm-text] nil)
      (assoc-in [:actions] [])
      ;; (assoc-in [:call-on-ok-fn] #())
      ;; (assoc-in [:dispatch-on-ok] {})
      (assoc-in [:error-fields] {})
      (assoc-in [:api-error-text] nil)
      (assoc-in [:data] {})))

;; new-dialog-state is a map
(defn- set-dialog-state
  "The arg 'new-dialog-state' is a map (keys similar to ones listed in 'init-dialog-map' fn)
   Returns the updated map"
  [db dialog-identifier-kw new-dialog-state]
  (assoc-in db [GENERIC-DIALOGS dialog-identifier-kw] new-dialog-state))

;; Called to initialize all fields of a dialog identified by 'dialog-identifier-kw'
;; The arg 'dialog-state' may have no :dialog-show key (default is false) or :dialog-show false 
;; If we want to initialize and show dialog, then the event ':generic-dialog-show-with-state' should be used
(reg-event-fx
 :generic-dialog-init
 (fn [{:keys [db]} [_event-id dialog-identifier-kw dialog-state]]
   (let [final-dialog-state (init-dialog-map)
         final-dialog-state (u/deep-merge final-dialog-state dialog-state) #_(merge final-dialog-state dialog-state)]
     {:db (set-dialog-state db dialog-identifier-kw final-dialog-state)})))

;; Shows the dialog
;; Called using macro a wrapper fn is created in this ns
;; May be called through (dispatch [:generic-dialog-show dialog-identifier-kw]) from other fx events
(reg-event-fx
 :generic-dialog-show
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   (let [db (assoc-in db [GENERIC-DIALOGS dialog-identifier-kw :dialog-show] true)]
     {:db db})))

;; Initializes the dialog data with the initial state map,
;; updates the state with passed arg 'state-m' and shows the dialog  

;; Typically called through a wrapper fn '(dialog-identifier-show-with-state state-m)'
;; which in turns call
;; (dispatch [:generic-dialog-show-with-state :dialog-identifier state-m])

(reg-event-fx
 :generic-dialog-show-with-state
 (fn [{:keys [_db]} [_event-id dialog-identifier-kw state-m]]
   {:fx [[:dispatch [:generic-dialog-init dialog-identifier-kw (assoc state-m :dialog-show true)]]]}))

;; The event arg is a map  
;; See event :generic-dialog-update where the event arg is a vec

;; state is a map having keys as in 'init-dialog-map'
;; Here we are assuming the 'dialog-state' for the given 'dialog-identifier-kw' is already initialized
;; if the arg 'state' has key ':dialog-show true', then the dialog will be shown
(reg-event-fx
 :generic-dialog-update-with-map
 (fn [{:keys [db]} [_event-id dialog-identifier-kw state]]
   (let [dialog-state (get-in db [GENERIC-DIALOGS dialog-identifier-kw])
         dialog-state (u/deep-merge dialog-state state)]
     {:db (-> db (assoc-in [GENERIC-DIALOGS dialog-identifier-kw] dialog-state))})))

;; The event arg is a vec  - [kws-v value]
;; See event :generic-dialog-update-with-map where the event arg is a map

(reg-event-fx
 :generic-dialog-update
 (fn [{:keys [db]} [_event-id dialog-identifier-kw [kws-v value]]]
   (let [db (assoc-in db (into [GENERIC-DIALOGS dialog-identifier-kw] (if (vector? kws-v)
                                                                        kws-v
                                                                        [kws-v])) value)
         ;; For now clear any previous errors set
         db (assoc-in db [GENERIC-DIALOGS dialog-identifier-kw :error-fields] {})]
     {:db db})))

(reg-event-fx
 :generic-dialog-set-api-error
 (fn [{:keys [db]} [_event-id dialog-identifier-kw error]]
   {:db (-> db (assoc-in [GENERIC-DIALOGS dialog-identifier-kw :api-error-text] error))}))

;; Called using macro a wrapper fn is created in this ns
;; May be called through (dispatch [:generic-dialog-close dialog-identifier-kw]) from other fx events

(reg-event-fx
 :generic-dialog-close
 (fn [{:keys [db]} [_event-id dialog-identifier-kw]]
   ;;(println ":generic-dialog-close is called " dialog-identifier-kw)
   {:db (assoc-in db [GENERIC-DIALOGS dialog-identifier-kw] (init-dialog-map))}))


;; Called using macro a wrapper fn is created in this ns
(reg-sub
 :generic-dialog-data
 (fn [db [_event-id dialog-identifier-kw]]
   (let [dlg-data (get-in db [GENERIC-DIALOGS dialog-identifier-kw])
         dlg-data (if-not (nil? dlg-data) dlg-data (init-dialog-map))]
     dlg-data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;  id based dialog ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; 

;; (defn dialog-with-id-show [dialog-id-kw]
;;   (dispatch [:generic-dialog-show dialog-id-kw]))

;; (defn dialog-with-id-close [dialog-id-kw]
;;   (dispatch [:generic-dialog-close dialog-id-kw]))

;; (defn dialog-with-id-update-with-map [dialog-id-kw state-m]
;;   (dispatch [:generic-dialog-update-with-map dialog-id-kw state-m]))

;; (defn dialog-with-id-show-with-state [dialog-id-kw state-m]
;;   (dispatch [:generic-dialog-show-with-state dialog-id-kw state-m]))

;; (defn dialog-with-id-data [dialog-id-kw]
;;   (subscribe [:generic-dialog-data dialog-id-kw]))


(comment

  (-> @re-frame.db/app-db keys)

  ;; To see all fns defined by the macro call (def-generic-dialog-events...)
  ;; Use the following in a repl
  (require '[clojure.repl :refer [dir]])
  (dir onekeepass.frontend.events.generic-dialogs))

