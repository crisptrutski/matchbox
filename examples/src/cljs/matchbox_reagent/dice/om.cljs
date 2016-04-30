(ns matchbox-reagent.dice.om
  (:require
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [sablono.core :as html :refer-macros [html]]
    [matchbox.core :as m]
    [matchbox.atom :as matom]
    [matchbox-reagent.dice.core :as dice]
    [matchbox-reagent.common :refer [get-ref ordered-list]]))

;; state

(def app-state (atom {:counter 0, :rolls {}}))

(matom/sync-rw-in app-state [:counter] (get-ref :counter))
(matom/sync-rw-in app-state [:rolls] (get-ref :rolls))
(matom/sync-list-in
  app-state [:top-values]
  (-> (get-ref :rolls)
      (m/order-by-value)
      (m/take-last 5))
  (comp vec reverse))

;; actions

(defn roll! [rolls]
  (om/transact! rolls #(merge {(dice/new-key) (dice/roll)} %)))

;; views

(defn counter-view [app owner]
  (om/component
    (html
      (dice/counter-view
        (:counter app)
        #(om/transact! app :counter inc)))))

(defn roller-view [rolls owner]
  (om/component
    (html
      (dice/roller-view
        :roll! #(roll! rolls)
        :clear! #(om/update! rolls nil)))))

(defn top-view [top-values owner]
  (om/component
    (html
      (dice/top-view top-values))))

(defn roll-view [[roll parent key] owner]
  (om/component
    (html
      (dice/roll-view
        roll
        :rotate! #(om/transact! parent key dice/inc6)
        :delete! #(om/update! parent key nil)))))

(defn rolls-view [rolls owner]
  (om/component
    (html
      [:div
       (ordered-list
         (for [[key item] (sort rolls)]
           [:li
            (om/build roll-view [item rolls key])]))])))

(defn page [app owner {key :secret-key}]
  (om/component
    (html
      [:div {:style {:background "#efe"}}
       [:div {:key key}
        [:h3 "[Om] Counter"]
        (om/build counter-view app)
        [:h3 "Top 5 Query"]
        (om/build roller-view (:rolls app))
        (om/build top-view (:top-values app))
        (om/build rolls-view (:rolls app))]])))

;;

(defn init [id]
  (let [elem (. js/document (getElementById (name id)))]
    (om/root page app-state {:target elem, :opts {:secret-key id}})))
