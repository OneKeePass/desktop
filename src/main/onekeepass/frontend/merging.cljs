(ns onekeepass.frontend.merging
  (:require
   [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-box mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-divider mui-stack
                                                     mui-typography]]
   [onekeepass.frontend.translation  :refer-macros [tr-bl] :refer [lstr-dlg-title]]))


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
                :on-click #(.stopPropagation %)
                :sx {:min-width "600px"
                     "& .MuiDialog-paper" {:max-width "650px" :width "90%"}}}
    [mui-dialog-title (lstr-dlg-title 'mergeResult)]
    [mui-dialog-content
     [mui-box
      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Entries added"]
       [mui-typography (count added-entries)]]
      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Entries updated"]
       [mui-typography (count updated-entries)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Entries moved"]
       [mui-typography (count parent-changed-entries)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Entries deleted"]
       [mui-typography (count permanently-deleted-entries)]]

      [mui-stack {:sx {:height "16px"}}]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Groups added"]
       [mui-typography (count added-groups)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Groups updated"]
       [mui-typography (count updated-groups)]]

      [mui-divider]
      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Groups moved"]
       [mui-typography (count parent-changed-groups)]]

      [mui-divider]

      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Groups deleted"]
       [mui-typography (count permanently-deleted-groups)]]

      [mui-stack {:sx {:height "16px"}}]

      [mui-divider]
      [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
       [mui-typography "Meta data changed"]
       [mui-typography (if meta-data-changed "Yes" "No")]]]]

    [mui-dialog-actions
     [mui-stack  {:sx {}}
      [mui-button {:on-click  gd-events/merge-result-dialog-close} (tr-bl "close")]]]])
  ([]
   (merge-result-dialog @(gd-events/merge-result-dialog-data))))


