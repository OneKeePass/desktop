(ns onekeepass.frontend.context-menu
  (:require
   [onekeepass.frontend.background :as bg]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.mui-components :refer [mui-divider mui-menu mui-menu-item]]
   [reagent.core :as r]))

(set! *warn-on-infer* true)

(def ^:private menu-state
  (r/atom {:open false
           :position nil
           :items []
           :target-el nil
           :selection nil}))

(def ^:private global-text-menu-handler (atom nil))
(def ^:private global-copy-handler (atom nil))

(defn editable-target-element
  "Returns the closest editable element for the event target, if any."
  [^js/Event e]
  (some-> (.-target e)
          (.closest "input, textarea, [contenteditable='true'], [contenteditable=''], [contenteditable='plaintext-only']")))

(defn editable-target?
  [^js/Event e]
  (boolean (editable-target-element e)))

(defn separator-item []
  {:item "Separator"})

(defn predefined-item
  ([item-type]
   {:item item-type})
  ([item-type extra]
   (assoc extra :item item-type)))

(defn action-item
  [{:keys [id text enabled? action]}]
  {:id id
   :text text
   :enabled (if (nil? enabled?) true enabled?)
   :action action})

(defn- close-context-menu! []
  (reset! menu-state {:open false
                      :position nil
                      :items []
                      :target-el nil
                      :selection nil}))

(defn- focus-target! [target-el]
  (when target-el
    (.focus target-el)))

(defn- input-like?
  [target-el]
  (or (= "INPUT" (.-tagName target-el))
      (= "TEXTAREA" (.-tagName target-el))))

(defn- sensitive-copy-target?
  "Returns true when the editable target is a protected entry field."
  [target-el]
  (= "true" (.getAttribute target-el "data-okp-sensitive-copy")))

(defn- contenteditable?
  [target-el]
  (some? (.getAttribute target-el "contenteditable")))

(defn- current-selection
  [target-el]
  (cond
    (input-like? target-el)
    {:type :input
     :start (or (.-selectionStart target-el) 0)
     :end (or (.-selectionEnd target-el) 0)}

    (contenteditable? target-el)
    {:type :contenteditable
     :text (str (.toString (.getSelection js/window)))}

    :else
    {:type :unknown}))

(defn- selected-text
  [target-el {:keys [type start end text]}]
  (cond
    (= type :input)
    (let [value (or (.-value target-el) "")]
      (.slice value start end))

    (= type :contenteditable)
    (or text "")

    :else
    ""))

(defn- selected-input-text
  "Returns selected text from input/textarea targets, or nil when no text is selected."
  [target-el]
  (when (input-like? target-el)
    (let [start (.-selectionStart target-el)
          end (.-selectionEnd target-el)
          value (or (.-value target-el) "")]
      (when (and start end (< start end))
        (.slice value start end)))))

(defn- selected-copy-text
  "Returns text that native copy will copy from an editable target."
  [target-el]
  (cond
    (input-like? target-el)
    (selected-input-text target-el)

    (contenteditable? target-el)
    (not-empty (str (.toString (.getSelection js/window))))

    :else
    nil))

