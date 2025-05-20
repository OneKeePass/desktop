(ns onekeepass.frontend.import-file.csv
  (:require
   [onekeepass.frontend.events.import-file.csv :as csv-events]
   [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-box
                                                     mui-chip
                                                     mui-alert
                                                     mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-stack
                                                     mui-divider
                                                     mui-typography
                                                     mui-form-control-label
                                                     mui-checkbox
                                                     get-theme-color]]
   [onekeepass.frontend.common-components :as cc :refer [confirm-text-dialog]]
   [onekeepass.frontend.translation  :refer-macros [tr-bl] :refer [lstr-l-cv lstr-field-name lstr-l]]
   [reagent.core :as r]
   [onekeepass.frontend.constants :as const]))

(set! *warn-on-infer* true)

(defn csv-imoprt-start-dialog-content []
  [mui-stack {:sx {}}
   [m/mui-typography {:sx {:mb 1}} "Importing only a generic comma separated values(csv) file is supported at this time."]
   [m/mui-typography {:sx {:mb 1}} "The first row of this csv file should be a header row."]
   [m/mui-typography {:sx {:mb 1}} "After loading the csv file, you need to map the header fields to keepass entry fields."]])

(defn csv-imoprt-start-dialog []
  [confirm-text-dialog
   "Import"
   csv-imoprt-start-dialog-content
   [{:label (tr-bl cancel) :on-click (fn []
                                       (gd-events/csv-imoprt-start-dialog-close))}
    {:label "Open csv file" :on-click (fn []
                                        (gd-events/csv-imoprt-start-dialog-close)
                                        (csv-events/open-file-explorer-on-click))}]
   @(gd-events/csv-imoprt-start-dialog-data)])

(defn stop-propagation [^js/Event e]
  (.stopPropagation e))

