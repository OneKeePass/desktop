(ns onekeepass.frontend.utils
  (:require
   [clojure.string :as str]
   [onekeepass.frontend.mui-components :as c]))

(set! *warn-on-infer* true)

(def UUID-DEFAULT "00000000-0000-0000-0000-000000000000")

(defn uuid-nil-or-default? [uuid]
  (or (nil? uuid) (= uuid UUID-DEFAULT)))

(defn tags->vec
  "Converts the incoming tag values from a string to a vector of tags to use in UI.
  The arg 'tags' is a string made from all tags searated by ';'
  "
  [tags]
  (if (or (nil? tags) (empty? (str/trim tags))) [] (str/split tags #"[;,]")))

(defn vec->tags
  "Called to convert a vector of tag values to a string with ';' as speaprator between
  tag values. Needs to be called before calling backend API.
  Returns a string made from all tags searated by ';'
   "
  [tagsv]
  (if (empty? tagsv) "" (str/join ";" tagsv)))

(defn add-months
  "Adds 'n' number of months to the given date and returns new Date object which is in UTC"
  [date n]
  (.addMonths c/date-fns-utils date n))

(defn add-months-today
  "Adds 'n' number of months to current date and returns new Date object which is in UTC"
  [n]
  (.addMonths c/date-fns-utils (js/Date.) n))

(defn to-UTC-ISO-string
  "This returns a string in simplified extended ISO format (ISO 8601) using 
  native javascript method on Date The timezone is always zero UTC offset, as denoted by the suffix 'Z'
  See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/toISOString
  "
  [^js/Date date]
  (.toISOString date))

(defn to-UTC
  "Converts a datetime str of format 'yyyy-MM-dd'T'HH:mm:ss' to UTC datetime
  The datetime-str may have suffix 'Z'. In that case the string is already a UTC string

  Returns Date object which is in UTC
  "
  [datetime-str]
  (let [s (if (str/ends-with? datetime-str "Z")
            datetime-str (str datetime-str "Z"))]
    (js/Date. s)))


;; See https://date-fns.org/v2.29.2/docs/format and https://date-fns.org/v2.29.2/docs/parse
;; for the format supported by date-fns and in turn date-fns-utils

(defn utc-to-local-datetime-str
  "Incoming Date represents a UTC datetime and returns formatted string in local TZ"
  ([^js/Date date format-str]
   ;; .formatByString expects the date as a UTC object
   (.formatByString c/date-fns-utils date format-str))
  ([^js/Date date]
   (utc-to-local-datetime-str date "yyyy-MM-dd'T'HH:mm:ss")))


(defn to-local-datetime-str
  "The arg datetime-str comes from backend which is in UTC formated string 
   which may have suffix 'Z'
   e.g  '2022-09-10T10:42:25' or '2022-09-10T10:42:25Z' or '2022-09-10T10:42:25.000Z'

   The arg out-format-str specifies the expected output str format.

   The serde crate serializes chrono::NaiveDateTime to the format like '2022-09-10T10:42:25'

   Returns the datetime formatted string in local TZ
   e.g '2022-09-10T03:42:25'   -  that is UTC -7:00 
   "
  ([datetime-str out-format-str]
   ;; First we need to convert the utc str to utc date object and 
   ;; then convert to local datetime str
   (-> datetime-str to-UTC (utc-to-local-datetime-str out-format-str)))
  ([datetime-str]
   (-> datetime-str to-UTC utc-to-local-datetime-str)))


(defn strip-utc-tz
  "JS formatted UTC-ISO-string has a suffix .000Z and before sending to backend api,
  that needs to stripped so that 'serde' can dserilize the datestr to chrono::NaiveDateTime
  "
  [datetime-str]
  ;;"2022-08-30T01:05:00.000Z" => "2022-08-30T01:05:00"
  (first (str/split datetime-str  #"\.")))

;;https://bsless.github.io/code-smells/
(defn remove-nil [m k v] (if (nil? v) (dissoc m k) m))
(defn prune-nils [m] (reduce-kv remove-nil m m))

(defn find-index
  "Finds the index of a value  in a vector
  Returns the index of item found or nil
  In case of duplicate values, the index of first instance returned 
  "
  [vec val]
  (reduce-kv
   (fn [_ k v]
     (when (= val v)
       (reduced k)))
   nil
   vec))

(defn str->int
  "Converts the incoming 'data' to an integer or returns nil"
  [data]
  (if (int? data)
    data
    (if (and (string? data) (re-matches #"\d+" data))  ;;(re-matches #"\d+"  data) will return nil when data includes space or any non numeric char
      (js/parseInt data)
      nil)))


(defn contains-val?
  "A sequential search to find a member with an early exit"
  [coll val]
  (reduce #(if (= val %2) (reduced true) %1) false coll))


(defn capitalize-words
  "Capitalize every word in a string"
  [s]
  (->> (str/split (str s) #"\b")
       (map str/capitalize)
       str/join))

#_(defn contains-val?
    [coll val]
    (boolean (seq (filter #(= % val)  coll))))

(comment
  (capitalize-words "custom fields") ;; => "Custom Fields"
  (capitalize-words "CUSTOM FIELDS") ;; => "Custom Fields"
  (str->int "3.3") ;; => nil
  (str->int "3 3") ;; => nil
  (str->int "aa") ;; => nil
  (str->int "3aa") ;; => nil
  
  (str->int 3) ;; => 3
  (str->int "33") ;; => 33
  ;;https://github.com/clj-commons/camel-snake-kebab/blob/version-0.4.3/src/camel_snake_kebab/extras.cljc
  (defn transform-keys
    "Recursively transforms all map keys in coll with t."
    [t coll]
    (letfn [(transform [[k v]] [(t k) v])]
      (postwalk (fn [x] (if (map? x) (with-meta (into {} (map transform x)) (meta x)) x)) coll)))

  (.formatByString c/date-fns-utils (js/Date.) "yyyy-MM-dd") ;; => "2022-01-03"
  
  (.formatByString c/date-fns-utils (js/Date.) "yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
  ;; => "2022-01-03T16:53:17.311-08:00"  Local timezone Pacific
  
  (.formatByString c/date-fns-utils (js/Date.) "yyyy-MM-dd'T'HH:mm:ss")
  ;; => "2022-09-09T18:48:22" Local timezone Pacific
  
  (.formatByString c/date-fns-utils (js/Date.) "dd MMM yyyy HH:mm:ss")

  (.date c/date-fns-utils)
  ;; => #inst "2022-09-10T00:27:51.811-00:00" A date obj UTC timezone
  
  (js/Date.)
  ;; => #inst "2022-01-04T00:43:40.691-00:00"  A date obj UTC timezone
  
  (js/Date. "2022-09-10T05:29:28Z")  ;; Z indicates that the datetime string is a UTC datetime
  ;; => #inst "2022-09-10T05:29:28.000-00:00"
  
  (js/Date. "2022-09-10T05:29:28")  ;; No Z at the end. The string is local timezone datetime str
  ;; => #inst "2022-09-10T12:29:28.000-00:00"
  
  ;; Formats a UTC datetime to local datetime string
  (.formatByString c/date-fns-utils (js/Date. "2022-09-10T05:29:28Z") "yyyy-MM-dd'T'HH:mm:ss")
   ;; => "2022-09-09T22:29:28"
  
  (to-UTC-ISO-string (js/Date. "2022-09-09T22:29:28"))
  ;; "2022-09-10T05:29:28.000Z"
  

  (.parse c/date-fns-utils "2022-09-09T17:42:35" "yyyy-MM-dd'T'HH:mm:ss")

  ;; => #inst "2022-09-10T00:42:35.000-00:00" A date obj UTC timezone
  
  (to-UTC-ISO-string (.parse c/date-fns-utils "2022-09-09T17:42:35" "yyyy-MM-dd'T'HH:mm:ss"))
  ;; => "2022-09-10T00:42:35.000Z" 
  
  (.toISOString (js/Date.)) ;; => "2022-01-04T01:02:07.594Z"    This is in UTC 
  
  (.toISO c/date-fns-utils (js/Date.)) ;; "2022-01-03T16:44:14-08:00" in Pacific time
  
  (utc-to-local-datetime-str (js/Date.) "yyyy-MM-dd HH mm ss")
  )