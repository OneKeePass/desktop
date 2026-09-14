(ns onekeepass.frontend.otp-badge
  "The current token of an entry, shown on the secondary line of an entry list row.

   Kept apart from the entry form's otp field, which is a full text field with a countdown
   ring beside it. On a row the code shares the username's line so that the title keeps the
   full width, and a line that small has no room for a bar under the code, so the time left
   is a small ring beside it. The browser runs the ring's animation itself, so a row does no
   work between one token and the next."
  (:require [clojure.string :as str]
            [onekeepass.frontend.mui-components :refer [custom-theme-atom
                                                        mui-box
                                                        mui-typography
                                                        theme-color]]
            [reagent.core :as r]))

(def ^:private RING-SIZE 14)

(def ^:private RING-STROKE 2)

(def ^:private RING-RADIUS (/ (- RING-SIZE RING-STROKE) 2))

(def ^:private RING-CIRCUMFERENCE (* 2 js/Math.PI RING-RADIUS))

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

(defn- animate-ring
  "Runs the ring down from the share of the period the token has left to nothing, over
   exactly the time it has left. Returns the Animation so that it can be cancelled.

   The time left is worked out from 'expires-at' and the clock rather than from the ttl the
   backend replied with. That ttl is what was left at the moment of the fetch and is never
   updated afterwards, so a row mounting later - scrolled back into view, say - would read
   it as a full period and start the ring from the top.

   The arc is drawn by the stroke dash, and emptied by moving the dash offset out to the
   full circumference. The browser plays that itself, so no javascript runs while it does."
  [^js node expires-at period]
  (let [remaining-ms (max 0 (- expires-at (js/Date.now)))
        period-ms (* 1000 (max 1 (or period 1)))
        share (min 1 (/ remaining-ms period-ms))]
    (.animate node
              #js [#js {:strokeDashoffset (str (* RING-CIRCUMFERENCE (- 1 share)) "px")}
                   #js {:strokeDashoffset (str RING-CIRCUMFERENCE "px")}]
              #js {:duration remaining-ms
                   :easing "linear"
                   :fill "forwards"})))

(defn otp-badge
  "The token of one entry with the time it has left.

   'token-data' is a map of [token expires-at period], or nil when the entry has no code to
   show - in which case nothing is rendered at all, so a row without TOTP looks exactly as
   it did before."
  [_token-data]
  (let [ring-node (atom nil)
        animation (atom nil)
        ;; The expiry the running animation was started for. The ring is left alone until a
        ;; new one arrives. Keyed on the expiry rather than on the token so that a refresh
        ;; which happens to return the same token still restarts the ring
        started-for (atom nil)
        cancel-animation (fn []
                           (when-let [^js running @animation]
                             (.cancel running))
                           (reset! animation nil))
        ensure-animation (fn [{:keys [token expires-at period]}]
                           (when (and @ring-node token expires-at (not= expires-at @started-for))
                             (cancel-animation)
                             (reset! started-for expires-at)
                             (reset! animation (animate-ring @ring-node expires-at period))))
        ;; Defined once so that React does not detach and reattach the ref on every render
        set-ring-node (fn [node]
                        (reset! ring-node node)
                        (when (nil? node)
                          (cancel-animation)
                          (reset! started-for nil)))]

    (r/create-class
     {:display-name "otp-badge"

      ;; argv holds the component itself at the head, so the first argument is second.
      ;; React attaches refs before either of these runs, so the ring's node is in hand
      :component-did-mount
      (fn [this] (ensure-animation (second (r/argv this))))

      :component-did-update
      (fn [this _old-argv] (ensure-animation (second (r/argv this))))

      :component-will-unmount
      (fn [_this] (cancel-animation))

      :reagent-render
      (fn [{:keys [token]}]
        (when-not (str/blank? token)
          (let [center (/ RING-SIZE 2)]
            [mui-box {:component "span"
                      :sx {:display "inline-flex"
                           :align-items "center"
                           :flex-shrink 0
                           :gap "6px"
                           :ml 1}}
             [mui-typography {:component "span"
                              :variant "body2"
                              :sx {:color "text.primary"
                                   :letter-spacing "0.5px"
                                   :white-space "nowrap"
                                   :font-variant-numeric "tabular-nums"}}
              (formatted-token token)]

             ;; Rotated so that the arc is anchored at the top, then mirrored so that the
             ;; remaining arc sits to the left of it and empties clockwise, like a clock hand
             ;; sweeping round. That is also how the entry form's ring runs (it passes MUI a
             ;; negative value), and the two are on screen together
             [:svg {:width RING-SIZE
                    :height RING-SIZE
                    :view-box (str "0 0 " RING-SIZE " " RING-SIZE)
                    :style {:transform "scaleX(-1) rotate(-90deg)"
                            :flex-shrink 0}}
              [:circle {:cx center
                        :cy center
                        :r RING-RADIUS
                        :fill "none"
                        :stroke-width RING-STROKE
                        :stroke (theme-color @custom-theme-atom :divider-color1)}]
              [:circle {:ref set-ring-node
                        :cx center
                        :cy center
                        :r RING-RADIUS
                        :fill "none"
                        :stroke-width RING-STROKE
                        :stroke (theme-color @custom-theme-atom :primary-main)
                        :stroke-dasharray RING-CIRCUMFERENCE}]]])))})))
