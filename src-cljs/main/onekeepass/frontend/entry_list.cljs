(ns onekeepass.frontend.entry-list
  (:require [onekeepass.frontend.common-components :refer [list-items-factory
                                                           menu-action]]
            [onekeepass.frontend.context-menu :as ctx-menu]
            [onekeepass.frontend.constants :as const :refer [ASCENDING
                                                             CREATED_TIME
                                                             MODIFIED_TIME
                                                             TITLE
                                                             UUID_OF_ENTRY_TYPE_LOGIN]]
            [onekeepass.frontend.db-icons :refer [entry-icon render-entry-icon]]
            [onekeepass.frontend.dnd :as dnd]
            [onekeepass.frontend.entry-form-ex :as entry-form-ex]
            [onekeepass.frontend.events.clone-entry-to-other-db :as clone-events]
            [onekeepass.frontend.events.common :as cmn-events]
            [onekeepass.frontend.events.entry-form-dialogs :as dlg-events]
            [onekeepass.frontend.events.entry-form-ex :as form-events]
            [onekeepass.frontend.events.entry-list :as el-events]
            [onekeepass.frontend.events.group-tree-content :as gt-events]
            [onekeepass.frontend.events.move-group-entry :as move-events]
            [onekeepass.frontend.events.remote-storage :as rs-events]
            [onekeepass.frontend.events.tauri-events :as tauri-events]
            [onekeepass.frontend.group-tree-content :as gt-content]
            [onekeepass.frontend.keyboard-shortcuts :as kb-shortcuts]
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
            [onekeepass.frontend.translation :as t]
            [onekeepass.frontend.translation :refer-macros [tr-bl tr-ml] :refer [lstr-ml]]
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

(defn- entry-row-context-items
  [item selected-ids deleted-cat? recycle-bin? group-in-recycle-bin? multi-db-open? active-db-key
   favorites? history-available? os-name mas-build?]
  (let [uuid (:uuid item)
        parent-group-uuid (:parent-group-uuid item)
        deleted? (or deleted-cat? recycle-bin? group-in-recycle-bin?)
        multi-selected? (> (count selected-ids) 1)]
    #_(println "In entry-row-context-items selected-ids multi-selected?" selected-ids multi-selected?)
    (cond
      (and multi-selected? (not deleted?))
      [(ctx-menu/action-item
        {:id "entry-delete-all"
         :text (t/lstr-bl 'deleteAll)
         :action #(el-events/delete-selected-entries-start selected-ids)})]

      deleted?
      [(ctx-menu/action-item
        {:id "entry-put-back"
         :text (t/lstr-ml 'putBack)
         :action #(move-events/move-group-entry-dialog-show :entry true)})
       (ctx-menu/action-item
        {:id "entry-delete-permanently"
         :text (t/lstr-ml 'deletePermanent)
         :action #(move-events/delete-permanent-group-entry-dialog-show :entry true)})]

      :else
      (vec
       (remove nil?
               [;; Entry field copy/open actions. The actions use the entry form data
                ;; loaded for this entry on selection and are safe no-op when the
                ;; entry does not have a value for the field
                (ctx-menu/action-item
                 {:id "entry-copy-username"
                  :text (t/lstr-ml 'copyUsername)
                  :shortcut (kb-shortcuts/menu-shortcut-hint os-name "B")
                  :action #(form-events/copy-entry-form-field-to-clipboard const/USERNAME)})
                (ctx-menu/action-item
                 {:id "entry-copy-password"
                  :text (t/lstr-ml 'copyPassword)
                  :shortcut (kb-shortcuts/menu-shortcut-hint os-name "C")
                  :action #(form-events/copy-entry-form-field-to-clipboard const/PASSWORD)})
                (ctx-menu/action-item
                 {:id "entry-copy-url"
                  :text (t/lstr-ml 'copyUrl)
                  :shortcut (kb-shortcuts/menu-shortcut-hint os-name "U" :shift? true)
                  :action #(form-events/copy-entry-form-field-to-clipboard const/URL)})
                (ctx-menu/action-item
                 {:id "entry-open-url"
                  :text (t/lstr-ml 'openUrl)
                  :shortcut (kb-shortcuts/menu-shortcut-hint os-name "U")
                  :action form-events/open-selected-entry-url})
                (ctx-menu/action-item
                 {:id "entry-copy-totp"
                  :text (t/lstr-ml 'copyTotp)
                  :shortcut (kb-shortcuts/menu-shortcut-hint os-name "T")
                  :action form-events/copy-entry-form-otp-token-to-clipboard})
                (ctx-menu/separator-item)
                (ctx-menu/action-item
                 {:id "entry-edit"
                  :text (t/lstr-ml 'edit)
                  :action form-events/edit-mode-menu-clicked})
                (ctx-menu/action-item
                 {:id "entry-clone"
                  :text (t/lstr-ml 'clone)
                  :action #(dlg-events/clone-entry-options-dialog-show uuid)})
                (when multi-db-open?
                  (ctx-menu/action-item
                   {:id "entry-clone-to-database"
                    :text (t/lstr-ml "cloneToDatabase")
                    :action #(clone-events/clone-entry-to-other-db-dialog-show uuid)}))
                (ctx-menu/action-item
                 {:id "entry-move"
                  :text (t/lstr-ml 'move)
                  :action #(gt-content/move-group-or-entry-dialog-show-with-state
                            :entry
                            (t/lstr-dlg-title 'moveEntry)
                            uuid
                            parent-group-uuid
                            active-db-key)})
                (ctx-menu/action-item
                 {:id "entry-delete"
                  :text (t/lstr-ml 'delete)
                  :action #(form-events/entry-delete-start uuid)})
                (ctx-menu/separator-item)
                (ctx-menu/action-item
                 {:id "entry-favorites"
                  :text (t/lstr-ml 'favorites)
                  :action #(form-events/favorite-menu-checked (not favorites?))})
                (when (and (= os-name const/MACOS) (not mas-build?))
                  (ctx-menu/separator-item))
                (when (and (= os-name const/MACOS) (not mas-build?))
                  (ctx-menu/action-item
                   {:id "entry-perform-auto-type"
                    :text (t/lstr-ml 'performAutoType)
                    :action #(form-events/perform-auto-type-start)}))
                (when (and (= os-name const/MACOS) (not mas-build?))
                  (ctx-menu/action-item
                   {:id "entry-edit-auto-type"
                    :text (t/lstr-ml 'editAutoType)
                    :action #(form-events/entry-auto-type-edit)}))
                (ctx-menu/separator-item)
                (ctx-menu/action-item
                 {:id "entry-history"
                  :text (t/lstr-ml 'history)
                  :enabled? history-available?
                  :action #(form-events/load-history-entries-summary uuid)})
                (when (const/remote-connection-entry-type? (:entry-type-uuid item))
                  (ctx-menu/separator-item))
                (when (const/remote-connection-entry-type? (:entry-type-uuid item))
                  (ctx-menu/action-item
                   {:id "entry-open-remote"
                    :text (t/lstr-l "openRemote")
                    :action #(rs-events/open-entry-remote
                              (:entry-type-uuid item)
                              uuid)}))])))))

(defn- row-item-draggable
  "Form-1 component rendered with :f> so React treats it as a function component.
  use-draggable must be called here, not inside a form-2 inner fn, because hooks
  require a React function component context.
  Selection state arrives as plain deref'd props from row-item (form-2), which has
  Reagent reactive tracking and re-renders when either subscription changes."
  [item style selected-id selected-ids drag-active-uuid deleted-cat? recycle-bin? group-in-recycle-bin? multi-db-open? active-db-key
   favorites? history-available? os-name mas-build?]
  (let [uuid             (:uuid item)
        ^js drag-obj     (dnd/use-draggable #js {:id uuid})
        set-node-ref     (.-setNodeRef drag-obj)
        ^js listeners    (.-listeners drag-obj)
        is-dragging      (.-isDragging drag-obj)
        is-selected      (or (= selected-id uuid) (contains? selected-ids uuid))
        drag-in-progress (boolean drag-active-uuid)
        ;; With DragOverlay: hide the original element during drag (opacity 0 preserves
        ;; layout space so the list doesn't reflow). Two cases to hide:
        ;;   1. This item is the active draggable (isDragging)
        ;;   2. A drag is in progress AND this item is selected (multi-select group hides together)
        
        ;; Using opacity 0 creates blank spaces. Using opacity 1 so that the entries remains in the list
        drag-style       (if (or is-dragging (and drag-in-progress is-selected))
                           (assoc style :opacity 1)
                           style)]
    ;; The outer div occupies the full react-window slot (carries the positioning
    ;; style). The inner mui-list-item is inset with transparent margins, which
    ;; creates the visible gap between rows and lets the selection show as a
    ;; rounded rectangle.
    [:div {:style drag-style}
    [mui-list-item
     (cond-> {:ref    set-node-ref

              ;; Inset the row inside its slot: :my creates the vertical gap between
              ;; entries, :mx the horizontal inset, :border-radius the rounded corners.
              ;; height is reduced by the total vertical margin so rows don't overlap.
              :sx (cond-> {"& .MuiListItemSecondaryAction-root" {:right 0}
                           :height "calc(100% - 8px)"
                           :my "4px"
                           :mx 1
                           ;; ListItem defaults to width:100%; with the 8px left+right
                           ;; margins (:mx 1) that overflows the slot, clipping the right
                           ;; rounded corners. Shrink the width to keep both margins visible
                           ;; so the box is fully rounded and clears the vertical divider.
                           :width "calc(100% - 16px)"
                           :box-sizing "border-box"
                           :border-radius 2
                           ;; Thin separator line between entries (kept commented out;
                           ;; using spacing + rounded selection instead).
                           #_:border-bottom #_"1px solid"
                           #_:border-color #_"divider"}
                    ;; MUI v7 ListItem does not visually reflect :selected prop; apply bgcolor explicitly
                    ;; covers both single-select (selected-id) and multi-select (selected-ids).
                    ;; Subtle translucent primary tint (:selected-item), keeping normal text color.
                    ;; "&:hover" keeps the same bg on mouse-over to suppress hover color on selected items.
                    is-selected
                    (assoc :bgcolor (theme-color @custom-theme-atom :selected-item)
                           "&:hover" {:bgcolor (theme-color @custom-theme-atom :selected-item)}))
              :button true
              :value  uuid
              :on-click (fn [^js e]
                          (if (or (.-ctrlKey e) (.-metaKey e))
                            (el-events/toggle-entry-selection uuid)
                            (do (el-events/clear-entry-selection)
                                (el-events/update-selected-entry-id uuid))))
              :on-context-menu (fn [^js e]
                                 (when-not is-selected
                                   (el-events/clear-entry-selection)
                                   (el-events/update-selected-entry-id uuid))
                                 (ctx-menu/show-app-context-menu!
                                  e
                                  (entry-row-context-items
                                   item
                                   ;; selected-ids does not include the first entry item clicked when user clicks more than one
                                   (conj selected-ids selected-id)
                                   deleted-cat?
                                   recycle-bin?
                                   group-in-recycle-bin?
                                   multi-db-open?
                                   active-db-key
                                   favorites?
                                   history-available?
                                   os-name
                                   mas-build?)))
              :selected (or (= selected-id uuid) (contains? selected-ids uuid))
              :secondaryAction (when (= selected-id uuid)
                                 ;; We are reusing the menu components from entry-form-ex directly here
                                 (r/as-element [entry-form-ex/form-menu selected-id]))}
       
       (and listeners (.-onPointerDown listeners))
       (assoc :on-pointer-down (.-onPointerDown listeners))
       
       (and listeners (.-onKeyDown listeners))
       (assoc :on-key-down (.-onKeyDown listeners)))
     
     [mui-list-item-avatar
      ;; Rounded-square avatar with a subtle tinted background, so both built-in
      ;; SVG icons and custom PNG icons sit on a consistent surface in the list.
      [mui-avatar {:variant "rounded"
                   :sx {:bgcolor (theme-color @custom-theme-atom :selected-item)
                        :border-radius 2}}
       [render-entry-icon {:db-key active-db-key
                           :icon-id (:icon-id item)
                           :custom-icon-uuid (:custom-icon-uuid item)}]]]
     [mui-list-item-text
      {:primaryTypographyProps {:max-width 155
                                :white-space "nowrap"
                                :text-overflow "ellipsis"
                                :overflow "hidden"}
       :secondaryTypographyProps {:max-width 155
                                  :white-space "nowrap"
                                  :text-overflow "ellipsis"
                                  :overflow "hidden"}
       :primary (:title item) :secondary (:secondary-title item)}]]]))

(defn row-item
  "Renders a list item.
  The arg 'props' is a map passed from 'fixed-size-list'.
  Subscriptions are obtained once in the outer fn (form-2 setup) and deref'd
  in the inner fn so Reagent's reactive tracking registers them as dependencies.
  When selected-id, selected-ids, or drag-active-uuid change, Reagent force-updates
  this component and passes fresh plain values down to row-item-draggable (:f> React FC)."
  []
  (let [items-sub                 (el-events/get-selected-entry-items)
        selected-id-sub           (el-events/get-selected-entry-id)
        selected-ids-sub          (el-events/get-selected-entry-ids)
        drag-active-sub           (el-events/get-drag-active-uuid)
        deleted-cat?-sub          (el-events/deleted-category-showing)
        recycle-bin?-sub          (gt-events/recycle-group-selected?)
        group-in-recycle-bin?-sub (gt-events/selected-group-in-recycle-bin?)
        multi-db-open?-sub        (clone-events/multi-db-open?)
        active-db-key-sub         (cmn-events/active-db-key)
        favorites?-sub            (form-events/favorites?)
        history-available?-sub    (form-events/history-available)
        os-name-sub               (cmn-events/os-name)
        mas-build?-sub            (cmn-events/is-mas-build?)]
    (fn [props]
      (let [item             (nth @items-sub (:index props))
            selected-id      @selected-id-sub
            selected-ids     @selected-ids-sub
            drag-active-uuid @drag-active-sub]
        ;; :f> ensures row-item-draggable is a React function component so use-draggable hook is valid
        [:f> row-item-draggable
         item
         (:style props)
         selected-id
         selected-ids
         drag-active-uuid
         @deleted-cat?-sub
         @recycle-bin?-sub
         @group-in-recycle-bin?-sub
         @multi-db-open?-sub
         @active-db-key-sub
         @favorites?-sub
         @history-available?-sub
         @os-name-sub
         @mas-build?-sub]))))

;; A functional component to use react useEffect
(defn fn-entry-list-content
  []
  (let [entries (el-events/get-selected-entry-items)
        
        ;; See common-components for :div-style use
        item-index @(el-events/selected-entry-item-index) 
        entry-items (list-items-factory entries
                                        row-item :div-style {:min-width 225} 
                                        :scroll-to-item-index item-index)
        recycle-bin? (gt-events/recycle-group-selected?)
        deleted-cat? (el-events/deleted-category-showing)
        group-in-recycle-bin? (gt-events/selected-group-in-recycle-bin?)
        
        group-info @(el-events/initial-group-selection-info)
        entry-type-uuid @(el-events/selected-entry-type)
        disable-action (or @recycle-bin? @group-in-recycle-bin? @deleted-cat?)

        {:keys [key-name direction]} @(el-events/entry-list-sort-creteria)
        entries-found? (> (count @entries) 1)

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
           (lstr-ml key-name)]
          [entries-sort-menu]]])]
     ;; Need to use some height for 'gcontent' div so that 
     ;; entry list is shown - particularly in mac Catalina OS (10.15+)
     ;; Need to check in Windows,Linux 
     [:div {:class "gcontent" :style {:margin-bottom 2 :height "200px"}}
      [entry-items]]
     [:div {:class "gfooter" :style {:margin-top 5
                                     :background (theme-color @custom-theme-atom :header-footer)
                                     } }
      [mui-stack {:style {:alignItems "center"
                          ;; need this to align this footer with entry form footer
                          :max-height "46px"}}
       [:div {:style {:margin-top 10 :margin-bottom 10 :margin-right 5 :margin-left 5}}
        [mui-button {;; :variant "outlined"
                     ;; :color "inherit"
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
