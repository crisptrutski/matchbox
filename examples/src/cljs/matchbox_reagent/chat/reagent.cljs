(ns matchbox-reagent.chat.reagent
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [cljs.core :as cljs]
            [reagent.core :as reagent :refer [atom]]
            [matchbox.core :as m]
            [matchbox.reagent :as r]
            [matchbox-reagent.common :refer [get-ref ->v enter? ordered-list]]))

;; state

(def user         (atom (str "Anonymous " (rand-int 1000))))
(def channels     (atom ["Clojure" "ClojureScript" "Firebase"]))
(def current-chan (atom (first @channels)))

(def messages-ref (get-ref :messages))
(def message-subs (atom {}))

(defn sub-channel [chan]
  (r/sync-list (-> (m/get-in messages-ref chan)
                   (m/take-last 10))))

(defn read-channel [chan]
  (or (@message-subs chan)
      (let [sub (sub-channel chan)]
        (swap! message-subs assoc chan sub)
        sub)))

(def messages
  (reaction
   (when-let [c @current-chan]
     @(read-channel c))))

;; actions

(defn set-user! [new-user] (reset! user new-user))

(defn set-channel! [chan] (reset! current-chan chan))

(defn post-msg!    [chan msg]
  (let [user-name @user
        user-name (if (seq user-name) user-name "Anon")
        msg       (if (seq msg) msg "*mumble*")]
    (m/conj-in! messages-ref chan
                [user-name msg])))

;; views

(defn name-view []
  [:input {:value @user, :on-change (comp set-user! ->v)}])

(defn channels-view []
  [:ol
   (for [chan @channels]
     ^{:key chan}
     [:li [:a {:on-click #(set-channel! chan)} chan]])])

(defn message-view [user msg]
  [:span [:b user] " " msg])

(defn messages-view []
  (ordered-list
    (for [[user msg] @messages]
      [:li (message-view user msg)])))

(defn post-view []
  (let [msg   (cljs/atom "")
        post! #(post-msg! @current-chan @msg)]
    (fn []
      [:div
       [:input {:on-change   #(reset! msg (->v %))
                :on-key-down #(when (enter? %) (post!))}] " "
       [:a.btn.btn-default {:on-click post!} "post"]])))

(defn page []
  ^{:key 'reagent-chat}
  [:div
   [:h2 "Fireside Chat"]
   [:h3 "Channels"]
   [channels-view]
   [:hr]
   [:h3 @current-chan " Messages"]
   [name-view]
   [post-view]
   [messages-view]])

;;

(defn init [id]
  (let [elem (.getElementById js/document id)]
    (reagent/render-component [page] elem)))
