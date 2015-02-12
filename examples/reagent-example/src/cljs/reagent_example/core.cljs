(ns reagent-example.core
  (:require [reagent.core :as reagent :refer [atom]] ;; wrap
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [pani.cljs.core :as pani]
            [goog.events :as events]
            [goog.history.EventType :as EventType]

            [cljs.core.async :as async :refer [<! >! chan put! merge]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import goog.History))

(enable-console-print!)

;; (R)Atom to Pani value binding

(defn- path [ref]
  (clojure.string/replace (.toString ref) #"http.*?firebaseio.com" ""))

(defn bind-to-ratom [ratom ref & [korks]]
  (let [[ch close!] (pani/bind ref :value korks)]
    (go-loop [snapshot (<! ch)]
      (if snapshot
        (do
          (prn "Bind: " (path ref) (:val snapshot))
          (reset! ratom (:val snapshot))
          (recur (<! ch)))
        (close!)))))

;; Note: order still not guaranteed for cljs maps, even array-map :((
;; https://groups.google.com/forum/#!msg/clojurescript/ynOwEgigAaM/1w3FtUKa5RwJ

;; So lets use vectors.

(defn dissoc- [xs k]
  (vec (remove (comp #{k} first) xs)))

(defn assoc- [xs k v]
  (let [p?     (comp #{k} first)
        xss    (partition-by p? xs)
        first? (p? (ffirst xss))
        pre    (if-not first? (first xss))
        post   (first (drop (if first? 1 2) xss))]
    (vec (concat pre [[k v]] post))))

(defn listen-into-ratom [ratom ref & [korks]]
  (let [ch (pani/listen< ref korks)]
    (go-loop [[evt key val :as v] (<! ch)]
      (prn "Listen: " (path ref) v)
      (when v
        (case evt
          :child-added   (swap! ratom assoc- key val)
          :child-changed (swap! ratom assoc- key val)
          :child-removed (swap! ratom dissoc- key)
          :child-moved   nil)
        (recur (<! ch))))))

;; -------------------------
;; Firebase init + wrappers

;; (def firebase-app-url "https://<your app>.firebaseio.com/")
(def firebase-url "https://luminous-torch-5788.firebaseio.com/")

(def fb-root (pani/root firebase-url))

(def fb-counter (pani/walk-root fb-root [:path :to :counter]))

(defn inc-counter! [] (pani/transact! fb-counter [] inc))

(pani/set! fb-counter 1)

;; usually works fine, but for robustness should only proceed once set! acknowledged

(pani/transact! fb-counter [] inc)        ; 1 => 2
(pani/transact! fb-counter [] #(* 2 %))   ; 2 => 4
(pani/transact! fb-counter [] #(* % % %)) ; 4 => 64
(pani/transact! fb-counter [] dec)        ; 64 => 63

(go (prn "Counter starts at: " (<! (pani/value fb-counter))))

(def fb-items (pani/walk-root fb-root [:path :to :items]))

(defn reset-items! []
  (pani/remove! fb-items))

(defn update-item-value! [key f]
  (pani/transact! fb-items [key :value] f))

(defn remove-item! [key]
  (pani/remove! (pani/walk-root fb-items [key])))

(defn create-item []
  {:value (rand-nth (range 10))})

(defn create-item! []
  (pani/push! fb-items (create-item)))

;; (reset-items!)

;; (create-item!)

(go (prn "Initial items: " (map :value (vals (<! (pani/get-in fb-items []))))))

(def counter (atom 0))

(bind-to-ratom counter fb-counter)

(def items (atom []))

(listen-into-ratom items fb-items)

;; -------------------------
;; Views

(defn counter-view []
  [:div
   "Count: "
   @counter
   [:button {:on-click #(inc-counter!)} "+"]])

(defn item-view [key item]
  [:li {:key key}
   (:value item) " "
   [:button {:on-click #(update-item-value! key inc)} "+"] " "
   [:button {:on-click #(remove-item! key)} "delete"]])

(defn items-view [items]
  [:div
   (into [:ul]
         (for [[key item] @items]
           (item-view key item)))
   [:a {:onClick create-item!} "add-item"]
   [:br]
   [:a {:onClick reset-items!} "reset-items"]])

(defn eg-page []
  [:div [:h2 "Welcome to reagent-example"]
   [counter-view]
   [items-view items]
   [:div [:a {:href "#/alt"} "go to alt page"]]])

;; alternate implementation using wraps

(comment
    (defn counter-view-alt [counter]
    [:div
     "Count: "
     @counter
     [:a {:on-click #(swap! counter inc)} "+"]])

  (defn item-view-alt [item-value]
    [:li {:key key}
     @item-value " "
     ;; demonstrates cascading delete in firebase
     [:a {:on-click #(swap! item-value inc)} "+"] " "
     [:a {:on-click #(reset! item-value nil)} "delete"]])

  (defn items-view-alt [items]
    [:div
     (into [:ul]
           (for [[key item] @items]
             (item-view (wrap (:value item) pani/set! items [key :value]))))
     [:a {:onClick create-item!} "add-item"]
     [:br]
     [:a {:onClick reset-items!} "reset-items"]]))

(defn alt-page []
  [:div [:h2 "Alternate reagent-example"]
   [:p "Pending v0.5.0 :)"]
   ;; [counter-view-alt (wrap @counter pani/set! counter)]
   ;; [items-view items]
   [:div [:a {:href "#/"} "go to the regular page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'eg-page))

(secretary/defroute "/alt" []
  (session/put! :current-page #'alt-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn init! []
  (hook-browser-navigation!)
  (reagent/render-component [current-page] (.getElementById js/document "app")))
