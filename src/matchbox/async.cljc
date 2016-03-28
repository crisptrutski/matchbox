(ns matchbox.async
  (:require [matchbox.core :as m]
            [#?(:clj clojure.core.async
                :cljs cljs.core.async)
             :refer [<! >! chan put! close!]]))

(defn with-chan
  "Call a function with a fresh channel, then return the channel"
  [f]
  (let [ch (chan)] (f ch) ch))

(defn chan->cb
  "Create callback that pushes non-nil arguments onto given chan"
  [ch]
  (fn [val] (when val (put! ch val))))

(defn chan->cb-once
  "Create callback that pushes arguments onto chan at-most once"
  [ch]
  (fn [val]
    (when val (put! ch val))
    (close! ch)))

(defn chan->auth-cb
  "Creates a callback to push [err, value] arguments onto a chan, exactly once"
  [ch]
  (fn [err val]
    (put! ch [err val])
    (close! ch)))

;; auth

(defn auth< [ref email password & [session-only?]]
  (with-chan #(m/auth ref email password (chan->auth-cb %) session-only?)))

(defn auth-anon< [ref & [session-only?]]
  (with-chan #(m/auth-anon ref (chan->auth-cb %) session-only?)))

(defn auth-custom<
  "Authenticates a Firebase client using an authentication token or Firebase Secret."
  ([ref secret]
   (with-chan #(m/auth-custom ref secret (chan->auth-cb %))))
  #?(:cljs
      ([ref secret session-only?]
       (with-chan #(m/auth-custom ref secret (chan->auth-cb %) session-only?)))))

;; users

(defn create-user< [ref email password]
  (with-chan #(m/create-user ref email password (chan->cb-once %))))

;; async

(defn deref< [ref]
  (with-chan #(m/deref ref (chan->cb-once %))))

(defn deref-list< [ref]
  (with-chan #(m/deref-list ref (chan->cb-once %))))

(defn reset!< [ref val]
  (with-chan #(m/reset! ref val (chan->cb-once %))))

(defn reset-with-priority!< [ref val priority]
  (with-chan #(m/reset-with-priority! ref val priority (chan->cb-once %))))

(defn merge!< [ref val]
  (with-chan #(m/merge! ref val (chan->cb-once %))))

(defn conj!< [ref val]
  (with-chan #(m/conj! ref val (chan->cb-once %))))

(defn swap!< [ref f & args]
  (assert (every? #(not= :callback %) args))
  (with-chan #(apply m/swap! ref f (into (vec args) [:callback (chan->cb-once %)]))))

(defn dissoc!< [ref]
  (with-chan #(m/remove! ref (chan->cb-once %))))

(def remove!< dissoc!<)

(defn set-priority!< [ref priority]
  (with-chan #(m/set-priority! ref priority (chan->cb-once %))))

(defn export< [ref-or-ds]
  (with-chan #(m/export ref-or-ds (chan->cb-once %))))

(defn priority< [ref-or-ds]
  (with-chan #(m/priority ref-or-ds (chan->cb-once %))))

;; async + in

(defn deref-in< [ref korks]
  (deref< (m/get-in ref korks)))

(defn deref-list-in< [ref korks]
  (deref-list< (m/get-in ref korks)))

(defn reset-in!< [ref korks val]
  (reset!< (m/get-in ref korks) val))

(defn reset-with-priority-in!< [ref korks val priority]
  (reset-with-priority!< (m/get-in ref korks) val priority))

(defn merge-in!< [ref korks val]
  (merge!< (m/get-in ref korks) val))

(defn conj-in!< [ref korks val]
  (conj!< (m/get-in ref korks) val))

(defn swap-in!< [ref korks f & args]
  (apply swap!< (m/get-in ref korks) f args))

;; watchout - naming exception

(defn dissoc-in!< [ref korks]
  (remove!< (m/get-in ref korks)))

(def remove-in!< dissoc-in!<)

(defn set-priority-in!< [ref korks priority]
  (set-priority!< (get-in ref korks) priority))

(defn export-in< [ref-or-ds korks]
  (with-chan #(m/export-in ref-or-ds korks (chan->cb-once %))))

(defn priority-in< [ref-or-ds korks]
  (with-chan #(m/priority-in ref-or-ds korks (chan->cb-once %))))

;; subscriptions

;; TODO: unsubscribe all relevant when closing received channel
;;       (or provide another mechanism to plug the leak)

(defn listen-to<
  ([ref type]
   (with-chan #(m/listen-to ref type (chan->cb %))))
  ([ref korks type]
   (listen-to< (m/get-in ref korks) type)))

(defn listen-list<
  ([ref]
   (with-chan #(m/listen-list ref (chan->cb %))))
  ([ref korks]
   (listen-list< (m/get-in ref korks))))

(defn listen-children<
  ([ref]
   (with-chan #(m/listen-children ref (chan->cb %))))
  ([ref korks]
   (listen-children< (m/get-in ref korks))))
