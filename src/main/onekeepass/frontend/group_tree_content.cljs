(ns onekeepass.frontend.group-tree-content
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.translation  :refer-macros [tr-l
                                                    tr-t
                                                    tr-ml
                                                    tr-h
                                                    tr-bl
                                                    tr-dlg-title
                                                    tr-dlg-text]]
   [onekeepass.frontend.db-icons :refer [group-icon]]
   [onekeepass.frontend.group-form :as gf]
   [onekeepass.frontend.common-components :refer [selection-autocomplete
                                                  alert-dialog-factory
                                                  dialog-factory
                                                  confirm-text-dialog
                                                  menu-action]]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.group-tree-content :as gt-events]
   [onekeepass.frontend.events.group-form :as gf-events]
   [onekeepass.frontend.events.move-group-entry :as move-events]
   [onekeepass.frontend.events.tauri-events :as tauri-events]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.mui-components :as m :refer [mui-tree-view
                                                     mui-tree-item
                                                     mui-icon-arrow-right
                                                     mui-icon-arrow-drop-down
                                                     mui-typography
                                                     mui-menu
                                                     mui-menu-item
                                                     mui-icon-button
                                                     mui-box
                                                     mui-stack
                                                     mui-alert
                                                     mui-linear-progress
                                                     mui-icon-more-vert]]))
(set! *warn-on-infer* true)

;;;;;;;;;;;;;;;;;;;;; empty-recycle-bin related ;;;;;;;;;;;;;;;;;;;;;
(def empty-recycle-bin-confirm-dialog-data (r/atom {:dialog-show false}))

(defn show-empty-recycle-bin-confirm-dialog []
  (reset! empty-recycle-bin-confirm-dialog-data {:dialog-show true}))

(defn empty-recycle-bin-confirm-dialog [dialog-data]
  ;; we can use either 'alert-dialog-factory' or confirm-text-dialog for this
  [confirm-text-dialog
   (tr-dlg-title emptyRecycleBin)
   (tr-dlg-text emptyRecycleBin)
   [{:label (tr-bl yes) :on-click (fn []
                                    (move-events/empty-trash)
                                    (reset! empty-recycle-bin-confirm-dialog-data {:dialog-show false}))}
    {:label (tr-bl no) :on-click (fn []
                                   (reset! empty-recycle-bin-confirm-dialog-data {:dialog-show false}))}]
   dialog-data])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- delete-group-permanent-dialog [dialog-data group-uuid]
  [(alert-dialog-factory
    (tr-dlg-title groupDeletePermanent)
    (tr-dlg-text groupDeletePermanent)
    [{:label (tr-bl yes)  :on-click #(move-events/delete-permanent-group-entry-ok :group group-uuid)}
     {:label (tr-bl no) :on-click #(move-events/delete-permanent-group-entry-dialog-show :group false)}])
   dialog-data])

(defn move-dialog-content
  "The main content of the move dialog"
  [{{:keys [status group-selection-info api-error-text field-error]} :dialog-data
    :keys [groups-listing on-change]}]
  [mui-stack
   [mui-typography (tr-t selectAGroup)]
   [mui-box
    [selection-autocomplete {:label (tr-l "group")
                             :options groups-listing
                             :current-value group-selection-info
                             :on-change on-change
                             :required true
                             :error field-error
                             :error-text (when field-error (tr-h selectAValidGroup))}]]
   (when api-error-text
     [mui-alert {:severity "error" :style {:width "100%"} :sx {:mt 1}} api-error-text])
   (when (and (nil? api-error-text) (= status :in-progress))
     [mui-linear-progress {:sx {:mt 2}}])])

(defn move-dialog
  "The arg is a map with keys [dialog-data ..] that is expected by the mui-dialog
   created and returned by 'dialog-factory' 
   dialog-data is a map that provides all dialog data
   "
  [{:keys [title cancel-on-click-factory ok-on-click-factory]}]
  (let [dlg (dialog-factory
             title
             move-dialog-content
             ;; Actions
             [{:label  (tr-bl cancel)  :on-click-factory cancel-on-click-factory}
              {:label  (tr-bl ok)  :on-click-factory ok-on-click-factory}])]
    ;; Returns a Form 2 reagent component
    (fn [data]
      [dlg data])))

(defn tree-item-menu-items []
  (fn [anchor-el g-uuid _db-key]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}

     [mui-menu-item {:divider true
                     :on-click  (menu-action anchor-el gt-events/initiate-new-blank-group-form g-uuid)}
      (tr-ml addGroup)]

     [mui-menu-item {:divider false
                     :on-click (menu-action anchor-el gf-events/find-group-by-id g-uuid :edit)}
      (tr-ml edit)]
     [mui-menu-item {:divider true
                     :on-click (menu-action anchor-el gf-events/find-group-by-id g-uuid :info)}
      (tr-ml info)]

     [mui-menu-item {:divider false
                     :on-click (menu-action anchor-el gt-events/sort-groups g-uuid true)}
      (tr-ml "sortAtoZ")]

     [mui-menu-item {:divider true
                     :on-click (menu-action anchor-el gt-events/sort-groups g-uuid false)}
      (tr-ml "sortZtoA")]

     #_[mui-menu-item {:divider false
                       :on-click (menu-action anchor-el move-events/move-group-entry-dialog-show :group true)}
        "Move"]

     [mui-menu-item {:divider false
                     :disabled @(gt-events/root-group-selected?)
                     :on-click (menu-action anchor-el gt-events/group-delete-start g-uuid)}
      (tr-ml delete)]]))

