(ns examples.cljs.om-value-changes.core
  (:require [pani.cljs.core :as pani]
            [cljs.core.async :refer [<!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;; TODO: Set this to a firebase app URL
(def firebase-app-url "https://<your app>.firebaseio.com/")

(enable-console-print!)

(def app-state (atom {:count 0}))

;;; state modifiers

(defn set-item-fn [{:keys [name val]}]
  (fn [items]
    (let [items (or items (sorted-map))]
      (assoc items name val))))

(defn remove-item-fn [{:keys [name]}]
  #(dissoc % name))

;;; event handlers

(defn inc-counter-handler [app r]
  (om/transact! app :count inc)
  (pani/transact! r :counter inc))

(defn add-item-handler [app r]
  (let [v {:value (js/Math.random)}]
    (pani/push! r :items v)))

(defn mutate-item-handler [app r name]
  (let [v {:value (js/Math.random)}]
    (pani/set! r [:items name] v)))

(defn del-items-handler [app r]
  (om/update! app :items (sorted-map))
  (pani/set! r :items nil))

;;; om components

(defn counter-view [app owner {:keys [r]}]
  (om/component
    (dom/div nil
     (:count app) " "
     (dom/a #js {:onClick (partial inc-counter-handler app r)} "+"))))

(defn item-view [[_ item] owner]
  (om/component
    (dom/li nil (:value item))))

(defn items-view [app owner {:keys [r]}]
  (om/component
    (dom/div nil
     (apply dom/ul nil (om/build-all item-view (:items app) {:key :value}))
     (dom/a #js {:onClick (partial add-item-handler app r)} "add item") (dom/br nil)
     (dom/a #js {:onClick (partial del-items-handler app r)} "reset items"))))

(defn app-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [r (pani/root firebase-app-url)
            c (pani/bind r :value :counter)]
        ;; wire up counter, via channel
        (go-loop [{:keys [val] :as v} (<! c)]
                 (om/transact! app :count #(max % val))
                 (recur (<! c)))
        ;; wire up items, via callbacks
        (pani/bind r :child_added   :items #(om/transact! app :items (set-item-fn %)))
        (pani/bind r :child_changed :items #(om/transact! app :items (set-item-fn %)))
        (pani/bind r :child_removed :items #(om/transact! app :items (remove-item-fn %)))
        (om/set-state! owner :fb-root r)))

    om/IRender
    (render [_]
      (let [r (om/get-state owner :fb-root)]
        (dom/div nil
         (om/build counter-view app {:opts {:r r}})
         (om/build items-view app {:opts {:r r}}))))))

(om/root app-view app-state
         {:target (.getElementById js/document "app")})