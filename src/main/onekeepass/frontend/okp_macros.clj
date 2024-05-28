(ns onekeepass.frontend.okp-macros)

;; (macroexpand-1 '(as-map [a b])) => {:a a, :b b}
;; Here variables a and b are already set in the calling site
(defmacro as-map
  "Returns a map using the passed variable names"
  [variable-names]
  (reduce (fn [m s] (assoc m (keyword s) s)) {} variable-names))