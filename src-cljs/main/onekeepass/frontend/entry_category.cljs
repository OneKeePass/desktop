(ns onekeepass.frontend.entry-category
  (:require [camel-snake-kebab.core] ;; required for use in macro
            [onekeepass.frontend.common-components :refer [message-sanckbar-alert
                                                           overflow-tool-tip]]
            [onekeepass.frontend.constants :as const :refer [GROUPING_LABEL_CATEGORIES
                                                             GROUPING_LABEL_GROUPS
                                                             GROUPING_LABEL_TAGS
                                                             GROUPING_LABEL_TYPES]]
            [onekeepass.frontend.context-menu :as ctx-menu]
            [onekeepass.frontend.db-icons :refer [entry-type-icon group-icon]]
            [onekeepass.frontend.events.common :as cmn-events] 
            ;; need to be replaced events from ec-events?
            [onekeepass.frontend.events.entry-category :as ec-events]
            [onekeepass.frontend.events.group-form :as gf-events]
            [onekeepass.frontend.group-form :as gf]
            [onekeepass.frontend.group-tree-content :as gt]
            [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                              mui-avatar
                                                              mui-box
                                                              mui-divider
                                                              mui-icon-access-time
                                                              mui-icon-button
                                                              mui-icon-check
                                                              mui-icon-delete-outline
                                                              mui-icon-done-all
                                                              mui-icon-favorite-border
                                                              mui-icon-more-vert
                                                              mui-icon-sell-outlined
                                                              mui-list
                                                              mui-list-item-button
                                                              mui-list-item-icon
                                                              mui-list-item-text
                                                              mui-menu
                                                              mui-menu-item
                                                              mui-stack
                                                              mui-typography 
                                                              theme-color]]
            [onekeepass.frontend.translation :as t :refer-macros [tr-l tr-bl tr-ml tr-l-cv tr-entry-type-title-cv]]
            [reagent.core :as r]))

;;(set! *warn-on-infer* true)

(defn- menu-action [anchor-el action & action-args]
  (fn [^js/Event e]
    (reset! anchor-el nil)
    (apply action action-args)
    (.stopPropagation ^js/Event e)))

(defn category-title-menu-items
  []
  (fn [anchor-el showing-groups-as]
    (let [show-category? (or (nil? showing-groups-as) (= showing-groups-as :category))
          show-groups? (= showing-groups-as :group)
          show-types? (= showing-groups-as :type)
          show-tags? (= showing-groups-as :tag)
          root-group-uuid @(ec-events/root-group-uuid)]
      [mui-menu {:anchorEl @anchor-el
                 :open (if @anchor-el true false)
                 :on-close #(reset! anchor-el nil)}

       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action anchor-el ec-events/show-as-type-category)}
        (when show-types?
          [mui-list-item-icon [mui-icon-check]])
        (if show-types? (tr-l-cv GROUPING_LABEL_TYPES)
            [mui-list-item-text {:inset true} (tr-l-cv GROUPING_LABEL_TYPES)])]

       [mui-menu-item {:sx {:padding-left "1px"}
                       :on-click (menu-action anchor-el ec-events/show-as-tag-category)}
        (when show-tags?
          [mui-list-item-icon [mui-icon-check]])
        (if show-tags? (tr-l-cv GROUPING_LABEL_TAGS)
            [mui-list-item-text {:inset true} (tr-l-cv GROUPING_LABEL_TAGS)])]

       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider false
                       :on-click (menu-action anchor-el ec-events/show-as-group-category)}
        (when show-category?
          [mui-list-item-icon [mui-icon-check]])
        (if show-category? (tr-l-cv GROUPING_LABEL_CATEGORIES)
            [mui-list-item-text {:inset true} (tr-l-cv GROUPING_LABEL_CATEGORIES)])]

       [mui-menu-item {:sx {:padding-left "1px"}
                       :divider (if show-category? true false)
                       :on-click (menu-action anchor-el ec-events/show-as-group-tree)}
        (when show-groups?
          [mui-list-item-icon [mui-icon-check]])
        (if show-groups? (tr-l-cv GROUPING_LABEL_GROUPS)
            [mui-list-item-text {:inset true} (tr-l-cv GROUPING_LABEL_GROUPS)])]

       (when show-category?
         [mui-menu-item {:sx {:padding-left "1px"}
                         :divider false
                         :on-click (menu-action anchor-el ec-events/initiate-new-blank-group-form root-group-uuid)}
          [mui-list-item-text {:inset true} (tr-ml addCategory)]])])))

