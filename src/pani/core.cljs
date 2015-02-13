(ns pani.core
  (:refer-clojure :exclude [get-in set! reset! conj! swap! dissoc! deref parents key])
  (:require cljsjs.firebase
            [clojure.string :as str]
            [pani.registry :refer [register-listener]]))

;; constants

(def child-events
  [:child-added
   :child-changed
   :child-moved
   :child-removed])

(def all-events
  (conj child-events :value))

;; utils

;; FIXME: camel-case keys?
;;        hydrate to custom vectors to preserve rich keys?
;;        preserve sets (don't coerce to vector)
;;        similarly preserve keywords as values

(defn- hydrate [js-val]
  (js->clj js-val :keywordize-keys true))

(defn- serialize [clj-val]
  (clj->js clj-val))

(defn key
  "Last segment in reference or snapshot path"
  [ref]
  (.key ref))

(defn- value [snapshot]
  (hydrate (.val snapshot)))

(defn- kebab->underscore [keyword]
  (-> keyword name (str/replace "-" "_")))

(defn- underscore->kebab [string]
  (-> string (str/replace "_" "-") keyword))

(defn- korks->path [korks]
  (if (sequential? korks)
    (str/join "/" (map name korks))
    (when korks (name korks))))

(defn- wrap-snapshot [snapshot]
  ;; TODO: enhance with snapshot protocol
  [(key snapshot) (value snapshot)])

;; references

(defn connect
  "Create a reference for firebase"
  [url]
  (js/Firebase. url))

(defn get-in
  "Obtain child reference from base by following korks"
  [ref korks]
  (let [path (korks->path korks)]
    (if-not (seq path) ref (.child ref path))))

(defn parent
  "Immediate ancestor of reference, if any"
  [ref]
  (and ref (.parent ref)))

(defn parents
  "Probably don't need this. Or maybe we want more zipper nav (siblings, in-order, etc)"
  [ref]
  (take-while identity (iterate parent (parent ref))))


;; --------------------
;; getters 'n setters

(def undefined) ;; firebase methods do not take kindly to nil callbacks

(defn deref [ref cb]
  (.once ref "value" (comp cb value)))

(defn reset! [ref val & [cb]]
  (.set ref (serialize val) (or cb undefined)))

(defn merge! [ref val & [cb]]
  (.update ref (serialize val) (or cb undefined)))

(defn conj! [ref val & [cb]]
  (.push ref (serialize val) (or cb undefined)))

(defn- extract-cb [args]
  (if (and (>= 2 (count args))
           (= (first (take-last 2 args)) :callback))
    [(last args) (drop-last 2 args)]
    [nil args]))

(defn swap! [ref f & args]
  (let [[cb args] (extract-cb args)
        f' #(-> % hydrate ((fn [x] (apply f x args))) serialize)]
    (.transaction ref f' (or cb undefined))))

(defn dissoc! [ref & [cb]]
  (.remove ref (or cb undefined)))

(def remove! dissoc!)

;; nested variants

(defn deref-in [ref korks & [cb]]
  (deref (get-in ref korks) cb))

(defn reset-in! [ref korks val & [cb]]
  (reset! (get-in ref korks) val cb))

(defn merge-in! [ref korks val & [cb]]
  (merge! (get-in ref korks) val cb))

(defn conj-in! [ref korks val & [cb]]
  (conj! (get-in ref korks) val cb))

(defn swap-in! [ref korks f & [cb]]
  (swap! (get-in ref korks) f cb))

(defn dissoc-in! [ref korks & [cb]]
  (dissoc! (get-in ref korks) cb))

(def remove-in! remove-in!)


;; ------------------
;; subscriptions

(defn listen-to
  "Subscribe to notifications of given type"
  ([ref type cb]
   (assert (some #{type} all-events) (str "Unknown type: " type))
   (let [type (kebab->underscore type)]
     (let [cb' (comp cb wrap-snapshot)
           unsub! #(.off ref type cb')]
       (.on ref type cb')
       (register-listener ref type unsub!)
       unsub!)))
  ([ref korks type cb]
   (listen-to (get-in ref korks) type cb)))

(defn listen-children
  "Subscribe to all children notifications on a reference, and return an unsubscribe"
  ([ref cb]
   (let [cbs (->> child-events
                  (map (fn [type] #(vector type %)))
                  (map #(comp cb %)))
         unsubs (doall (map listen-to (repeat ref) child-events cbs))]
     (fn []
       (doseq [unsub! unsubs]
         (unsub!)))))
  ([ref korks cb]
   (listen-children (get-in ref korks) cb)))
