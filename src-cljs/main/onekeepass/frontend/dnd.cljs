(ns onekeepass.frontend.dnd
  "Reagent wrappers for @dnd-kit/core primitives.
  Follow the same pattern as mui_components.cljs for hook bindings and adapt-react-class.
  Hooks (use-draggable, use-droppable, use-sensor, use-sensors) must be called inside
  Reagent component render functions, not at the top level."
  (:require
   ["@dnd-kit/core" :as dnd-core]
   ["@dnd-kit/utilities" :as dnd-utils]
   [reagent.core :as r]))

;; Hook function bindings — same pattern as mui_components.cljs lines 43-45
(def use-draggable ^js (.-useDraggable ^js dnd-core))
(def use-droppable ^js (.-useDroppable ^js dnd-core))
(def use-sensor    ^js (.-useSensor    ^js dnd-core))
(def use-sensors   ^js (.-useSensors   ^js dnd-core))

;; Sensor classes
(def PointerSensor  (.-PointerSensor  ^js dnd-core))
(def KeyboardSensor (.-KeyboardSensor ^js dnd-core))

;; Collision detection strategies
(def closest-center (.-closestCenter ^js dnd-core))

;; Reagent-wrapped top-level components — same pattern as adapt-react-class in mui_components.cljs
(def dnd-context  (r/adapt-react-class (.-DndContext  ^js dnd-core)))
(def drag-overlay (r/adapt-react-class (.-DragOverlay ^js dnd-core)))

(defn css-translate
  "Converts a @dnd-kit transform object to a CSS transform string.
  Use this to apply the drag transform to the draggable element's style."
  [^js transform]
  (when transform
    (.toString (.-Translate ^js (.-CSS ^js dnd-utils)) transform)))
