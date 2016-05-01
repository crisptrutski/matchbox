(ns matchbox.reagent
  (:refer-clojure :exclude [atom])
  (:require [matchbox.core :as m]
            [matchbox.atom :as matom]
            [reagent.core]))

(defn- with-ratom [value f & args]
  (let [a (reagent.core/atom value)]
    (apply f a args)
    a))

(defn sync-r
  [query & [xform]]
  (with-ratom nil matom/sync-r query xform))

(defn sync-list
  [query & [xform]]
  (with-ratom nil matom/sync-list query xform))

(defn sync-rw
  [ref & [value]]
  (with-ratom value matom/sync-rw ref))

(defn cursor
  "Less efficient reagent.core/cursor but works with more general data, like reactions."
  [atomlike path]
  (reagent.core/wrap (get-in @atomlike path)
                     swap! atomlike assoc-in path))
