(ns onekeepass.frontend.group-form
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.common-components :as cc :refer [tags-field]]
   [onekeepass.frontend.db-icons :as db-icons :refer [group-icon]]
   [onekeepass.frontend.entry-form.common :refer [ENTRY_DATETIME_FORMAT theme-content-sx]]
   [onekeepass.frontend.entry-form.fields :refer [text-area-field text-field]]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.group-form :as gf-events]
   [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                     mui-box
                                                     mui-button
                                                     mui-checkbox
                                                     mui-dialog
                                                     mui-dialog-actions
                                                     mui-dialog-content
                                                     mui-dialog-title
                                                     mui-form-control-label
                                                     mui-icon-button
                                                     mui-stack
                                                     mui-typography]]
   [onekeepass.frontend.translation :as t :refer-macros [tr-l tr-bl tr-dlg-title]]
   [onekeepass.frontend.utils :as u :refer [vec->tags]]
   [reagent.core :as r]
   [onekeepass.frontend.events.generic-dialogs :as gd-events]))

(def ^:private icons-dialog-flag (r/atom false))

(defn- close-icons-dialog []
  (reset! icons-dialog-flag false))

(defn- show-icons-dialog []
  (reset! icons-dialog-flag true))

(defn- icons-dialog []
  (fn [dialog-open?]
    [:div [mui-dialog {:open (if (nil? dialog-open?) false dialog-open?)
                       :dir (t/dir)
                       :on-click #(.stopPropagation ^js/Event %)
                       :sx {"& .MuiDialog-paper" {:width "85%"}}}
           [mui-dialog-title (tr-dlg-title "icons")]
           [mui-dialog-content {:dividers true}
            [mui-box {:sx {:display "flex" :flex-wrap "wrap"}}
             (for [[idx svg-icon] db-icons/all-icons]
               ^{:key idx} [:div {:style {:margin "4px"}
                                  :on-click #(do
                                               (gf-events/update-form-data :icon-id idx)
                                               (close-icons-dialog))}
                            svg-icon])]]
           [mui-dialog-actions
            [mui-button {:variant "contained" :color "secondary"
                         :on-click close-icons-dialog} (tr-bl "close")]]]]))

(defn- group-content [{:keys [name icon-id tags notes times]}]
  [mui-box {:sx (theme-content-sx @custom-theme-atom)}

   ;; Name + Icon — mirrors title-with-icon-field in entry_form_ex.cljs
   [mui-stack {:direction "row" :spacing 1}
    [mui-stack {:direction "row" :sx {:width "88%" :justify-content "center"}}
     [text-field {:key (tr-l "name")
                  :value (or name "")
                  :edit true
                  :no-end-icons true
                  :on-change-handler (gf-events/form-on-change-factory :name)}]]
    [mui-stack {:direction "row" :sx {:width "12%" :justify-content "center" :align-items "center"}}
     [mui-typography {:sx {:padding-left "5px"} :align "center" :variant "subtitle1"} (tr-l "icons")]
     [mui-icon-button {:edge "end" :color "primary" :on-click show-icons-dialog}
      [group-icon icon-id]]]]

   ;; Tags — mirrors tags-selection in entry_form_ex.cljs
   [tags-field @(cmn-events/all-tags) tags gf-events/on-tags-selection true]

   ;; Category checkbox
   [mui-stack {:sx {:margin-top "8px"}}
    [mui-form-control-label {:control (r/as-element
                                       [mui-checkbox {:checked (boolean @(gf-events/marked-as-category))
                                                      :disabled (boolean @(gf-events/showing-groups-as-category))
                                                      :on-change gf-events/marked-as-category-on-check}])
                             :label (tr-l "category")}]]

   ;; Notes — mirrors notes-content in entry_form_ex.cljs
   [mui-stack {:sx {:margin-top "8px"}}
    [text-area-field {:key "notes"
                      :value (or notes "")
                      :edit true
                      :on-change-handler (gf-events/form-on-change-factory :notes)}]]

   ;; Timestamps (read-only) — same row pattern as uuid-times-content
   [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-top "10px"}}
    [mui-typography (str (tr-l "creationTime") ":")]
    [mui-typography (u/to-local-datetime-str (:creation-time times) ENTRY_DATETIME_FORMAT)]]
   [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-top "5px"}}
    [mui-typography (str (tr-l "lastModificationTime") ":")]
    [mui-typography (u/to-local-datetime-str (:last-modification-time times) ENTRY_DATETIME_FORMAT)]]

   [icons-dialog @icons-dialog-flag]])

