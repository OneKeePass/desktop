(ns onekeepass.frontend.events.entry-form-dialogs
  (:require
   [clojure.string :as str]
   [re-frame.core :refer [reg-fx reg-event-db reg-event-fx reg-sub  dispatch subscribe]]
   [onekeepass.frontend.events.common :as cmn-events :refer [on-error
                                                             check-error
                                                             active-db-key
                                                             assoc-in-key-db
                                                             get-in-key-db
                                                             fix-tags-selection-prefix]]
   [onekeepass.frontend.constants :refer [PASSWORD ONE_TIME_PASSWORD]]
   [onekeepass.frontend.utils :as u :refer [contains-val?]]
   [onekeepass.frontend.background :as bg])
  )


(def otp-field-dialog-init-data {:dialog-show false 
                                 :section-name nil
                                 :secret-code nil
                                 :field-name nil 
                                 :error-fields {}  
                                 :custom-settings false 
                                 :data-type ONE_TIME_PASSWORD})