(ns onekeepass.frontend.background-common
  (:require
   [re-frame.core :refer [dispatch]]
   [cljs.core.async :refer [go]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [camel-snake-kebab.extras :as cske]
   [camel-snake-kebab.core :as csk]
   
   ;; All tauri side corresponding endpoint command apis can be found in 
   ;; https://github.com/tauri-apps/tauri/tree/tauri-v1.8.1/core/tauri/src/endpoints
   ;; The api implementation is in 
   ;; https://github.com/tauri-apps/tauri/tree/tauri-v1.8.1/core/tauri/src/api 

   ["@tauri-apps/api/tauri" :refer (invoke)]))

(defn to-snake-case [cljs-map]
  (->> cljs-map (cske/transform-keys csk/->snake_case)))

;; Tauri expects all API arguments names passed in JS api to be in camelCase which 
;; are in turn deserialized as snake_case to match rust argument names used in tauri commands.

(defn as-tauri-args 
  " 
  The arg 'api-args' is a map and its keys are converted to be in 'camelCase' as expected by tauri. 
  These keys of 'api-args' are then converted to command fns args 'snake_case' by tauri deserilaization.
  However if we pass any map as value in the tauri command fn arg, then we need to make sure keys of that map are in 'snake_case' 
  
   Returns a json object as expected by tauri's 'invoke' command
   "
  [api-args]
  (clj->js
   (reduce (fn [acc [k v]]
             (assoc 
              ;; arg map keys are in camelCase
              acc (csk/->camelCaseString k)
              (if (map? v) 
                ;; keys of value map should be in snake_case
                (to-snake-case v) v))) {} api-args)))

(defn invoke-api
  "Invokes the backend command API calls using the tauri's 'invoke' command.
   Args 
    'name' is the tauri command name
    
     'api-args' is the argument map that is serialized and then parsed as arguments to the tauri command fn. 
     The args must be serializable by the tauri API. 

    'dispatch-fn' is a function that will be called when the tauri command call's promise is resolved or in error. 
     The call back function 'distach-fn' should accept a map (keys are  :result, :error) as input arg  

    IMPORTANT: If the returned value is a string instead of a map or any other type 
    and we want the string as {:result \"some string value\"}, then we need to pass :convert-response false
  "
  [name api-args dispatch-fn &
   {:keys [convert-request convert-response convert-response-fn]
    :or {convert-request true convert-response true}}]
  (go
    (try
      (let [;; when convert-request is false, the api-args is assumed to be a js object 
            ;; that can deserialized to Rust names and types as expected by the 'command' api
            ;; When convert-request is true, the api args are converted to 'camelCaseString' as expected by tauri command fns 
            ;; so that args can be deserialized to tauri types
            ;; When convert-request is false and api-args is a js object, (cske/transform-keys csk/->camelCaseString) 
            ;; does not make any changes as expected to be in a proper deserilaizable format
            args (if convert-request
                   ;; changes all keys to camelCase (e.g db-key -> dbKey)
                   ;; Tauri expects all API arguments names passed in JS api to be in camelCase which 
                   ;; are in turn deserialized as snake_case to match rust argument names used in 
                   ;; tauri commands.  
                   ;; Note
                   ;; Only the api argument names are expected to be in camelCase. The keys of value passed are not changed to cameCase 
                   ;; and they deserialized by the the corresponding struct serde definition. As result, mostly  convert-request = false
                   
                   ;; (->> api-args (cske/transform-keys csk/->camelCaseString) clj->js)
                   
                   ;; TODO: 
                   ;; Review all background fns where we are using :convert-request false and 
                   ;; see whether that is required any more or not as using as-tauri-args in most of the cases
                   (as-tauri-args api-args)
                   
                   api-args)
            r (<p! (invoke name args))]
        ;; (println "r is " r)
        ;; Call the dispatch-fn with the resolved value 'r' which is a json obj (before any js->clj call)
        ;; represented as #js {..} 
        ;; As compared to mobile cljs, here 'r' is typically serialized string from stuct Result
        (dispatch-fn {:result (cond

                                (not (nil? convert-response-fn))
                                (-> r js->clj convert-response-fn) ;; custom transformer of response
                                
                                (and convert-response (string? r))
                                (csk/->kebab-case-keyword r)
                                
                                ;; Recursively the keys are transformed as kw
                                convert-response
                                (->> r js->clj (cske/transform-keys csk/->kebab-case-keyword))

                                ;; No conversion is done and just js->clj
                                :else
                                (js->clj r))})
        ;; Just to track db modifications if any
        (dispatch [:common/db-api-call-completed name]))
      
      ;; Promise returns error,when tauri calls return Err(..) and then this catch clause is called
      ;; This is different from the way done in mobile cljs
      ;; In mobile cljs r is a map {:ok somevalue, :error nil} or {:ok nil, :error someError}
      ;; Also promise reject in mobile app results in {:ok nil, :error rejectError}
      (catch js/Error err 
        (do
          ;;Call the dispatch-fn with any error returned by the backend API
          (dispatch-fn {:error (ex-cause err)})
          (js/console.log (ex-cause err)))))))



