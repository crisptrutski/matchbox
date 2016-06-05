(ns matchbox.serialization.stable
  (:require
    [clojure.walk :as walk]
    [matchbox.serialization.keyword :as keyword]
    [linked.map]
    [matchbox.utils :as utils])
  #?(:clj (:import (java.util HashMap ArrayList))))

(defn map->linked [m]
  (if (instance? linked.map.LinkedMap m) m (linked.map/->linked-map (map (juxt (comp keyword key) val) m))))

(defn hydrate-shallow [x]
  (cond
    #?@(:clj [(instance? HashMap x) (recur (map->linked x))
              (instance? ArrayList x) (recur (into [] x))])
    (map? x) (map->linked x)
    #?@(:cljs [(array? x) (vec x)
               (identical? js/Object (type x)) (linked.map/->linked-map (for [k (js-keys x)] [(keyword k) (aget x k)]))])
    :else (keyword/hydrate-kw x)))

(defn hydrate [v] (walk/prewalk hydrate-shallow v))

(def serialize keyword/serialize)

(defn set-default! []
  (utils/set-date-config! hydrate serialize))
