(ns onekeepass.frontend.entry-list
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.constants :refer [UUID_OF_ENTRY_TYPE_LOGIN]]
   [onekeepass.frontend.events.entry-list :as el-events]
   [onekeepass.frontend.events.group-tree-content :as gt-events]
   [onekeepass.frontend.entry-form-ex :as entry-form-ex]
   [onekeepass.frontend.common-components :refer [list-items-factory]]
   [onekeepass.frontend.db-icons :refer [entry-icon]]
   [onekeepass.frontend.mui-components :as m :refer [mui-button
                                                     mui-stack
                                                     mui-avatar mui-list-item mui-list-item-text
                                                     mui-list-item-avatar]]))
(set! *warn-on-infer* true)

(defn row-item
  "Renders a list item. 
  The arg 'props' is a map passed from 'fixed-size-list'
  "
  []
  (fn [props]
    (let [items  (el-events/get-selected-entry-items)
          item (nth @items (:index props))
          selected-id (el-events/get-selected-entry-id)]
      [mui-list-item {:style (:style props)
                      ;; MuiListItemSecondaryAction-root is the CSS used for the secondaryAction component
                      ;; This was found by using Inspect Element. By default the right side More icon is placed 16px
                      ;; absolutely from right edge wasting more space. It is overriden here setting to 0 px
                      :sx {"& .MuiListItemSecondaryAction-root" {:right 0}}
                      :button true
                      :value (:uuid item)
                      :on-click #(el-events/update-selected-entry-id (:uuid item))
                      :selected (if (= @selected-id (:uuid item)) true false)
                      :secondaryAction (when (= @selected-id (:uuid item))
                                         ;; We are reusing the menu components from entry-form-ex directly here
                                         ;; Need to move this a common UI name space
                                         (r/as-element [entry-form-ex/form-menu @selected-id] #_[mui-icon-button {:edge "end"} [mui-icon-more-vert]]))}
       [mui-list-item-avatar
        [mui-avatar [entry-icon (:icon-id item)]]]
       [mui-list-item-text
        {:primaryTypographyProps {:max-width 155
                                  :white-space "nowrap"
                                  :text-overflow "ellipsis"
                                  :overflow "hidden"}
         :secondaryTypographyProps {:max-width 155
                                    :white-space "nowrap"
                                    :text-overflow "ellipsis"
                                    :overflow "hidden"}
         :primary (:title  item) :secondary (:secondary-title item)}]])))


(defn entry-list-content
  []
  (let [;; See common-components for :div-style use
        entry-items (list-items-factory (el-events/get-selected-entry-items) 
                                        row-item :div-style {:min-width 225})
        recycle-bin? (gt-events/recycle-group-selected?)
        deleted-cat? (el-events/deleted-category-showing)
        group-in-recycle-bin? (gt-events/selected-group-in-recycle-bin?)
        group-info @(el-events/initial-group-selection-info)
        entry-type-uuid @(el-events/selected-entry-type)]
    [:div {:class "gbox"
           :style {;;:margin 0
                   :width "100%"}}
     [:div {:class "gheader"}]
     ;; Need to use some height for 'gcontent' div so that 
     ;; entry list is shown - particularly in mac Catalina OS (10.15+)
     ;; Need to check in Windows,Linux 
     [:div {:class "gcontent" :style {:margin-bottom 2 :height "200px"}}
      [entry-items]]
     [:div {:class "gfooter" :style {:margin-top 5 
                                     :background "var(--mui-color-grey-200)"}}
      [mui-stack {:style {:alignItems "center"
                          ;; need this to align this footer with entry form footer
                          :max-height "46px"}}
       [:div {:style {:margin-top 10 :margin-bottom 10 :margin-right 5 :margin-left 5}}
        [mui-button {:variant "outlined"
                     :color "inherit"
                     :disabled (or @recycle-bin? @group-in-recycle-bin? @deleted-cat?)
                     ;; We need to use derefenced group-info in event call. If we use @group-info directly in on-click,
                     ;; there will be a re-frame warning indicating reg-sub is called out of context
                     :on-click #(el-events/add-new-entry
                                 group-info
                                 (if (nil? entry-type-uuid) 
                                   UUID_OF_ENTRY_TYPE_LOGIN 
                                   entry-type-uuid))} 
         "Add Entry"]]]]]))