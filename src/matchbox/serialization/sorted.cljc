(ns matchbox.serialization.sorted
  (:require
    [clojure.walk :as walk]
    [matchbox.serialization.keyword :as keyword]
    [matchbox.utils :as utils])
  (:import
    (java.util HashMap ArrayList)
    (clojure.lang PersistentTreeMap)))

(defn map->sorted [m]
  (if (instance? PersistentTreeMap m) m (into (sorted-map) (map (juxt (comp keyword key) val) m))))

(defn hydrate-shallow [x]
  (cond
    #?@(:clj [(instance? HashMap x) (recur (map->sorted x))
              (instance? ArrayList x) (recur (into [] x))])
    (map? x) (map->sorted x)
    :else (keyword/hydrate-kw x)))

(defn hydrate [v]
  #?(:clj  (walk/prewalk hydrate-shallow v)
     :cljs (walk/postwalk hydrate-shallow (js->clj v))))

(def serialize keyword/serialize)

(defn set-default! []
  (utils/set-date-config! hydrate serialize))
