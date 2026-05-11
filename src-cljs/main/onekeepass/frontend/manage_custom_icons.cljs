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
   [onekeepass.frontend.translation :as t :refer [lstr-bl lstr-l]]))

(defn- icon-card [{:keys [uuid name]}]
  (let [data-url @(ci-events/icon-data-url uuid)]
    [mui-box {:sx {:display "flex" :flex-direction "column" :align-items "center"
                   :width "80px" :padding "6px" :border "1px solid"
                   :border-color "divider" :border-radius "4px"}}
     (if (seq data-url)
       [:img {:src data-url :style {:width "48px" :height "48px" :object-fit "contain"}}]
       [mui-box {:sx {:width "48px" :height "48px" :display "flex"
                      :align-items "center" :justify-content "center"}}
        [mui-typography {:variant "caption"} "…"]])
     [mui-tooltip {:title name}
      [mui-typography {:variant "caption"
                       :sx {:max-width "68px" :overflow "hidden"
                            :text-overflow "ellipsis" :white-space "nowrap"}}
       name]]
     [mui-icon-button {:size "small"
                       :on-click (fn [] (gd-events/custom-icons-delete-confirm-dialog-show-with-state {:uuid uuid}))}
      "✕"]]))

(defn custom-icons-delete-confirm-dialog

  ([{:keys [uuid] :as dialog-data}]
   [confirm-text-dialog
    (t/lstr-dlg-title 'deleteCustomIcon)
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
     [mui-dialog-title (t/lstr-dlg-title 'manageCustomIcons)]
     [mui-dialog-content {:dividers true}
      (if (empty? icons)
        [mui-typography {:color "text.secondary" :sx {:padding "16px"}}
         (t/lstr-l 'noCustomIcons)]
        [mui-box {:sx {:display "flex" :flex-wrap "wrap" :gap "8px" :padding "8px"}}
         (for [icon icons]
           ^{:key (:uuid icon)} [icon-card icon])])]
     [mui-dialog-actions
      [mui-stack {:direction "row" :spacing 1 :sx {:width "100%" :justify-content "space-between"}}
       [mui-stack {:direction "row" :spacing 1}
        [mui-button {:variant "outlined"
                     :on-click #(ci-events/add-icon-from-file)}
         (t/lstr-l 'addFromFile)]]
       [mui-button {:variant "contained" :color "secondary"
                    :on-click ci-events/close-manage-dialog}
        (t/lstr-bl 'close)]]]]))