(defn- replace-input-selection!
  [target-el {:keys [start end]} insert-text]
  (let [value (or (.-value target-el) "")
        prefix (.slice value 0 start)
        suffix (.slice value end)
        next-value (str prefix insert-text suffix)
        next-caret (+ start (count insert-text))]
    (set! (.-value target-el) next-value)
    (when (fn? (.-setSelectionRange target-el))
      (.setSelectionRange target-el next-caret next-caret))
    (.dispatchEvent target-el (js/Event. "input" #js {:bubbles true}))))

(defn- copy-selection!
  "Copies text from our custom context menu and applies protected-field handling."
  [target-el selection]
  (let [text (selected-text target-el selection)
        sensitive-copy? (sensitive-copy-target? target-el)]
    (if (and sensitive-copy? (not (empty? text)))
      (cmn-events/write-sensitive-to-clipboard text)
      (cmn-events/write-to-clipboard text))))

(defn- cut-selection!
  [target-el selection]
  (copy-selection! target-el selection)
  (when (= :input (:type selection))
    (replace-input-selection! target-el selection "")))

(defn- paste-into-selection!
  [target-el selection]
  (bg/read-from-clipboard
   (fn [clipboard-text]
     (when (= :input (:type selection))
       (replace-input-selection! target-el selection (or clipboard-text ""))))))

(defn- select-all!
  [target-el]
  (focus-target! target-el)
  (if (fn? (.-select target-el))
    (.select target-el)
    (.execCommand js/document "selectAll")))

(defn- popup-position [^js/Event e]
  {:left (.-clientX e)
   :top (.-clientY e)})

(defn- item->display-item
  [target-el selection {:keys [item] :as menu-item}]
  (case item
    "Separator"
    (assoc menu-item :kind :separator)

    "Undo"
    {:id "undo" :kind :action :text "Undo" :enabled true :action #(.execCommand js/document "undo")}

    "Redo"
    {:id "redo" :kind :action :text "Redo" :enabled true :action #(.execCommand js/document "redo")}

    "Cut"
    {:id "cut" :kind :action :text "Cut" :enabled true :action #(cut-selection! target-el selection)}

    "Copy"
    {:id "copy" :kind :action :text "Copy" :enabled true :action #(copy-selection! target-el selection)}

    "Paste"
    {:id "paste" :kind :action :text "Paste" :enabled true :action #(paste-into-selection! target-el selection)}

    "SelectAll"
    {:id "select-all" :kind :action :text "Select All" :enabled true :action #(select-all! target-el)}

    (assoc menu-item :kind :action :enabled (:enabled menu-item true))))

(defn show-app-context-menu!
  [^js/Event e items]
  (let [target-el (.-target e)
        selection (current-selection target-el)]
    (.preventDefault e)
    (.stopPropagation e)
    (reset! menu-state {:open true
                        :position (popup-position e)
                        :items (mapv #(item->display-item target-el selection %) (remove nil? items))
                        :target-el target-el
                        :selection selection})))

(defn show-text-edit-context-menu!
  [^js/Event e]
  (when-let [target-el (editable-target-element e)]
    (focus-target! target-el)
    (let [read-only? (or (true? (.-disabled target-el))
                         (true? (.-readOnly target-el))
                         (= "false" (.getAttribute target-el "contenteditable")))
          items (vec
                 (remove nil?
                         [(predefined-item "Undo")
                          (predefined-item "Redo")
                          (separator-item)
                          (when-not read-only? (predefined-item "Cut"))
                          (predefined-item "Copy")
                          (when-not read-only? (predefined-item "Paste"))
                          (separator-item)
                          (predefined-item "SelectAll")]))]
      (let [selection (current-selection target-el)]
        (.preventDefault e)
        (.stopPropagation e)
        (reset! menu-state {:open true
                            :position (popup-position e)
                            :items (mapv #(item->display-item target-el selection %) (remove nil? items))
                            :target-el target-el
                            :selection selection}))
      true)))

(defn install-global-text-context-menu! []
  ;; Our custom text context menu handles right-click Copy/Paste.
  (when-not @global-text-menu-handler
    (let [handler (fn [^js/Event e]
                    (show-text-edit-context-menu! e))]
      (.addEventListener js/document "contextmenu" handler)
      (reset! global-text-menu-handler handler)))
  ;; Native Edit > Copy and Cmd/Ctrl+C bypass the custom menu, so observe
  ;; copy events without preventing the browser/webview default copy action.
  (when-not @global-copy-handler
    (let [handler (fn [^js/Event e]
                    (when-let [target-el (editable-target-element e)]
                      (when-let [text (selected-copy-text target-el)]
                        (cmn-events/notify-copied-to-clipboard)
                        (when (sensitive-copy-target? target-el)
                          (cmn-events/schedule-sensitive-clipboard-clear text)))))]
      (.addEventListener js/document "copy" handler true)
      (reset! global-copy-handler handler))))

(defn uninstall-global-text-context-menu! []
  ;; Keep global listeners paired with the React effect lifecycle.
  (when-let [handler @global-text-menu-handler]
    (.removeEventListener js/document "contextmenu" handler)
    (reset! global-text-menu-handler nil))
  (when-let [handler @global-copy-handler]
    (.removeEventListener js/document "copy" handler true)
    (reset! global-copy-handler nil)))

(defn context-menu-root []
  (let [{:keys [open position items]} @menu-state]
    [mui-menu {:open open
               :on-close close-context-menu!
               :anchorReference "anchorPosition"
               :anchorPosition position}
     (doall
      (map-indexed
       (fn [idx {:keys [id kind text enabled action]}]
         (if (= kind :separator)
           ^{:key (str "separator-" idx)} [mui-divider]
           ^{:key (or id (str "item-" idx))}
           [mui-menu-item {:disabled (not enabled)
                           :on-click (fn [^js/Event evt]
                                       (close-context-menu!)
                                       (when action
                                         (action))
                                       (.stopPropagation evt))}
            text]))
       items))]))
