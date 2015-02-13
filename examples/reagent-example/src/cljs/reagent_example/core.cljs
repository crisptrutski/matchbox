(ns reagent-example.core
  (:require [clojure.string :as str]
            [reagent.core :as reagent :refer [atom]] ;; wrap
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]

            [pani.core :as p]
            [pani.async :as pa]
            [cljs.core.async :as async :refer [<! >! chan put! merge]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import goog.History))

(enable-console-print!)

;; (R)Atom to Pani value binding

(defn- path [ref]
  (str/replace (.toString ref) #"http.*?firebaseio.com" ""))

;; FIXME: does not unsubscribe
(defn bind-to-ratom [ratom ref & [korks]]
  (let [ch (pa/listen-to< ref korks :value)]
    (go-loop [[key val] (<! ch)]
      (if val
        (do
          (prn "Bind: " (path ref) val)
          (reset! ratom val)
          (recur (<! ch)))))))

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
  (let [ch (pa/listen-children< ref korks)]
    (go-loop [[evt [key val]] (<! ch)]
      (prn (str "Listen: " (path ref) evt ", " key " => " val))
      (when evt
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

(def fb-root (p/connect firebase-url))

(def fb-counter (p/get-in fb-root [:path :to :counter]))

(defn reset-counter! [] (p/reset! fb-counter 1))

(defn inc-counter! [] (p/swap! fb-counter inc))

;; await ackownledgment of previous message.
#_(go
  (<! (pa/reset!< fb-counter 1))
  (<! (pa/swap!< fb-counter inc))        ; 1 => 2
  (<! (pa/swap!< fb-counter #(* 2 %)))   ; 2 => 4
  (<! (pa/swap!< fb-counter #(* % % %))) ; 4 => 64
  (<! (pa/swap!< fb-counter dec)))       ; 64 => 63

;; much more responsive, since all compose locally first.
(do
  (p/reset! fb-counter 1)
  (p/swap! fb-counter inc)              ; 1 => 2
  (p/swap! fb-counter #(* 2 %))         ; 2 => 4
  (p/swap! fb-counter #(* % % %))       ; 4 => 64
  (p/swap! fb-counter dec))             ; 64 => 63

(go (prn "Counter starts at: " (<! (pa/deref< fb-counter))))

(def fb-items (p/get-in fb-root [:path :to :items]))

(defn reset-items! []
  (p/dissoc! fb-items))

(defn update-item-value! [key f]
  (p/swap-in! fb-items [key :value] f))

(defn remove-item! [key]
  (p/dissoc-in! fb-items [key]))

(defn create-item []
  {:value (rand-nth (range 10))})

(defn create-item! []
  (p/conj! fb-items (create-item)))

;; (reset-items!)

;; (create-item!)

(go (prn "Initial items: " (map :value (vals (<! (pa/deref< fb-items))))))

(def counter (atom 0))

(bind-to-ratom counter fb-counter)

(def items (atom []))

(listen-into-ratom items fb-items)

;; -------------------------
;; Views

(defn counter-view []
  [:div
   "Count: "
   @counter " "
   [:button {:on-click #(inc-counter!)} "+"] " "
   [:button {:on-click #(reset-counter!)} "reset"]])

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
             (item-view (wrap (:value item) p/reset-in! items [key :value]))))
     [:a {:onClick create-item!} "add-item"]
     [:br]
     [:a {:onClick reset-items!} "reset-items"]]))

(defn alt-page []
  [:div [:h2 "Alternate reagent-example"]
   [:p "Pending v0.5.0 :)"]
   ;; [counter-view-alt (wrap @counter p/reset! counter)]
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
