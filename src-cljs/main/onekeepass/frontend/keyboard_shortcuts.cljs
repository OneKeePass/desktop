(ns onekeepass.frontend.keyboard-shortcuts
  "Application wide keyboard shortcuts (KeePass style) that act on the
   currently selected entry
     Ctrl/Cmd+B       - copy user name
     Ctrl/Cmd+C       - copy password
     Ctrl/Cmd+Shift+U - copy url
     Ctrl/Cmd+U       - open url
     Ctrl/Cmd+T       - copy totp token
   The shortcuts act only when the key press is not meant for any regular text
   editing or text copying. This way the default Copy/Paste handling of the
   system Edit menu, context menus and text fields are never affected
   See https://github.com/OneKeePass/desktop/issues/79"
  (:require [clojure.string :as str]
            [onekeepass.frontend.constants :as const :refer [PASSWORD URL USERNAME]]
            [onekeepass.frontend.events.entry-form-ex :as form-events]))

(defn menu-shortcut-hint
  "Forms the shortcut hint text that is shown on the right side of the entry menu items
   e.g (menu-shortcut-hint os-name \"B\") returns '⌘B' on macOS and 'Ctrl+B' on others"
  [os-name key-label & {:keys [shift?]}]
  (if (= os-name const/MACOS)
    (str (when shift? "⇧") "⌘" key-label)
    (str "Ctrl+" (when shift? "Shift+") key-label)))

(defn- input-element? [^js el]
  (let [tag (some-> el .-tagName str/upper-case)]
    (or (= tag "INPUT") (= tag "TEXTAREA"))))

(defn- text-editing-target?
  "True when the focused element accepts text input and accordingly any key press
   should be left alone for the normal editing use"
  [^js el]
  (boolean (and el
                (or (.-isContentEditable el)
                    (and (input-element? el) (not (.-readOnly el)))))))

(defn- text-selection-found?
  "True when the user has selected some text in the window or within the focused
   read only field. Then Ctrl/Cmd+C is left alone to do the default text copy"
  [^js el]
  (or (not (str/blank? (str (.getSelection js/window))))
      (boolean (and el
                    (input-element? el)
                    ;; selectionStart/End access throws for few input types (e.g number)
                    (try
                      (not= (.-selectionStart el) (.-selectionEnd el))
                      (catch js/Error _e false))))))

(defn- dialog-shown?
  "True when any MUI dialog based popup (open db, settings, search etc.) is open"
  []
  (boolean (.querySelector js/document ".MuiDialog-root")))

(defn- shortcut-action
  "Returns a zero arg action fn for the pressed key combo or nil when the
   key combo is not one of the entry field shortcuts. The action fn returns
   true when it handled the key press by copying or opening a field value"
  [k shift?]
  (cond
    (and (not shift?) (= k "b")) #(form-events/copy-entry-form-field-to-clipboard USERNAME)
    (and (not shift?) (= k "c")) #(form-events/copy-entry-form-field-to-clipboard PASSWORD)
    (and shift? (= k "u"))       #(form-events/copy-entry-form-field-to-clipboard URL)
    (and (not shift?) (= k "u")) form-events/open-selected-entry-url
    (and (not shift?) (= k "t")) form-events/copy-entry-form-otp-token-to-clipboard))

(defn- handle-key-down [^js e]
  (when (and (or (.-ctrlKey e) (.-metaKey e))
             (not (.-altKey e))
             (not (.-isComposing e)))
    (let [k (some-> e .-key str/lower-case)
          el (.-activeElement js/document)
          action (shortcut-action k (.-shiftKey e))]
      (when (and action
                 (not (dialog-shown?))
                 (not (text-editing-target? el))
                 ;; For the copy key, any selected text is left to the default copy handling
                 (or (not= k "c") (not (text-selection-found? el))))
        (when (action)
          (.preventDefault e))))))

(defonce ^:private key-down-handler (atom nil))

(defn install-shortcuts!
  "Called once during the app startup to install the document level key handler"
  []
  (when-not @key-down-handler
    (let [handler (fn [^js e] (handle-key-down e))]
      (.addEventListener js/document "keydown" handler)
      (reset! key-down-handler handler))))
