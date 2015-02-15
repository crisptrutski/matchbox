(ns pani.async
  (:require [pani.core :as p]
            [cljs.core.async :refer [<! >! chan put! close!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn with-chan
  "Call a function with a fresh channel, then return the channel"
  [f]
  (let [ch (chan)] (f ch) ch))

(defn chan->cb
  "Create callback that pushes non-nil arguments onto given chan"
  [ch]
  (fn [val] (if val (put! ch val))))

(defn chan->cb-once
  "Create callback that pushes arguments onto chan exactly once"
  [ch]
  (fn [val]
    (if val (put! ch val))
    (close! ch)))

;; async

(defn deref< [ref]
  (with-chan #(p/deref ref (chan->cb-once %))))

(defn reset!< [ref val]
  (with-chan #(p/reset! ref val (chan->cb-once %))))

(defn merge!< [ref val]
  (with-chan #(p/merge! ref val (chan->cb-once %))))

(defn conj!< [ref val]
  (with-chan #(p/conj! ref val (chan->cb-once %))))

(defn swap!< [ref f & args]
  (with-chan #(apply p/swap! ref f (into (vec args) [:callback (chan->cb-once %)]))))

(defn dissoc!< [ref]
  (with-chan #(p/remove! ref (chan->cb-once %))))

(def remove!< dissoc!<)

;; async + in

(defn deref-in< [ref korks]
  (deref< (p/get-in ref korks)))

(defn reset-in!< [ref korks val]
  (reset!< (p/get-in ref korks) val))

(defn merge-in!< [ref korks val]
  (merge!< (p/get-in ref korks) val))

(defn conj-in!< [ref korks val]
  (conj!< (p/get-in ref korks) val))

(defn swap-in!< [ref korks f & args]
  (apply swap!< (p/get-in ref korks) f args))

;; watchout - naming exception

(defn dissoc-in!< [ref korks]
  (remove!< (p/get-in ref korks)))

(def remove-in!< dissoc-in!<)

;; subscriptions

;; TODO: unsubscribe all relevant when closing received channel
;;       (or provide another mechanism to plug the leak)

(defn listen-to<
  ([ref type]
   (with-chan #(p/listen-to ref type (chan->cb %))))
  ([ref korks type]
   (listen-to< (p/get-in ref korks) type)))

(defn listen-children<
  ([ref]
   (with-chan #(p/listen-children ref (chan->cb %))))
  ([ref korks]
   (listen-children< (p/get-in ref korks))))