(defn tree-item-recycle-sub-group-menu-items []
  (fn [anchor-el _g-uuid]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     [mui-menu-item {:divider false
                     :on-click (menu-action anchor-el move-events/move-group-entry-dialog-show :group true)}
      (tr-ml putBack)]
     [mui-menu-item {:divider false
                     :on-click (menu-action anchor-el move-events/delete-permanent-group-entry-dialog-show :group true)}
      (tr-ml deletePermanent)]]))

(defn tree-item-recycle-sub-group-menu []
  (let [anchor-el (r/atom nil)]
    (fn [g-uuid]
      [:div {:style {:height 24}}
       [mui-icon-button {:edge "start"
                         :on-click (fn [e]
                                     (reset! anchor-el (-> ^js/Event e .-currentTarget))
                                     ;; prevents tree collapsing
                                     (.stopPropagation ^js/Event e))
                         :style {:padding 0
                                 :margin-left 15}} [mui-icon-more-vert]]
       [tree-item-recycle-sub-group-menu-items anchor-el g-uuid]])))

(defn tree-item-recycle-bin-menu-items [anchor-el]
  [mui-menu {:anchorEl @anchor-el
             :open (if @anchor-el true false)
             :on-close #(reset! anchor-el nil)}
   [mui-menu-item {:divider false
                   :disabled @(gt-events/recycle-bin-empty-check)
                   :on-click (menu-action anchor-el show-empty-recycle-bin-confirm-dialog)}
    "Empty recycle bin"]])

(defn tree-item-recycle-bin-menu []
  (let [anchor-el (r/atom nil)]
    [mui-stack
     [mui-icon-button {:edge "start"
                       :on-click (fn [e]
                                   (reset! anchor-el (-> ^js/Event e .-currentTarget))
                                   (.stopPropagation ^js/Event e))
                       :style {:padding 0
                               :margin-left 15}} [mui-icon-more-vert]]
     [tree-item-recycle-bin-menu-items anchor-el]]))

;; Keep the group uuid for which the system menu is active
(def menu-event-uuid (atom nil))

;; A functional component that uses react useEffect
(defn tree-item-menu []
  (let [anchor-el (r/atom nil)]
    (fn [g-uuid]
      (let [recycle-bin? (gt-events/recycle-group-selected?)
            group-in-recycle-bin? (gt-events/selected-group-in-recycle-bin?)]

        ;;;;;; 
        (m/react-use-effect (fn []
                              (reset! menu-event-uuid g-uuid)
                              (tauri-events/enable-app-menu const/MENU_ID_NEW_GROUP (not @recycle-bin?))
                              (tauri-events/enable-app-menu const/MENU_ID_EDIT_GROUP (not @recycle-bin?))
                              ;; cleanup fn is returned which is called when this component unmounts
                              (fn []
                                ;; Sometime this clean up call is called for the previous group after the build call for 
                                ;; the new group is called  
                                ;; Need to ensure that "Menu Disable" -> "Menu Enable" and not "Menu Enable" -> "Menu Disable"
                                ;; when moving one group to another in the group tree view 
                                (when (or (nil? @menu-event-uuid) (= g-uuid @menu-event-uuid))
                                  (tauri-events/enable-app-menu const/MENU_ID_NEW_GROUP false)
                                  (tauri-events/enable-app-menu const/MENU_ID_EDIT_GROUP false)))) (clj->js []))
        ;;;;;;

        [:div {:style {:height 24}}
         [mui-icon-button {:edge "start"
                           :on-click (fn [e]
                                       (reset! anchor-el (-> ^js/Event e .-currentTarget))
                                       ;;prevent tree collapsing
                                       (.stopPropagation ^js/Event e))
                           :style {:padding 0
                                   :margin-left 15}} [mui-icon-more-vert]]
         (cond
           (and @group-in-recycle-bin? (not @recycle-bin?))
           [tree-item-recycle-sub-group-menu-items anchor-el g-uuid]

           (not @recycle-bin?)
           [tree-item-menu-items anchor-el g-uuid @(cmn-events/active-db-key)])]))))

(defn tree-label []
  (let [g-uuid (gt-events/selected-group)
        recycle-bin? (gt-events/recycle-group-selected?)
        group-in-recycle-bin? (gt-events/selected-group-in-recycle-bin?)]
    (fn [uuid name icon_id]
      [mui-box {:sx {:display "flex" :alignItems "center"  :p 0.5 :pr 0}}
       [mui-box {:sx {;;"& svg" {:width "1em" :height "1em"}
                      :mr 1  ;; 1 => 8px
                      :display "flex"
                      :alignItems "center"}}
        [group-icon icon_id]]
       ;; Based on the discussions
       ;; https://github.com/mui/material-ui/issues/19953#issuecomment-1184953127
       [mui-typography {:variant "body1" :sx {:flex-grow 1}
                        ;; This disables the tree item expanding/collapsing when user clicks on the label by calling stopPropagation
                        ;; in the onclick event. 
                        ;; User needs to click on expand icon to see all child tree items under a tree item
                        :on-click (fn [^js/Event e]
                                    ;; Need to call this event so that group is selected without expanding or collapsing
                                    (gt-events/node-on-select nil uuid)
                                    (.stopPropagation e))} name]

       ;; Shows three dot veritical icon for the menu popup
       (when (= uuid @g-uuid)
         (cond
           @recycle-bin?
           [tree-item-recycle-bin-menu]

           (and @group-in-recycle-bin? (not @recycle-bin?))
           [tree-item-recycle-sub-group-menu @g-uuid]

           (not @recycle-bin?)
           [:f> tree-item-menu @g-uuid]))])))

;; Need to use :strs to retrive values from map argument 
;; as "uuid name icon_id" are the string keys in the map
(defn make-tree-item [{:strs [uuid name icon_id]}]
  [mui-tree-item {:nodeId uuid
                  ;; :label (r/as-element [:div name [mui-icon-more-vert]]) ;; Need more work
                  ;; :icon (r/as-element [mui-icon-more-vert]) ;; Not working; Replaces expand icon
                  :label (r/as-element [tree-label uuid name icon_id])}
   ;; We reuse the group form dialog from group-form ns
   [gf/group-content-dialog-main]])

(defn group-visitor-action
  "Visits a group and its children recursively and form tree items
  The args are
   'group-uuid' is the current group id 
   'parent-tree-item' is reagent tree item component of parent group or nil in case of root group
   'groups' is toplevel map (comes from :groups-tree :data) that has all groups. In that toplevel map
   'key' is the group uuid and value is that group map
  Returns the root tree item at the end
  "
  [group-uuid parent-tree-item groups]
  (let [g (get groups group-uuid)
        ;; Get direct children of this gorup 'g'
        child-group-ids (get g "group_uuids")]
    (loop [c-id (first child-group-ids)
           remaining (next child-group-ids)
           tree-item (make-tree-item g)]
      (if (nil? c-id)
        (if (nil? parent-tree-item)
          tree-item
          (conj parent-tree-item tree-item))
        (recur (first remaining)
               (next remaining)
               (group-visitor-action c-id tree-item groups))))))

(defn group-tree-view []
  (let [gd @(gt-events/groups-tree-data)
        selected-group-uuid @(gt-events/selected-group)
        root-id @(gt-events/root-group-uuid)
        expanded @(gt-events/expanded-nodes)
        groups-listing (gt-events/groups-listing)]
    (if (nil? gd)
      ;; Just :div in case group tree data is nil
      [:div]
      ;; Tree view is shown when the group tree data is loaded and data is not nil
      [mui-tree-view
       {:defaultCollapseIcon (r/as-element [mui-icon-arrow-drop-down])
        :defaultExpandIcon (r/as-element [mui-icon-arrow-right])
        :onNodeSelect gt-events/node-on-select
        :onNodeToggle gt-events/on-node-toggle
        :expanded (if (nil? expanded) [root-id] expanded)
        ;; This ensures to clear any previous selection that when some other entry category item is selected
        :selected selected-group-uuid}
       ;; Form the children tree items 
       (when-not (nil? gd)
         (group-visitor-action
          (get gd "root_uuid") nil (get gd "groups")))

       [delete-group-permanent-dialog @(move-events/delete-permanent-group-entry-dialog-data :group) selected-group-uuid]

       [empty-recycle-bin-confirm-dialog @empty-recycle-bin-confirm-dialog-data]

       [move-dialog
        {:dialog-data @(move-events/move-group-entry-dialog-data :group)
         :title (tr-dlg-title putBack)
         :groups-listing @groups-listing
         :selected-group-uuid selected-group-uuid
         :on-change (move-events/move-group-entry-group-selected-factory :group)
         :cancel-on-click-factory (fn [_data]
                                    #(move-events/move-group-entry-dialog-show :group false))
         :ok-on-click-factory (fn [data]
                                #(move-events/move-group-entry-ok :group (:selected-group-uuid data)))}]])))

(defn group-tree-panel []
  [mui-stack  [group-tree-view]])
