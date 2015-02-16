(ns sunog.common)

(def child-events
  [:child-added
   :child-changed
   :child-moved
   :child-removed])

(def all-events
  (conj child-events :value))
