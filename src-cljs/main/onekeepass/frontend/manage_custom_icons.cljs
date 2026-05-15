(ns onekeepass.frontend.manage-custom-icons
  "Manage custom icons dialog — opened from the DB settings toolbar."
  (:require
   [onekeepass.frontend.common-components :refer [confirm-text-dialog]]
   [onekeepass.frontend.events.custom-icons :as ci-events]
   [onekeepass.frontend.events.generic-dialogs :as gd-events]
   [onekeepass.frontend.mui-components :as m :refer [mui-box mui-button
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-icon-button mui-stack
                                                     mui-tooltip
                                                     mui-typography]]
   [onekeepass.frontend.translation :as t :refer [lstr-bl lstr-dlg-title
                                                  lstr-l]]))

(defn- icon-card [{:keys [uuid name]}]
  (let [data-url @(ci-events/icon-data-url uuid)]
    [mui-tooltip {:title (or name "")}
     [mui-box {:sx {:display "flex" :flex-direction "column"
                    :align-items "center" :justify-content "center"
                    :width "72px" :height "78px" :padding "4px 8px 8px"
                    :border "1px solid" :border-color "divider"
                    :border-radius "4px"}}
      [mui-box {:sx {:display "flex" :justify-content "flex-end"
                     :width "100%" :height "20px"}}
       [mui-icon-button {:size "small"
                         :sx {:width "20px" :height "20px" :padding 0}
                         :on-click (fn [] (gd-events/custom-icons-delete-confirm-dialog-show-with-state {:uuid uuid}))}
        "✕"]]
      [mui-box {:sx {:display "flex" :align-items "center" :justify-content "center"
                     :width "48px" :height "48px"}}
       (when (seq data-url)
         [:img {:src data-url
                :style {:max-width "48px" :max-height "48px"
                        :width "auto" :height "auto" :object-fit "contain"}}])]]]))

(defn custom-icons-delete-confirm-dialog

  ([{:keys [uuid] :as dialog-data}]
   [confirm-text-dialog
    (lstr-dlg-title 'deleteCustomIcon)
    (lstr-l 'confirmDeleteCustomIcon)
    [{:label (lstr-bl 'cancel) :on-click (fn []
                                           (gd-events/custom-icons-delete-confirm-dialog-close))}
     {:label (lstr-bl 'ok) :on-click (fn []
                                       (gd-events/custom-icons-delete-confirm-dialog-close)
                                       (ci-events/remove-icon uuid))}]
    dialog-data])
  ([]  (custom-icons-delete-confirm-dialog @(gd-events/custom-icons-delete-confirm-dialog-data))))

(defn manage-custom-icons-dialog-main
  "Mount once in tool_bar.cljs. Opens via ci-events/show-manage-dialog."
  []
  (let [open? @(ci-events/manage-dialog-open?)
        icons @(ci-events/icons-list)]
    [mui-dialog {:open (boolean open?)
                 :on-click #(.stopPropagation ^js/Event %)
                 :sx {"& .MuiDialog-paper" {:width "80%" :max-width "600px"}}}
     [mui-dialog-title (lstr-dlg-title 'manageCustomIcons)]
     [mui-dialog-content {:dividers true}
      (if (empty? icons)
        [mui-typography {:color "text.secondary" :sx {:padding "16px"}}
         (lstr-l 'noCustomIcons)]
        [mui-box {:sx {:display "flex" :flex-wrap "wrap" :gap "8px" :padding "8px"}}
         (for [icon icons]
           ^{:key (:uuid icon)} [icon-card icon])])]
     [mui-dialog-actions
      [mui-stack {:direction "row" :spacing 1 :sx {:width "100%" :justify-content "space-between"}}
       [mui-stack {:direction "row" :spacing 1}
        [mui-button {:variant "outlined"
                     :on-click #(ci-events/add-icon-from-file)}
         (lstr-l 'addFromFile)]]
       [mui-button {:variant "contained" :color "secondary"
                    :on-click ci-events/close-manage-dialog}
        (lstr-bl 'close)]]]]))
