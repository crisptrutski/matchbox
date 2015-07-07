(ns matchbox.coerce
  (:require [clojure.walk :as walk])
  #?(:clj (:import [java.util HashMap ArrayList])))

(defn- encode-key
  "Record keyword as string."
  [x]
  (if (keyword? x) (str x) x))

(defn- decode-key
  "Recover keyword from string."
  [x]
  (if (and (string? x) (= \: (first x)))
    (keyword (subs x 1))
    x))

(defn- map-keys
  "Return map with keys updated via function."
  [f hsh]
  (reduce-kv #(assoc %1 (f %2) %3) (empty hsh) hsh))

#?(:clj
   (defn- hydrate* [x]
     (cond (instance? HashMap x)   (recur (into {} x))
           (instance? ArrayList x) (recur (into [] x))
           (map? x)                (map-keys keyword x)
           :else                   (decode-key x))))

(defn- serialize* [v]
  (walk/prewalk ;; pre-walk to hit keys via map first
   (fn [x]
     (if (map? x)
       ;; assumes not namespaced.. or numeric
       (map-keys name x)
       (encode-key x)))
   v))

;; public

(defn ensure-map
  "Coerces sequential types to associative, by indexing if necessary."
  [x]
  (cond (not (coll? x)) x
        (and (not (vector? x)) (associative? x)) x
        :else (zipmap (range) x)))

(defn hydrate
  "Recover Clojure data from Firebase client library deserialization."
  [v]
  #?(:clj  (walk/prewalk hydrate* v)
     :cljs (walk/postwalk decode-key (js->clj v :keywordize-keys true))))

(defn serialize
  "Prepare Clojure data for Firebase client library serialization.
  Ensures natural associative nesting, and avoidless needless type loss."
  [v]
  #?(:clj   (serialize* v)
     :cljs  (clj->js (serialize* v))))

;; TODO: Seriously consider inlining, or reusing hyrdate (despite overhead)
(defn ensure-kw-map
  "Coerce java.util.HashMap and friends to keywordized maps"
  [data]
  (walk/keywordize-keys (into {} data)))
