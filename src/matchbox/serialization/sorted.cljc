(ns matchbox.serialization.sorted
  (:require
    [clojure.walk :as walk]
    [matchbox.serialization.keyword :as keyword]
    [matchbox.utils :as utils])
  #?(:clj (:import (java.util HashMap ArrayList))))

(defn map->sorted [m]
  (if (sorted? m) m (into (sorted-map) (map (juxt (comp keyword key) val) m))))

(defn hydrate-shallow [x]
  (cond
    #?@(:clj [(instance? HashMap x) (recur (map->sorted x))
              (instance? ArrayList x) (recur (into [] x))])
    (map? x) (map->sorted x)
    #?@(:cljs [(array? x) (vec x)
               (identical? js/Object (type x)) (into (sorted-map) (for [k (js-keys x)] [(keyword k) (aget x k)]))])
    :else (keyword/hydrate-kw x)))

(defn hydrate [v] (walk/prewalk hydrate-shallow v))

(def serialize keyword/serialize)

(defn set-default! []
  (utils/set-date-config! hydrate serialize))
