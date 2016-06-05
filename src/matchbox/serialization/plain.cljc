(ns matchbox.serialization.plain
  (:require
    [clojure.walk :as walk]
    [matchbox.utils :as utils])
  (:import
    (java.util HashMap ArrayList)))

(defn hydrate-raw [x]
  #?(:cljs x
     :clj
     (cond
       (instance? HashMap x) (recur (into {} x))
       (instance? ArrayList x) (recur (into [] x))
       :else x)))

(defn hydrate [v]
  #?(:clj  (walk/prewalk hydrate-raw v)
     :cljs (js->clj v)))

(defn serialize [v]
  #?(:clj (walk/stringify-keys v)
     :cljs (clj->js v)))

(defn set-default! []
  (utils/set-date-config! hydrate serialize))
