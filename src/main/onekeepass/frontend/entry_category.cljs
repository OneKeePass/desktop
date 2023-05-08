(ns onekeepass.frontend.entry-category
  (:require
   [reagent.core :as r]
   [onekeepass.frontend.events.entry-category :as ec-events]
   [onekeepass.frontend.events.common :as cmn-events]
   [onekeepass.frontend.events.group-form :as gf-events]  ;;need to be replaced events from ec-events 
   
   [onekeepass.frontend.db-icons :refer [group-icon entry-type-icon]]
   [onekeepass.frontend.group-form :as gf]
   [onekeepass.frontend.group-tree-content :as gt]
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.common-components :refer [overflow-tool-tip message-sanckbar-alert]] 
   [onekeepass.frontend.mui-components :as m :refer [color-primary-main
                                                     color-secondary-main
                                                     mui-icon-button
                                                     mui-menu
                                                     mui-menu-item
                                                     mui-list-item-icon
                                                     mui-list-item-text
                                                     mui-icon-check
                                                     mui-typography
                                                     mui-box
                                                     mui-list
                                                     mui-list-item-button
                                                     mui-stack
                                                     mui-icon-done-all
                                                     mui-icon-favorite-border
                                                     mui-icon-delete-outline
                                                     mui-icon-access-time
                                                     mui-icon-more-vert]]))

;;(set! *warn-on-infer* true)

(defn- menu-action [anchor-el action & action-args]
  (fn [^js/Event e]
    (reset! anchor-el nil)
    (apply action action-args)
    (.stopPropagation ^js/Event e)))

(defn category-title-menu-items []
  (fn [anchor-el showing-groups-as]
    (let [show-category? (or (nil? showing-groups-as) (= showing-groups-as :category))
          show-groups? (= showing-groups-as :group)
          show-types? (= showing-groups-as :type)
          root-group-uuid @(ec-events/root-group-uuid)]
      [mui-menu {:anchorEl @anchor-el
                 :open (if @anchor-el true false)
                 :on-close #(reset! anchor-el nil)}

       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action anchor-el ec-events/show-as-type-category)}
        (when show-types?
          [mui-list-item-icon [mui-icon-check]])
        (if show-types? "Types" [mui-list-item-text {:inset true} "Types"])]

       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action anchor-el ec-events/show-as-group-category)}
        (when show-category?
          [mui-list-item-icon [mui-icon-check]])
        (if show-category? "Categories" [mui-list-item-text {:inset true} "Categories"])]

       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider (if show-category? true false)
                       :on-click (menu-action anchor-el ec-events/show-as-group-tree)}
        (when show-groups?
          [mui-list-item-icon [mui-icon-check]])
        (if show-groups? "Groups" [mui-list-item-text {:inset true} "Groups"])]

       (when show-category?
         [mui-menu-item {:sx {:padding-left "1px"}
                         :divider false
                         :on-click (menu-action anchor-el ec-events/initiate-new-blank-group-form root-group-uuid)}
          [mui-list-item-text {:inset true} "Add category"]])])))

(defn category-title-menu []
  (fn [showing-groups-as]
    (let [anchor-el (r/atom nil)]
      [:div
       [mui-icon-button {:edge "start"
                         :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))
                         :style {:color "#000000"}} [mui-icon-more-vert]]
       [category-title-menu-items anchor-el showing-groups-as]
       [gf/group-content-dialog-main]])))

(defn group-category-item-menu-items []
  (fn [anchor-el g-uuid]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}

     [mui-menu-item {:divider false
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gf-events/find-group-by-id g-uuid :edit)
                                  (.stopPropagation ^js/Event %))} "Edit"]
     [mui-menu-item {:divider false
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gf-events/find-group-by-id g-uuid :info)
                                  (.stopPropagation ^js/Event %))} "Info"]]))

(defn group-category-item-menu []
  (let [anchor-el (r/atom nil)]
    (fn [g-uuid]
      [:div {:style {:height 24}}
       [mui-icon-button {:edge "start"
                         :on-click (fn [e]
                                     (reset! anchor-el (-> ^js/Event e .-currentTarget))
                                     (.stopPropagation ^js/Event e))
                         :style {:color "#000000"
                                 :padding 0
                                 :margin-left 15}} [mui-icon-more-vert]]
       [group-category-item-menu-items anchor-el g-uuid]
       [gf/group-content-dialog-main]])))

