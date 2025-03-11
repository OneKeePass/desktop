(ns  onekeepass.frontend.core  ;;ns ^:figwheel-always onekeepass.frontend.core 
  (:require [onekeepass.frontend.common-components :as cc]
            [onekeepass.frontend.constants :as const :refer [THEME_LIGHT]]
            [onekeepass.frontend.entry-category :as ec]
            [onekeepass.frontend.entry-form-ex :as eform-ex]
            [onekeepass.frontend.entry-list :as el]
            [onekeepass.frontend.events.common :as cmn-events] 
            ;; Just to load events defined in this ns
            [onekeepass.frontend.events.auto-open]
            [onekeepass.frontend.events.tauri-events :as tauri-events]
            [onekeepass.frontend.mui-components :as m :refer [custom-theme-atom
                                                              mui-box
                                                              mui-button
                                                              mui-css-baseline
                                                              mui-icon-button
                                                              mui-icon-fingerprint
                                                              mui-stack
                                                              mui-styled-engine-provider
                                                              mui-tab mui-tabs
                                                              mui-theme-provider
                                                              mui-typography
                                                              split-pane
                                                              theme-color]]
            [onekeepass.frontend.start-page :as sp]
            [onekeepass.frontend.tool-bar :as tool-bar]
            [onekeepass.frontend.translation :as t :refer-macros [tr-t tr-bl]]
            [reagent.dom :as rdom]))

;;(set! *warn-on-infer* true)

(defn right-content
  "Component that has entry list and any selected entry content"
  []
  [split-pane {:split "vertical"
                 ;;:size "200" 
               :minSize "200"
               :maxSize "275"
               :primary "first"
               :resizerClassName  (if (= @(cmn-events/app-theme) THEME_LIGHT) 
                                    "Resizer1 vertical" "Resizer2 vertical")
               :style {:position "relative"}}
   ;; Pane1
   [el/entry-list-content]
   ;; Pane2
   [eform-ex/entry-content-core]])

(defn group-entry-content
  "Shows the group and entry content from the current active db "
  []
  [split-pane {:split "vertical"
               ;;:size "200" 
               :minSize "250"
               :maxSize "260"
               :primary "first"
               :style {:position "relative"}
               :pane1Style {:background (theme-color @custom-theme-atom :bg-default)}
               :resizerClassName (if (= @(cmn-events/app-theme) THEME_LIGHT) 
                                   "Resizer1 vertical" "Resizer2 vertical")

              ;; Another way of styling split pane's resizer. But did not work as expected
              ;; Leaving it here commenting out for future use if possible
              ;;  :resizerStyle {:background "darkslategrey" 
              ;;                 :width "11px"
              ;;                 :margin "0 -5px"
              ;;                 :border-left "5px solid rgba(255, 255, 255, 0)"
              ;;                 :border-right "5px solid rgba(255, 255, 255, 0)" 
              ;;                 :hover {:border-left "5px solid rgba(0, 0, 0, 0.5)"
              ;;                         :border-right "5px solid rgba(0, 0, 0, 0.5)"
              ;;                         ;;:background "yellow"
              ;;                         }
              ;;                 }

               ;;:pane2Style {:max-width "25%"}
               }
   ;; Pane1
   [ec/entry-category-content]
   ;; Pane2
   [right-content]])