(defn csv-mapping-completed-dialog
  ([{:keys [dialog-show]}]
   (when dialog-show
     (let [{:keys [mapped-headers not-mapped-headers unmapped-custom-field current-db-file-name]} @(csv-events/import-csv-mapping-result)
           ;;matched not-matched
           matched-count (count mapped-headers)
           not-matched-count (count not-mapped-headers)]

       ;; (println "mapped-headers not-mapped-headers vec count " (count (vec mapped-headers)))
       [mui-dialog {:open dialog-show
                    :on-click stop-propagation
                    :sx {:min-width "600px"
                         "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
        [mui-dialog-title "Import Into"]
        [mui-dialog-content {:dividers true}
         [mui-box
          [mui-stack {:sx {:mr 2 :ml 2}}
           [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
            [mui-stack {:sx {:align-items "left"}}
             [mui-typography (if (= matched-count 1) "Mapped header field" "Mapped header fields")]]
            [mui-stack {:sx {}}
             [mui-typography matched-count]]]
           (when (not= not-matched-count 0)
             [mui-stack
              [mui-divider {:sx {:mb 1 :mt 1}}]

              [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
               [mui-stack {:sx {:align-items "left"}}
                [mui-typography (if (= not-matched-count 1) "Not mapped header field" "Not mapped header fields")]]
               [mui-stack {:sx {}}
                [mui-typography not-matched-count]]]

              [mui-divider {:sx {:mb 1 :mt 1}}]

              [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
               [mui-form-control-label
                {:control (r/as-element
                           [mui-checkbox
                            {:checked unmapped-custom-field
                             :on-change (fn [^js/CheckedEvent e] (csv-events/import-csv-map-custom-field (-> e .-target  .-checked)))}])
                 :label "Consider unmapped headers as custom fields"}]]

              [mui-divider {:sx {:mb 1 :mt 1}}]
              (when unmapped-custom-field
                [mui-stack {:direction "row" :sx {:flex-wrap "wrap" :gap "2px"}}
                 (doall
                  (for [umf not-mapped-headers]
                    [mui-chip {:label umf :on-delete #()}]))])])]]]
        [mui-dialog-actions
         [mui-button {:on-click  gd-events/csv-mapping-completed-dialog-close} (tr-bl "cancel")]
         [mui-button {:on-click  csv-events/import-csv-new-database} "New Database"]
         (when-not (nil? current-db-file-name)
           [mui-button {:on-click  gd-events/csv-mapping-completed-dialog-close} "current opened database"])]])))
  ([]
   [csv-mapping-completed-dialog @(gd-events/csv-mapping-completed-dialog-data)]))


(defn update-selection [mapped okp-field-name value]
  (let [um (map (fn [{:keys [field-name] :as m}] (if (= field-name okp-field-name)
                                                   (assoc m :mapped-name value)
                                                   m)) mapped)]
    (gd-events/csv-columns-mapping-dialog-update-with-map {:data {:mapping-options um}
                                                           :api-error-text nil})))

(defn- tr-mapping-field-label [field-name]
  (cond
    (= field-name const/GROUP)
    (lstr-l-cv field-name)

    (= field-name const/OTP)
    (lstr-l 'oneTimePasswordTotp)

    :else
    (lstr-field-name field-name)))

(defn mapping-row [okp-field-name mapped-field-name select-field-options mapped]
  [mui-stack {:direction "row" :sx {:width "100%" :justify-content "space-between"}}
   [mui-stack {:sx {:width "100%" :margin-bottom "16px"}}
    [cc/simple-selection-field {:field-name (tr-mapping-field-label okp-field-name)
                                :value mapped-field-name
                                :edit true
                                :on-change-handler (fn [^js/Event e]
                                                     (update-selection mapped okp-field-name
                                                                       #_(. ^js/EventT (.-target e) -value)
                                                                       (->  e ^js/EventT (.-target) .-value)))
                                :select-field-options select-field-options}]]])

;; Forms as two columns with mapping options
(defn fields-mapping-form [csv-headers mapped]
  ;; mapped is a vec of maps 
  ;; (see okp-filed-to-csv-header-mapping) and we split that into two columns (5 rows each) 
  (let [[column1 column2] (split-at 5 mapped)]

    [mui-stack {:direction "row"
                :divider (r/as-element [mui-divider {:orientation "vertical"
                                                     :flexItem true
                                                     :sx {:border-color (get-theme-color :divider-color1)
                                                          :margin-left "8px"
                                                          :margin-right "8px"}}])
                :sx {:overflow-y "scroll" :width "100%"}}

     ;; First column
     [mui-stack {:direction "row" :sx {:width "50%"}}
      [mui-stack {:direction "column" :sx {:width "100%" :height "100%"}}
       [mui-stack {:sx {}}
        (doall (for [{:keys [field-name mapped-name]} column1]
                 ^{:key [field-name mapped-name]} [mapping-row field-name mapped-name csv-headers mapped]))]]]

     ;; Second column
     [mui-stack {:direction "row" :sx {:width "50%"}}
      [mui-stack {:direction "column" :sx {:width "100%" :height "100%"}}

       [mui-stack {:sx {}}
        (doall (for [{:keys [field-name mapped-name]} column2]
                 ^{:key [field-name mapped-name]} [mapping-row field-name mapped-name csv-headers mapped]))]]]]))

(defn csv-columns-mapping-dialog
  ([{:keys [dialog-show api-error-text]
     {:keys [csv-headers mapping-options]} :data}]
   [:<>
    [mui-dialog {:open dialog-show
                 :on-click stop-propagation
                 :sx {:min-width "600px"
                      "& .MuiDialog-paper" {:max-width "650px" :width "90%"
                                            ;; It appears .MuiDialog-paper will have min-height = 500px when 
                                            ;; set even when we set 100px. In this dialog, we need not set the min height
                                            ;;:min-height "100px"
                                            }}}
     [mui-dialog-title "CSV Mapping"]
     [mui-dialog-content {:dividers true}
      [mui-box {:sx {}}
       [fields-mapping-form csv-headers mapping-options]
       (when api-error-text
         [mui-alert {:severity "error" :sx {:mt 1}} api-error-text])]]

     [mui-dialog-actions
      [mui-button {:on-click  gd-events/csv-columns-mapping-dialog-close} (tr-bl "cancel")]
      [mui-button {:on-click  (fn []
                                #_(gd-events/csv-columns-mapping-dialog-close)
                                (csv-events/import-csv-mapped mapping-options)
                                #_(gd-events/dialog-with-id-show :csv-mapping-completed-dialog))} (tr-bl "ok")]]]
    [csv-mapping-completed-dialog]])
  ([]
   [csv-columns-mapping-dialog @(gd-events/csv-columns-mapping-dialog-data)]))
