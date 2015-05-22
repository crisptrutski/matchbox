(ns matchbox-reagent.dice.core
  (:require [matchbox-reagent.common :refer [space pipe]]))

;; data

(defn new-key [] (keyword (str (.getTime (js/Date.)) (rand-int 100000))))

(defn roll [] (rand-int 6))

(def inc6 (comp #(mod % 6) inc))

;; views

(defn counter-view [value inc!]
  (space "Count:"
         value
         [:a {:on-click inc!} "⬆"]))

(defn roller-view[& {:keys [roll! clear!]}]
  (pipe
    [:a {:on-click roll!}  "Roll"]
    [:a {:on-click clear!} "Clear"]))

(defn die-face [n]
  (nth ["⚀" "⚁" "⚂" "⚃" "⚄" "⚅"] n))

(defn top-view [top-values]
  [:p (if-let [xs (seq top-values)]
        (apply str "Best 5: " (interpose " " (map die-face xs)))
        "No rolls yet.")])

(defn roll-view [roll & {:keys [rotate! delete!]}]
  (space (die-face roll)
         [:small [:a {:on-click rotate!} "↻"]]
         [:small [:a {:on-click delete!} "⨉"]]))