(defn category-title-menu
  "Menu popup to provide options to swicth between various category grouping in the left bottom panel"
  []
  (fn [showing-groups-as]
    (let [anchor-el (r/atom nil)]
      [:div
       [mui-icon-button {:edge "start"
                         :on-click (fn [^js/Event e] (reset! anchor-el (-> e .-currentTarget)))
                         :style {}} [mui-icon-more-vert]]
       [category-title-menu-items anchor-el showing-groups-as]
       [gf/group-content-dialog-main]])))

(defn group-category-item-menu-items
  "Shows the menu items for the group category is selected"
  []
  (fn [anchor-el g-uuid]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}

     [mui-menu-item {:divider false
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gf-events/find-group-by-id g-uuid :edit)
                                  (.stopPropagation ^js/Event %))} (tr-l "edit")]
     [mui-menu-item {:divider false
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (gf-events/find-group-by-id g-uuid :info)
                                  (.stopPropagation ^js/Event %))} (tr-l "info")]]))

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

(defn type-category-item-menu-items
  "Type category specific menu items when it is selected"
  []
  (fn [anchor-el entries-count entry-type-uuid]
    [mui-menu {:anchorEl @anchor-el
               :open (if @anchor-el true false)
               :on-close #(reset! anchor-el nil)}

     [mui-menu-item {:divider false
                     :disabled (> entries-count 0)
                     :on-click #(do
                                  (reset! anchor-el nil)
                                  (ec-events/delete-custom-entry-type entry-type-uuid)
                                  (.stopPropagation ^js/Event %))} (tr-l "deleteType")]]))

(defn type-category-item-menu
  "Menu popup to provide menu options when a type category is selected on the bottom panel"
  []
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
  ;; Section header that separates the top general categories from the group/type/tag
  ;; list below. A top divider + extra vertical spacing give breathing room above and
  ;; below, and the label itself is styled distinctly (section-header color, small bold
  ;; uppercase) so it reads as a heading rather than a selectable row.
  [mui-stack {:direction "row"
              :sx {:width "100%"
                   :align-items "center"
                   :mt "6px"
                   :mb "6px"
                   :pt "8px"
                   :pb "8px"
                   :background-color (theme-color @custom-theme-atom :color1)
                   
                   ;; :border-top ".5px solid"
                   ;; :border-color "divider"
                   
                   }}
   [mui-stack {:direction "row" :sx {:width "90%"  :align-items "center"}}
    [mui-typography {:sx {:padding-left "16px"
                          :color (theme-color @custom-theme-atom :section-header)
                          :font-size "0.95rem"
                          :font-weight 700
                          :letter-spacing "0.08em"
                          ;;:text-transform "uppercase"
                          }}
     (cond
       (or (nil? showing-groups-as) (= showing-groups-as :type))
       (tr-l-cv GROUPING_LABEL_TYPES)

       (= showing-groups-as :tag)
       (tr-l-cv GROUPING_LABEL_TAGS)

       (= showing-groups-as :category)
       (tr-l-cv GROUPING_LABEL_CATEGORIES)

       (= showing-groups-as :group)
       (tr-l-cv GROUPING_LABEL_GROUPS))]]
   [mui-stack {:direction "row" :sx {:width "10%"}}
    [category-title-menu showing-groups-as]]])

(def general-category-icons {const/CATEGORY_ALL_ENTRIES mui-icon-done-all
                             const/CATEGORY_FAV_ENTRIES mui-icon-favorite-border
                             const/CATEGORY_DELETED_ENTRIES mui-icon-delete-outline})

