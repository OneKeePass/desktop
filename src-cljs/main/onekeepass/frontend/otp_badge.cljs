(ns onekeepass.frontend.otp-badge
  "The current token of an entry, shown on a row of the entry list.

   Kept apart from the entry form's otp field, which is a full text field with a countdown
   ring beside it. A row has room for a code and little else, so the time left is shown by a
   hairline bar under the code. The browser runs the bar's animation itself, so a row does
   no work between one token and the next."
  (:require [clojure.string :as str]
            [onekeepass.frontend.mui-components :refer [custom-theme-atom
                                                        mui-box
                                                        mui-typography
                                                        theme-color]]
            [reagent.core :as r]))

;; Width the bar is drawn at. The bar sits under the code and is scaled horizontally, so
;; this is also the width the code is centred over
(def ^:private BAR-WIDTH 64)

(def ^:private BAR-HEIGHT 2)

(defn formatted-token
  "Groups digits with spaces between them for easy reading"
  [token]
  (let [len (count token)
        n (cond
            (or (= len 6) (= len 7) (= len 9))
            3

            (or (= len 8) (= len 10))
            4

            :else
            3)
        ;; step = n, pad = ""
        parts (partition n n "" token)
        parts (map (fn [c] (str/join c)) parts)
        spaced (str/join " " parts)]
    spaced))

(defn- animate-bar
  "Runs the bar down from the share of the period the token has left to nothing, over
   exactly the time it has left. Returns the Animation so that it can be cancelled.

   The time left is worked out from 'expires-at' and the clock rather than from the ttl the
   backend replied with. That ttl is what was left at the moment of the fetch and is never
   updated afterwards, so a row mounting later - scrolled back into view, say - would read
   it as a full period and start the bar from the top.

   Only the transform is animated, which the browser runs on its compositor without the
   page's javascript - that is what makes a bar per row free. Scaling rather than resizing is
   what keeps it there, as a width change would need a layout on every frame."
  [^js node expires-at period]
  (let [remaining-ms (max 0 (- expires-at (js/Date.now)))
        period-ms (* 1000 (max 1 (or period 1)))]
    (.animate node
              #js [#js {:transform (str "scaleX(" (min 1 (/ remaining-ms period-ms)) ")")}
                   #js {:transform "scaleX(0)"}]
              #js {:duration remaining-ms
                   :easing "linear"
                   :fill "forwards"})))

(defn otp-badge
  "The token of one entry with the time it has left.

   'token-data' is a map of [token expires-at period], or nil when the entry has no code to
   show - in which case nothing is rendered at all, so a row without TOTP looks exactly as
   it did before."
  [_token-data]
  (let [bar-node (atom nil)
        animation (atom nil)
        ;; The expiry the running animation was started for. The bar is left alone until a
        ;; new one arrives. Keyed on the expiry rather than on the token so that a refresh
        ;; which happens to return the same token still restarts the bar
        started-for (atom nil)
        cancel-animation (fn []
                           (when-let [^js running @animation]
                             (.cancel running))
                           (reset! animation nil))
        ensure-animation (fn [{:keys [token expires-at period]}]
                           (when (and @bar-node token expires-at (not= expires-at @started-for))
                             (cancel-animation)
                             (reset! started-for expires-at)
                             (reset! animation (animate-bar @bar-node expires-at period))))
        ;; Defined once so that React does not detach and reattach the ref on every render
        set-bar-node (fn [node]
                       (reset! bar-node node)
                       (when (nil? node)
                         (cancel-animation)
                         (reset! started-for nil)))]

    (r/create-class
     {:display-name "otp-badge"

      ;; argv holds the component itself at the head, so the first argument is second.
      ;; React attaches refs before either of these runs, so the bar's node is in hand
      :component-did-mount
      (fn [this] (ensure-animation (second (r/argv this))))

      :component-did-update
      (fn [this _old-argv] (ensure-animation (second (r/argv this))))

      :component-will-unmount
      (fn [_this] (cancel-animation))

      :reagent-render
      (fn [{:keys [token]}]
        (when-not (str/blank? token)
          [mui-box {:sx {:display "flex"
                         :flex-direction "column"
                         :align-items "center"
                         :flex-shrink 0
                         :ml 1}}
           [mui-typography {:variant "body1"
                            :sx {:color "text.secondary"
                                 :letter-spacing "0.5px"
                                 :white-space "nowrap"
                                 :font-variant-numeric "tabular-nums"}}
            (formatted-token token)]

           ;; The track stays put and clips the bar, which is scaled from its left edge so
           ;; that it empties towards the right the way a countdown reads
           [mui-box {:sx {:mt "3px"
                          :width BAR-WIDTH
                          :height BAR-HEIGHT
                          :border-radius "1px"
                          :overflow "hidden"
                          :bgcolor "divider"}}
            [:div {:ref set-bar-node
                   :style {:width "100%"
                           :height "100%"
                           :background-color (theme-color @custom-theme-atom :primary-main)
                           :transform-origin "left"}}]]]))})))
