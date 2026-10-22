(ns onekeepass.frontend.find-entries
  "The on demand Find Entries page. Entries matching the selected kind are listed on the left
   and the selected entry is shown in the (editable) entry form on the right"
  (:require
   [onekeepass.frontend.common-components :refer [list-items-factory]]
   [onekeepass.frontend.db-icons :as db-icons]
   [onekeepass.frontend.entry-form-ex :as eform-ex]
   [onekeepass.frontend.events.find-entries :as fe-events]
   [onekeepass.frontend.mui-components :refer [mui-avatar
                                               mui-box
                                               mui-divider
                                               mui-link
                                               mui-list-item
                                               mui-list-item-avatar
                                               mui-list-item-text
                                               mui-menu-item
                                               mui-stack
                                               mui-text-field
                                               mui-tooltip
                                               mui-typography]]
   [onekeepass.frontend.translation :refer [lstr-bl lstr-l]]
   [onekeepass.frontend.utils :refer [to-file-size-str]]
   [reagent.core :as r]))

(def ^:private kind-label-keys {:with-attachments "findEntriesWithAttachments"
                                :with-history "findEntriesWithHistory"
                                :passkeys "findEntriesPasskeys"
                                :totp "findEntriesTotp"
                                :by-size "findEntriesBySize"})

(defn- count-text
  "Picks the singular or plural label key for the count"
  [n one-key many-key]
  (lstr-l (if (= n 1) one-key many-key) {:count n}))

(defn- kind-detail
  "The second line of a row - what makes this entry part of the selected kind's list"
  [kind {:keys [summary data-size attachment-count history-count]}]
  (condp = kind
    :with-attachments (count-text attachment-count "attachmentCount" "attachmentsCount")
    :with-history (count-text history-count "versionCount" "versionsCount")
    :by-size (to-file-size-str data-size)
    (:secondary-title summary)))

(defn- row-item [_props]
  (fn [props]
    (let [items (fe-events/found-items)
          kind @(fe-events/find-kind)
          selected-id @(fe-events/selected-entry-id)
          {:keys [summary data-size] :as item} (nth @items (:index props))
          {:keys [uuid title icon-id custom-icon-uuid]} summary
          detail (kind-detail kind item)]
      [mui-list-item {:style (:style props)
                      :button true
                      :value uuid
                      :on-click #(fe-events/entry-selected uuid)
                      :selected (= selected-id uuid)}
       [mui-list-item-avatar
        [mui-avatar [db-icons/render-entry-icon {:icon-id icon-id
                                                 :custom-icon-uuid custom-icon-uuid}]]]
       [mui-list-item-text
        {:primary title
         :secondary (if (= kind :by-size)
                      (r/as-element
                       [mui-tooltip {:title (lstr-l "bytesCount" {:count (.toLocaleString data-size)})
                                     :placement "bottom-start"
                                     :enterDelay 500}
                        [:span detail]])
                      detail)}]])))

(defn- header []
  (let [kind @(fe-events/find-kind)
        n (count @(fe-events/found-items))
        total-size @(fe-events/total-data-size)]
    [mui-stack {:direction "row"
                :sx {:align-items "center"
                     :padding "8px 16px"
                     :gap "16px"}}
     [mui-text-field {:select true
                      :variant "standard"
                      :value (name kind)
                      :on-change #(fe-events/kind-selected (keyword (-> % .-target .-value)))
                      :sx {:min-width "200px"}}
      (doall
       (for [k fe-events/find-kinds]
         ^{:key k} [mui-menu-item {:value (name k)} (lstr-l (get kind-label-keys k))]))]

     [mui-typography {:variant "body2"}
      (count-text n "entryFoundCount" "entriesFoundCount")
      (when (and (= kind :by-size) (some? total-size))
        (str " · " (to-file-size-str total-size)))]

     [mui-box {:sx {:flex-grow 1}}]

     [mui-link {:sx {:color "primary.main" :cursor "pointer"}
                :underline "always"
                :variant "subtitle1"
                :on-click #(fe-events/find-entries-close)}
      (lstr-bl 'close)]]))

(defn- entries-list []
  (let [entry-items (list-items-factory (fe-events/found-items)
                                        row-item
                                        :item-size 60
                                        :list-style {:max-width nil}
                                        :div-style {:width "100%"})]
    (fn []
      (if (empty? @(fe-events/found-items))
        [mui-stack {:sx {:height "100%" :align-items "center" :justify-content "center"}}
         [mui-typography {:variant "body2"} (lstr-l "noEntriesFound")]]
        [entry-items]))))

(defn find-entries-content-main []
  [mui-stack {:sx {:height "100%" :overflow "hidden"}}
   [header]
   [mui-divider]
   ;; The two panes are centered with a max width so that the list and the form do not
   ;; spread too wide on large windows
   [mui-stack {:direction "row"
               :sx {:flex-grow 1 :min-height 0 :justify-content "center"}}
    [mui-stack {:direction "row"
                :divider (r/as-element [mui-divider {:orientation "vertical" :flexItem true}])
                :sx {:width "100%" :max-width "1400px" :height "100%"}}
     [mui-stack {:sx {:width "35%" :min-width "280px" :height "100%"}}
      [entries-list]]
     [mui-stack {:direction "row"
                 :sx {:flex-grow 1 :min-width 0 :height "100%" :overflow-y "auto"}}
      [eform-ex/entry-content-core]]]]])
