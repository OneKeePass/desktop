(ns onekeepass.frontend.group-tree-content
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.db-icons :refer [group-icon]]
   [onekeepass.frontend.group-form :as gf]
   [onekeepass.frontend.common-components :refer [selection-autocomplete
                                                  alert-dialog-factory
                                                  dialog-factory]]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.group-tree-content :as gt-events]
   [onekeepass.frontend.events.group-form :as gf-events]
   [onekeepass.frontend.events.move-group-entry :as move-events]
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

(defn- delete-group-permanent-dialog [dialog-data group-uuid]
  [(alert-dialog-factory "Group Delete Permanent"
                         "Are you sure you want to delete this group and children permanently?"
                         [{:label "Yes" :on-click #(move-events/delete-permanent-group-entry-ok :group group-uuid)}
                          {:label "No" :on-click #(move-events/delete-permanent-group-entry-dialog-show :group false)}])
   dialog-data])

(defn move-dialog-content
  "The main content of the move dialog"
  [{{:keys [status group-selection-info api-error-text field-error]} :dialog-data
    :keys [groups-listing on-change]}]
  [mui-stack
   [mui-typography "Select a group"]
   [mui-box
    [selection-autocomplete {:label "Group"
                             :options groups-listing
                             :current-value group-selection-info
                             :on-change on-change
                             :required true
                             :error field-error
                             :error-text (when field-error "Please select a valid group")}]]
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
             [{:label "Cancel" :on-click-factory cancel-on-click-factory}
              {:label "Ok" :on-click-factory ok-on-click-factory}])]
    ;; Returns a Form 2 reagent component
    (fn [data]
      [dlg data])))

(defn tree-item-menu-items []
  (fn [anchor-el g-uuid _db-key]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     ;;TODOD: Refactor the menu items to a fn (at least on-click handler)
     #_[mui-menu-item {:divider false
                       :on-click #(do
                                    (reset! anchor-el nil)
                                    (gt-events/mark-group-as-category db-key g-uuid)
                                    (.stopPropagation ^js/Event %) ;;prevents tree collapsing
                                    )}"Mark As Category"]

     [mui-menu-item {:divider true
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gt-events/initiate-new-blank-group-form g-uuid)
                                  (.stopPropagation ^js/Event %) ;;prevents tree collapsing
                                  )}
      "Add Group"]

     [mui-menu-item {:divider false
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gf-events/find-group-by-id g-uuid :edit)
                                  (.stopPropagation ^js/Event %) ;;prevents tree collapsing
                                  )}
      "Edit"]
     [mui-menu-item {:divider true
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gf-events/find-group-by-id g-uuid :info)
                                  (.stopPropagation ^js/Event %) ;;prevents tree collapsing
                                  )}
      "Info"]
     [mui-menu-item {:divider false
                     :disabled @(gt-events/root-group-selected?)
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gt-events/group-delete-start g-uuid)
                                  (.stopPropagation ^js/Event %) ;;prevents tree collapsing
                                  )}
      "Delete"]]))

(defn tree-item-recycle-sub-group-menu-items []
  (fn [anchor-el _g-uuid]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     [mui-menu-item {:divider false
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (move-events/move-group-entry-dialog-show :group true)
                                  ;;prevents tree collapsing
                                  (.stopPropagation ^js/Event %))}
      "Put Back"]
     [mui-menu-item {:divider false
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (move-events/delete-permanent-group-entry-dialog-show :group true)
                                  ;;prevents tree collapsing
                                  (.stopPropagation ^js/Event %))}
      "Delete Permanent"]]))

(defn tree-item-recycle-sub-group-menu []
  (let [anchor-el (r/atom nil)]
    (fn [g-uuid]
      [:div {:style {:height 24}}
       [mui-icon-button {:edge "start"
                         :on-click (fn [e]
                                     (reset! anchor-el (-> ^js/Event e .-currentTarget))
                                     ;; prevents tree collapsing
                                     (.stopPropagation ^js/Event e))
                         :style {:color "#000000"
                                 :padding 0
                                 :margin-left 15}} [mui-icon-more-vert]]
       [tree-item-recycle-sub-group-menu-items anchor-el g-uuid]])))

(defn tree-item-menu []
  (let [anchor-el (r/atom nil)]
    (fn [g-uuid]
      (let [recycle-bin? (gt-events/recycle-group-selected?)
            group-in-recycle-bin? (gt-events/selected-group-in-recycle-bin?)]
        [:div {:style {:height 24}}
         [mui-icon-button {:edge "start"
                           :on-click (fn [e]
                                       (reset! anchor-el (-> ^js/Event e .-currentTarget))
                                       ;;prevent tree collapsing
                                       (.stopPropagation ^js/Event e))
                           :style {:color "#000000"
                                   :padding 0
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
       [mui-typography {:variant "body1" :sx {:flex-grow 1}} name]
       (when (= uuid @g-uuid)
         (cond
           (and @group-in-recycle-bin? (not @recycle-bin?))
           [tree-item-recycle-sub-group-menu @g-uuid]

           (not @recycle-bin?)
           [tree-item-menu @g-uuid]))])))

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
        :expanded (if (nil? expanded) [] expanded)
        ;; This ensures to clear any previous selection that when some other entry category item is selected
        :selected selected-group-uuid}
       ;; Form the children tree items 
       (when-not (nil? gd) 
         (group-visitor-action 
          (get gd "root_uuid") nil (get gd "groups")))

       [delete-group-permanent-dialog @(move-events/delete-permanent-group-entry-dialog-data :group) selected-group-uuid]

       [move-dialog
        {:dialog-data @(move-events/move-group-entry-dialog-data :group)
         :title "Put back"
         :groups-listing @groups-listing
         :selected-group-uuid selected-group-uuid
         :on-change (move-events/move-group-entry-group-selected-factory :group)
         :cancel-on-click-factory (fn [_data]
                                    #(move-events/move-group-entry-dialog-show :group false))
         :ok-on-click-factory (fn [data]
                                #(move-events/move-group-entry-ok :group (:selected-group-uuid data)))}]])))

(defn group-tree-panel []
  [mui-stack  [group-tree-view]])
