(ns matchbox.serialization.keyword
  (:require
    [matchbox.serialization.plain :as plain]
    [clojure.walk :as walk]))

(defn hydrate-kw [x]
  (if (and (string? x) (= \: (first x))) (keyword (subs x 1)) x))

(defn hydrate-map [x]
  (if (map? x) (into (empty x) (map (fn [[k v]] [(keyword k) v]) x)) x))

(defn hydrate [v]
  #?(:clj (walk/prewalk (comp hydrate-kw hydrate-map plain/hydrate-raw) v)
     :cljs (walk/postwalk (comp hydrate-kw hydrate-map) (js->clj v :keywordize-keys true))))

(defn kw->str [x] (if (keyword? x) (str x) x))

(defn serialize [v]
  (->> (walk/stringify-keys v)
       (walk/postwalk kw->str)
       #?(:cljs clj->js)))
