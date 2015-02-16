(ns sunog.impl
  (:refer-clojure :exclude [get-in set! reset! conj! swap! dissoc! deref parents key])
  (:require cljsjs.firebase
            [sunog.utils :as utils]
            [sunog.registry :refer [register-listener]]))

(def undefined) ;; firebase methods do not take kindly to nil callbacks

(def SERVER_TIMESTAMP js/Firebase.ServerValue.TIMESTAMP)

;; circular refs not working, we repeat

(declare key)
(declare value)

(def child-events
  [:child-added
   :child-changed
   :child-moved
   :child-removed])

(def all-events
  (conj child-events :value))

(defn- wrap-snapshot [snapshot]
  ;; TODO: enhance with snapshot protocol
  [(key snapshot) (value snapshot)])

;;

(defn hydrate [js-val]
  (js->clj js-val :keywordize-keys true))

(defn serialize [clj-val]
  (clj->js clj-val))

(defn key
  "Last segment in reference or snapshot path"
  [ref]
  (.key ref))

(defn value [snapshot]
  (hydrate (.val snapshot)))

(defn connect
  "Create a reference for firebase"
  [url]
  (js/Firebase. url))

(defn get-in
  "Obtain child reference from base by following korks"
  [ref korks]
  (let [path (utils/korks->path korks)]
    (if-not (seq path) ref (.child ref path))))

(defn parent
  "Immediate ancestor of reference, if any"
  [ref]
  (and ref (.parent ref)))

;;

(defn deref [ref cb]
  (.once ref "value" (comp cb value)))

(defn reset! [ref val & [cb]]
  (.set ref (serialize val) (or cb undefined)))

(defn reset-with-priority! [ref val priority & [cb]]
  (.setWithPriority ref (serialize val) priority (or cb impl/undefined)))

(defn merge! [ref val & [cb]]
  (.update ref (serialize val) (or cb undefined)))

(defn conj! [ref val & [cb]]
  (.push ref (serialize val) (or cb undefined)))

(defn swap! [ref f & args]
  (let [[cb args] (utils/extract-cb args)
        f' #(-> % hydrate ((fn [x] (apply f x args))) serialize)]
    (.transaction ref f' (or cb undefined))))

(defn dissoc! [ref & [cb]]
  (.remove ref (or cb undefined)))

;; ------------------
;; subscriptions

(defn listen-to
  "Subscribe to notifications of given type"
  [ref type cb]
  (assert (some #{type} all-events) (str "Unknown type: " type))
  (let [type (utils/kebab->underscore type)]
    (let [cb' (comp cb wrap-snapshot)
          unsub! #(.off ref type cb')]
      (.on ref type cb')
      (register-listener ref type unsub!)
      unsub!)))

(defn listen-children
  "Subscribe to all children notifications on a reference, and return an unsubscribe"
  [ref cb]
  (let [cbs (->> child-events
                 (map (fn [type] #(vector type %)))
                 (map #(comp cb %)))
        unsubs (doall (map listen-to (repeat ref) child-events cbs))]
    (fn []
      (doseq [unsub! unsubs]
        (unsub!)))))