(defn general-category-icon [name]
  [(get general-category-icons name mui-icon-access-time)])

(def ^:private category-icon-colors
  "Per entry-type / general-category icon colors. Keyed by the category title
   (entry-type name constant, or general-category constant). The avatar
   background uses a light alpha tint of the same color — see 'category-colors'.
   Tags use a single color and group categories fall back to the theme color
   (their KeePass icons are already multi-colored).

   To tweak later:
   - Change an individual icon color: edit its hex value below (and 'tag-icon-color'
     for tags). The avatar background tint is derived automatically from it.
   - Change how strong the background tint is for ALL icons: edit the alpha suffix
     in 'category-colors' (the (str c \"33\") part). It is the last two hex digits of
     an 8-digit #RRGGBBAA color: \"33\" ≈ 20%, \"4d\" ≈ 30%, \"66\" ≈ 40%, \"1a\" ≈ 10%.
     Bump it up if the tint looks too faint (e.g. on the dark theme)."
  {const/LOGIN_TYPE_NAME "#1e88e5"                     ;; blue
   const/CREDIT_DEBIT_CARD_TYPE_NAME "#43a047"         ;; green
   const/BANK_ACCOUNT_TYPE_NAME "#00897b"              ;; teal
   const/WIRELESS_ROUTER_TYPE_NAME "#3949ab"           ;; indigo
   const/PASSPORT_TYPE_NAME "#5e35b1"                  ;; deep purple
   const/AUTO_DB_OPEN_TYPE_NAME "#6d4c41"              ;; brown
   const/IDENTITY_TYPE_NAME "#00acc1"                  ;; cyan
   const/DRIVER_LICENSE_TYPE_NAME "#f4511e"            ;; deep orange
   const/EMAIL_ACCOUNT_TYPE_NAME "#e53935"             ;; red
   const/SSH_LOGIN_TYPE_NAME "#546e7a"                 ;; blue grey
   const/API_CREDENTIAL_TYPE_NAME "#8e24aa"            ;; purple
   const/DATABASE_CREDENTIAL_TYPE_NAME "#5c6bc0"       ;; indigo light
   const/SOFTWARE_LICENSE_TYPE_NAME "#7cb342"          ;; light green
   const/MEMBERSHIP_TYPE_NAME "#fb8c00"                ;; orange
   const/CRYPTO_WALLET_TYPE_NAME "#fdd835"             ;; yellow
   const/INSURANCE_POLICY_TYPE_NAME "#039be5"          ;; light blue
   const/REMOTE_CONNECTION_SFTP_TYPE_NAME "#26a69a"    ;; teal light
   const/REMOTE_CONNECTION_WEBDAV_TYPE_NAME "#00acc1"  ;; cyan
   const/CATEGORY_ALL_ENTRIES "#5c6bc0"                ;; indigo
   const/CATEGORY_FAV_ENTRIES "#e53935"                ;; red
   const/CATEGORY_DELETED_ENTRIES "#757575"})          ;; grey

(def ^:private tag-icon-color "#fb8c00")               ;; amber for all tags

(defn- category-colors
  "Returns [icon-color avatar-bg] for a category row. When a specific color is
   defined, the avatar background is a ~20% alpha tint of it (8-digit hex). Falls
   back to the theme icon color + neutral selection tint otherwise."
  [theme categories-kind title]
  (let [c (if (= categories-kind :tag-categories)
            tag-icon-color
            (get category-icon-colors title))]
    (if c
      [c (str c "33")]
      [(theme-color theme :entry-category-icons)
       (theme-color theme :selected-item)])))

(defn translate-category-item-title
  [title categories-kind custom-entry-type?]
  (cond
    ;; It appears 'category-item' is called with nil title initially
    ;; Should not call macros with nil key!
    (nil? title)
    title

    (and (= categories-kind :type-categories) (not custom-entry-type?))
    (tr-entry-type-title-cv title)

    (= categories-kind :general-categories)
    (tr-l-cv title)

    :else
    title))

(defn- category-context-items
  [category-detail-m categories-kind custom-entry-type? entries-count]
  (vec
   (remove nil?
           [(ctx-menu/action-item
             {:id "category-show-entries"
              :text "Show Entries"
              :action #(ec-events/load-category-entry-items category-detail-m categories-kind)})
            (when (= categories-kind :group-categories)
              (ctx-menu/action-item
               {:id "category-group-edit"
                :text (t/lstr-l "edit")
                :action #(gf-events/find-group-by-id (:group-uuid category-detail-m) :edit)}))
            (when (= categories-kind :group-categories)
              (ctx-menu/action-item
               {:id "category-group-info"
                :text (t/lstr-l "info")
                :action #(gf-events/find-group-by-id (:group-uuid category-detail-m) :info)}))
            (when (and (= categories-kind :type-categories) custom-entry-type?)
              (ctx-menu/action-item
               {:id "category-type-delete"
                :text (t/lstr-l "deleteType")
                :enabled? (zero? entries-count)
                :action #(ec-events/delete-custom-entry-type (:entry-type-uuid category-detail-m))}))])))

(defn category-item
  "Returns form-2 reagent component"
  []
  (fn [{:keys [title display-title
               entries-count icon-id
               icon-name
               group-uuid
               entry-type-uuid] :as category-detail-m}
       categories-kind]

    (let [display-name (if (nil? display-title) title display-title)
          icon-comp (condp = categories-kind
                      :type-categories
                      (entry-type-icon title icon-name)

                      :general-categories
                      (general-category-icon title)

                      :group-categories
                      [group-icon icon-id]

                      :tag-categories
                      [mui-icon-sell-outlined])

          group-category?  (= categories-kind :group-categories)
          type-category?  (= categories-kind :type-categories)
          general-category? (= categories-kind :general-categories)

          row-selected? @(ec-events/is-selected-category category-detail-m)
          custom-entry-type?  (if-not type-category? false @(cmn-events/is-custom-entry-type entry-type-uuid))
          display-name (translate-category-item-title display-name categories-kind custom-entry-type?)
          [icon-color icon-bg] (category-colors @custom-theme-atom categories-kind title)]

      [mui-list-item-button {:sx {"&.MuiListItemButton-root" {:padding-right "1px"}
                                  ;; Match the entry-list rows: inset rounded rectangle with a
                                  ;; gap between items. width is shrunk by the 8px left+right
                                  ;; margins so both sides stay rounded and clear of the edges.
                                  :border-radius 2
                                  :my "4px"
                                  :mx 1
                                  :width "calc(100% - 16px)"
                                  :box-sizing "border-box"
                                  ;; Use the shared selection tint (theme customColors.selectedItem)
                                  ;; instead of MUI's default .Mui-selected color.
                                  "&.Mui-selected" {:bgcolor (theme-color @custom-theme-atom :selected-item)}
                                  "&.Mui-selected:hover" {:bgcolor (theme-color @custom-theme-atom :selected-item)}}
                             :on-click #(ec-events/load-category-entry-items
                                         category-detail-m categories-kind)
                             :on-context-menu (fn [^js/Event e]
                                                (ec-events/load-category-entry-items
                                                 category-detail-m categories-kind)
                                                (ctx-menu/show-app-context-menu!
                                                 e
                                                 (category-context-items
                                                  category-detail-m
                                                  categories-kind
                                                  custom-entry-type?
                                                  entries-count)))
                             :selected row-selected?}

     ;;Include context menus for a category
       #_[category-context-menu]
       [mui-stack {:direction "row" :sx {:width "100%" :align-items "center"}}

        [mui-stack {:sx {:width "10%"}}
         ;; Rounded-square tinted avatar matching the entry-list item icons, so
         ;; both panels share a consistent icon surface. The SvgIcon color
         ;; override keeps the monochrome category icons on theme.
         [mui-avatar {:variant "rounded"
                      :sx {:width 28 :height 28
                           :bgcolor icon-bg
                           :border-radius 2
                           ;; Normalize every icon to one size regardless of source
                           ;; (MUI type/general icons render ~24px, KeePass group icons
                           ;; ~16px). '&&' raises specificity to beat the per-icon
                           ;; '& svg' em sizing set inside entry-icon / group-icon.
                           ;; Tune the avatar (:width/:height above) and the icon size
                           ;; (18px below) together if you want them larger/smaller.
                           "&& .MuiSvgIcon-root" {:color icon-color
                                                  :width "18px" :height "18px" :font-size "18px"}}}
          icon-comp]]

        [mui-stack {:sx {:width "70%"
                         :padding-left "16px"
                         :max-width "150px"
                         "& .MuiTypography-root" {:font-weight (if row-selected? "bold" "regular")}}}
         [:f> overflow-tool-tip display-name]]

        [mui-stack {:sx {:width "10%"}}
         ;; General categories (AllEntries/Favorites/Deleted) always show the
         ;; count; bottom-panel grouped items show it only when selected, to keep
         ;; the grouping list uncluttered.
         ;; :variant "caption" keeps the count font size consistent with the
         ;; group tree count pill (see tree-label in group_tree_content)
         (when (or general-category? row-selected?)
           [mui-typography  {:variant "caption"
                             :sx {:padding-right "0px"
                                  :color "white"
                                  :background-color (theme-color @custom-theme-atom :category-item)
                                  :border-radius "10px"
                                  :text-align "center"
                                  :width "30px"}}
            entries-count])]
        ;; Determine what menus to show based on grouping kind selection
        (cond
          (and row-selected? group-category?)
          [mui-stack {:sx {:width "10%" :align-items "center"}}
           [group-category-item-menu group-uuid]]

          (and row-selected? type-category? custom-entry-type?)
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
        tag-cats  @(ec-events/tag-categories)]

    [mui-box {:style {:height "100%"
                      ;; This will result in a vertical scroll bar for the entire
                      ;; left side panel when the height of main window is small
                      :overflow-y "auto"
                      :overflow-x "hidden"
                      ;; Space below the group tree so it isn't flush with the bottom edge
                      :padding-bottom "12px"
                      :width "100%"}}
     [mui-box
      [mui-list
       ^{:key :all} [category-item all :general-categories]
       ^{:key :fav} [category-item fav :general-categories]
       ^{:key :deleted} [category-item deleted :general-categories]]
      #_[mui-divider {:sx {}}]
      [category-title showing-groups-as]

      (cond
        (or (nil? showing-groups-as) (= showing-groups-as :category))
        [mui-list {:sx {;;:max-height "400px" 
                        ;; This overflow-y is effective only when height of list is more than max-height
                        ;;:overflow-y "auto"
                        }}
         (doall
          (for [{:keys [group-uuid] :as group-category-cat-detail} gcats]
            ;; nil check is done to avoid react unique key warning for category-item
            ;; It seems that when Cat selection is switched from Type->Tag->Category
            ;; entry-category-content is called first with previous entry category kind
            ;; followed by the selected 'showing-groups-as'
            (when-not (nil? group-uuid)
              ^{:key (:group-uuid group-category-cat-detail)} [category-item group-category-cat-detail :group-categories])))]

        (= showing-groups-as :type)
        [mui-list
         (doall
          (for [{:keys [entry-type-uuid] :as type-cat-detail} type-cats]
            (when-not (nil? entry-type-uuid)
              ^{:key entry-type-uuid} [category-item type-cat-detail :type-categories])))]

        (= showing-groups-as :tag)
        [mui-list
         (doall
          (for [{:keys [tag-id] :as tag-cat-detail} tag-cats]
            (when-not (nil? tag-id)
              ^{:key tag-id} [category-item tag-cat-detail :tag-categories])))]

        (= showing-groups-as :group)
        [gt/group-tree-panel])]]))
