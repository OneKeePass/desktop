(ns onekeepass.frontend.search
  (:require [clojure.string :as str]
            [onekeepass.frontend.common-components :refer [list-items-factory]]
            [onekeepass.frontend.db-icons :refer [entry-icon]]
            [onekeepass.frontend.events.common :as cmn-events]
            [onekeepass.frontend.events.search :as sch-event]
            [onekeepass.frontend.mui-components :as m :refer [mui-typography
                                                              mui-alert
                                                              mui-avatar
                                                              mui-button
                                                              mui-dialog
                                                              mui-dialog-actions
                                                              mui-dialog-content
                                                              mui-dialog-title
                                                              mui-icon-button
                                                              mui-icon-clear-outlined
                                                              mui-icon-more-vert
                                                              mui-input-adornment
                                                              mui-list-item-avatar
                                                              mui-list-item-button
                                                              mui-list-item-secondary-action
                                                              mui-list-item-text
                                                              mui-stack
                                                              mui-tooltip
                                                              get-theme-color]]
            [onekeepass.frontend.translation :refer [tr-dlg-title tr-l tr-h]]
            [reagent.core :as r]))

#_(set! *warn-on-infer* true)

(defn row-item
  "Renders a list item. 
  The arg 'props' is a map passed from 'fixed-size-list'
  "
  []
  (fn [props]
    (let [items  (sch-event/search-result-entry-items)
          item (nth @items (:index props))
          selected-id (sch-event/selected-entry-id)]
      ;; Need to use mui-list-item-button instead of mui-list-item so that focus to the list works
      ;; while using 'Tab' key. The other places the tab navigation works when mui-list-item is used
      ;; As per API doc mui-list-item autoFocus prop is deprecated and recommended to use mui-list-item-button
      [mui-list-item-button {:style (:style props)
                             :divider true
                             :value (:uuid item)
                             :onDoubleClick (fn []
                                              (sch-event/show-selected-entry-item (:uuid item)))
                             :on-click (fn [_e]
                                         (sch-event/search-selected-entry-id-update (:uuid item)))
                             :selected (if (= @selected-id (:uuid item)) true false)
                             ;; secondaryAction will work with mui-list-item-button and we need to use
                             ;; mui-list-item-secondary-action as last child in mui-list-item-button
                             }
       [mui-list-item-avatar
        [mui-avatar [entry-icon (:icon-id item)]]]
       [mui-list-item-text
        ;; We can use react node for primary and secondary props
        ;; e.g :primary (r/as-element [:span (str "Primary:" (:title  item))])
        {:primary (:title  item)  :secondary (:secondary-title item)}]

       (when (= @selected-id (:uuid item))
         [mui-list-item-secondary-action
          [mui-tooltip {:title "Show Details" :enterDelay 2000}
           [mui-icon-button {:edge "end"
                             :on-click (fn []
                                         (sch-event/show-selected-entry-item (:uuid item)))}
            [mui-icon-more-vert]]]])])))

(defn search-result-list-content [matched-entries]
  (let [result-items (list-items-factory matched-entries #_(sch-event/search-result-entry-items) row-item :list-style {})]
    [result-items]))

(defn- focus [^js/InputRef comp-ref]
  ;; calling  (.getElementById js/document "search_fld") will also work. But 'id' of input element 
  ;; should be unique
  #_(.focus (.getElementById js/document "search_fld"))
  (if-let [comp-id (some-> comp-ref .-props .-id)]
    (.focus  (.getElementById js/document comp-id))
    (println "inputRef called back with invalid ref or nil ref")))

(defn search-dialog [{:keys [dialog-show term not-matched error-text result]} _db-key]
  (let [input-comp-ref (atom nil)
        matched-entries (:entry-items result)
        matched-count (count matched-entries)]
    [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
                 :on-click #(.stopPropagation %)
                 :classes {:paper "pwd-dlg-root"}}
     [mui-dialog-title (tr-dlg-title "search")]
     [mui-dialog-content
      [mui-stack
       [m/text-field {:label (tr-l "searchTerm")
                      :value term
                      :id "search_fld" ;; needs to be a unique id to use .getElementById and to call focus
                      ;; Using :ref callback fn returns #object[HTMLDivElement [object HTMLDivElement]]
                      ;; whereas :inputRef returns #object[reagent2] for the 'input' text box and we can use to check properties
                      :inputRef (fn [e]
                                  (reset! input-comp-ref e))
                      :autoFocus (when (str/blank? term) true)
                      ;;:on-key-press (enter-key-pressed-factory #(sch-event/search-on-click db-key term))
                      :on-change sch-event/search-term-update ;; a fn that needs to accept an event object
                      :variant "standard"
                      :fullWidth true
                      :InputProps {:endAdornment (r/as-element
                                                  [mui-input-adornment {:position "end"}
                                                   [mui-icon-button
                                                    {:edge false
                                                     :on-click (fn []
                                                                 (focus @input-comp-ref)
                                                                 (sch-event/search-term-clear))}
                                                    [mui-icon-clear-outlined]]])}}]
       (when (seq matched-entries)
         [mui-stack {:sx {:mt 2 :height "250px"
                          :background-color (get-theme-color :header-footer)}}
          [search-result-list-content matched-entries]])

       (cond
         (not (nil? error-text))
         [mui-alert {:severity "error" :sx {:mt 1}} error-text]

         (> matched-count 0)
         [mui-stack {:direction "row" :justify-content "space-between" :sx {:margin-top 1 :margin-bottom 1}}
          [mui-typography (str (tr-h "noOfEntriesFound") " : "  matched-count)]
          [mui-typography (tr-h "doubleClickOnEntry")]]

         (and not-matched (not (str/blank? term)))
         [mui-alert {:severity "warning" :sx {:mt 1}} (tr-h "noMatchingEntryFound")])]]


     [mui-dialog-actions
      [mui-button {:on-click sch-event/cancel-on-click} "Close"]]]))

(defn search-dialog-main []
  [search-dialog @(sch-event/dialog-data) @(cmn-events/active-db-key)])