(ns onekeepass.frontend.entry-list
  (:require [onekeepass.frontend.common-components :refer [list-items-factory
                                                           menu-action]]
            [onekeepass.frontend.constants :as const :refer [ASCENDING
                                                             CREATED_TIME
                                                             MODIFIED_TIME
                                                             TITLE
                                                             UUID_OF_ENTRY_TYPE_LOGIN]]
            [onekeepass.frontend.db-icons :refer [entry-icon]]
            [onekeepass.frontend.entry-form-ex :as entry-form-ex]
            [onekeepass.frontend.events.entry-list :as el-events]
            [onekeepass.frontend.events.group-tree-content :as gt-events]
            [onekeepass.frontend.events.tauri-events :as tauri-events]
            [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom 
                                                              mui-avatar
                                                              mui-button
                                                              mui-icon-arrow-drop-down-outlined
                                                              mui-icon-arrow-drop-up-outlined
                                                              mui-icon-button
                                                              mui-icon-keyboard-arrow-down-outlined
                                                              mui-list-item
                                                              mui-list-item-avatar
                                                              mui-list-item-text
                                                              mui-menu
                                                              mui-menu-item
                                                              mui-stack
                                                              theme-color]]
            [onekeepass.frontend.translation :refer-macros [tr-bl tr-ml]]
            [reagent.core :as r]))
(set! *warn-on-infer* true)

;;;;;;;;;;;;;;;;;;;;;;;; Menu ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn entries-sort-menu-items []
  (fn [anchor-el]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}
     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el #(el-events/entry-list-sort-key-changed TITLE))}
      [mui-list-item-text {:inset true} (tr-ml title)]]

     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el #(el-events/entry-list-sort-key-changed MODIFIED_TIME))}
      [mui-list-item-text {:inset true} (tr-ml modifiedTime)]]

     [mui-menu-item {:sx {:padding-left "1px"}
                     :divider false
                     :on-click (menu-action anchor-el #(el-events/entry-list-sort-key-changed CREATED_TIME))}
      [mui-list-item-text {:inset true} (tr-ml createdTime)]]]))

(defn entries-sort-menu []
  (let [anchor-el (r/atom nil)]
    [:div
     [mui-icon-button {:sx {:ml "5px"}
                       :edge "start"
                       :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))
                       :style {}} [mui-icon-keyboard-arrow-down-outlined]]
     [entries-sort-menu-items anchor-el]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;; A functional component to use react useEffect
(defn fn-entry-list-content
  []
  (let [;; See common-components for :div-style use
        entries (el-events/get-selected-entry-items)
        entry-items (list-items-factory entries
                                        row-item :div-style {:min-width 225})
        recycle-bin? (gt-events/recycle-group-selected?)
        deleted-cat? (el-events/deleted-category-showing)
        group-in-recycle-bin? (gt-events/selected-group-in-recycle-bin?)
        group-info @(el-events/initial-group-selection-info)
        entry-type-uuid @(el-events/selected-entry-type)
        disable-action (or @recycle-bin? @group-in-recycle-bin? @deleted-cat?)

        {:keys [key-name direction]} @(el-events/entry-list-sort-creteria)
        entries-found? (> (count @entries) 1) #_(boolean (seq @entries))

        entry-type-uuid (if (nil? entry-type-uuid)
                          UUID_OF_ENTRY_TYPE_LOGIN
                          entry-type-uuid)]
    ;;;;;;; 
    ;; Note: Menu enable is called everytime there is new cat selection
    (tauri-events/enable-app-menu const/MENU_ID_NEW_ENTRY
                                  (not disable-action)
                                  {:callback-fn
                                   (fn []
                                     (el-events/add-new-entry
                                      group-info
                                      entry-type-uuid))})
    (m/react-use-effect
     (fn []
       ;;cleanup fn is returned which is called when this component unmounts
       (fn []
         (tauri-events/enable-app-menu const/MENU_ID_NEW_ENTRY false))) (clj->js []))
    ;;;;;;;

    [:div {:class "gbox"
           :style {;;:margin 0
                   :width "100%"}}
     [:div {:class "gheader"}
      (when entries-found?
        [mui-stack {;; box-shadow css found in https://mui.com/material-ui/react-stack/ example component
                    ;; and copied here
                    :sx {:bgcolor (theme-color @custom-theme-atom :header-footer) ;;"text.disabled"
                         :box-shadow "rgba(0, 0, 0, 0.2) 0px 2px 1px -1px, rgba(0, 0, 0, 0.14) 0px 1px 1px 0px, rgba(0, 0, 0, 0.12) 0px 1px 3px 0px"}
                    :style {:alignItems "center"
                            ;;:background "primary.main" #_"var(--mui-color-grey-200)"
                            :margin-bottom 10
                            :margin-right 0
                            :margin-left 0}}
         [mui-stack {:direction "row"}
          [mui-button {:sx {:border "1px"}
                       :variant "outlined"
                       :color "inherit"
                       :on-click el-events/entry-list-sort-direction-toggle
                       :startIcon (if (= direction ASCENDING)
                                    (r/as-element [mui-icon-arrow-drop-up-outlined])
                                    (r/as-element [mui-icon-arrow-drop-down-outlined]))}
           key-name]
          [entries-sort-menu]]])]
     ;; Need to use some height for 'gcontent' div so that 
     ;; entry list is shown - particularly in mac Catalina OS (10.15+)
     ;; Need to check in Windows,Linux 
     [:div {:class "gcontent" :style {:margin-bottom 2 :height "200px"}}
      [entry-items]]
     [:div {:class "gfooter" :style {:margin-top 5
                                     :background (theme-color @custom-theme-atom :header-footer)}}
      [mui-stack {:style {:alignItems "center"
                          ;; need this to align this footer with entry form footer
                          :max-height "46px"}}
       [:div {:style {:margin-top 10 :margin-bottom 10 :margin-right 5 :margin-left 5}}
        [mui-button {:variant "outlined"
                     :color "inherit"
                     :disabled disable-action
                     ;; We need to use derefenced group-info in event call. If we use @group-info directly in on-click,
                     ;; there will be a re-frame warning indicating reg-sub is called out of context
                     :on-click #(el-events/add-new-entry
                                 group-info
                                 entry-type-uuid
                                 #_(if (nil? entry-type-uuid)
                                     UUID_OF_ENTRY_TYPE_LOGIN
                                     entry-type-uuid))}
         (tr-bl addEntry)]]]]]))

(defn entry-list-content []
  [:f> fn-entry-list-content])