(defn- group-info-content [{:keys [uuid name tags notes times]}]
  ;; Mirrors uuid-times-content layout from entry_form_ex.cljs — no Grid
  [mui-box {:sx (theme-content-sx @custom-theme-atom)}

   ;; UUID — matching entry_form_ex.cljs uuid-times-content
   [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
    [mui-typography (str (tr-l "uuid") ":")]
    [mui-typography uuid]]

   [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
    [mui-typography (str (tr-l "name") ":")]
    [mui-typography name]]

   [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
    [mui-typography (str (tr-l "tags") ":")]
    [mui-typography (vec->tags tags)]]

   (when-not (str/blank? notes)
     [mui-stack {:sx {:margin-bottom "10px"}}
      [mui-typography (str (tr-l "notes") ":")]
      [text-area-field {:key "notes" :value notes :edit false}]])

   [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
    [mui-typography (str (tr-l "creationTime") ":")]
    [mui-typography (u/to-local-datetime-str (:creation-time times) ENTRY_DATETIME_FORMAT)]]

   [mui-stack {:direction "row" :sx {:justify-content "space-between" :margin-bottom "10px"}}
    [mui-typography "Last Accessed:"]
    [mui-typography (u/to-local-datetime-str (:last-access-time times) ENTRY_DATETIME_FORMAT)]]

   [mui-stack {:direction "row" :sx {:justify-content "space-between"}}
    [mui-typography (str (tr-l "lastModificationTime") ":")]
    [mui-typography (u/to-local-datetime-str (:last-modification-time times) ENTRY_DATETIME_FORMAT)]]])

(defn- group-content-dialog [flag form-data mode new-group]
  [:div
   [mui-dialog {:open (if (nil? flag) false flag)
                :dir (t/dir)
                :on-click #(.stopPropagation ^js/Event %)
                :sx {"& .MuiDialog-paper" {:width "85%"}}}
    [mui-dialog-title (tr-dlg-title "groupDetails")]
    [mui-dialog-content
     (if (= mode :edit)
       [group-content form-data]
       [group-info-content form-data])]
    (if (= mode :edit)
      (let [modified @(gf-events/form-modified)]
        [mui-dialog-actions
         [mui-button {:variant "contained" :color "secondary"
                      :on-click gd-events/group-form-dialog-close}
          (t/lstr-bl 'cancel)]
         [mui-button {:variant "contained" :color "secondary"
                      :on-click (if new-group gf-events/ok-new-group-on-click gf-events/ok-edit-on-click)
                      :disabled (not modified)}
          (t/lstr-bl 'ok)]])
      [mui-dialog-actions
       [mui-button {:variant "contained" :color "secondary"
                    :on-click
                    gd-events/group-form-dialog-close}
        (t/lstr-bl 'cancel)]
       [mui-button {:variant "contained" :color "secondary"
                    :on-click #(gf-events/edit-form)}
        (tr-bl "edit")]])]])

(defn group-content-dialog-main []
  (let [{:keys [dialog-show data mode new-group]} @(gd-events/group-form-dialog-data)]
    ;; Guard prevents rendering content components with nil data after generic-dialog-close
    ;; resets :data to {} (which would crash u/to-local-datetime-str on nil times)
    (when dialog-show
      [:div [group-content-dialog dialog-show data mode new-group]])))
