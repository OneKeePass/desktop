(ns onekeepass.frontend.db-icons
  (:require
   [onekeepass.frontend.constants :as const]
   [onekeepass.frontend.utils :refer  [str->int]]
   [onekeepass.frontend.mui-components :as m :refer [mui-svg-icon
                                                     mui-box
                                                     mui-icon-vpn-key-outlined
                                                     mui-icon-credit-card-outlined
                                                     mui-icon-flight-takeoff-outlined
                                                     mui-icon-launch
                                                     mui-icon-login-outlined
                                                     mui-icon-wifi-outlined
                                                     mui-icon-account-balance-outlined]]))

;; TODO: May need to replace this with the techique used for custom icon loadings from resource files
;; All standard KP icons with the mapping from icon idex to the svg icon reagent component
(def all-icons {0
                [mui-svg-icon {:xmlns:dc "http://purl.org/dc/elements/1.1/" :xmlns:cc "http://creativecommons.org/ns#" :xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#" :xmlns:svg "http://www.w3.org/2000/svg" :xmlns "http://www.w3.org/2000/svg" :id "svg10" :enable-background "new 0 0 48 48" :viewBox "0 0 48 48" :version "1"}
                 [:defs {:id "defs14"}]
                 [:g {:style {:fill "#ffa000"} :transform "matrix(0.73333333,0,0,1,7.4259892,0)" :id "g6"}
                  [:polygon {:id "polygon2" :points "22,45 18,41 18,21 30,21 30,29 28,31 30,33 30,35 28,37 30,39 30,41 26,45"}]
                  [:path {:id "path4" :d "M38 7.8c-.5-1.8-2-3.1-3.7-3.6C31.9 3.7 28.2 3 24 3s-7.9.7-10.3 1.2C12 4.7 10.5 6 10 7.8c-.5 1.7-1 4.1-1 6.7s.5 5 1 6.7c.5 1.8 1.9 3.1 3.7 3.5 2.4.6 6.1 1.3 10.3 1.3 4.2.0 7.9-.7 10.3-1.2 1.8-.4 3.2-1.8 3.7-3.5s1-4.1 1-6.7c0-2.7-.5-5.1-1-6.8zM29 13H19c-1.1.0-2-.9-2-2V9c0-.6 3.1-1 7-1s7 .4 7 1v2c0 1.1-.9 2-2 2z"}]]
                 [:rect {:style {:fill "#d68600"} :id "rect8" :height "19" :width "2" :y "26" :x "23.559322"}]]
                1
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#7cb342" :d "M24 4C13 4 4 13 4 24s9 20 20 20 20-9 20-20S35 4 24 4z"}]
                 [:path {:fill "#0277bd" :d "M45 24c0 11.7-9.5 21-21 21S3 35.7 3 24 12.3 3 24 3s21 9.3 21 21zM23.8 33.7c0-.4-.2-.6-.6-.8-1.3-.4-2.5-.4-3.6-1.5-.2-.4-.2-.8-.4-1.3-.4-.4-1.5-.6-2.1-.8-.8.0-1.7.0-2.7.0-.4.0-1.1.0-1.5.0-.6-.2-1.1-1.1-1.5-1.7.0-.2.0-.6-.4-.6-.4-.2-.8.2-1.3.0-.2-.2-.2-.4-.2-.6.0-.6.4-1.3.8-1.7.6-.4 1.3.2 1.9.2.2.0.2.0.4.2.6.2.8 1 .8 1.7.0.2.0.4.0.4.0.2.2.2.4.2.2-1.1.2-2.1.4-3.2.0-1.3 1.3-2.5 2.3-2.9.4-.2.6.2 1.1.0 1.3-.4 4.4-1.7 3.8-3.4-.4-1.5-1.7-2.9-3.4-2.7-.4.2-.6.4-1 .6-.6.4-1.9 1.7-2.5 1.7-1.1-.2-1.1-1.7-.8-2.3.2-.8 2.1-3.6 3.4-3.1.2.2.6.6.8.8.4.2 1.1.2 1.7.2.2.0.4.0.6-.2.2-.2.2-.2.2-.4.0-.6-.6-1.3-1-1.7-.4-.4-1.1-.8-1.7-1.1-2.1-.6-5.5.2-7.1 1.7s-2.9 4-3.8 6.1c-.4 1.3-.8 2.9-1 4.4-.2 1-.4 1.9.2 2.9.6 1.3 1.9 2.5 3.2 3.4.8.6 2.5.6 3.4 1.7.6.8.4 1.9.4 2.9.0 1.3.8 2.3 1.3 3.4.2.6.4 1.5.6 2.1.0.2.2 1.5.2 1.7 1.3.6 2.3 1.3 3.8 1.7.2.0 1-1.3 1-1.5.6-.6 1.1-1.5 1.7-1.9.4-.2.8-.4 1.3-.8.4-.4.6-1.3.8-1.9C23.8 35.1 24 34.3 23.8 33.7zM24.2 14.3c.2.0.4-.2.8-.4.6-.4 1.3-1.1 1.9-1.5.6-.4 1.3-1.1 1.7-1.5.6-.4 1.1-1.3 1.3-1.9.2-.4.8-1.3.6-1.9-.2-.4-1.3-.6-1.7-.8-1.7-.4-3.1-.6-4.8-.6-.6.0-1.5.2-1.7.8-.2 1.1.6.8 1.5 1.1.0.0.2 1.7.2 1.9.2 1-.4 1.7-.4 2.7.0.6.0 1.7.4 2.1L24.2 14.3zM41.8 29c.2-.4.2-1.1.4-1.5.2-1 .2-2.1.2-3.1.0-2.1-.2-4.2-.8-6.1-.4-.6-.6-1.3-.8-1.9-.4-1.1-1-2.1-1.9-2.9-.8-1.1-1.9-4-3.8-3.1-.6.2-1 1-1.5 1.5-.4.6-.8 1.3-1.3 1.9-.2.2-.4.6-.2.8.0.2.2.2.4.2.4.2.6.2 1 .4.2.0.4.2.2.4.0.0.0.2-.2.2-1 1.1-2.1 1.9-3.1 2.9-.2.2-.4.6-.4.8s.2.2.2.4-.2.2-.4.4c-.4.2-.8.4-1.1.6-.2.4.0 1.1-.2 1.5-.2 1.1-.8 1.9-1.3 2.9-.4.6-.6 1.3-1 1.9.0.8-.2 1.5.2 2.1 1 1.5 2.9.6 4.4 1.3.4.2.8.2 1.1.6.6.6.6 1.7.8 2.3.2.8.4 1.7.8 2.5.2 1 .6 2.1.8 2.9 1.9-1.5 3.6-3.1 4.8-5.2C40.6 32.4 41.2 30.7 41.8 29z"}]]
                2
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#f44336" :d "M21.2 44.8l-18-18c-1.6-1.6-1.6-4.1.0-5.7l18-18c1.6-1.6 4.1-1.6 5.7.0l18 18c1.6 1.6 1.6 4.1.0 5.7l-18 18c-1.6 1.6-4.2 1.6-5.7.0z"}]
                 [:path {:fill "#fff" :d "M21.6 32.7c0-.3.1-.6.2-.9.1-.3.3-.5.5-.7s.5-.4.8-.5.6-.2 1-.2.7.1 1 .2c.3.1.6.3.8.5s.4.4.5.7c.1.3.2.6.2.9s-.1.6-.2.9-.3.5-.5.7-.5.4-.8.5-.6.2-1 .2-.7-.1-1-.2-.5-.3-.8-.5c-.2-.2-.4-.4-.5-.7S21.6 33.1 21.6 32.7zm4.2-4.6h-3.6L21.7 13h4.6L25.8 28.1z"}]]
                3
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:g {:fill "#d1c4e9"}
                  [:path {:d "M38 7H10C8.9 7 8 7.9 8 9v6c0 1.1.9 2 2 2h28c1.1.0 2-.9 2-2V9C40 7.9 39.1 7 38 7z"}]
                  [:path {:d "M38 19H10c-1.1.0-2 .9-2 2v6c0 1.1.9 2 2 2h25.1c1.3-1.3 4.9-.9 4.9-2v-6C40 19.9 39.1 19 38 19z"}]
                  [:path {:d "M34.4 31H10c-1.1.0-2 .9-2 2v6c0 1.1.9 2 2 2h28c1.1.0 2-.9 2-2v-2.4c0-3.1-2.5-5.6-5.6-5.6z"}]]
                 [:path {:fill "#009688" :d "M46 25H32c-1.1.0-2 .9-2 2v11.8c0 1.3.6 2.4 1.6 3.2l7.4 5.5 7.4-5.5c1-.8 1.6-1.9 1.6-3.2V27C48 25.9 47.1 25 46 25z"}]]
                4
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#455a64" :d "M36 4H26c0 1.1-.9 2-2 2s-2-.9-2-2H12C9.8 4 8 5.8 8 8v32c0 2.2 1.8 4 4 4h24c2.2.0 4-1.8 4-4V8c0-2.2-1.8-4-4-4z"}]
                 [:path {:fill "#fff" :d "M36 41H12c-.6.0-1-.4-1-1V8c0-.6.4-1 1-1h24c.6.0 1 .4 1 1v32C37 40.6 36.6 41 36 41z"}]
                 [:g {:fill "#90a4ae"}
                  [:path {:d "M26 4c0 1.1-.9 2-2 2s-2-.9-2-2h-7v4c0 1.1.9 2 2 2h14c1.1.0 2-.9 2-2V4H26z"}]
                  [:path {:d "M24 0c-2.2.0-4 1.8-4 4s1.8 4 4 4 4-1.8 4-4-1.8-4-4-4zm0 6c-1.1.0-2-.9-2-2s.9-2 2-2 2 .9 2 2S25.1 6 24 6z"}]]
                 [:g {:fill "#cfd8dc"}
                  [:rect {:x "21" :y "20" :width "12" :height "2"}]
                  [:rect {:x "15" :y "19" :width "4" :height "4"}]]
                 [:g {:fill "#03a9f4"}
                  [:rect {:x "21" :y "29" :width "12" :height "2"}]
                  [:rect {:x "15" :y "28" :width "4" :height "4"}]]]
                5
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:polygon {:fill "#ff9800" :points "24,37 19,31 19,25 29,25 29,31"}]
                 [:g {:fill "#ffa726"}
                  [:circle {:cx "33" :cy "19" :r "2"}]
                  [:circle {:cx "15" :cy "19" :r "2"}]]
                 [:path {:fill "#ffb74d" :d "M33 13c0-7.6-18-5-18 0 0 1.1.0 5.9.0 7 0 5 4 9 9 9s9-4 9-9c0-1.1.0-5.9.0-7z"}]
                 [:path {:fill "#424242" :d "M24 4c-6.1.0-10 4.9-10 11 0 .8.0 2.3.0 2.3l2 1.7v-5l12-4 4 4v5l2-1.7s0-1.5.0-2.3c0-4-1-8-6-9l-1-2H24z"}]
                 [:g {:fill "#784719"}
                  [:circle {:cx "28" :cy "19" :r "1"}]
                  [:circle {:cx "20" :cy "19" :r "1"}]]
                 [:polygon {:fill "#fff" :points "24,43 19,31 24,32 29,31"}]
                 [:polygon {:fill "#d32f2f" :points "23,35 22.3,39.5 24,43.5 25.7,39.5 25,35 26,34 24,32 22,34"}]
                 [:path {:fill "#546e7a" :d "M29 31l-5 12-5-12S8 33 8 44h32c0-11-11-13-11-13z"}]]
                6
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#e65100" :d "M25.6 34.4c.1-.4.1-.9.1-1.4s0-.9-.1-1.4l2.8-2c.3-.2.4-.6.2-.9l-2.7-4.6c-.2-.3-.5-.4-.8-.3L22 25.3c-.7-.6-1.5-1-2.4-1.4l-.3-3.4c0-.3-.3-.6-.6-.6h-5.3c-.3.0-.6.3-.6.6L12.4 24c-.9.3-1.6.8-2.4 1.4L6.9 24c-.3-.1-.7.0-.8.3l-2.7 4.6c-.2.3-.1.7.2.9l2.8 2c-.1.4-.1.9-.1 1.4s0 .9.1 1.4l-2.8 2c-.3.2-.4.6-.2.9l2.7 4.6c.2.3.5.4.8.3L10 41c.7.6 1.5 1 2.4 1.4l.3 3.4c0 .3.3.6.6.6h5.3c.3.0.6-.3.6-.6l.3-3.4c.9-.3 1.6-.8 2.4-1.4l3.1 1.4c.3.1.7.0.8-.3l2.7-4.6c.2-.3.1-.7-.2-.9l-2.7-2.2zM16 38c-2.8.0-5-2.2-5-5 0-2.8 2.2-5 5-5s5 2.2 5 5-2.2 5-5 5z"}]
                 [:path {:fill "#ffa000" :d "M41.9 15.3C42 14.8 42 14.4 42 14s0-.8-.1-1.3l2.5-1.8c.3-.2.3-.5.2-.8l-2.5-4.3c-.2-.3-.5-.4-.8-.2l-2.9 1.3c-.7-.5-1.4-.9-2.2-1.3l-.3-3.1C36 2.2 35.8 2 35.5 2h-4.9c-.3.0-.6.2-.6.5l-.3 3.1c-.8.3-1.5.7-2.2 1.3l-2.9-1.3c-.3-.1-.6.0-.8.2l-2.5 4.3c-.2.3-.1.6.2.8l2.5 1.8C24 13.2 24 13.6 24 14s0 .8.1 1.3l-2.5 1.8c-.3.2-.3.5-.2.8l2.5 4.3c.2.3.5.4.8.2l2.9-1.3c.7.5 1.4.9 2.2 1.3l.3 3.1c0 .3.3.5.6.5h4.9c.3.0.6-.2.6-.5l.3-3.1c.8-.3 1.5-.7 2.2-1.3l2.9 1.3c.3.1.6.0.8-.2l2.5-4.3c.2-.3.1-.6-.2-.8l-2.8-1.8zM33 19c-2.8.0-5-2.2-5-5s2.2-5 5-5 5 2.2 5 5-2.2 5-5 5z"}]]
                7
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(2,0,0,2,0,-2056.8)"}
                  [:g {:transform "matrix(0.911796,0,0,0.873805,0.555331,131.585)"}
                   [:path {:d "M5 1030.4C3.895 1030.4 3 1031.3 3 1032.4v18c0 1.09999999999991.895 2 2 2H19c1.105.0 2-.900000000000091 2-2v-18c0-1.10000000000014-.895-2-2-2H5z" :style {:fill "#d8d8d8" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(0.911796,0,0,0.873805,0.555331,131.585)"}
                   [:path {:d "M6 1028.4C5.448 1028.4 5 1028.8 5 1029.4v3c0 .5.448 1 1 1s1-.5 1-1v-3c0-.600000000000136-.448-1-1-1zm4 0C9.448 1028.4 9 1028.8 9 1029.4v3c0 .5.448 1 1 1s1-.5 1-1v-3c0-.600000000000136-.448-1-1-1zm4 0c-.552.0-1 .399999999999864-1 1v3c0 .5.448 1 1 1s1-.5 1-1v-3c0-.600000000000136-.448-1-1-1zm4 0c-.552.0-1 .399999999999864-1 1v3c0 .5.448 1 1 1s1-.5 1-1v-3c0-.600000000000136-.448-1-1-1z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]
                   [:rect {:x "6" :y "1043.4" :width "12" :height "1" :style {:fill "#95a5a6"}}]
                   [:rect {:x "6" :y "1040.4" :width "12" :height "1" :style {:fill "#95a5a6"}}]
                   [:rect {:x "6" :y "1037.4" :width "12" :height "1" :style {:fill "#95a5a6"}}]
                   [:g {:transform "matrix(1,0,0,1,0,1028.4)"}
                    [:rect {:x "6" :y "18" :width "4" :height "1" :style {:fill "#95a5a6"}}]]]
                  [:g {:transform "matrix(0.911796,0,0,0.873805,0.555331,131.585)"}
                   [:path {:d "M5 1032.4c0 .5.448 1 1 1s1-.5 1-1H5zm4 0c0 .5.448 1 1 1s1-.5 1-1H9zm4 0c0 .5.448 1 1 1s1-.5 1-1H13zm4 0c0 .5.448 1 1 1s1-.5 1-1H17z" :style {:fill "#7f8c8d" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(0.911796,0,0,0.873805,0.555331,131.585)"}
                   [:path {:d "M3 1049.4v1c0 1.09999999999991.895 2 2 2H19c1.105.0 2-.900000000000091 2-2v-1c0 1.09999999999991-.895 2-2 2H5C3.895 1051.4 3 1050.5 3 1049.4z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(0.64474,0.617876,-0.64474,0.617876,705.61,455.249)"}
                   [:path {:d "M-63 1003.4v13l2 2 2-2v-13h-4z" :style {:fill "#ecf0f1" :fill-rule "nonzero"}}]
                   [:path {:d "M-61 1003.4v15l2-2v-13h-2z" :style {:fill "#bdc3c7" :fill-rule "nonzero"}}]
                   [:rect {:x "-63" :y "1004.4" :width "4" :height "11" :style {:fill "#e67e22"}}]
                   [:path {:d "M-61 1000.4c-1.105.0-2 .899999999999977-2 2v1h4v-1C-59 1001.3-59.895 1000.4-61 1000.4z" :style {:fill "#7f8c8d" :fill-rule "nonzero"}}]
                   [:g {:transform "matrix(1,0,0,1,-7,1)"}
                    [:path {:d "M-55.406 1016-54 1017.4-52.594 1016h-2.812z" :style {:fill "#34495e" :fill-rule "nonzero"}}]
                    [:path {:d "M-54 1016V1017.4L-52.594 1016H-54z" :style {:fill "#2c3e50" :fill-rule "nonzero"}}]]
                   [:path {:d "M-61 1000.4c-1.105.0-2 .899999999999977-2 2v1h2v-3z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]
                   [:rect {:x "-61" :y "1004.4" :width "2" :height "11" :style {:fill "#d35400"}}]]]]
                8
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:circle {:fill "#b2dfdb" :cx "24" :cy "31" :r "14"}]
                 [:g {:fill "#009688"}
                  [:polygon {:points "24,3.3 33,14 15,14"}]
                  [:rect {:x "21" :y "11" :width "6" :height "23"}]]]
                9
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#673ab7" :d "M40 7H8c-2.2.0-4 1.8-4 4v26c0 2.2 1.8 4 4 4h5v-1.3c-.6-.3-1-1-1-1.7.0-1.1.9-2 2-2s2 .9 2 2c0 .7-.4 1.4-1 1.7V41h18v-1.3c-.6-.3-1-1-1-1.7.0-1.1.9-2 2-2s2 .9 2 2c0 .7-.4 1.4-1 1.7V41h5c2.2.0 4-1.8 4-4V11c0-2.2-1.8-4-4-4z"}]
                 [:g {:fill "#d1c4e9"}
                  [:circle {:cx "24" :cy "18" :r "4"}]
                  [:path {:d "M31 28s-1.9-4-7-4-7 4-7 4v2h14V28z"}]]]
                10
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#673ab7" :d "M38 44H12V4h26c2.2.0 4 1.8 4 4v32c0 2.2-1.8 4-4 4z"}]
                 [:path {:fill "#311b92" :d "M10 4h2v40h-2c-2.2.0-4-1.8-4-4V8c0-2.2 1.8-4 4-4z"}]
                 [:path {:fill "#fff" :d "M36 24.2c-.1 4.8-3.1 6.9-5.3 6.7-.6-.1-2.1-.1-2.9-1.6-.8 1-1.8 1.6-3.1 1.6-2.6.0-3.3-2.5-3.4-3.1-.1-.7-.2-1.4-.1-2.2.1-1 1.1-6.5 5.7-6.5 2.2.0 3.5 1.1 3.7 1.3L30 27.2c0 .3-.2 1.6 1.1 1.6 2.1.0 2.4-3.9 2.4-4.6.1-1.2.3-8.2-7-8.2-6.9.0-7.9 7.4-8 9.2-.5 8.5 6 8.5 7.2 8.5 1.7.0 3.7-.7 3.9-.8l.4 2c-.3.2-2 1.1-4.4 1.1-2.2.0-10.1-.4-9.8-10.8C16.1 23.1 17.4 14 26.6 14c9.2.0 9.4 8.1 9.4 10.2zM24.1 25.5c-.1 1 0 1.8.2 2.3s.6.8 1.2.8c.1.0.3.0.4-.1.2-.1.3-.1.5-.3.2-.1.3-.3.5-.6.2-.2.3-.6.4-1l.5-5.4c-.2-.1-.5-.1-.7-.1-.5.0-.9.1-1.2.3s-.6.5-.9.8c-.2.4-.4.8-.6 1.3S24.2 24.8 24.1 25.5z"}]]
                11
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#546e7a" :d "M14 13H8v-1.8C8 10.5 8.5 10 9.2 10h3.6c.7.0 1.2.5 1.2 1.2V13z"}]
                 [:path {:fill "#5e35b1" :d "M40 40H8c-2.2.0-4-1.8-4-4V22h40v14c0 2.2-1.8 4-4 4z"}]
                 [:path {:fill "#42257a" :d "M12.7 22c-.4 1.3-.7 2.6-.7 4 0 6.6 5.4 12 12 12s12-5.4 12-12c0-1.4-.3-2.7-.7-4H12.7z"}]
                 [:path {:fill "#78909c" :d "M8 12h32c2.2.0 4 1.8 4 4v6H4v-6c0-2.2 1.8-4 4-4z"}]
                 [:path {:fill "#78909c" :d "M33.9 13.1H14.2L17.6 8c.4-.6 1-.9 1.7-.9h9.6c.7.0 1.3.3 1.7.9l3.3 5.1z"}]
                 [:path {:fill "#455a64" :d "M35.3 22c-1.6-4.7-6.1-8-11.3-8s-9.7 3.3-11.3 8H35.3z"}]
                 [:circle {:fill "#b388ff" :cx "24" :cy "26" :r "9"}]
                 [:path {:fill "#c7a7ff" :d "M29 23c-1.2-1.4-3-2.2-4.8-2.2s-3.6.8-4.8 2.2c-.5.5-.4 1.3.1 1.8s1.3.4 1.8-.1c1.5-1.7 4.3-1.7 5.8.0.3.3.6.4 1 .4.3.0.6-.1.9-.3C29.4 24.4 29.5 23.5 29 23z"}]
                 [:rect {:x "36" :y "15" :fill "#dbe2e5" :width "5" :height "4"}]]
                12
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:rect {:x "17" :y "29" :fill "#039be5" :width "14" :height "2"}]
                 [:rect {:x "13" :y "33" :fill "#039be5" :width "22" :height "2"}]
                 [:rect {:x "9" :y "37" :fill "#039be5" :width "30" :height "2"}]
                 [:rect {:x "5" :y "41" :fill "#039be5" :width "38" :height "2"}]
                 [:path {:fill "#81d4fa" :d "M35 13c-.4.0-.8.0-1.2.1C32.9 8.5 28.9 5 24 5c-4.1.0-7.6 2.5-9.2 6-.3.0-.5.0-.8.0-4.4.0-8 3.6-8 8s3.6 8 8 8c2.4.0 18.5.0 21 0 3.9.0 7-3.1 7-7s-3.1-7-7-7z"}]
                 [:path {:fill "#039be5" :d "M28 21c0-2.2-1.8-4-4-4s-4 1.8-4 4c0 .5.0 6 0 6h8s0-5.5.0-6z"}]]
                13
                [mui-svg-icon {:xmlns:dc "http://purl.org/dc/elements/1.1/" :xmlns:cc "http://creativecommons.org/ns#" :xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#" :xmlns:svg "http://www.w3.org/2000/svg" :xmlns "http://www.w3.org/2000/svg" :xmlns:sodipodi "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd" :xmlns:inkscape "http://www.inkscape.org/namespaces/inkscape" :id "svg859" :viewBox "0 0 16.933333 16.933333" :height "48pt" :width "48pt" :sodipodi:docname "C13_KGPG_Key3.svg" :inkscape:version "0.92.4 (5da689c313, 2019-01-14)"}
                 [:sodipodi:namedview {:pagecolor "#ffffff" :bordercolor "#666666" :borderopacity "1" :objecttolerance "10" :gridtolerance "10" :guidetolerance "10" :inkscape:pageopacity "0" :inkscape:pageshadow "2" :inkscape:window-width "3840" :inkscape:window-height "2050" :id "namedview2690" :showgrid "false" :inkscape:zoom "14.75" :inkscape:cx "32" :inkscape:cy "32" :inkscape:window-x "-12" :inkscape:window-y "-12" :inkscape:window-maximized "1" :inkscape:current-layer "layer1"}]
                 [:defs {:id "defs853"}]
                 [:g {:transform "translate(0,-280.06665)" :id "layer1"}
                  [:g {:id "g16" :style {:isolation "isolate"} :transform "matrix(0.35277777,0,0,0.35277777,0.84704631,280.27082)"}
                   [:path {:style {:fill "#bbb7af"} :id "path10" :d "m26.919 47-4.54-1.997-10-17.321 7.621-4.4 4 6.928-.27 2.466 2.27.998 1 1.732-.27 2.466 2.27.999 1 1.732-.54 4.93z" :inkscape:connector-curvature "0"}]
                   [:path {:style {:fill "#bbb7af"} :id "path12" :d "M18.481 8.917c-1.218-1.375-2.82-1.951-4.15-1.761-1.774.447-4.474 1.198-7.142 2.738-2.667 1.54-4.667 3.502-5.941 4.815-.83 1.057-1.132 2.733-.55 4.475.533 1.655 1.415 3.917 2.715 6.169s2.818 4.147 3.985 5.436c1.218 1.375 2.757 1.988 4.1 1.674 1.824-.36 4.524-1.111 7.191-2.651 2.668-1.54 4.668-3.503 5.942-4.816.943-1.006 1.132-2.732.6-4.387-.533-1.656-1.415-3.918-2.715-6.17-1.35-2.338-2.868-4.233-4.035-5.522zm-3.116 7.804-6.351 3.666c-.699.404-1.72-.046-2.27-.999l-1-1.732c-.3-.519 1.469-2.002 3.945-3.432 2.477-1.43 4.646-2.221 4.946-1.701l1 1.732c.55.953.429 2.062-.27 2.466z" :inkscape:connector-curvature "0"}]
                   [:rect {:style {:fill "#99968e"} :id "rect14" :transform "matrix(0.866,-0.5,0.5,0.866,-16.05,16.645)" :height "19" :width "2" :y "28.771999" :x "22.035"}]]
                  [:g {:id "g24" :style {:isolation "isolate"} :transform "matrix(0.35277777,0,0,0.35277777,0.33319598,280.49083)"}
                   [:path {:style {:fill "#c1b395"} :id "path18" :d "M40.107 35.664l-4.959-.134L19.346 23.27l5.394-6.952 6.321 4.903.681 2.385 2.48.067 1.58 1.226.681 2.385 2.479.067 1.58 1.226 1.363 4.77z" :inkscape:connector-curvature "0"}]
                   [:path {:style {:fill "#c1b395"} :id "path20" :d "M17.907 3.591C16.26 2.777 14.559 2.85 13.4 3.528 11.926 4.612 9.709 6.327 7.821 8.76c-1.887 2.434-2.998 5.007-3.681 6.704-.37 1.291-.017 2.957 1.181 4.35 1.118 1.332 2.79 3.093 4.844 4.687 2.054 1.594 4.175 2.775 5.743 3.527 1.647.814 3.303.8 4.429.002 1.552-1.023 3.769-2.738 5.657-5.171 1.888-2.434 2.998-5.006 3.682-6.703.493-1.289.016-2.958-1.102-4.29-1.119-1.331-2.79-3.092-4.845-4.686-2.133-1.655-4.254-2.837-5.822-3.589zm.063 8.402-4.495 5.794c-.495.638-1.61.607-2.479-.067L9.415 16.494c-.474-.368.604-2.409 2.357-4.669 1.753-2.259 3.463-3.81 3.937-3.443l1.58 1.226c.869.675 1.176 1.748.681 2.385z" :inkscape:connector-curvature "0"}]
                   [:rect {:style {:fill "#998e76"} :id "rect22" :transform "matrix(0.613,-0.79,0.79,0.613,-10.103,37.482)" :height "18.999001" :width "2" :y "19.555" :x "32.212002"}]]
                  [:g {:id "g32" :style {:isolation "isolate"} :transform "matrix(0.35277777,0,0,0.35277777,0.3165993,280.13828)"}
                   [:path {:style {:fill "#ffa000"} :id "path26" :d "m46.241 21.973-4.623 1.798-19.319-5.176 2.278-8.501 7.727 2.071 1.553 1.934 2.311-.899 1.932.518 1.552 1.934 2.312-.899 1.931.518L47 19.14z" :inkscape:connector-curvature "0"}]
                   [:path {:style {:fill "#ffa000"} :id "path28" :d "M13.345 1.011C11.512.9 9.971 1.626 9.166 2.7 8.227 4.271 6.849 6.711 6.052 9.686c-.798 2.975-.824 5.777-.796 7.606.16 1.334 1.131 2.733 2.775 3.553 1.547.794 3.77 1.769 6.282 2.442 2.511.673 4.924.94 6.661 1.026 1.834.112 3.355-.543 4.083-1.715 1.035-1.545 2.414-3.984 3.211-6.959.797-2.975.823-5.777.796-7.607C29.019 6.654 27.932 5.3 26.385 4.505c-1.547-.794-3.77-1.769-6.282-2.442-2.608-.699-5.021-.966-6.758-1.052zm3.315 7.721-1.898 7.084c-.209.779-1.249 1.183-2.312.899l-1.932-.518c-.579-.155-.377-2.455.363-5.217.74-2.763 1.715-4.855 2.295-4.7l1.932.518c1.062.285 1.761 1.155 1.552 1.934z" :inkscape:connector-curvature "0"}]
                   [:rect {:style {:fill "#d68600"} :id "rect30" :transform "matrix(0.259,-0.966,0.966,0.259,9.747,49.803)" :height "19.002001" :width "2" :y "9.0480003" :x "36.331001"}]]
                  [:path {:id "path1476" :d "m1.2718723 285.05146c-.4335907-.51274-.66891663-1.19158-.60320725-1.91499.13142168-1.44685 1.41721875-2.52408 2.87190845-2.40606 1.4546897.11801 2.5274119 1.38658 2.3959935 2.83343-.032853.3617-.2719825.91422-.6349261 1.27228-.3629437.35806-.8161697.60089-1.1766211.66014" :style {:fill "none" :fill-opacity "1" :stroke "#7e7e7e" :stroke-width ".78058332" :stroke-linecap "round" :stroke-linejoin "round" :stroke-miterlimit "3" :stroke-dasharray "none" :stroke-opacity "1"} :inkscape:connector-curvature "0"}]]]
                14
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:g {:fill "#ff9800"}
                  [:rect {:x "3" :y "28" :width "26" :height "4"}]
                  [:rect {:x "3" :y "16" :width "26" :height "4"}]]
                 [:path {:fill "#2196f3" :d "M43 11H20v26h23c1.1.0 2-.9 2-2V13C45 11.9 44.1 11 43 11z"}]
                 [:path {:fill "#64b5f6" :d "M20 9h-2v30h2c1.1.0 2-.9 2-2V11C22 9.9 21.1 9 20 9z"}]]
                15
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:g {:fill "#546e7a"}
                  [:rect {:x "5" :y "34" :width "6" :height "3"}]
                  [:rect {:x "37" :y "34" :width "6" :height "3"}]]
                 [:path {:fill "#78909c" :d "M44 35H4c-2.2.0-4-1.8-4-4V17c0-2.2 1.8-4 4-4h40c2.2.0 4 1.8 4 4v14c0 2.2-1.8 4-4 4z"}]
                 [:g {:fill "#37474f"}
                  [:rect {:x "5" :y "19" :width "2" :height "2"}]
                  [:rect {:x "5" :y "23" :width "2" :height "2"}]
                  [:rect {:x "5" :y "27" :width "2" :height "2"}]
                  [:rect {:x "9" :y "19" :width "2" :height "2"}]
                  [:rect {:x "9" :y "23" :width "2" :height "2"}]
                  [:rect {:x "9" :y "27" :width "2" :height "2"}]
                  [:rect {:x "13" :y "19" :width "2" :height "2"}]
                  [:rect {:x "13" :y "23" :width "2" :height "2"}]
                  [:rect {:x "13" :y "27" :width "2" :height "2"}]
                  [:rect {:x "17" :y "19" :width "2" :height "2"}]
                  [:rect {:x "17" :y "23" :width "2" :height "2"}]
                  [:rect {:x "17" :y "27" :width "2" :height "2"}]
                  [:rect {:x "21" :y "19" :width "2" :height "2"}]
                  [:rect {:x "21" :y "23" :width "2" :height "2"}]
                  [:rect {:x "21" :y "27" :width "2" :height "2"}]]
                 [:circle {:fill "#37474f" :cx "37" :cy "24" :r "8"}]
                 [:circle {:fill "#a0f" :cx "37" :cy "24" :r "6"}]
                 [:path {:fill "#ea80fc" :d "M40.7 21.7c-1-1.1-2.3-1.7-3.7-1.7s-2.8.6-3.7 1.7c-.4.4-.3 1 .1 1.4s1 .3 1.4-.1c1.2-1.3 3.3-1.3 4.5.0.2.2.5.3.7.3s.5-.1.7-.3C41.1 22.7 41.1 22.1 40.7 21.7z"}]]
                16
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#f44336" :d "M37 43l-13-6-13 6V9c0-2.2 1.8-4 4-4h18c2.2.0 4 1.8 4 4V43z"}]]
                17
                [mui-svg-icon {:xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :style {:isolation "isolate"} :viewBox "0 0 48 48" :width "48pt" :height "48pt"}
                 [:defs {}
                  [:clippath {:id "_clipPath_9XF8Wkv3dtlahEmPO8KNtUI9KomMyJLu"}
                   [:rect {:width "48" :height "48"}]]]
                 [:g {:clip-path "url(#_clipPath_9XF8Wkv3dtlahEmPO8KNtUI9KomMyJLu)"}
                  [:path {:d "M24 2C11.85 2 2 11.8 2 24c0 12 9.85 22 22 22S46 36 46 24C46 11.8 36.15 2 24 2zm0 8c7.732.0 14 6.2 14 14 0 7.6-6.268 14-14 14S10 31.6 10 24c0-7.8 6.268-14 14-14z" :fill "rgb(154,159,162)"}]
                  [:path {:d "M34 6.6C24.434 1 12.202 4.4 6.68 14 1.157 23.4 4.434 35.8 14 41.2 23.566 46.8 35.798 43.4 41.32 34 46.844 24.4 43.566 12.2 34 6.6zM26 20.4c1.914 1.2 2.568 3.6 1.464 5.6-1.104 1.8-3.55 2.4-5.464 1.4-1.914-1.2-2.569-3.6-1.464-5.4 1.104-2 3.55-2.6 5.464-1.6z" :fill "rgb(158,186,204)"}]
                  [:path {:d "M27 18.8C24.13 17 20.46 18 18.804 21c-1.657 2.8-.674 6.4 2.196 8.2C23.87 30.8 27.54 29.8 29.196 27c1.658-3 .674-6.6-2.196-8.2zm-1 1.6c1.914 1.2 2.568 3.6 1.464 5.6-1.104 1.8-3.55 2.4-5.464 1.4-1.914-1.2-2.569-3.6-1.464-5.4 1.104-2 3.55-2.6 5.464-1.6z" :fill "rgb(236,240,241)"}]
                  [:path {:d "M12.02 7.8c-4.498 3.4-7.244 8.4-7.875 13.6l15.929 2C20.204 22.2 20.734 21.4 21.638 20.6L12.02 7.8zM43.846 26.2l-15.876-2C27.84 25.4 27.256 26.2 26.354 27l9.618 12.8c4.496-3.4 7.242-8.4 7.874-13.6z" :fill "rgb(214,228,232)"}]
                  [:path {:d "M24 16c-4.418.0-8 3.6-8 8s3.582 8 8 8 8-3.6 8-8-3.582-8-8-8zm0 2c3.314.0 6 2.6 6 6 0 3.2-2.686 6-6 6s-6-2.8-6-6c0-3.4 2.686-6 6-6z" :fill "rgb(154,159,162)"}]]]
                18
                [mui-svg-icon {:xmlns:dc "http://purl.org/dc/elements/1.1/" :xmlns:cc "http://creativecommons.org/ns#" :xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#" :xmlns:svg "http://www.w3.org/2000/svg" :xmlns "http://www.w3.org/2000/svg" :xmlns:sodipodi "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd" :xmlns:inkscape "http://www.inkscape.org/namespaces/inkscape" :width "48pt" :height "48pt" :viewBox "0 0 16.933334 16.933333" :id "svg2112" :inkscape:version "0.92.4 (5da689c313, 2019-01-14)" :sodipodi:docname "C18_Display.svg"}
                 [:defs {:id "defs2106"}]
                 [:sodipodi:namedview {:id "base" :pagecolor "#ffffff" :bordercolor "#666666" :borderopacity "1" :inkscape:pageopacity "0" :inkscape:pageshadow "2" :inkscape:zoom "7.9195959" :inkscape:cx "-2.8571425" :inkscape:cy "54.923727" :inkscape:document-units "pt" :inkscape:current-layer "layer1" :showgrid "false" :units "pt" :inkscape:window-width "3840" :inkscape:window-height "2050" :inkscape:window-x "-12" :inkscape:window-y "-12" :inkscape:window-maximized "1"}]
                 [:g {:inkscape:label "Layer 1" :inkscape:groupmode "layer" :id "layer1" :transform "translate(0,-280.06668)"}
                  [:g {:transform "matrix(0.33674243,0,0,0.35277778,0.38484848,280.06668)" :style {:isolation "isolate"} :id "g882"}
                   [:path {:style {:fill "#95a5a6"} :inkscape:connector-curvature "0" :d "m18.271 33.952c.023.306.057.592.057.905.0 3.371-1.369 6.343-3.495 8.143h7.334 3.666 7.334c-2.127-1.8-3.495-4.772-3.495-8.143.0-.313.033-.599.057-.905h-3.896-3.666z" :id "path872"}]
                   [:path {:style {:fill "#7f8c8d"} :inkscape:connector-curvature "0" :d "m18.271 33.952c.023.181.057.543.057.905.0.905-.085 1.81-.286 2.714h11.916c-.201-.904-.286-1.809-.286-2.714.0-.362.033-.724.057-.905h-3.896-3.666z" :id "path874"}]
                   [:path {:style {:fill "#bdc3c7"} :inkscape:connector-curvature "0" :d "M3.833 5C2.821 5 2 5.706 2 6.765v1.764 3.53 3.529 3.53 1.764 1.765 1.765 3.529 3.53 1.764C2 34.118 2.821 35 3.833 35h1.834H29.5h11 1.833 1.834C45.179 35 46 34.118 46 33.235V31.471 19.118 17.353 15.588 12.059 8.529 6.765C46 5.706 45.179 5 44.167 5H42.333 33.167 31.333h-16.5H7.5 5.667z" :id "path876"}]
                   [:rect {:style {:fill "#4d6680"} :x "5" :y "8" :width "38" :height "24" :id "rect878"}]
                   [:path {:style {:fill "#3f5873"} :inkscape:connector-curvature "0" :d "M43 8 5 32H40.434 43v-2.954z" :id "path880"}]]]]
                19
                [mui-svg-icon {:xmlns:dc "http://purl.org/dc/elements/1.1/" :xmlns:cc "http://creativecommons.org/ns#" :xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#" :xmlns:svg "http://www.w3.org/2000/svg" :xmlns "http://www.w3.org/2000/svg" :xmlns:sodipodi "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd" :xmlns:inkscape "http://www.inkscape.org/namespaces/inkscape" :style {:isolation "isolate"} :viewBox "0 0 48 48" :width "48pt" :height "48pt" :id "svg3279" :sodipodi:docname "C19_Mail_Generic.svg" :inkscape:version "0.92.4 (5da689c313, 2019-01-14)"}
                 [:sodipodi:namedview {:pagecolor "#ffffff" :bordercolor "#666666" :borderopacity "1" :objecttolerance "10" :gridtolerance "10" :guidetolerance "10" :inkscape:pageopacity "0" :inkscape:pageshadow "2" :inkscape:window-width "3840" :inkscape:window-height "2050" :id "namedview3281" :showgrid "false" :inkscape:zoom "14.75" :inkscape:cx "32" :inkscape:cy "32" :inkscape:window-x "-12" :inkscape:window-y "-12" :inkscape:window-maximized "1" :inkscape:current-layer "g3277"}]
                 [:defs {:id "defs3263"}
                  [:clippath {:id "_clipPath_yCn9pWrMJ5G2bSE2XYUdljYvpNSOoGZX"}
                   [:rect {:width "48" :height "48" :id "rect3260"}]]]
                 [:g {:clip-path "url(#_clipPath_yCn9pWrMJ5G2bSE2XYUdljYvpNSOoGZX)" :id "g3277"}
                  [:path {:d "M40 44H8c-2.2.0-4-1.8-4-4V19.1c0-1.3.6-2.5 1.7-3.3L24 3 42.3 15.8c1.1.7 1.7 2 1.7 3.3V40c0 2.2-1.8 4-4 4z" :id "path3265" :inkscape:connector-curvature "0" :style {:fill "#78909c"}}]
                  [:rect {:x "12" :y "14" :width "24" :height "22" :id "rect3267" :style {:fill "#fff"}}]
                  [:path {:d "M40 44H8c-2.2.0-4-1.8-4-4V20L24 33 44 20v20c0 2.2-1.8 4-4 4z" :id "path3269" :inkscape:connector-curvature "0" :style {:fill "#cfd8dc"}}]
                  [:path {:style {:fill "none" :stroke "#1c7b9d" :stroke-width "1" :stroke-linecap "butt" :stroke-linejoin "miter" :stroke-miterlimit "4" :stroke-dasharray "none" :stroke-opacity "1"} :d "m16.220641 18.2845h15" :id "path3828" :inkscape:connector-curvature "0" :sodipodi:nodetypes "cc"}]
                  [:path {:style {:isolation "isolate" :fill "none" :stroke "#1c7b9d" :stroke-width "1.00023127" :stroke-linecap "butt" :stroke-linejoin "miter" :stroke-miterlimit "4" :stroke-dasharray "none" :stroke-opacity "1"} :d "m16.220642 22.374742h15" :id "path3828-4" :inkscape:connector-curvature "0" :sodipodi:nodetypes "cc"}]
                  [:path {:style {:isolation "isolate" :fill "none" :stroke "#1c7b9d" :stroke-width ".99975002" :stroke-linecap "butt" :stroke-linejoin "miter" :stroke-miterlimit "4" :stroke-dasharray "none" :stroke-opacity "1"} :d "m19.720642 26.464985h8" :id "path3828-7" :inkscape:connector-curvature "0" :sodipodi:nodetypes "cc"}]]]
                20
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#607d8b" :d "M39.6 27.2c.1-.7.2-1.4.2-2.2s-.1-1.5-.2-2.2l4.5-3.2c.4-.3.6-.9.3-1.4L40 10.8c-.3-.5-.8-.7-1.3-.4l-5 2.3c-1.2-.9-2.4-1.6-3.8-2.2L29.4 5c-.1-.5-.5-.9-1-.9h-8.6c-.5.0-1 .4-1 .9l-.5 5.5c-1.4.6-2.7 1.3-3.8 2.2l-5-2.3c-.5-.2-1.1.0-1.3.4l-4.3 7.4c-.3.5-.1 1.1.3 1.4l4.5 3.2c-.1.7-.2 1.4-.2 2.2s.1 1.5.2 2.2L4 30.4c-.4.3-.6.9-.3 1.4L8 39.2c.3.5.8.7 1.3.4l5-2.3c1.2.9 2.4 1.6 3.8 2.2l.5 5.5c.1.5.5.9 1 .9h8.6c.5.0 1-.4 1-.9l.5-5.5c1.4-.6 2.7-1.3 3.8-2.2l5 2.3c.5.2 1.1.0 1.3-.4l4.3-7.4c.3-.5.1-1.1-.3-1.4L39.6 27.2zM24 35c-5.5.0-10-4.5-10-10s4.5-10 10-10 10 4.5 10 10-4.5 10-10 10z"}]
                 [:path {:fill "#455a64" :d "M24 13c-6.6.0-12 5.4-12 12s5.4 12 12 12 12-5.4 12-12-5.4-12-12-12zm0 17c-2.8.0-5-2.2-5-5s2.2-5 5-5 5 2.2 5 5-2.2 5-5 5z"}]]
                21
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#455a64" :d "M36 4H26c0 1.1-.9 2-2 2s-2-.9-2-2H12C9.8 4 8 5.8 8 8v32c0 2.2 1.8 4 4 4h24c2.2.0 4-1.8 4-4V8c0-2.2-1.8-4-4-4z"}]
                 [:path {:fill "#fff" :d "M36 41H12c-.6.0-1-.4-1-1V8c0-.6.4-1 1-1h24c.6.0 1 .4 1 1v32C37 40.6 36.6 41 36 41z"}]
                 [:g {:fill "#90a4ae"}
                  [:path {:d "M26 4c0 1.1-.9 2-2 2s-2-.9-2-2h-7v4c0 1.1.9 2 2 2h14c1.1.0 2-.9 2-2V4H26z"}]
                  [:path {:d "M24 0c-2.2.0-4 1.8-4 4s1.8 4 4 4 4-1.8 4-4-1.8-4-4-4zm0 6c-1.1.0-2-.9-2-2s.9-2 2-2 2 .9 2 2S25.1 6 24 6z"}]]
                 [:polygon {:fill "#4caf50" :points "30.6,18.6 21.6,27.6 17.4,23.3 14.9,25.8 21.7,32.5 33.1,21.1"}]]
                22
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:polygon {:fill "#90caf9" :points "40,45 8,45 8,3 30,3 40,13"}]
                 [:polygon {:fill "#e1f5fe" :points "38.5,14 29,14 29,4.5"}]]
                23
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:rect {:x "4" :y "7" :fill "#bbdefb" :width "40" :height "34"}]
                 [:rect {:x "9" :y "12" :fill "#3f51b5" :width "30" :height "5"}]
                 [:g {:fill "#2196f3"}
                  [:rect {:x "9" :y "21" :width "13" :height "16"}]
                  [:rect {:x "26" :y "21" :width "13" :height "16"}]]]
                24
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:polygon {:fill "#ffc107" :points "33,22 23.6,22 30,5 19,5 13,26 21.6,26 17,45"}]]
                25
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:rect {:x "8" :y "39" :fill "#455a64" :width "6" :height "3"}]
                 [:rect {:x "34" :y "39" :fill "#455a64" :width "6" :height "3"}]
                 [:path {:fill "#78909c" :d "M40 41H8c-2.2.0-4-1.8-4-4V11c0-2.2 1.8-4 4-4h32c2.2.0 4 1.8 4 4v26c0 2.2-1.8 4-4 4z"}]
                 [:path {:fill "#90a4ae" :d "M40 38H8c-.6.0-1-.4-1-1V11c0-.6.4-1 1-1h32c.6.0 1 .4 1 1v26C41 37.6 40.6 38 40 38z"}]
                 [:path {:fill "#37474f" :d "M29 14c-5.5.0-10 4.5-10 10s4.5 10 10 10 10-4.5 10-10-4.5-10-10-10zm0 17c-3.9.0-7-3.1-7-7s3.1-7 7-7 7 3.1 7 7-3.1 7-7 7z"}]
                 [:g {:fill "#b0bec5"}
                  [:path {:d "M35.3 19.1l.4-.4c.4-.4.4-1 0-1.4s-1-.4-1.4.0l-.4.4C34.4 18.1 34.9 18.6 35.3 19.1z"}]
                  [:path {:d "M22.7 19.1c.4-.5.9-1 1.4-1.4l-.4-.4c-.4-.4-1-.4-1.4.0s-.4 1 0 1.4L22.7 19.1z"}]
                  [:path {:d "M21 24c0-.3.0-.7.1-1h-.6c-.6.0-1 .4-1 1s.4 1 1 1h.6C21 24.7 21 24.3 21 24z"}]
                  [:path {:d "M29 16c.3.0.7.0 1 .1v-.6c0-.6-.4-1-1-1s-1 .4-1 1v.6C28.3 16 28.7 16 29 16z"}]
                  [:path {:d "M35.3 28.9c-.4.5-.9 1-1.4 1.4l.4.4c.2.2.5.3.7.3s.5-.1.7-.3c.4-.4.4-1 0-1.4L35.3 28.9z"}]
                  [:path {:d "M22.7 28.9l-.4.4c-.4.4-.4 1 0 1.4.2.2.5.3.7.3s.5-.1.7-.3l.4-.4C23.6 29.9 23.1 29.4 22.7 28.9z"}]
                  [:path {:d "M37.5 23h-.6c0 .3.1.7.1 1s0 .7-.1 1h.6c.6.0 1-.4 1-1S38.1 23 37.5 23z"}]
                  [:path {:d "M29 32c-.3.0-.7.0-1-.1v.6c0 .6.4 1 1 1s1-.4 1-1v-.6C29.7 32 29.3 32 29 32z"}]]
                 [:path {:fill "#455a64" :d "M12 20c-1.1.0-2 .9-2 2v8c0 1.1.9 2 2 2s2-.9 2-2v-8c0-1.1-.9-2-2-2z"}]
                 [:path {:fill "#cfd8dc" :d "M12 18c-1.1.0-2 .9-2 2v8c0 1.1.9 2 2 2s2-.9 2-2v-8c0-1.1-.9-2-2-2z"}]]
                26
                [mui-svg-icon {:xmlns:dc "http://purl.org/dc/elements/1.1/" :xmlns:cc "http://creativecommons.org/ns#" :xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#" :xmlns:svg "http://www.w3.org/2000/svg" :xmlns "http://www.w3.org/2000/svg" :xmlns:sodipodi "http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd" :xmlns:inkscape "http://www.inkscape.org/namespaces/inkscape" :height "48pt" :width "48pt" :id "svg24" :sodipodi:docname "C26_FileSave.svg" :inkscape:version "0.92.4 (5da689c313, 2019-01-14)"}
                 [:defs {:id "defs28"}]
                 [:sodipodi:namedview {:pagecolor "#ffffff" :bordercolor "#666666" :borderopacity "1" :objecttolerance "10" :gridtolerance "10" :guidetolerance "10" :inkscape:pageopacity "0" :inkscape:pageshadow "2" :inkscape:window-width "3840" :inkscape:window-height "2050" :id "namedview26" :showgrid "false" :units "pt" :inkscape:zoom "9.83" :inkscape:cx "12" :inkscape:cy "12" :inkscape:window-x "-12" :inkscape:window-y "-12" :inkscape:window-maximized "1" :inkscape:current-layer "svg24"}]
                 [:g {:transform "matrix(2.7777778,0,0,2.777774,-1.3333334,-2857.9961)" :id "g22"}
                  [:path {:d "m5 1031.4c-1.1046.0-2 .9-2 2v14c0 1.1.8954 2 2 2h13 1c1.105.0 2-.9 2-2v-13l-3-3z" :id "path2" :inkscape:connector-curvature "0" :style {:fill "#3498db"}}]
                  [:path {:d "m7 3v5c0 .5523.4477 1 1 1h8c.552.0 1-.4477 1-1V3z" :transform "translate(0,1028.4)" :id "path6" :inkscape:connector-curvature "0" :style {:fill "#ecf0f1"}}]
                  [:path {:d "m6 1040.4c-.5523.0-1 .4-1 1v3 2 3h4 6 4v-3-2-3c0-.6-.448-1-1-1h-4-4z" :id "path8" :inkscape:connector-curvature "0" :style {:fill "#ecf0f1"}}]
                  [:g {:id "g16" :style {:fill "#bdc3c7"}}
                   [:rect {:height "1" :width "14" :y "1048.4" :x "5" :id "rect10"}]
                   [:rect {:height "1" :width "10" :y "1042.4" :x "7" :id "rect12"}]
                   [:rect {:height "1" :width "10" :y "1044.4" :x "7" :id "rect14"}]]
                  [:rect {:height "4" :width "3" :y "1032.4" :x "13" :id "rect18" :style {:fill "#3498db"}}]]]
                27
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:id "g12" :transform "matrix(2.22222,0,0,1.81818,-2.66667,-1872.31)"}
                  [:g {:id "rect10-6-5-2" :transform "matrix(-5.25,7.59998e-16,5.08759e-16,-0.172483,-35.25,872.761)"}
                   [:rect {:x "-10.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#7f8c8d"}}]]
                  [:g {:id "rect10-6-5-21" :serif:id "rect10-6-5-2" :transform "matrix(-2,2.73541e-16,1.83114e-16,-0.172483,-5.99999,873.412)"}
                   [:rect {:x "-10.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#7f8c8d"}}]]
                  [:path {:id "path2" :d "M19.875 1035.94v12.25C19.875 1049.15 19.092 1049.94 18.125 1049.94H5.875c-.967.0-1.75-.789999999999964-1.75-1.75v-12.25h15.75z" :style {:fill "#7f8c8d" :fill-rule "nonzero"}}]
                  [:path {:id "path4" :d "M4.125 1035.07v12.25c0 .960000000000036.783 1.75 1.75 1.75h12.25C19.092 1049.07 19.875 1048.28 19.875 1047.32v-12.25H4.125z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]
                  [:rect {:id "rect6" :x "14.625" :y "1045.57" :width "4.375" :height "3.5" :style {:fill "#7f8c8d"}}]
                  [:path {:id "path8" :d "M5.875 1030.69c-.967.0-1.75.779999999999973-1.75 1.75v12.25c0 .970000000000027.783 1.75 1.75 1.75h12.25C19.092 1046.44 19.875 1045.66 19.875 1044.69v-12.25C19.875 1031.47 19.092 1030.69 18.125 1030.69H5.875z" :style {:fill "#bdc3c7" :fill-rule "nonzero"}}]
                  [:rect {:id "rect10" :x "15.5" :y "1047.32" :width "2.625" :height ".875" :style {:fill "#f1c40f"}}]
                  [:g {:id "rect10-6" :transform "matrix(2,0,0,1.66667,-12,-703.612)"}
                   [:rect {:x "10.5" :y "1053.6" :width "3" :height "1.65" :style {:fill "#edb715"}}]]
                  [:g {:id "rect10-6-5" :transform "matrix(-2.74376e-16,-0.667461,1.48632,-7.10324e-16,-5.83586,349.146)"}
                   [:rect {:x "-1053.61" :y "11.325" :width "3.667" :height "1.35" :style {:fill "#bdc3c7"}}]]
                  [:g {:id "rect10-6-5-22" :serif:id "rect10-6-5-2" :transform "matrix(-1.625,9.35494e-17,6.2624e-17,-1,-8.0625,-0.760633)"}
                   [:rect {:x "-10.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#bdc3c7"}}]]
                  [:g {:id "rect10-6-5-2-1" :transform "matrix(-1.625,9.35494e-17,6.2624e-17,-1,-6.9375,-0.760633)"}
                   [:rect {:x "-16.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#bdc3c7"}}]]]]
                28
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#3f51b5" :d "M43 39V24h-4v15c0 5 4 9 9 9v-4c-2.8.0-5-2.2-5-5z"}]
                 [:circle {:fill "#90a4ae" :cx "24" :cy "24" :r "19"}]
                 [:circle {:fill "#37474f" :cx "24" :cy "24" :r "2"}]
                 [:g {:fill "#253278"}
                  [:circle {:cx "24" :cy "14" :r "5"}]
                  [:circle {:cx "24" :cy "34" :r "5"}]
                  [:circle {:cx "34" :cy "24" :r "5"}]
                  [:circle {:cx "14" :cy "24" :r "5"}]]]
                29
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {}
                  [:path {:d "M41 6H7c-.6.0-1 .4-1 1V42H42V7C42 6.4 41.6 6 41 6z" :style {:fill "#cfd8dc" :fill-rule "nonzero"}}]]
                 [:rect {:x "8" :y "13" :width "32" :height "27" :style {:fill "#263238"}}]
                 [:g {:transform "matrix(1.00639,0,0,1.00639,12.7027,31.2351)"}
                  [:g {}
                   [:text {:x "0" :y "0" :style {:font-family "roboto-bold,roboto" :font-weight "700" :font-size "22px" :fill "#76ff03"}} ">_"]]]
                 [:g {}
                  [:circle {:cx "13.5" :cy "9.5" :r "1.5" :style {:fill "#90a4ae"}}]
                  [:circle {:cx "9.5" :cy "9.5" :r "1.5" :style {:fill "#90a4ae"}}]]
                 [:g {:transform "matrix(1,0,0,1,1.02128,-0.291791)"}
                  [:g {:id "polygon2" :transform "matrix(0.438989,0,0,0.598621,-3.05709,20.013)"}
                   [:path {:d "M22 45l-4-4V21H30v8l-2 2 2 2v2l-2 2 2 2v2l-4 4H22z" :style {:fill "#ffa000" :fill-rule "nonzero"}}]]
                  [:g {:id "path4" :transform "matrix(0.438989,0,0,0.598621,-3.05709,20.013)"}
                   [:path {:d "M38 7.8c-.5-1.8-2-3.1-3.7-3.6C31.9 3.7 28.2 3 24 3s-7.9.7-10.3 1.2C12 4.7 10.5 6 10 7.8c-.5 1.7-1 4.1-1 6.7s.5 5 1 6.7c.5 1.8 1.9 3.1 3.7 3.5C16.1 25.3 19.8 26 24 26S31.9 25.3 34.3 24.8C36.1 24.4 37.5 23 38 21.3s1-4.1 1-6.7c0-2.7-.5-5.1-1-6.8zM29 13H19c-1.1.0-2-.9-2-2V9c0-.6 3.1-1 7-1s7 .4 7 1v2C31 12.1 30.1 13 29 13z" :style {:fill "#ffa000" :fill-rule "nonzero"}}]]
                  [:g {:id "rect8" :transform "matrix(0.598621,0,0,0.598621,-7.50244,20.013)"}
                   [:rect {:x "23.559" :y "26" :width "2" :height "19" :style {:fill "#d68600"}}]]]]
                30
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {}
                  [:path {:d "M41 6H7c-.6.0-1 .4-1 1V42H42V7C42 6.4 41.6 6 41 6z" :style {:fill "#cfd8dc" :fill-rule "nonzero"}}]]
                 [:rect {:x "8" :y "13" :width "32" :height "27" :style {:fill "#263238"}}]
                 [:g {:transform "matrix(1.00639,0,0,1.00639,12.7027,31.2351)"}
                  [:g {}
                   [:text {:x "0" :y "0" :style {:font-family "roboto-bold,roboto" :font-weight "700" :font-size "22px" :fill "#76ff03"}} ">_"]]]
                 [:g {}
                  [:circle {:cx "13.5" :cy "9.5" :r "1.5" :style {:fill "#90a4ae"}}]
                  [:circle {:cx "9.5" :cy "9.5" :r "1.5" :style {:fill "#90a4ae"}}]]]
                31
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:rect {:x "9" :y "11" :fill "#424242" :width "30" :height "3"}]
                 [:path {:fill "#616161" :d "M4 25h40v-7c0-2.2-1.8-4-4-4H8c-2.2.0-4 1.8-4 4v7z"}]
                 [:path {:fill "#424242" :d "M8 36h32c2.2.0 4-1.8 4-4v-8H4v8c0 2.2 1.8 4 4 4z"}]
                 [:circle {:fill "#00e676" :cx "40" :cy "18" :r "1"}]
                 [:rect {:x "11" :y "4" :fill "#90caf9" :width "26" :height "10"}]
                 [:path {:fill "#242424" :d "M37.5 31h-27C9.7 31 9 30.3 9 29.5v0c0-.8.7-1.5 1.5-1.5h27c.8.0 1.5.7 1.5 1.5v0C39 30.3 38.3 31 37.5 31z"}]
                 [:rect {:x "11" :y "31" :fill "#90caf9" :width "26" :height "11"}]
                 [:rect {:x "11" :y "29" :fill "#42a5f5" :width "26" :height "2"}]
                 [:g {:fill "#1976d2"}
                  [:rect {:x "16" :y "33" :width "17" :height "2"}]
                  [:rect {:x "16" :y "37" :width "13" :height "2"}]]]
                32
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#90caf9" :d "M10 10v28h28V10H10zM34 34H14V14h20V34z"}]
                 [:rect {:x "6" :y "6" :fill "#d81b60" :width "12" :height "12"}]
                 [:g {:fill "#2196f3"}
                  [:rect {:x "30" :y "6" :width "12" :height "12"}]
                  [:rect {:x "6" :y "30" :width "12" :height "12"}]
                  [:rect {:x "30" :y "30" :width "12" :height "12"}]]]
                33
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#ff5722" :d "M6 10v28c0 2.2 1.8 4 4 4h28c2.2.0 4-1.8 4-4V10c0-2.2-1.8-4-4-4H10C7.8 6 6 7.8 6 10z"}]
                 [:g {:fill "#bf360c"}
                  [:rect {:x "6" :y "35" :width "36" :height "2"}]
                  [:rect {:x "6" :y "31" :width "36" :height "2"}]
                  [:path {:d "M6.1 39c.2.8.6 1.5 1.2 2h33.2c.6-.5 1-1.2 1.2-2H6.1z"}]
                  [:path {:d "M6.1 9h35.7c-.2-.8-.6-1.5-1.2-2H7.4C6.8 7.5 6.3 8.2 6.1 9z"}]
                  [:rect {:x "6" :y "23" :width "36" :height "2"}]
                  [:rect {:x "6" :y "27" :width "36" :height "2"}]
                  [:rect {:x "6" :y "15" :width "36" :height "2"}]
                  [:rect {:x "6" :y "11" :width "36" :height "2"}]
                  [:rect {:x "6" :y "19" :width "36" :height "2"}]]
                 [:g {:fill "#ff8a65"}
                  [:rect {:x "27" :y "6" :width "2" :height "5"}]
                  [:rect {:x "27" :y "13" :width "2" :height "6"}]
                  [:rect {:x "27" :y "29" :width "2" :height "6"}]
                  [:rect {:x "31" :y "6" :width "2" :height "1"}]
                  [:rect {:x "19" :y "29" :width "2" :height "6"}]
                  [:rect {:x "31" :y "9" :width "2" :height "6"}]
                  [:rect {:x "23" :y "6" :width "2" :height "1"}]
                  [:rect {:x "23" :y "25" :width "2" :height "6"}]
                  [:rect {:x "23" :y "9" :width "2" :height "6"}]
                  [:rect {:x "19" :y "21" :width "2" :height "6"}]
                  [:rect {:x "23" :y "17" :width "2" :height "6"}]
                  [:rect {:x "23" :y "33" :width "2" :height "6"}]
                  [:rect {:x "27" :y "21" :width "2" :height "6"}]
                  [:rect {:x "39" :y "33" :width "2" :height "6"}]
                  [:rect {:x "39" :y "17" :width "2" :height "6"}]
                  [:rect {:x "39" :y "25" :width "2" :height "6"}]
                  [:path {:d "M39 6.1V7h1.6C40.2 6.6 39.6 6.3 39 6.1z"}]
                  [:rect {:x "31" :y "17" :width "2" :height "6"}]
                  [:path {:d "M40.6 41H39v.9C39.6 41.7 40.2 41.4 40.6 41z"}]
                  [:rect {:x "35" :y "13" :width "2" :height "6"}]
                  [:rect {:x "31" :y "33" :width "2" :height "6"}]
                  [:rect {:x "35" :y "29" :width "2" :height "6"}]
                  [:rect {:x "39" :y "9" :width "2" :height "6"}]
                  [:rect {:x "35" :y "21" :width "2" :height "6"}]
                  [:rect {:x "31" :y "25" :width "2" :height "6"}]
                  [:rect {:x "35" :y "37" :width "2" :height "5"}]
                  [:rect {:x "35" :y "6" :width "2" :height "5"}]
                  [:rect {:x "31" :y "41" :width "2" :height "1"}]
                  [:rect {:x "23" :y "41" :width "2" :height "1"}]
                  [:rect {:x "27" :y "37" :width "2" :height "5"}]
                  [:rect {:x "19" :y "37" :width "2" :height "5"}]
                  [:rect {:x "7" :y "17" :width "2" :height "6"}]
                  [:path {:d "M9 41H7.4c.5.4 1 .7 1.6.9V41z"}]
                  [:path {:d "M7.4 7H9V6.1c-.6.2-1.2.5-1.6.9z"}]
                  [:rect {:x "7" :y "33" :width "2" :height "6"}]
                  [:rect {:x "7" :y "25" :width "2" :height "6"}]
                  [:rect {:x "7" :y "9" :width "2" :height "6"}]
                  [:rect {:x "11" :y "29" :width "2" :height "6"}]
                  [:rect {:x "15" :y "17" :width "2" :height "6"}]
                  [:rect {:x "15" :y "33" :width "2" :height "6"}]
                  [:rect {:x "15" :y "9" :width "2" :height "6"}]
                  [:rect {:x "15" :y "6" :width "2" :height "1"}]
                  [:rect {:x "19" :y "6" :width "2" :height "5"}]
                  [:rect {:x "15" :y "25" :width "2" :height "6"}]
                  [:rect {:x "15" :y "41" :width "2" :height "1"}]
                  [:rect {:x "11" :y "21" :width "2" :height "6"}]
                  [:rect {:x "11" :y "6" :width "2" :height "5"}]
                  [:rect {:x "11" :y "37" :width "2" :height "5"}]
                  [:rect {:x "19" :y "13" :width "2" :height "6"}]
                  [:rect {:x "11" :y "13" :width "2" :height "6"}]]]
                34
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#607d8b" :d "M44.7 11 36 19.6s-2.6.0-5.2-2.6-2.6-5.2-2.6-5.2l8.7-8.7c-4.9-1.2-10.8.4-14.4 4-5.4 5.4-.6 12.3-2 13.7C12.9 28.7 5.1 34.7 4.9 35c-2.3 2.3-2.4 6-.2 8.2s5.9 2.1 8.2-.2c.3-.3 6.7-8.4 14.2-15.9 1.4-1.4 8 3.7 13.6-1.8C44.2 21.7 45.9 15.9 44.7 11zM9.4 41.1c-1.4.0-2.5-1.1-2.5-2.5C6.9 37.1 8 36 9.4 36s2.5 1.1 2.5 2.5-1.1 2.6-2.5 2.6z"}]]
                35
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:id "Layer-1" :serif:id "Layer 1" :transform "matrix(1,0,0,1,0,-793.89)"}
                  [:g {:id "g882" :transform "matrix(0.336742,0,0,0.352778,1.09091,793.89)"}
                   [:path {:id "path876" :d "M10.865 14.173c-2.868.0-5.196 2.001-5.196 5.003V94.209C5.669 96.712 7.997 99.213 10.865 99.213H125.198C128.066 99.213 130.394 96.712 130.394 94.209V19.176c0-3.002-2.328-5.003-5.19600000000001-5.003H10.865z" :style {:fill "#bdc3c7" :fill-rule "nonzero"}}]
                   [:rect {:id "rect878" :x "14.173" :y "22.677" :width "107.717" :height "68.031" :style {:fill "#78c2e6"}}]]
                  [:g {:transform "matrix(1.02638,0,0,0.871313,-1.30567,796.29)"}
                   [:path {:d "M16.119 22.586v4.773l-6.078-7.16 6.078-7.16v4.773H33.192V13.039l6.078 7.16-6.078 7.16V22.586H16.119z" :style {:fill "#297dd4"}}]]
                  [:g {:id "rect10-6-5-2" :transform "matrix(-11.6667,1.38181e-15,1.13058e-15,-0.313605,-81,506.683)"}
                   [:rect {:x "-10.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#7f8c8d"}}]]
                  [:g {:id "rect10-6-5-21" :serif:id "rect10-6-5-2" :transform "matrix(-4.44445,4.97347e-16,4.06921e-16,-0.313605,-16,507.502)"}
                   [:rect {:x "-10.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#7f8c8d"}}]]
                  [:g {:id "rect10-6-5" :transform "matrix(-6.9496e-16,-1.4362,3.30294,-1.29644e-15,-15.6353,-679.095)"}
                   [:rect {:x "-1053.61" :y "11.325" :width "3.667" :height "1.35" :style {:fill "#bdc3c7"}}]]
                  [:g {:id "rect10-6-5-22" :serif:id "rect10-6-5-2" :transform "matrix(-3.61111,1.7009e-16,1.39164e-16,-1.81818,-20.5833,-1081.54)"}
                   [:rect {:x "-10.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#bdc3c7"}}]]
                  [:g {:id "rect10-6-5-2-1" :transform "matrix(-3.61111,1.7009e-16,1.39164e-16,-1.81818,-18.0833,-1081.54)"}
                   [:rect {:x "-16.5" :y "-1055.25" :width "3" :height "1.65" :style {:fill "#bdc3c7"}}]]
                  [:g {:id "rect10-6" :transform "matrix(4.44444,0,0,2.81115,-29.3333,-2128.55)"}
                   [:rect {:x "10.5" :y "1053.6" :width "3" :height "1.65" :style {:fill "#1cad07"}}]]]]
                36
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:path {:id "path2" :d "M40 12H22L18 8H8C5.8 8 4 9.8 4 12v8H44V16c0-2.2-1.8-4-4-4z" :style {:fill "#ffa000" :fill-rule "nonzero"}}]
                 [:path {:id "path4" :d "M40 12H8c-2.2.0-4 1.8-4 4V36c0 2.2 1.8 4 4 4H40c2.2.0 4-1.8 4-4V16c0-2.2-1.8-4-4-4z" :style {:fill "#ffca28" :fill-rule "nonzero"}}]
                 [:g {:transform "matrix(1,0,0,1,9.45665e-05,1.57568)"}
                  [:rect {:id "rect822" :x "20.492" :y "13" :width "7.015" :height "2" :style {:fill "#ff0025"}}]
                  [:rect {:id "rect822-7" :x "20.492" :y "17.045" :width "7.015" :height "2" :style {:fill "#ff0025"}}]
                  [:rect {:id "rect822-7-8" :x "20.492" :y "21.09" :width "7.015" :height "2" :style {:fill "#ff0025"}}]
                  [:rect {:id "rect822-7-33" :x "20.492" :y "25.135" :width "7.015" :height "2" :style {:fill "#ff0025"}}]
                  [:g {:transform "matrix(0.941767,0,0,0.465036,11.2143,14.2783)"}
                   [:path {:d "M17.301 27.646l-3.725 6.451-3.724-6.451h7.449z" :style {:fill "#ff0025" :fill-rule "nonzero"}}]]]]
                37
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd"}}
                 [:g {:transform "matrix(1.00042,0,0,0.960739,-0.00969865,-761.485)"}
                  [:g {:id "Layer-1" :serif:id "Layer 1"}
                   [:g {:id "text1433"}
                    [:path {:id "path1435" :d "M13.263 817.539C10.951 817.539 9.107 816.781 7.732 815.266 6.357 813.734 5.669 811.695 5.669 809.148 5.669 806.367 6.396 804.18 7.849 802.586 9.302 800.977 11.263 800.172 13.732 800.172c2.39.0 4.242.741999999999962 5.555 2.226C20.615 803.883 21.279 805.969 21.279 808.656 21.279 811.344 20.537 813.5 19.052 815.125 17.583 816.735 15.654 817.539 13.263 817.539zM13.591 802.75C12.107 802.75 10.935 803.305 10.076 804.414 9.216 805.508 8.786 807.016 8.786 808.938 8.786 810.828 9.208 812.305 10.052 813.367 10.896 814.414 12.044 814.938 13.497 814.938 14.966 814.938 16.115 814.391 16.943 813.297 17.771 812.203 18.185 810.688 18.185 808.75 18.185 806.859 17.771 805.391 16.943 804.344 16.13 803.281 15.013 802.75 13.591 802.75zM35.763 800.687 14.201 834.532H10.849l21.539-33.845h3.375zM33.279 834.813C30.966 834.813 29.123 834.047 27.748 832.516 26.373 830.985 25.685 828.953 25.685 826.422 25.685 823.641 26.419 821.446 27.888 819.836 29.357 818.211 31.31 817.399 33.748 817.399c2.375.0 4.226.75 5.554 2.25C40.631 821.149 41.295 823.25 41.295 825.953 41.295 828.625 40.552 830.774 39.068 832.399 37.599 834.008 35.67 834.813 33.279 834.813zM33.607 820.047C32.107 820.047 30.927 820.594 30.068 821.688 29.224 822.781 28.802 824.297 28.802 826.235 28.802 828.094 29.224 829.555 30.068 830.618 30.912 831.664 32.06 832.188 33.513 832.188 34.982 832.188 36.131 831.641 36.959 830.547 37.787 829.438 38.201 827.922 38.201 826 38.201 824.094 37.787 822.625 36.959 821.594 36.146 820.563 35.029 820.047 33.607 820.047z" :style {:fill "#5e35b1" :fill-rule "nonzero" :stroke "#5e35b1" :stroke-width ".75px"}}]]]]]
                38
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:id "Layer-1" :serif:id "Layer 1" :transform "matrix(1,0,0,1,0,-793.89)"}
                  [:g {:id "g882" :transform "matrix(0.336742,0,0,0.352778,1.09091,793.89)"}
                   [:path {:id "path872" :d "M51.792 96.242C51.857 97.109 51.953 97.92 51.953 98.807c0 9.556-3.88 17.98-9.907 23.083H94.017C87.987 116.787 84.11 108.363 84.11 98.807 84.11 97.92 84.203 97.109 84.271 96.242H51.792z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]
                   [:path {:id "path874" :d "M51.792 96.242C51.857 96.755 51.953 97.781 51.953 98.807c0 2.566-.241 5.131-.810000000000002 7.693H84.92C84.351 103.938 84.11 101.373 84.11 98.807 84.11 97.781 84.203 96.755 84.271 96.242H51.792z" :style {:fill "#7f8c8d" :fill-rule "nonzero"}}]
                   [:path {:id "path876" :d "M10.865 14.173c-2.868.0-5.196 2.001-5.196 5.003V94.209C5.669 96.712 7.997 99.213 10.865 99.213H125.198C128.066 99.213 130.394 96.712 130.394 94.209V19.176c0-3.002-2.328-5.003-5.19600000000001-5.003H10.865z" :style {:fill "#bdc3c7" :fill-rule "nonzero"}}]
                   [:rect {:id "rect878" :x "14.173" :y "22.677" :width "107.717" :height "68.031" :style {:fill "#78c2e6"}}]]
                  [:g {:transform "matrix(0.907586,0,0,0.944558,3.45296,44.9469)"}
                   [:g {:transform "matrix(1,0,0,1,-0.8462,791.224)"}
                    [:path {:d "M23.008 13.977 11.395 15.698 11.365 22.381H23.008V13.977z" :style {:fill "#297dd4"}}]]
                   [:g {:transform "matrix(1,0,0,-1,-0.8462,836.988)"}
                    [:path {:d "M23.008 13.977 11.395 15.698 11.365 22.381H23.008V13.977z" :style {:fill "#297dd4"}}]]
                   [:g {:transform "matrix(-1,0,0,-1,46.1246,836.988)"}
                    [:path {:d "M23.008 13.831 11.365 12.324V22.381H23.008v-8.55z" :style {:fill "#297dd4"}}]]
                   [:g {:transform "matrix(-1,0,0,1,46.1246,791.224)"}
                    [:path {:d "M23.008 13.831 11.365 12.266V22.381H23.008v-8.55z" :style {:fill "#297dd4"}}]]]]]
                39
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:circle {:fill "#00acc1" :cx "24" :cy "24" :r "20"}]
                 [:circle {:fill "#eee" :cx "24" :cy "24" :r "16"}]
                 [:rect {:x "23" :y "11" :width "2" :height "13"}]
                 [:rect {:x "26.1" :y "22.7" :transform "matrix(-.707 .707 -.707 -.707 65.787 27.25)" :width "2.3" :height "9.2"}]
                 [:circle {:cx "24" :cy "24" :r "2"}]
                 [:circle {:fill "#00acc1" :cx "24" :cy "24" :r "1"}]]
                40
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:g {:fill "#616161"}
                  [:rect {:x "34.6" :y "28.1" :transform "matrix(.707 -.707 .707 .707 -15.154 36.586)" :width "4" :height "17"}]
                  [:circle {:cx "20" :cy "20" :r "16"}]]
                 [:rect {:x "36.2" :y "32.1" :transform "matrix(.707 -.707 .707 .707 -15.839 38.239)" :fill "#37474f" :width "4" :height "12.3"}]
                 [:circle {:fill "#64b5f6" :cx "20" :cy "20" :r "13"}]
                 [:path {:fill "#bbdefb" :d "M26.9 14.2c-1.7-2-4.2-3.2-6.9-3.2s-5.2 1.2-6.9 3.2c-.4.4-.3 1.1.1 1.4.4.4 1.1.3 1.4-.1C16 13.9 17.9 13 20 13s4 .9 5.4 2.5c.2.2.5.4.8.4.2.0.5-.1.6-.2C27.2 15.3 27.2 14.6 26.9 14.2z"}]]
                41
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:g {:fill "#ff9800"}
                  [:rect {:x "36.1" :y "8.1" :transform "matrix(.707 .707 -.707 .707 21.201 -25.184)" :width "9.9" :height "9.9"}]
                  [:rect {:x "36" :y "8" :width "10" :height "10"}]]
                 [:circle {:fill "#ffeb3b" :cx "41" :cy "13" :r "3"}]
                 [:polygon {:fill "#2e7d32" :points "16.5,18 0,42 33,42"}]
                 [:polygon {:fill "#4caf50" :points "33.6,24 19.2,42 48,42"}]]
                42
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#ff9800" :d "M44 18v-4H34V4h-4v10h-4V4h-4v10h-4V4h-4v10H4v4h10v4H4v4h10v4H4v4h10v10h4V34h4v10h4V34h4v10h4V34h10v-4H34v-4h10v-4H34v-4H44z"}]
                 [:path {:fill "#4caf50" :d "M8 12v24c0 2.2 1.8 4 4 4h24c2.2.0 4-1.8 4-4V12c0-2.2-1.8-4-4-4H12C9.8 8 8 9.8 8 12z"}]
                 [:path {:fill "#37474f" :d "M31 31H17c-1.1.0-2-.9-2-2V19c0-1.1.9-2 2-2h14c1.1.0 2 .9 2 2v10C33 30.1 32.1 31 31 31z"}]]
                43
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#b39ddb" :d "M30.6 44H17.4c-2 0-3.7-1.4-4-3.4L9 11h30l-4.5 29.6C34.2 42.6 32.5 44 30.6 44z"}]
                 [:path {:fill "#7e57c2" :d "M38 13H10c-1.1.0-2-.9-2-2v0c0-1.1.9-2 2-2h28c1.1.0 2 .9 2 2v0C40 12.1 39.1 13 38 13z"}]]
                44
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(2,0,0,2,0,-2056.8)"}
                  [:g {:transform "matrix(1,0,0,1,0,1028.4)"}
                   [:path {:d "M2 4V20c0 1.105.895 2 2 2H16l6-6V4H2z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]
                  [:path {:d "M22 1044.4l-6 6v-4c0-1.10000000000014.895-2 2-2h4z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]
                  [:g {:transform "matrix(1,0,0,1,0,1028.4)"}
                   [:path {:d "M4 2C2.895 2 2 2.895 2 4V6H22V4c0-1.105-.895-2-2-2H4z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]
                  [:g {}
                   [:rect {:x "5" :y "1034.4" :width "14" :height "2" :style {:fill "#f39c12"}}]
                   [:rect {:x "5" :y "1038.4" :width "14" :height "2" :style {:fill "#f39c12"}}]
                   [:rect {:x "5" :y "1042.4" :width "9" :height "2" :style {:fill "#f39c12"}}]]]]
                45
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(0.983029,0,0,0.983029,-2.15274,-4.69129)"}
                  [:circle {:cx "26.604" :cy "29.187" :r "20.345" :style {:fill "#ff3737"}}]]
                 [:g {:transform "matrix(27.6873,0,0,27.6873,15.1954,33.8428)"}
                  [:path {:d "M.318-.466.451-.711H.62L.413-.358.625.0H.455L.318-.249.181.0H.011L.223-.358.016-.711H.185l.133.245z" :style {:fill "#fff" :fill-rule "nonzero"}}]]]
                46
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(0.983029,0,0,0.983029,-2.15274,-4.69129)"}
                  [:circle {:cx "26.604" :cy "29.187" :r "20.345" :style {:fill "#0090ff"}}]]
                 [:g {:transform "matrix(31.8224,0,0,31.8224,16.3008,35.374)"}
                  [:path {:d "M.169-.218C.169-.263.175-.3.186-.327.197-.354.217-.38.247-.407.276-.433.296-.454.306-.471.315-.487.32-.505.32-.523.32-.578.295-.605.244-.605.22-.605.201-.598.186-.583.172-.568.164-.548.164-.522H.022C.023-.584.043-.633.082-.668.122-.703.176-.721.244-.721.313-.721.367-.704.405-.671.443-.637.462-.59.462-.529.462-.501.456-.475.443-.451.431-.426.409-.399.378-.369L.339-.331C.314-.307.3-.28.296-.248l-.002.03H.169zm-.014.15C.155-.09.163-.108.177-.122.192-.136.211-.143.234-.143S.276-.136.291-.122c.015.014.022.032.022.054C.313-.047.306-.029.292-.015.277-.001.258.006.234.006.211.006.191-.001.177-.015.163-.029.155-.047.155-.068z" :style {:fill "#fff" :fill-rule "nonzero"}}]]]
                47
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#ff9800" :d "M38 42H10c-2.2.0-4-1.8-4-4V10c0-2.2 1.8-4 4-4h28c2.2.0 4 1.8 4 4v28c0 2.2-1.8 4-4 4z"}]
                 [:path {:fill "#8a5100" :d "M29.5 16h-11c-.8.0-1.5-.7-1.5-1.5v0c0-.8.7-1.5 1.5-1.5h11c.8.0 1.5.7 1.5 1.5v0C31 15.3 30.3 16 29.5 16z"}]]
                48
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#ffa000" :d "M40 12H22l-4-4H8c-2.2.0-4 1.8-4 4v8h40v-4c0-2.2-1.8-4-4-4z"}]
                 [:path {:fill "#ffca28" :d "M40 12H8c-2.2.0-4 1.8-4 4v20c0 2.2 1.8 4 4 4h32c2.2.0 4-1.8 4-4V16c0-2.2-1.8-4-4-4z"}]]
                49
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#ffa000" :d "M38 12H22l-4-4H8c-2.2.0-4 1.8-4 4v24c0 2.2 1.8 4 4 4h31c1.7.0 3-1.3 3-3V16c0-2.2-1.8-4-4-4z"}]
                 [:path {:fill "#ffca28" :d "M42.2 18H15.3c-1.9.0-3.6 1.4-3.9 3.3L8 40h31.7c1.9.0 3.6-1.4 3.9-3.3l2.5-14c.5-2.4-1.4-4.7-3.9-4.7z"}]]
                50
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:g {:fill "#d1c4e9"}
                  [:path {:d "M38 7H10C8.9 7 8 7.9 8 9v6c0 1.1.9 2 2 2h28c1.1.0 2-.9 2-2V9C40 7.9 39.1 7 38 7z"}]
                  [:path {:d "M38 19H10c-1.1.0-2 .9-2 2v6c0 1.1.9 2 2 2h25.1c1.3-1.3 4.9-.9 4.9-2v-6C40 19.9 39.1 19 38 19z"}]
                  [:path {:d "M34.4 31H10c-1.1.0-2 .9-2 2v6c0 1.1.9 2 2 2h28c1.1.0 2-.9 2-2v-2.4c0-3.1-2.5-5.6-5.6-5.6z"}]]
                 [:g {:fill "#ffa000"}
                  [:polygon {:points "43,46 41,48 39,48 37,46 37,35.4 43,35.4 43,40 42,41 43,42 43,43 42,44 43,45"}]
                  [:path {:d "M47.5 28.5c-.3-.9-1-1.6-2-1.8C44.2 26.4 42.2 26 40 26s-4.2.4-5.5.6c-1 .2-1.7.9-2 1.8C32.3 29.4 32 30.6 32 32s.3 2.6.5 3.5c.3.9 1 1.6 2 1.8 1.3.3 3.2.6 5.5.6s4.2-.4 5.5-.6c1-.2 1.7-.9 2-1.8s.5-2.1.5-3.5S47.7 29.4 47.5 28.5zM42.9 31h-5.7c-.6.0-1.1-.5-1.1-1.1v-1.4c0-.3 1.8-.6 4-.6s4 .3 4 .6v1.4C44 30.5 43.5 31 42.9 31z"}]]
                 [:rect {:x "39" :y "37.1" :fill "#d68600" :width "1" :height "10.9"}]]
                51
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(2,0,0,2,0,-2056.8)"}
                  [:g {:transform "matrix(1,0,0,0.956522,0,44.7565)"}
                   [:path {:d "M5 1037.4C3.895 1037.4 3 1038.3 3 1039.4v8c0 1.09999999999991.895 2 2 2H19c1.105.0 2-.900000000000091 2-2v-8c0-1.10000000000014-.895-2-2-2H5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(1,0,0,0.956522,0,44.7565)"}
                   [:path {:d "M5 1040.4C3.895 1040.4 3 1041.3 3 1042.4v8c0 1.09999999999991.895 2 2 2H19c1.105.0 2-.900000000000091 2-2v-8c0-1.10000000000014-.895-2-2-2H5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(1,0,0,0.956522,0,44.7565)"}
                   [:path {:d "M12 1029.4C8.134 1029.4 5 1032.5 5 1036.4v2C5.998 1039.11 6.996 1039.09 7.994 1038.4v-2c0-2.20000000000005 1.797-4 4.006-4 1.525.0 2.971 1 3.647 2.25C15.807 1034.95 18.41 1033.52 18.15 1033.04 16.963 1030.86 14.654 1029.4 12 1029.4z" :style {:fill "#adafb0" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(1,0,0,0.956522,0,1028.44)"}
                   [:path {:d "M5 14v1H19V14H5zm0 2v1H19V16H5zm0 2v1H19V18H5zm0 2v1H19V20H5z" :style {:fill "#e67e22" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(0.991894,0,0,0.748024,0.15401,1030.3)"}
                   [:path {:d "M16 10C16 10.552 16.672 11 17.5 11s1.5-.448 1.5-1H16z" :style {:fill "#e67e22" :fill-rule "nonzero"}}]]]]
                52
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(2,0,0,2,0,-2056.8)"}
                  [:g {:transform "matrix(1,0,0,0.956522,0,44.7565)"}
                   [:path {:d "M5 1037.4C3.895 1037.4 3 1038.3 3 1039.4v8c0 1.09999999999991.895 2 2 2H19c1.105.0 2-.900000000000091 2-2v-8c0-1.10000000000014-.895-2-2-2H5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(1,0,0,0.956522,0,44.7565)"}
                   [:path {:d "M5 1040.4C3.895 1040.4 3 1041.3 3 1042.4v8c0 1.09999999999991.895 2 2 2H19c1.105.0 2-.900000000000091 2-2v-8c0-1.10000000000014-.895-2-2-2H5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(1,0,0,0.956522,0,44.7565)"}
                   [:path {:d "M12 1029.4C8.134 1029.4 5 1032.5 5 1036.4v2C5.998 1039.11 6.996 1039.09 7.994 1038.4v-2c0-2.20000000000005 1.797-4 4.006-4S16.006 1034.2 16.006 1036.4v2C17.023 1039.13 18.022 1039.11 19 1038.4v-2c0-3.90000000000009-3.134-7-7-7z" :style {:fill "#adafb0" :fill-rule "nonzero"}}]]
                  [:g {:transform "matrix(1,0,0,0.956522,0,1028.44)"}
                   [:path {:d "M5 14v1H19V14H5zm0 2v1H19V16H5zm0 2v1H19V18H5zm0 2v1H19V20H5z" :style {:fill "#e67e22" :fill-rule "nonzero"}}]]]]
                53
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(0.983029,0,0,0.983029,-2.15274,-4.69129)"}
                  [:circle {:cx "26.604" :cy "29.187" :r "20.345" :style {:fill "#4db446"}}]]
                 [:g {:transform "matrix(1.36992,-1.36992,0.673517,0.673517,-16.5039,31.1727)"}
                  [:path {:d "M24.613 24.744H14.276L14.288 16.448 12.225 16.431l.016 12.486H24.613V24.744z" :style {:fill "#fff"}}]]]
                54
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(1.76777,1.76777,-1.76777,1.76777,1915.49,-1651.82)"}
                  [:g {}
                   [:path {:d "M-63 1003.4v13l2 2 2-2v-13h-4z" :style {:fill "#ecf0f1" :fill-rule "nonzero"}}]
                   [:path {:d "M-61 1003.4v15l2-2v-13h-2z" :style {:fill "#bdc3c7" :fill-rule "nonzero"}}]
                   [:rect {:x "-63" :y "1004.4" :width "4" :height "11" :style {:fill "#e67e22"}}]
                   [:path {:d "M-61 1000.4c-1.105.0-2 .899999999999977-2 2v1h4v-1C-59 1001.3-59.895 1000.4-61 1000.4z" :style {:fill "#7f8c8d" :fill-rule "nonzero"}}]
                   [:g {:transform "matrix(1,0,0,1,-7,1)"}
                    [:path {:d "M-55.406 1016-54 1017.4-52.594 1016h-2.812z" :style {:fill "#3c546c" :fill-rule "nonzero"}}]
                    [:path {:d "M-54 1016V1017.4L-52.594 1016H-54z" :style {:fill "#4d5b6a" :fill-rule "nonzero"}}]]
                   [:path {:d "M-61 1000.4c-1.105.0-2 .899999999999977-2 2v1h2v-3z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]
                   [:rect {:x "-61" :y "1004.4" :width "2" :height "11" :style {:fill "#d35400"}}]]]]
                55
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:polygon {:fill "#90caf9" :points "40,45 8,45 8,3 30,3 40,13"}]
                 [:polygon {:fill "#e1f5fe" :points "38.5,14 29,14 29,4.5"}]
                 [:polygon {:fill "#1565c0" :points "21,23 14,33 28,33"}]
                 [:polygon {:fill "#1976d2" :points "28,26.4 23,33 33,33"}]
                 [:circle {:fill "#1976d2" :cx "31.5" :cy "24.5" :r "1.5"}]]
                56
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#ff7043" :d "M38 44H12V4h26c2.2.0 4 1.8 4 4v32c0 2.2-1.8 4-4 4z"}]
                 [:path {:fill "#bf360c" :d "M10 4h2v40h-2c-2.2.0-4-1.8-4-4V8c0-2.2 1.8-4 4-4z"}]
                 [:g {:fill "#ab300b"}
                  [:circle {:cx "26" :cy "20" :r "4"}]
                  [:path {:d "M33 30s-1.9-4-7-4-7 4-7 4v2h14V30z"}]]]
                57
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#90caf9" :d "M39 16v7h-6v-7h-2v7h-6v-7h-2v7h-7v2h7v6h-7v2h7v6h-7v2h25V16H39zm0 9v6h-6v-6h6zM25 25h6v6h-6V25zm0 8h6v6h-6V33zm8 6v-6h6v6H33z"}]
                 [:polygon {:fill "#00bcd4" :points "40,8 8,8 8,40 16,40 16,16 40,16"}]
                 [:path {:fill "#0097a7" :d "M7 7v34h10V17h24V7H7zM9 23v-6h6v6H9zm6 2v6H9v-6h6zM17 9h6v6h-6V9zm8 0h6v6h-6V9zM15 9v6H9V9h6zM9 39v-6h6v6H9zM39 15h-6V9h6v6z"}]]
                58
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:circle {:fill "#ffb74d" :cx "24" :cy "11" :r "6"}]
                 [:path {:fill "#607d8b" :d "M36 26.1S32.7 19 24 19s-12 7.1-12 7.1V30h24V26.1z"}]
                 [:polygon {:fill "#b0bec5" :points "41,25 7,25 6,29 11,32 9,29 39,29 37,32 42,29"}]
                 [:polygon {:fill "#78909c" :points "9,29 39,29 35,41 13,41"}]]
                59
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(2,0,0,2,0,-2056.8)"}
                  [:g {}
                   [:g {:transform "matrix(1,0,0,1,-1,0)"}
                    [:path {:d "M6.094 1029.5v1L10 1034.4l-1 3-3 1L2.094 1034.5C2.039 1034.8 2 1035 2 1035.4 2 1035.4 1.996 1035.5 2 1035.6 1.996 1035.7 2 1035.8 2 1035.9c0 3 2.462 5.5 5.5 5.5C7.959 1041.4 8.387 1041.3 8.813 1041.2L10.969 1043.3 15 1047.4l3 3C18.507 1050.9 19.214 1051.2 20 1051.2 21.541 1051.2 22.781 1050 22.781 1048.4 22.781 1047.6 22.487 1046.9 22 1046.4L21.969 1046.3 19 1043.4 14.969 1039.3 12.812 1037.2C12.918 1036.7 13 1036.3 13 1035.9 13 1032.8 10.538 1030.4 7.5 1030.4H7.094V1029.5h-1zM20 1046.9C20.828 1046.9 21.5 1047.5 21.5 1048.4 21.5 1049.2 20.828 1049.9 20 1049.9S18.5 1049.2 18.5 1048.4C18.5 1047.5 19.172 1046.9 20 1046.9z" :style {:fill "#7f8c8d" :fill-rule "nonzero"}}]
                    [:path {:d "M7 1029.4c-.313.0-.609.0-.906.0999999999999091L10 1033.4l-1 3-3 1L2.094 1033.5C2.039 1033.8 2 1034 2 1034.4 2 1034.4 1.996 1034.5 2 1034.6 1.996 1034.7 2 1034.8 2 1034.9c0 3 2.462 5.5 5.5 5.5C7.959 1040.4 8.387 1040.3 8.813 1040.2L10.969 1042.3 15 1046.4l3 3C18.507 1049.9 19.214 1050.2 20 1050.2 21.541 1050.2 22.781 1049 22.781 1047.4 22.781 1046.6 22.487 1045.9 22 1045.4L21.969 1045.3 19 1042.4 14.969 1038.3 12.812 1036.2C12.918 1035.7 13 1035.3 13 1034.9 13 1031.8 10.538 1029.4 7.5 1029.4H7zm13 16.5C20.828 1045.9 21.5 1046.5 21.5 1047.4 21.5 1048.2 20.828 1048.9 20 1048.9S18.5 1048.2 18.5 1047.4C18.5 1046.5 19.172 1045.9 20 1045.9z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]]
                   [:path {:d "M11 1038.4 9.031 1040.3 9 1040.4C9.007 1040.4 8.994 1040.4 9 1040.4 8.999 1040.4 8.997 1040.4 9 1040.5 9.077 1040.7 9.063 1041 9 1041.4L9.969 1042.3 12 1044.4C12.365 1044.3 12.719 1044.3 13 1044.4l2-2-1.5-1.5 1.5-1.5-1-1-1.5 1.5-1.5-1.5z" :style {:fill "#7f8c8d" :fill-rule "nonzero"}}]
                   [:path {:d "M11.5 1039.9l1 1 9-9-1-1-9 9z" :style {:fill "#bdc3c7" :fill-rule "nonzero"}}]
                   [:path {:d "M21 1031.4l-9 9 .5.5 9-9-.5-.5z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]
                   [:path {:d "M22 1029.4l-2.5 1.5 2 2 1.5-2.5-1-1z" :style {:fill "#95a5a6" :fill-rule "nonzero"}}]
                   [:g {}
                    [:path {:d "M11 1037.4l-2 2C9.337 1040.2 8.584 1041.8 8 1042.4 7.416 1042.9 5.884 1043.7 5 1043.4l-4 4 4 4 4-4c-.337-.900000000000091.416-2.5 1-3C10.584 1043.8 12.116 1043 13 1043.4l2-2-4-4z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]
                    [:path {:d "M11 1037.4l-2 2C9.337 1040.2 8.584 1041.8 8 1042.4 7.416 1042.9 5.884 1043.7 5 1043.4l-4 4 2 2 10-10-2-2z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]
                    [:path {:d "M9.031 1039.3 9 1039.4C9.011 1039.4 8.991 1039.4 9 1039.5 9.01 1039.4 9.023 1039.4 9.031 1039.3zM14.5 1040.9l-1.5 1.5C12.116 1042 10.584 1042.8 10 1043.4 9.435 1043.9 8.742 1045.4 9 1046.3 9.176 1045.5 9.62 1044.7 10 1044.4 10.584 1043.8 12.116 1043 13 1043.4l2-2-.5-.5zm-5.531 5.5-3.969 4-3.5-3.5-.5.5 4 4 4-4C8.895 1047.1 8.9 1046.7 8.969 1046.4z" :style {:fill "#e67e22" :fill-rule "nonzero"}}]]]]]
                60
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:polygon {:fill "#e8eaf6" :points "42,39 6,39 6,23 24,6 42,23"}]
                 [:g {:fill "#c5cae9"}
                  [:polygon {:points "39,21 34,16 34,9 39,9"}]
                  [:rect {:x "6" :y "39" :width "36" :height "5"}]]
                 [:polygon {:fill "#b71c1c" :points "24,4.3 4,22.9 6,25.1 24,8.4 42,25.1 44,22.9"}]
                 [:rect {:x "18" :y "28" :fill "#d84315" :width "12" :height "16"}]
                 [:rect {:x "21" :y "17" :fill "#01579b" :width "6" :height "6"}]
                 [:path {:fill "#ff8a65" :d "M27.5 35.5c-.3.0-.5.2-.5.5v2c0 .3.2.5.5.5S28 38.3 28 38v-2C28 35.7 27.8 35.5 27.5 35.5z"}]]
                61
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(1.02812,0,0,1.02812,-5.655,-5.49468)"}
                  [:path {:d "M28.844 10.213l5.042 13.485 14.384.628-11.267 8.963L40.85 47.163 28.844 39.217 16.838 47.163l3.847-13.874L9.418 24.326l14.383-.628 5.043-13.485z" :style {:fill "#f8c400"}}]]]
                62
                [mui-svg-icon {:xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :viewBox "0 2 48 48" :enable-background "new 0 2 48 48"}
                 [:polygon {:fill "#eceff1" :points "20.1,18.2 20.2,20.5 18.6,23.5 16.1,28.4 15.6,32.5 17.4,38.3 21.5,40.6 27.7,40.6 33.5,36.2 36.1,29.3 30.1,22 28.4,17.9"}]
                 [:path {:fill "#263238" :d "M34.3 23.9c-1.6-2.3-2.9-3.7-3.6-6.6-.7-2.9.2-2.1-.4-4.6-.3-1.3-.8-2.2-1.3-2.9-.6-.7-1.3-1.1-1.7-1.2-.9-.5-3-1.3-5.6.1-2.7 1.4-2.4 4.4-1.9 10.5.0.4-.1.9-.3 1.3-.4.9-1.1 1.7-1.7 2.4-.7 1-1.4 2-1.9 3.1-1.2 2.3-2.3 5.2-2 6.3.5-.1 6.8 9.5 6.8 9.7.4-.1 2.1-.1 3.6-.1 2.1-.1 3.3-.2 5 .2.0-.3-.1-.6-.1-.9.0-.6.1-1.1.2-1.8.1-.5.2-1 .3-1.6-1 .9-2.8 1.9-4.5 2.2-1.5.3-4-.2-5.2-1.7.1.0.3.0.4-.1.3-.1.6-.2.7-.4.3-.5.1-1-.1-1.3-.2-.3-1.7-1.4-2.4-2s-1.1-.9-1.5-1.3c0 0-.6-.6-.8-.8-.2-.2-.3-.4-.4-.5-.2-.5-.3-1.1-.2-1.9.1-1.1.5-2 1-3 .2-.4.7-1.2.7-1.2s-1.7 4.2-.8 5.5c0 0 .1-1.3.5-2.6.3-.9.8-2.2 1.4-2.9s2.1-3.3 2.2-4.9c0-.7.1-1.4.1-1.9-.4-.4 6.6-1.4 7-.3.1.4 1.5 4 2.3 5.9.4.9.9 1.7 1.2 2.7.3 1.1.5 2.6.5 4.1.0.3.0.8-.1 1.3.2.0 4.1-4.2-.5-7.7.0.0 2.8 1.3 2.9 3.9.1 2.1-.8 3.8-1 4.1.1.0 2.1.9 2.2.9.4.0 1.2-.3 1.2-.3.1-.3.4-1.1.4-1.4C37.6 29.9 35.9 26.2 34.3 23.9z"}]
                 [:g {}
                  [:ellipse {:fill "#eceff1" :cx "21.6" :cy "15.3" :rx "1.3" :ry "2"}]
                  [:ellipse {:fill "#eceff1" :cx "26.1" :cy "15.2" :rx "1.7" :ry "2.3"}]]
                 [:g {}
                  [:ellipse {:transform "matrix(-0.1254 -0.9921 0.9921 -0.1254 8.9754 38.9969)" :fill "#212121" :cx "21.7" :cy "15.5" :rx "1.2" :ry ".7"}]
                  [:ellipse {:fill "#212121" :cx "26" :cy "15.6" :rx "1" :ry "1.3"}]]
                 [:g {}
                  [:path {:fill "#ffc107" :d "M39.3 37.6c-.4-.2-1.1-.5-1.7-1.4-.3-.5-.2-1.9-.7-2.5-.3-.4-.7-.2-.8-.2-.9.2-3 1.6-4.4.0-.2-.2-.5-.5-1-.5s-.7.2-.9.6-.2.7-.2 1.7c0 .8.0 1.7-.1 2.4-.2 1.7-.5 2.7-.5 3.7.0 1.1.3 1.8.7 2.1.3.3.8.5 1.9.5 1.1.0 1.8-.4 2.5-1.1.5-.5.9-.7 2.3-1.7 1.1-.7 2.8-1.6 3.1-1.9.2-.2.5-.3.5-.9C40 37.9 39.6 37.7 39.3 37.6z"}]
                  [:path {:fill "#ffc107" :d "M19.2 37.9c-1-1.6-1.1-1.9-1.8-2.9-.6-1-1.9-2.9-2.7-2.9-.6.0-.9.3-1.3.7-.4.4-.8 1.3-1.5 1.8-.6.5-2.3.4-2.7 1-.4.6.4 1.5.4 3 0 .6-.5 1-.6 1.4-.1.5-.2.8.0 1.2.4.6.9.8 4.3 1.5 1.8.4 3.5 1.4 4.6 1.5 1.1.1 3 0 3-2.7C21 39.9 20.1 39.5 19.2 37.9z"}]
                  [:path {:fill "#ffc107" :d "M21.1 19.8C20.5 19.4 20 19 20 18.4s.4-.8 1-1.3c.1-.1 1.2-1.1 2.3-1.1s2.4.7 2.9.9c.9.2 1.8.4 1.7 1.1-.1 1-.2 1.2-1.2 1.7-.7.2-2 1.3-2.9 1.3-.4.0-1 0-1.4-.1C22.1 20.8 21.6 20.3 21.1 19.8z"}]]
                 [:g {}
                  [:path {:fill "#634703" :d "M20.9 19c.2.2.5.4.8.5.2.1.5.2.5.2.4.0.7.0.9.0.5.0 1.2-.2 1.9-.6.7-.3.8-.5 1.3-.7.5-.3 1-.6.8-.7-.2-.1-.4.0-1.1.4-.6.4-1.1.6-1.7.9-.3.1-.7.3-1 .3s-.6.0-.9.0c-.3.0-.5-.1-.8-.2-.2-.1-.3-.2-.4-.2-.2-.1-.6-.5-.8-.6.0.0-.2.0-.1.1C20.6 18.7 20.7 18.8 20.9 19z"}]
                  [:path {:fill "#634703" :d "M23.9 16.8c.1.2.3.2.4.3.1.1.2.1.2.1.1-.1.0-.3-.1-.3C24.4 16.7 23.9 16.7 23.9 16.8z"}]
                  [:path {:fill "#634703" :d "M22.3 17c0 .1.2.2.2.1.1-.1.2-.2.3-.2.2-.1.1-.2-.2-.2C22.4 16.8 22.4 16.9 22.3 17z"}]]
                 [:path {:fill "#455a64" :d "M32 34.7c0 .1.0.2.0.3.2.4.7.5 1.1.5.6.0 1.2-.4 1.5-.8.0-.1.1-.2.2-.3.2-.3.3-.5.4-.6.0.0-.1-.1-.1-.2-.1-.2-.4-.4-.8-.5-.3-.1-.8-.2-1-.2-.9-.1-1.4.2-1.7.5.0.0.1.0.1.1.2.2.3.4.3.7C32.1 34.4 32 34.5 32 34.7z"}]]
                63
                [mui-svg-icon {:xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :width "48" :height "48" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:g {}
                  [:path {:fill "#7cb342" :d "M12 29.001c0 1.104-.896 2-2 2s-2-.896-2-2v-9c0-1.104.896-2 2-2s2 .896 2 2v9z"}]
                  [:path {:fill "#7cb342" :d "M40 29.001c0 1.104-.896 2-2 2s-2-.896-2-2v-9c0-1.104.896-2 2-2s2 .896 2 2v9z"}]
                  [:path {:fill "#7cb342" :d "M22 40c0 1.104-.896 2-2 2s-2-.896-2-2v-9c0-1.104.896-2 2-2s2 .896 2 2v9z"}]
                  [:path {:fill "#7cb342" :d "M30 40c0 1.104-.896 2-2 2s-2-.896-2-2v-9c0-1.104.896-2 2-2s2 .896 2 2v9z"}]
                  [:path {:fill "#7cb342" :d "M14 18.001V33c0 1.104.896 2 2 2h16c1.104.0 2-.896 2-2V18.001H14z"}]
                  [:path {:fill "#7cb342" :d "M24 8c-6 0-9.655 3.645-10 8h20C33.654 11.645 30 8 24 8zm-4 5.598c-.552.0-1-.448-1-1s.448-1 1-1 1 .448 1 1-.448 1-1 1zm8 0c-.553.0-1-.448-1-1s.447-1 1-1 1 .448 1 1-.446999999999999 1-1 1z"}]
                  [:line {:fill "none" :stroke "#7cb342" :stroke-width "2" :stroke-linecap "round" :x1 "30" :y1 "7" :x2 "28.334" :y2 "9.499"}]
                  [:line {:fill "none" :stroke "#7cb342" :stroke-width "2" :stroke-linecap "round" :x1 "18" :y1 "7" :x2 "19.333" :y2 "9.082"}]]]
                64
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(1.79561,0,0,1.79561,2.43561,2.30747)"}
                  [:path {:d "M21.086 8.255 20.399 8.741C19.912 9.171 19.499 9.681 19.169 10.246 18.739 11.016 18.519 11.886 18.525 12.766 18.54 13.849 18.815 14.801 19.365 15.626c.387.6.904 1.114 1.534 1.536C21.209 17.372 21.481 17.517 21.739 17.612 21.619 17.987 21.487 18.352 21.334 18.712 20.987 19.519 20.574 20.292 20.084 21.022 19.652 21.652 19.312 22.122 19.054 22.432 18.652 22.912 18.264 23.272 17.874 23.529c-.43.285-.934999999999999.436-1.452.436C16.072 23.98 15.722 23.935 15.388 23.838 15.098 23.743 14.812 23.636 14.532 23.515 14.239 23.381 13.936 23.267 13.627 23.175 13.247 23.075 12.857 23.027 12.463 23.028 12.063 23.028 11.673 23.078 11.303 23.173 10.993 23.261 10.693 23.369 10.396 23.498 9.976 23.673 9.701 23.788 9.541 23.838 9.217 23.934 8.885 23.992 8.551 24.013 8.031 24.013 7.547 23.863 7.065 23.563L7.078 23.55C6.605 23.234 6.185 22.847 5.834 22.4 5.451 21.937 5.096 21.45 4.77 20.946c-.766-1.12-1.365-2.345-1.78-3.636-.41-1.23-.647-2.418-.71-3.581C2.987 13.101 3.846 12.263 4.738 12.081 6.29 11.764 7.893 12.304 9.444 12.376 10.327 12.417 11.084 12.291 11.938 12.081 12.518 11.938 13.224 11.932 13.742 11.629 14.212 11.355 16.734 10.287 17.473 9.876 18.677 9.204 19.738 8.297 21.086 8.255z" :style {:fill "#4b98d1"}}]]
                 [:g {:transform "matrix(1.79561,0,0,1.79561,2.46527,2.44192)"}
                  [:path {:d "M2.277 13.661 2.247 12.963c0-1.57.34-2.94 1.002-4.09.49-.9 1.22-1.653 2.1-2.182C6.199 6.161 7.189 5.871 8.189 5.851c.35.0.73.0499999999999998 1.13.15C9.609 6.081 9.959 6.211 10.389 6.371 10.939 6.581 11.239 6.711 11.339 6.741c.32.12.59.17.799999999999999.17C12.299 6.911 12.529 6.861 12.784 6.781 12.929 6.731 13.204 6.641 13.594 6.471 13.98 6.331 14.286 6.211 14.529 6.121 14.899 6.011 15.257 5.911 15.579 5.861 15.969 5.801 16.356 5.781 16.727 5.811 17.437 5.861 18.087 6.011 18.667 6.231c1.016.408 1.79 1.045 2.403 1.95C20.574 8.585 19.562 9.507 19.562 9.507s-2.296 2.773-3.928 3.228C13.885 13.223 10.839 12.556 9.072 12.434 7.723 12.341 6.168 11.802 5.036 12.006c-1.055.19-2.052 1.032-2.759 1.655z" :style {:fill "#68b7f0"}}]]
                 [:g {:transform "matrix(1.79561,0,0,1.79561,2.46527,2.44192)"}
                  [:path {:d "M13.932 5.09c-.68.34-1.326.484-1.973.436C11.859 4.88 11.959 4.216 12.229 3.489c.24-.62.560000000000001-1.18 1-1.68C13.689 1.289 14.239.859 14.859.549c.66-.34 1.29-.52 1.89-.55.0800000000000019.68.0 1.35-.25 2.07C16.271 2.709 15.931 3.299 15.499 3.829 15.064 4.349 14.524 4.779 13.913 5.089L13.932 5.09z" :style {:fill "#67b7f0"}}]]]
                65
                [mui-svg-icon {:xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :width "48" :height "48" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#cfd8dc" :d "M6 10c0-2.209 1.791-4 4-4h28c2.209.0 4 1.791 4 4v28c0 2.209-1.791 4-4 4H10c-2.209.0-4-1.791-4-4V10z"}]
                 [:path {:fill "#37474f" :d "M39 17.271c0 .191-.148.349-.334.349h-1.799l-8.164 18.179c-.052.12-.17.2-.297.202h-.004c-.127.0-.242-.074-.298-.193l-3.874-8.039-4.18 8.049c-.06.116-.167.181-.303.184-.125-.004-.239-.082-.292-.199l-8.252-18.182h-1.87C9.149 17.619 9 17.462 9 17.271V16.35C9 16.155 9.149 16 9.333 16h6.657c.184.0.333.155.333.35v.921c0 .191-.149.349-.333.349h-1.433l5.696 13.748 2.964-5.793-3.757-7.953h-.904c-.184.0-.333-.157-.333-.35V16.35c0-.191.149-.348.333-.348h4.924c.184.0.333.156.333.348v.922c0 .192-.149.35-.333.35h-.867l2.162 4.948 2.572-4.948H25.77c-.187.0-.334-.157-.334-.35V16.35c0-.191.147-.348.334-.348h4.784c.187.0.333.156.333.348v.922c0 .192-.146.35-.333.35h-1.05l-3.757 7.141 3.063 6.584 5.905-13.725h-1.872c-.184.0-.334-.157-.334-.35V16.35c0-.191.15-.348.334-.348h5.822c.186.0.334.156.334.348V17.271z"}]]
                66
                [mui-svg-icon {:viewBox "0 0 48 48" :xmlns "http://www.w3.org/2000/svg" :xmlns:xlink "http://www.w3.org/1999/xlink" :xmlns:serif "http://www.serif.com/" :style {:fill-rule "evenodd" :clip-rule "evenodd" :stroke-linejoin "round" :stroke-miterlimit "2"}}
                 [:g {:transform "matrix(1.83333,0,0,1.83333,2,-1884.29)"}
                  [:g {:transform "matrix(1.02273,0,0,0.886365,-0.272728,120.043)"}
                   [:rect {:x "0" :y "1031.4" :width "24" :height "14" :style {:fill "#16a085"}}]
                   [:rect {:x "0" :y "1032.4" :width "24" :height "14" :style {:fill "#1abc9c"}}]
                   [:path {:d "M5 1033.4C5 1035.6 3.209 1037.4 1 1037.4V1041.3C3.209 1041.3 5 1043.1 5 1045.4H19V1045.3c0-2.20000000000005 1.791-4 4-4V1037.4c-2.209.0-4-1.80000000000018-4-4H5z" :style {:fill "#ecf0f1" :fill-rule "nonzero"}}]
                   [:g {:transform "matrix(1,0,0,1,0,1028.4)"}
                    [:path {:d "M16 11c0 2.761-1.791 5-4 5s-4-2.239-4-5 1.791-5 4-5 4 2.239 4 5z" :style {:fill "#1abc9c" :fill-rule "nonzero"}}]]
                   [:g {:transform "matrix(8.00001,0,0,9.23078,9.54947,1042.51)"}
                    [:path {:d "M.392-.136c0 .019.001.033.003.042C.397-.084.401-.076.406-.07.411-.064.419-.059.429-.056c.01.004.023.006.038.008.016.002.036.003.061.004V0H.106V-.044C.143-.046.168-.048.184-.051.199-.054.211-.058.219-.063.227-.069.233-.077.237-.087.241-.097.243-.113.243-.136V-.489C.243-.503.24-.512.235-.518.23-.524.223-.527.214-.527.206-.527.195-.523.18-.516.165-.508.141-.493.107-.47L.077-.521.332-.674H.395C.393-.643.392-.6.392-.544v.408z" :style {:fill "#fff" :fill-rule "nonzero"}}]]]
                  [:g {:transform "matrix(1.9176,0,0,1.1179,17.9635,-117.595)"}
                   [:g {:transform "matrix(1,0,0,1,-18.773,13.418)"}
                    [:g {:transform "matrix(0.52148,0,0,0.89455,-9.6523,1024.7)"}
                     [:path {:d "M40 5c-.875.0-1.642.202-2.188.5H37v1C37 7.329 38.343 8 40 8s3-.671 3-1.5v-1H42.188C41.642 5.202 40.875 5 40 5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                    [:g {:transform "matrix(0.52148,0,0,0.89455,-9.1309,1022.9)"}
                     [:path {:d "M42 7.5C42 8.328 40.657 9 39 9S36 8.328 36 7.5C36 6.672 37.343 6 39 6s3 .672 3 1.5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]]
                   [:g {}
                    [:g {:transform "matrix(1,0,0,1,-18.773,11.629)"}
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.6523,1024.7)"}
                      [:path {:d "M40 5c-.875.0-1.642.202-2.188.5H37v1C37 7.329 38.343 8 40 8s3-.671 3-1.5v-1H42.188C41.642 5.202 40.875 5 40 5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.1309,1022.9)"}
                      [:path {:d "M42 7.5C42 8.328 40.657 9 39 9S36 8.328 36 7.5C36 6.672 37.343 6 39 6s3 .672 3 1.5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]]
                    [:g {:transform "matrix(1,0,0,1,-18.773,9.8401)"}
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.6523,1024.7)"}
                      [:path {:d "M40 5c-.875.0-1.642.202-2.188.5H37v1C37 7.329 38.343 8 40 8s3-.671 3-1.5v-1H42.188C41.642 5.202 40.875 5 40 5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.1309,1022.9)"}
                      [:path {:d "M42 7.5C42 8.328 40.657 9 39 9S36 8.328 36 7.5C36 6.672 37.343 6 39 6s3 .672 3 1.5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]]
                    [:g {:transform "matrix(1,0,0,1,-18.773,8.0509)"}
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.6523,1024.7)"}
                      [:path {:d "M40 5c-.875.0-1.642.202-2.188.5H37v1C37 7.329 38.343 8 40 8s3-.671 3-1.5v-1H42.188C41.642 5.202 40.875 5 40 5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.1309,1022.9)"}
                      [:path {:d "M42 7.5C42 8.328 40.657 9 39 9S36 8.328 36 7.5C36 6.672 37.343 6 39 6s3 .672 3 1.5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]]
                    [:g {:transform "matrix(1,0,0,1,-15.123,13.418)"}
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.6523,1024.7)"}
                      [:path {:d "M40 5c-.875.0-1.642.202-2.188.5H37v1C37 7.329 38.343 8 40 8s3-.671 3-1.5v-1H42.188C41.642 5.202 40.875 5 40 5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.1309,1022.9)"}
                      [:path {:d "M42 7.5C42 8.328 40.657 9 39 9S36 8.328 36 7.5C36 6.672 37.343 6 39 6s3 .672 3 1.5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]]
                    [:g {:transform "matrix(1,0,0,1,-14.08,12.524)"}
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.6523,1024.7)"}
                      [:path {:d "M40 5c-.875.0-1.642.202-2.188.5H37v1C37 7.329 38.343 8 40 8s3-.671 3-1.5v-1H42.188C41.642 5.202 40.875 5 40 5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.1309,1022.9)"}
                      [:path {:d "M42 7.5C42 8.328 40.657 9 39 9S36 8.328 36 7.5C36 6.672 37.343 6 39 6s3 .672 3 1.5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]]
                    [:g {:transform "matrix(1,0,0,1,-18.773,6.2618)"}
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.6523,1024.7)"}
                      [:path {:d "M40 5c-.875.0-1.642.202-2.188.5H37v1C37 7.329 38.343 8 40 8s3-.671 3-1.5v-1H42.188C41.642 5.202 40.875 5 40 5z" :style {:fill "#f39c12" :fill-rule "nonzero"}}]]
                     [:g {:transform "matrix(0.52148,0,0,0.89455,-9.1309,1022.9)"}
                      [:path {:d "M42 7.5C42 8.328 40.657 9 39 9S36 8.328 36 7.5C36 6.672 37.343 6 39 6s3 .672 3 1.5z" :style {:fill "#f1c40f" :fill-rule "nonzero"}}]]]]]]]
                67
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:rect {:x "4" :y "9" :fill "#e8eaf6" :width "40" :height "30"}]
                 [:g {:fill "#5c6bc0"}
                  [:polygon {:points "30,34 32.8,34 27.8,29 25,31.8 30,36.8"}]
                  [:polygon {:points "18,34 15.2,34 20.2,29 23,31.8 18,36.8"}]]
                 [:rect {:x "11" :y "15" :fill "#9fa8da" :width "26" :height "4"}]
                 [:path {:fill "#9fa8da" :d "M24 23c-2.8.0-5 2.2-5 5s2.2 5 5 5 5-2.2 5-5-2.2-5-5-5zm0 8c-1.7.0-3-1.3-3-3s1.3-3 3-3 3 1.3 3 3-1.3 3-3 3z"}]
                 [:path {:fill "#9fa8da" :d "M3 8v32h42V8H3zM43 35c-1.7.0-3 1.3-3 3H8c0-1.7-1.3-3-3-3V13c1.7.0 3-1.3 3-3h32c0 1.7 1.3 3 3 3V35z"}]]
                68
                [mui-svg-icon {:version "1" :xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 48 48" :enable-background "new 0 0 48 48"}
                 [:path {:fill "#37474f" :d "M4 39V7c0-2.2 1.8-4 4-4h22c2.2.0 4 1.8 4 4v32c0 2.2-1.8 4-4 4H8c-2.2.0-4-1.8-4-4z"}]
                 [:path {:fill "#bbdefb" :d "M30 6H8c-.6.0-1 .4-1 1v29c0 .6.4 1 1 1h22c.6.0 1-.4 1-1V7C31 6.4 30.6 6 30 6z"}]
                 [:rect {:x "15" :y "39" :fill "#78909c" :width "6" :height "2"}]
                 [:path {:fill "#e38939" :d "M24 41V17c0-2.2 1.8-4 4-4h12c2.2.0 4 1.8 4 4v24c0 2.2-1.8 4-4 4H28c-2.2.0-4-1.8-4-4z"}]
                 [:path {:fill "#fff3e0" :d "M40 16H28c-.6.0-1 .4-1 1v22c0 .6.4 1 1 1h12c.6.0 1-.4 1-1V17C41 16.4 40.6 16 40 16z"}]
                 [:circle {:fill "#a6642a" :cx "34" :cy "42.5" :r "1.5"}]]})

(defn entry-icon
  "Returns the entry icon as form-2 reagent component for the given index"
  []
  (fn [index]
    [mui-box {:sx {"& svg" {:width "1.25em" :height "1.25em"}} ;; & svg sets additional style of child <svg> under this box(div)
              :display "flex" :alignItems "center"}            ;; Aligns the icon to the center of box
     (get all-icons index [mui-icon-vpn-key-outlined])]))

(defn group-icon
  "Returns the group icon as form-2 reagent component for the given index"
  []
  (fn [index]
    [mui-box {:sx  {"& svg" {:width "1em" :height "1em"}
                    :display "flex" :alignItems "center"}}
     (get all-icons index [mui-icon-vpn-key-outlined])]))

(def entry-type-icons-m 
  "Icons shown while showing entry types in category panel"
  {const/LOGIN_TYPE_NAME mui-icon-login-outlined
   const/CREDIT_DEBIT_CARD_TYPE_NAME mui-icon-credit-card-outlined
   const/BANK_ACCOUNT_TYPE_NAME mui-icon-account-balance-outlined
   const/WIRELESS_ROUTER_TYPE_NAME mui-icon-wifi-outlined
   const/PASSPORT_TYPE_NAME mui-icon-flight-takeoff-outlined
   const/AUTO_DB_OPEN_TYPE_NAME mui-icon-launch})

(defn entry-type-icon
  "Resturns an as form-1 reagent component for the given name"
  [name icon-name]
  (let [icon (get entry-type-icons-m name)]
    (if icon
      [icon]
      [entry-icon (str->int icon-name)])))
