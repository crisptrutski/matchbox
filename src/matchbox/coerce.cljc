(ns matchbox.coerce
  (:require [clojure.walk :as walk]))

(defn- encode-key [x]
  (if (keyword? x) (str x) x))

(defn- decode-key [x]
  (if (and (string? x) (= \: (first x)))
    (keyword (subs x 1))
    x))

(defn- map-keys [f hsh]
  (reduce-kv #(assoc %1 (f %2) %3) (empty hsh) hsh))

#?(:clj
   (defn- hydrate* [x]
     (cond (instance? java.util.HashMap x)   (recur (into {} x))
           (instance? java.util.ArrayList x) (recur (into [] x))
           (map? x)                          (map-keys keyword x)
           :else                             (decode-key x))))

(defn- serialize* [v]
  (walk/postwalk
   (fn [x]
     (if (map? x)
       ;; assumes not namespaced.. or numeric
       (map-keys name x)
       (encode-key x)))
   v))

;; public

(defn hydrate [v]
  ;; prewalk is less efficient, but needed since JVM may need to coerce
  ;; into clojure containers (but should double check that walk does not
  ;; walk java collections..)
  #?(:clj (walk/prewalk hydrate* v)
     :cljs (walk/postwalk decode-key (js->clj v :keywordize-keys true))))

(defn serialize [v]
  #?(:clj   (serialize* v)
     :cljs  (clj->js (serialize* v))))

;; this seems to just be a special subcase of hydrate*
;; i guess it's faster.. and not that long.. but hmmm
(defn ensure-kw-map
  "Coerce java.util.HashMap and friends to keywordized maps"
  [data]
  (walk/keywordize-keys (into {} data)))


;; this stuff is "inner loop", speaking loosely. i guess it's ok get so WET and fiddly