(defn type-category-item-menu-items []
  (fn [anchor-el entries-count entry-type-uuid]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}

     [mui-menu-item {:divider false
                     :disabled (> entries-count 0)
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (ec-events/delete-custom-entry-type entry-type-uuid)
                                  (.stopPropagation ^js/Event %))} "Delete type"]]))

(defn type-category-item-menu []
  (let [anchor-el (r/atom nil)]
    (fn [entries-count entry-type-uuid]
      [:div {:style {:height 24}}
       [mui-icon-button {:edge "start"
                         :on-click (fn [e]
                                     (reset! anchor-el (-> ^js/Event e .-currentTarget))
                                     (.stopPropagation ^js/Event e))
                         :style {:color "#000000"
                                 :padding 0
                                 :margin-left 15}} [mui-icon-more-vert]]

       [type-category-item-menu-items anchor-el entries-count entry-type-uuid]
       [message-sanckbar-alert]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;    Context menu example  ;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ctx-menu-state (r/atom {:open false :menu-state nil :data {}}))

(defn handle-close-context-menu []
  (reset! ctx-menu-state {:open false :menu-state nil :data {}}))

(defn  handle-right-click-factory [m]
  (fn [^js/Event e]
    (when-not (@ctx-menu-state :open)
      (reset! ctx-menu-state {:open true
                              :menu-state {:left (- (.-clientX e) 2)
                                           :top (- (.-clientY e) 4)}
                              :data m}))
    ;; prevents the default context menu popup
    (.preventDefault e)))

(defn category-context-menu []
  [mui-menu {:open  (@ctx-menu-state :open)
             :on-close handle-close-context-menu
             ;;the props of the Popover component are also available on Menu
             :anchorReference "anchorPosition"
             :anchorPosition (@ctx-menu-state :menu-state) #_(when-not (nil? @ctx-menu-state) @ctx-menu-state)}

   ;; Conditionall showing certain context menus
   ;; Diasble works
   #_[mui-menu-item {:onClick handle-close-context-menu :disabled (when-not (contains? (@ctx-menu-state :data) :uuid) true)} "Edit"]
   #_[mui-menu-item {:onClick handle-close-context-menu :disabled (when (contains? (@ctx-menu-state :data) :uuid) true)} "Copy"]
   #_[mui-menu-item {:onClick handle-close-context-menu} "Paste"]

   ;; Not working properly
   ;; When menus showing Edit and Info closes, Copy and Paste shows up (blinks) briefly. 
   [mui-menu-item {:onClick handle-close-context-menu}
    (if (contains? (@ctx-menu-state :data) :uuid) "Edit" "Copy")]
   [mui-menu-item {:onClick handle-close-context-menu}
    (if (contains? (@ctx-menu-state :data) :uuid) "Info" "Paste")]])

;;; To enable context menu, uncomment [category-context-menu] call in category-item and also
;;; uncomment :on-context-menu in category-item
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn category-title
  [showing-groups-as]

  [mui-stack {:direction "row" :sx {:width "100%"}}
   [mui-stack {:direction "row" :sx {:width "90%"  :align-items "center"}}
    [mui-typography {:sx {:padding-left "16px"}}  (cond
                                                    (or (nil? showing-groups-as) (= showing-groups-as :type))
                                                    "Types"

                                                    (= showing-groups-as :category)
                                                    "Categories"

                                                    (= showing-groups-as :group)
                                                    "Groups")]]
   [mui-stack {:direction "row" :sx {:width "10%"}}
    [category-title-menu showing-groups-as]]]

  #_[mui-grid {:container true}
     [mui-grid {:item true :xs 10  :style {:text-align "start"}}
      [mui-typography (cond
                        (or (nil? showing-groups-as) (= showing-groups-as :category))
                        "Categories"

                        (= showing-groups-as :type)
                        "Types"

                        (= showing-groups-as :group)
                        "Groups")]]
     [mui-grid {:item true :xs 2 :container true :justifyContent "flex-end"}
      [category-title-menu showing-groups-as]]])

(defn category-title-matched?
  "Checks whether the a category title matches with the selected one"
  [{:keys [title]} selected-title]
  (if (nil? selected-title)
    false
    (= title selected-title)))

(def general-category-icons {const/CATEGORY_ALL_ENTRIES mui-icon-done-all
                             const/CATEGORY_FAV_ENTRIES mui-icon-favorite-border
                             const/CATEGORY_DELETED_ENTRIES mui-icon-delete-outline})
(defn general-category-icon [name]
  [(get general-category-icons name mui-icon-access-time)])

(defn category-item
  []
  (fn [{:keys [title display-title 
               entries-count icon-id 
               icon-name uuid 
               entry-type-uuid] :as category-detail-m} 
       selected-title 
       categories-kind]
    
    (let [display-name (if (nil? display-title) title display-title)
          selected (category-title-matched? category-detail-m selected-title)
          icon-comp (condp = categories-kind
                      :type-categories
                      (entry-type-icon title icon-name)

                      :general-categories
                      (general-category-icon title)

                      :group-categories
                      [group-icon icon-id])
          group-category?  (= categories-kind :group-categories)
          type-category?  (= categories-kind :type-categories)
          selected (if type-category? @(ec-events/is-entry-type-selected entry-type-uuid) selected )
          custom-entry-type?  (if-not type-category? false @(cmn-events/is-custom-entry-type entry-type-uuid))]

      [mui-list-item-button {:sx {"&.MuiListItemButton-root" {:padding-right "1px"}}
                             :on-click #(ec-events/load-category-entry-items category-detail-m categories-kind)
                             ;;:on-context-menu (handle-right-click-factory category-detail-m)
                             :selected selected}

     ;;Include context menus for a category
       #_[category-context-menu]
       [mui-stack {:direction "row" :sx {:width "100%"}}

        [mui-stack {:sx {:width "10%"}}
         [mui-box {:sx {"& .MuiSvgIcon-root" {:color color-primary-main}}}
          icon-comp]]

        [mui-stack {:sx {:width "70%"
                         :padding-left "10px"
                         :max-width "150px"
                         "& .MuiTypography-root" {:font-weight (if selected "bold" "regular")}}}
         [:f> overflow-tool-tip display-name]]

        [mui-stack {:sx {:width "10%"}}
         [mui-typography  {:sx {:padding-right "0px"
                                :color "white"
                                :background-color color-secondary-main
                                :border-radius "10px"
                                :text-align "center"
                                :width "30px"}}
          entries-count]]
        ;; Determine what menus to show for what category item
        (cond
          (and  selected group-category?)
          [mui-stack {:sx {:width "10%" :align-items "center"}}
           [group-category-item-menu uuid]]

          (and selected type-category? custom-entry-type?)
          [mui-stack {:sx {:width "10%" :align-items "center"}}
           [type-category-item-menu entries-count entry-type-uuid]]

          :else
          [mui-stack {:sx {:width "10%" :align-items "center"}}])]])))

(defn entry-category-content []
  (let [showing-groups-as @(ec-events/showing-groups-as)
        all @(ec-events/all-entries-category)
        fav @(ec-events/favorite-entries-category)
        deleted @(ec-events/deleted-entries-category)
        gcats @(ec-events/group-categories)
        type-cats  @(ec-events/type-categories)
        selected-title @(ec-events/selected-category-title)] 
    [mui-box {:style {:height "100%"
                      ;; This will result in a vertical scroll bar for the entire 
                      ;; left side panel when the height of main window is small 
                      :overflow-y "auto"
                      :min-width "250px"}}
     [mui-box
      [mui-list
       [category-item all selected-title :general-categories]
       [category-item fav selected-title :general-categories]
       [category-item deleted selected-title :general-categories]]
      [category-title showing-groups-as]

      (cond
        (or (nil? showing-groups-as) (= showing-groups-as :category))
        [mui-list {:sx {;;:max-height "400px" 
                        ;; This overflow-y is effective only when height of list is more than max-height
                        ;;:overflow-y "auto"
                        }}
         (doall
          (for [g gcats]
            ^{:key (:uuid g)} [category-item  g selected-title :group-categories]))]

        (= showing-groups-as :type)
        [mui-list
         (doall
          (for [type type-cats]
            ^{:key (:entry-type-uuid type)} [category-item type selected-title :type-categories]))]

        (= showing-groups-as :group)
        [gt/group-tree-panel])]]))