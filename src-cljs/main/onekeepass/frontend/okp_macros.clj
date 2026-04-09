(ns onekeepass.frontend.okp-macros)

;; (macroexpand-1 '(as-map [a b])) => {:a a, :b b}
;; Here variables a and b are already set in the calling site
(defmacro as-map
  "The arg 'variable-names' is a vec of symbols. 
   Returns a map using the passed variable names"
  [variable-names]
  (reduce (fn [m s] (assoc m (keyword s) s)) {} variable-names))


;; 'dialog-events-by-name' is a helper fn
;; Though we are not using any gensym based variables here
(defn- dialog-events-by-name [dlg-name suffix args subscribe-event?]
  (let [dlg-name (if (symbol? dlg-name) dlg-name (symbol dlg-name))
        f-name (symbol (str dlg-name "-" (str suffix)))
        g-name (keyword (str "generic-dialog" "-" (str suffix)))
        dlg-id (keyword (str dlg-name))
        event-name (if subscribe-event? (symbol "re-frame.core/subscribe")  (symbol "re-frame.core/dispatch"))]
    (if (nil? args)
      `(defn ~f-name []
         (~event-name [~g-name ~dlg-id]))

      `(defn ~f-name [~args]
         (~event-name [~g-name ~dlg-id ~args])))))

;; We can see all functions defined by this macros call in a repl by using like 
;; (clojure.repl onekeepass.frontend.events.dialogs)

(defmacro def-generic-dialog-events
  "Generates all wrapper functions for a specific dialog events 
   dlg-name is a specific dialog name
   suffixes-with-args is a vector of vectors. 
     eg [[show nil] [close nil] [show-with-state args]]
   "
  [dlg-name suffixes-with-args subscribe-event?]
  `(do
     ~@(map (fn [[sx args]] (dialog-events-by-name dlg-name sx args subscribe-event?)) suffixes-with-args)))

(defmacro defn-generic-dialog-disp-events [dlg-name suffixes-with-args]
  `(def-generic-dialog-events ~dlg-name ~suffixes-with-args false))

(defmacro defn-generic-dialog-subs-events [dlg-name suffixes-with-args]
  `(def-generic-dialog-events ~dlg-name ~suffixes-with-args true))



(comment
  (macroexpand '(defn-generic-dialog-subs-events :setup-otp-action-kw-dialog [[data nil]]))

  (macroexpand '(defn-generic-dialog-subs-events setup-otp-action-sym-dialog [[data nil]]))

  (macroexpand '(defn-generic-dialog-disp-events :setup-otp-action-dialog [[show nil] [close nil] [update [k v]]]))

  (macroexpand '(def-generic-dialog-events setup-otp-action-dialog  [[show nil] [close nil] [show-with-state state-m]] false))

  ;; =>

  ;; (do
  ;;   (clojure.core/defn
  ;;     setup-otp-action-dialog-show
  ;;     []
  ;;     (re-frame.core/dispatch [:generic-dialog-show :setup-otp-action-dialog]))
  ;;   (clojure.core/defn
  ;;     setup-otp-action-dialog-close
  ;;     []
  ;;     (re-frame.core/dispatch [:generic-dialog-close :setup-otp-action-dialog]))
  ;;   (clojure.core/defn
  ;;     setup-otp-action-dialog-show-with-state
  ;;     [state-m]
  ;;     (re-frame.core/dispatch [:generic-dialog-show-with-state :setup-otp-action-dialog state-m])))
  )