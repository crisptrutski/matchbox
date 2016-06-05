(ns matchbox.serialization.stable
  (:require
    [clojure.walk :as walk]
    [matchbox.serialization.keyword :as keyword]
    [linked.core :as linked]
    [linked.map])
  (:import
    (java.util HashMap ArrayList)
    (clojure.lang PersistentTreeMap)))

(defn map->linked [m]
  (if (instance? linked.map.LinkedMap m) m (linked.map/->linked-map (map (juxt (comp keyword key) val) m))))

(defn hydrate-shallow [x]
  (cond
    #?@(:clj [(instance? HashMap x) (recur (map->linked x))
              (instance? ArrayList x) (recur (into [] x))])
    (map? x) (map->linked x)
    :else (keyword/hydrate-kw x)))

(defn hydrate [v]
  #?(:clj  (walk/prewalk hydrate-shallow v)
     :cljs (walk/postwalk hydrate-shallow (js->clj v))))

(def serialize keyword/serialize)
