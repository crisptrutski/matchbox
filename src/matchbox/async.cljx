(ns matchbox.async
  (:require [matchbox.core :as p]
            #+clj [clojure.core.async :refer [<! >! chan put! close!]]
            #+cljs [cljs.core.async :refer [<! >! chan put! close!]]))

(defn with-chan
  "Call a function with a fresh channel, then return the channel"
  [f]
  (let [ch (chan)] (f ch) ch))

(defn chan->cb
  "Create callback that pushes non-nil arguments onto given chan"
  [ch]
  (fn [val] (if val (put! ch val))))

(defn chan->cb-once
  "Create callback that pushes arguments onto chan at-most once"
  [ch]
  (fn [val]
    (if val (put! ch val))
    (close! ch)))

(defn chan->auth-cb
  "Creates a callback to push [err, value] arguments onto a chan, exactly once"
  [ch]
  (fn [err val]
    (put! ch [err val])
    (close! ch)))

;; auth

(defn auth< [ref email password & [session-only?]]
  (with-chan #(p/auth ref email password (chan->auth-cb %) session-only?)))

(defn auth-anon< [ref & [session-only?]]
  (with-chan #(p/auth-anon ref (chan->auth-cb %) session-only?)))

;; async

(defn deref< [ref]
  (with-chan #(p/deref ref (chan->cb-once %))))

(defn deref-list< [ref]
  (with-chan #(p/deref-list ref (chan->cb-once %))))

(defn reset!< [ref val]
  (with-chan #(p/reset! ref val (chan->cb-once %))))

(defn reset-with-priority!< [ref val priority]
  (with-chan #(p/reset-with-priority! ref val priority (chan->cb-once %))))

(defn merge!< [ref val]
  (with-chan #(p/merge! ref val (chan->cb-once %))))

(defn conj!< [ref val]
  (with-chan #(p/conj! ref val (chan->cb-once %))))

(defn swap!< [ref f & args]
  (assert (every? #(not= :callback %)) args)
  (with-chan #(apply p/swap! ref f (into (vec args) [:callback (chan->cb-once %)]))))

(defn dissoc!< [ref]
  (with-chan #(p/remove! ref (chan->cb-once %))))

(def remove!< dissoc!<)

(defn set-priority!< [ref priority]
  (with-chan #(p/set-priority! ref priority (chan->cb-once %))))

;; async + in

(defn deref-in< [ref korks]
  (deref< (p/get-in ref korks)))

(defn deref-list-in< [ref korks]
  (deref-list< (p/get-in ref korks)))

(defn reset-in!< [ref korks val]
  (reset!< (p/get-in ref korks) val))

(defn reset-with-priority-in!< [ref korks val priority]
  (reset-with-priority!< (p/get-in ref korks) val priority))

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

(defn set-priority-in!< [ref korks priority]
  (set-priority!< (get-in ref korks) priority))

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
