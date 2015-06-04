(ns matchbox-reagent.dice.reagent
  (:require [reagent.core :as reagent :refer [atom wrap]]
            [matchbox.core :as m]
            [matchbox.atom :as matom]
            [matchbox.reagent :as r]
            [matchbox-reagent.dice.core :as dice]
            [matchbox-reagent.common :refer [get-ref ordered-list]]))

;; state

(def counter    (r/sync-rw (get-ref :counter)))
(def rolls      (r/sync-rw (get-ref :rolls)))
(def top-values (r/sync-list (-> (get-ref :rolls)
                                 (m/order-by-value)
                                 (m/take-last 5))
                             reverse))

;; actions

(defn roll! [rolls]
  (swap! rolls #(merge {(dice/new-key) (dice/roll)} %)))

;; views

(defn counter-view [counter]
  (dice/counter-view @counter #(swap! counter (fnil inc 0))))

(defn roller [rolls]
  (dice/roller-view :roll!  #(roll! rolls)
                    :clear! #(reset! rolls nil)))

(defn top-view [top-values]
  (dice/top-view @top-values))

(defn roll-view [roll]
  (dice/roll-view @roll
                  :rotate! #(swap! roll dice/inc6)
                  :delete! #(reset! roll nil)))

(defn rolls-view [rolls]
  [:div
   (ordered-list
    (for [[key item] (sort @rolls)]
      ^{:key key}
      [:li [roll-view (r/cursor rolls [key])]]))])

(defn page [id]
  [:div {:style {:background "#eef"}}
   ;; unique key to disambiguate for react
   ^{:key id}
   [:div
    [:h3 "[Reagent] Counter"]
    [counter-view counter]
    [:h3 "Top 5 Query"]
    [roller rolls]
    [top-view top-values]
    [rolls-view rolls]]])

;;

(defn init [id]
  (let [elem (.getElementById js/document id)]
    (reagent/render-component [page id] elem)
    (reagent/force-update-all)))
