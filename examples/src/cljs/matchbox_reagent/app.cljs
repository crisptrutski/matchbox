(ns matchbox-reagent.app
  (:require
    [reagent.core :as reagent]
    [matchbox-reagent.chat.reagent :as re-chat]
    [matchbox-reagent.dice.reagent :as re-dice]
    [matchbox-reagent.dice.om :as om-dice]))

(def demos
  {"Chat (reagent)" re-chat/init
   "Dice (reagent)" re-dice/init
   "Dice (om)"      om-dice/init})

(def last-id (atom "container"))

(defn init-demo [init-fn]
  ;; replace container as framework agnostic way to kill demo app
  (let [old (.getElementById js/document @last-id)
        id# (name (gensym))
        new (.createElement js/document "div")]
    (reset! last-id id#)
    (when old
      (.remove old)
      (.setAttribute old "style" "display:none"))
    (.setAttribute new "id" id#)
    (.appendChild (.-body js/document) new)
    ;; ready to attack demo
    (init-fn id#)))

(defn link [label init-fn]
  [:a {:on-click #(init-demo init-fn)} label])

(defn page []
  [:div
   [:h3 "Demos"]
   (into [:ul]
         (for [[label init-fn] demos]
           [:li [link label init-fn]]))])

;;

(defn init []
  (let [elem (.getElementById js/document "selector")]
    (reagent/render-component [page] elem)))
