(ns examples.cljs.om-value-changes.core
  (:require [pani.core :as pani]
            [cljs.core.async :refer [<!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;; TODO: Set this to a firebase app URL
(def firebase-app-url "https://blazing-fire-1915.firebaseio.com/")

(enable-console-print!)

(def app-state (atom {:count 0}))

(defn counter-view [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [r (pani/root firebase-app-url)
            c (pani/bind r :value :counter)]
        (go-loop [m (<! c)]
          (om/update! app :count m)
          (recur (<! c)))))

    om/IRender
    (render [_]
      (dom/div nil (:count app)))))

(om/root counter-view app-state
         {:target (.getElementById js/document "app")})
