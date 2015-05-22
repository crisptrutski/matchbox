(ns matchbox-reagent.common
  (:require [matchbox.core :as m]))

(enable-console-print!)

;; firebase

(def base-uri "https://luminous-torch-5788.firebaseio.com/")

(defn get-ref [korks]
  (m/connect base-uri korks))

;; events

(defn enter? [e] (= 13 (.-keyCode e)))

(defn ->v [e] (.. e -target -value))

;; views

(defn space [& components]
  (into [:div] (interpose " " components)))

(defn pipe [& components]
  (into [:div] (interpose " | " components)))

(defn ordered-list [xs]
  (into [:ol] xs))
