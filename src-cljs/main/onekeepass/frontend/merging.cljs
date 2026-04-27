(ns onekeepass.frontend.merging
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.common-components :refer [selection-autocomplete]]
   [onekeepass.frontend.events.external-db-change :as external-db-change-events]
   [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.events.merging :as merging-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-box mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-divider mui-stack
                                                     mui-typography]]
   [onekeepass.frontend.translation :as t :refer-macros [tr-bl tr-dlg-text tr-dlg-title tr-l] :refer [lstr-dlg-title]]))


(defn merge-result-dialog
  ([{:keys [dialog-show]
     {:keys [added-entries
             updated-entries
             added-groups
             updated-groups
             parent-changed-entries
             parent-changed-groups
             meta-data-changed
             permanently-deleted-entries
             permanently-deleted-groups]
      :as _data} :data}]
   [mui-dialog {:open (if (nil? dialog-show) false dialog-show)
                :dir (t/dir)
                :on-click #(.stopPropagation %)
                :sx {:min-width "600px"
                     "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
    [mui-dialog-title (lstr-dlg-title 'mergeResult)]
    [mui-dialog-content
     [mui-box
      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultEntriesAdded")]
       [mui-typography (count added-entries)]]
      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultEntriesUpdated")]
       [mui-typography (count updated-entries)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultEntriesMoved")]
       [mui-typography (count parent-changed-entries)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultEntriesDeleted")]
       [mui-typography (count permanently-deleted-entries)]]

      [mui-stack {:sx {:height "16px"}}]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultGroupsAdded")]
       [mui-typography (count added-groups)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultGroupsUpdated")]
       [mui-typography (count updated-groups)]]

      [mui-divider]
      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultGroupsMoved")]
       [mui-typography (count parent-changed-groups)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultGroupsDeleted")]
       [mui-typography (count permanently-deleted-groups)]]

      [mui-stack {:sx {:height "16px"}}]

      [mui-divider]
      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography (tr-dlg-text "mergeResultMetaDataChanged")]
       [mui-typography (if meta-data-changed (tr-bl yes) (tr-bl no))]]]]

    [mui-dialog-actions
     [mui-stack  {:sx {}}
      [mui-button {:on-click  gd-events/merge-result-dialog-close} (tr-bl "close")]]]])
  ([]
   (merge-result-dialog @(gd-events/merge-result-dialog-data))))

(defn merge-opened-dbs-dialog
  ([{:keys [dialog-show source-db-key target-db-key]}]
   (when dialog-show
     (let [all-dbs @(merging-events/multiple-unlocked-dbs?)
           target-options (filterv #(not= (:db-key %) source-db-key) all-dbs)
           source-val (first (filter #(= (:db-key %) source-db-key) all-dbs))
           target-val (first (filter #(= (:db-key %) target-db-key) all-dbs))]
       [mui-dialog {:open true
                    :dir (t/dir)
                    :on-click #(.stopPropagation ^js/Event %)
                    :sx {"& .MuiPaper-root" {:width "55%"}}}
        [mui-dialog-title (tr-dlg-title "mergeOpenedDatabases")]
        [mui-dialog-content
         [mui-stack {:spacing 2 :sx {:mt 1}}
          [selection-autocomplete
           {:label (tr-l "sourceDatabase")
            :options all-dbs
            :current-value source-val
            :on-change (fn [_e v]
                         (let [m (js->clj v :keywordize-keys true)]
                           (merging-events/merge-opened-dbs-source-changed (:db-key m))))
            :required true}]
          [selection-autocomplete
           {:label (tr-l "targetDatabase")
            :options target-options
            :current-value target-val
            :on-change (fn [_e v]
                         (let [m (js->clj v :keywordize-keys true)]
                           (merging-events/merge-opened-dbs-target-changed (:db-key m))))
            :required true}]]]
        [mui-dialog-actions
         [mui-button {:on-click gd-events/merge-opened-dbs-dialog-close} (t/lstr-bl 'cancel)]
         [mui-button {:disabled (or (nil? target-db-key)
                                    (= source-db-key target-db-key))
                      :on-click merging-events/merge-opened-dbs-confirm}
          (tr-bl merge)]]])))
  ([]
   (merge-opened-dbs-dialog @(merging-events/merge-opened-dbs-dialog-data))))

(defn external-db-change-dialog
  ([{:keys [dialog-show] {:keys [db-key save-pending]} :data}]
   [mui-dialog {:open (boolean dialog-show)
                :on-click #(.stopPropagation %)}
    [mui-dialog-title (lstr-dlg-title 'externalDbChanged)]
    [mui-dialog-content
     [mui-typography (t/lstr "dialog.texts.externalDbChangedTxt1"
                             {:file-name (-> db-key (str/split #"/") last)})]
     (when save-pending
       [mui-typography {:color "warning.main"}
        (tr-dlg-text "externalDbChangedTxt2")])]
    [mui-dialog-actions
     [mui-stack {:direction "row" :spacing 2}
      [mui-button {:variant "contained"
                   :on-click #(external-db-change-events/external-change-merge-start db-key)}
       (tr-bl merge)]
      [mui-button {:variant "outlined"
                   :on-click #(external-db-change-events/external-change-reload-start db-key)}
       (tr-bl "reload")]
      [mui-button {:on-click #(external-db-change-events/external-change-ignore db-key)}
       (tr-bl "ignore")]]]])
  ([]
   (external-db-change-dialog @(gd-events/external-db-change-dialog-data))))