(defn locked-content []
  (let [biometric-type @(cmn-events/biometric-type-available)]
    [mui-stack {:sx {:height "100%"
                     :align-items "center"
                     :justify-content "center"}}
     [mui-box
      [mui-stack {:sx {:align-items "center"}}
       [mui-typography {:variant "h4"} (tr-t databaseLocked)]]

      [mui-stack {:sx {:mt 2}}
       [mui-typography {:variant "h6"}
        @(cmn-events/active-db-key)]]

      [mui-stack {:sx {:mt 3 :align-items "center"}}
       (cond
         (or (= biometric-type const/TOUCH_ID) (= biometric-type const/FACE_ID))
         [mui-icon-button {:aria-label "fingerprint"
                           :color "secondary"
                           :on-click #(cmn-events/unlock-current-db biometric-type)}
          [mui-icon-fingerprint {:sx {:font-size 40}}]]

         :else nil)

       [mui-button {:variant "outlined"
                    :color "inherit"
                    :on-click #(cmn-events/unlock-current-db biometric-type)}
        (tr-bl quickUnlock)]]]]))

(defn group-entry-content-tabs
  "Presents tabs for all opened dbs.The actual content of a selected 
   tab is determined in 'group-entry-content' through the 'db-key' selection
  The arg 'db-list' is a vector of db summary maps
  "
  [db-list]
  [mui-box
   [mui-box
    [mui-tabs {;; This determines which tab's content shown
               :value @(cmn-events/active-db-key)
               ;; Sets the active db so that group-entry-content can show selected db data
               ;; 'val' is from :value prop of 'mui-tab' below
               :on-change (fn [_event val] (cmn-events/set-active-db-key val))}
     (doall
      (for [{:keys [db-key database-name]} db-list]
        ^{:key db-key}  [mui-tab {:label database-name :value db-key}]))]]])

;; A functional component that can use effect 
(defn main-content []
  (fn []
    (let [content-to-show @(cmn-events/content-to-show)
          db-list @(cmn-events/opened-db-list)]

      [mui-stack {:sx {:height "100%"}
                  ;; Tracks the user activity so that session timeout can be initiated if no activity is seen beyond a limit 
                  :on-click cmn-events/user-action-detected}
       ;; Tabs are shown only when there are more than 1 databases are open
       (when (> (count db-list) 1)
         [group-entry-content-tabs db-list])

       ;; A Gap between tab header and content
       [:div {:style {:height "2px"
                      :border-bottom-width "1px"
                      :border-bottom-style "solid"
                      :border-bottom-color (theme-color @custom-theme-atom :color1)
                      :margin-bottom "2px"}}]
       (cond
         (= content-to-show :group-entry)
         [group-entry-content]

         (= content-to-show :entry-history)
         [eform-ex/entry-history-content-main]

         (= content-to-show :locked-content)
         [locked-content]

         :else
         [group-entry-content])])))

(defn header-bar []
  [:div  [:f> tool-bar/top-bar]])

(defn common-snackbars []
  [:<>
   ;; message-sanckbar shows generic message
   [cc/message-sanckbar]
   ;; message-sanckbar-alert mostly for error notification
   [cc/message-sanckbar-alert]])

(defn root-content
  "A functional component which is the root of all the app components"
  []
  (fn []
    (if @(cmn-events/show-start-page)
      [:<>
       [sp/welcome-content]
       [common-snackbars]]
      [:div {:class "box"}  ;;:style {:height "100vh"}
       [:div {:class "cust_row header"}
        [header-bar]
        [common-snackbars]]

       [:div {:class "cust_row content"
              :style {:height "80%"}} ;;height "80%" added for WebKit
        [:f> main-content]]

       ;;At this time, no use case for the footer
       #_[:div {:class "cust_row footer"}
          #_[:span "footer (fixed height)"]
          ;;need to make tag p's margin 0px if we use tag p. Include {:style {:margin 0}}
          [:p "footer (fixed height)"]]])))


(defn main-app-with-theme
  "Main component that uses custom theme"
  []
  (fn [theme-mode] 
    (let [theme (m/create-custom-theme theme-mode)]
      [mui-styled-engine-provider {:injectFirst true}
       [mui-theme-provider {:theme theme}
        [mui-css-baseline
         [:f> root-content]]]])))

(defn main-app []
  
  (if (and
       @(cmn-events/language-translation-loading-completed)
         @(cmn-events/app-preference-loading-completed)) 
      [:f> main-app-with-theme @(cmn-events/app-theme)]
      [:div "Please wait......"]))

(defn ^:export init
  "Render the created components to the element that has an id 'app' in index.html"
  []
  ;; Some initializations need to be done before app window loads
  (t/load-language-translation)
  (cmn-events/sync-initialize)
  (tauri-events/register-tauri-events)
  (cmn-events/init-session-timeout-tick)

  (rdom/render
   [main-app] (.getElementById  ^js/Window js/document "app")))

;;Renders on load
(init)


;;Renders on load
;; this only gets called once
;;(defonce start-up (do (init) true))




