(ns onekeepass.frontend.custom-icons
  "Provides custom icons based on the loaded svg files from backend resources"
  (:require
   [hickory.core :as hc]
   [onekeepass.frontend.events.custom-icons :as cie]
   [onekeepass.frontend.mui-components :as m :refer [mui-svg-icon mui-icon-vpn-key-outlined]]))

(set! *warn-on-infer* true)

(defn to-svg
  "Converts a valid svg xml string to hiccup formatted reagent component"
  [s]
  (first (map hc/as-hiccup (hc/parse-fragment s))))

(defn to-custom-icon
  "Returns a reagent component after forming a hiccup from svg str"
  [name]
  (if (= @(cie/custom-svg-icons-status) :done)
    (if-let [svg-str (cie/svg-icon-str name)]
      (let [h (to-svg svg-str)]
        (if (vector? h)
          [mui-svg-icon h]
          [mui-icon-vpn-key-outlined]))
      [mui-icon-vpn-key-outlined])
    [mui-icon-vpn-key-outlined]))

;; For now just one custom icon loaded using svg xml file as an example
;; We can use the same feature to load other svg based icons if required
(defn database-cog-outline []
  (to-custom-icon "database-cog-outline.svg"))

#_(defn file-cog-outline []
    (to-custom-icon "file-cog-outline.svg"))

(comment
  (def string->hickory (comp hc/as-hickory hc/parse))

  (string->hiccup "<p> Hello </p")
  ;; => ([:html {} [:head {}] [:body {} [:p {} " Hello "]]])

  (def test-svg   "<svg xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:cc=\"http://creativecommons.org/ns#\" xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:svg=\"http://www.w3.org/2000/svg\" xmlns=\"http://www.w3.org/2000/svg\" id=\"svg10\" enable-background=\"new 0 0 48 48\" viewBox=\"0 0 48 48\" version=\"1\"><defs id=\"defs14\"/><g style=\"fill:#ffa000\" transform=\"matrix(0.73333333,0,0,1,7.4259892,0)\" id=\"g6\"><polygon id=\"polygon2\" points=\"22,45 18,41 18,21 30,21 30,29 28,31 30,33 30,35 28,37 30,39 30,41 26,45\"/><path id=\"path4\" d=\"M38 7.8c-.5-1.8-2-3.1-3.7-3.6C31.9 3.7 28.2 3 24 3s-7.9.7-10.3 1.2C12 4.7 10.5 6 10 7.8c-.5 1.7-1 4.1-1 6.7s.5 5 1 6.7c.5 1.8 1.9 3.1 3.7 3.5 2.4.6 6.1 1.3 10.3 1.3 4.2.0 7.9-.7 10.3-1.2 1.8-.4 3.2-1.8 3.7-3.5s1-4.1 1-6.7c0-2.7-.5-5.1-1-6.8zM29 13H19c-1.1.0-2-.9-2-2V9c0-.6 3.1-1 7-1s7 .4 7 1v2c0 1.1-.9 2-2 2z\"/></g><rect style=\"fill:#d68600\" id=\"rect8\" height=\"19\" width=\"2\" y=\"26\" x=\"23.559322\"/></svg>")

  (def string->hiccup (comp hc/as-hiccup hc/parse))

  (map hc/as-hiccup (hc/parse-fragment "<p> Hello </p")) ;; => ([:p {} " Hello "])

  (map hc/as-hiccup (hc/parse-fragment test-svg)))