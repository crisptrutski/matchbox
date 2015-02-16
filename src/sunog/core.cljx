(ns sunog.core
  (:refer-clojure :exclude [get-in set! reset! conj! swap! dissoc! deref parents key])
  (:require [clojure.string :as str]
            [sunog.impl :as impl]))

;; constants

(def child-events
  [:child-added
   :child-changed
   :child-moved
   :child-removed])

(def all-events
  (conj child-events :value))

(def SERVER_TIMESTAMP impl/SERVER_TIMESTAMP)

;; FIXME: camel-case keys?
;;        hydrate to custom vectors to preserve rich keys?
;;        preserve sets (don't coerce to vector)
;;        similarly preserve keywords as values

(def hydrate impl/hydrate)

(def serialize impl/serialize)

(def key impl/key)

(def value impl/value)

(defn- wrap-snapshot [snapshot]
  ;; TODO: enhance with snapshot protocol
  [(key snapshot) (value snapshot)])

;; references

(def connect impl/connect)

(def get-in impl/get-in)

(def parent impl/parent)

(defn parents
  "Probably don't need this. Or maybe we want more zipper nav (siblings, in-order, etc)"
  [ref]
  (take-while identity (iterate parent (parent ref))))

;;

(defonce connected (atom true))

(defn disconnect! []
  #+cljs (.goOffline js/Firebase)
  (clojure.core/reset! connected false))

(defn reconnect! []
  #+cljs (.goOnline js/Firebase)
  (clojure.core/reset! connected true))

(defn check-connected?
  "Returns boolean around whether client is set to synchronise with server.
   Says nothing about actual connectivity."
  []
  @connected)

;; FIXME: find a better abstraction
;; https://github.com/crisptrutski/sunog/issues/4

(defn on-disconnect
  "Return an on"
  [ref]
  #+cljs (.onDisconnect ref))

(defn cancel [ref-disconnect & [cb]]
  #+cljs (.cancel ref-disconnect (or cb impl/undefined)))

;; --------------------
;; auth

(defn build-opts [session-only?]
  #+cljs (if session-only?
           #js {:remember "sessionOnly"}
           impl/undefined))

(defn- wrap-auth-cb [cb]
  #+cljs (if cb
           (fn [err info]
             (cb err (hydrate info)))
           impl/undefined))

(defn auth [ref email password & [cb session-only?]]
  #+cljs (.authWithPassword
          ref
          #js {:email email, :password password}
          (wrap-auth-cb cb)
          (build-opts session-only?)))

(defn auth-anon [ref & [cb session-only?]]
  #+cljs (.authAnonymously
          ref
          (wrap-auth-cb cb)
          (build-opts session-only?)))

(defn auth-info
  "Returns a map of uid, provider, token, expires - or nil if there is no session"
  [ref]
  #+cljs (hydrate (.getAuth ref)))

;; onAuth and offAuth are not wrapped yet

(defn unauth [ref]
  #+cljs (.unauth ref))

;; --------------------
;; getters 'n setters

(def deref impl/deref)

(def reset! impl/reset!)

(defn reset-with-priority! [ref val priority & [cb]]
  #+cljs (.setWithPriority ref (serialize val) priority (or cb impl/undefined)))

(def merge! impl/merge!)

(def conj! impl/conj!)

(def swap! impl/swap!)

(def dissoc! impl/dissoc!)

(def remove! dissoc!)

(defn set-priority! [ref priority & [cb]]
  #+cljs (.setPriority ref priority (or cb impl/undefined)))

;; nested variants

(defn deref-in [ref korks & [cb]]
  (deref (get-in ref korks) cb))

(defn reset-in! [ref korks val & [cb]]
  (reset! (get-in ref korks) val cb))

(defn reset-with-priority-in! [ref korks val priority & [cb]]
  (reset-with-priority! (get-in ref korks) val priority cb))

(defn merge-in! [ref korks val & [cb]]
  (merge! (get-in ref korks) val cb))

(defn conj-in! [ref korks val & [cb]]
  (conj! (get-in ref korks) val cb))

(defn swap-in! [ref korks f & [cb]]
  (swap! (get-in ref korks) f cb))

(defn dissoc-in! [ref korks & [cb]]
  (dissoc! (get-in ref korks) cb))

(def remove-in! remove-in!)

(defn set-priority-in! [ref korks priority & [cb]]
  (set-priority! (get-in ref korks) priority cb))

;; ------------------
;; subscriptions

(defn listen-to
  ([ref type cb] (impl/listen-to ref type cb))
  ([ref korks type cb] (listen-to (get-in ref korks) type cb)))

(defn listen-children
  ([ref cb] (impl/listen-children ref cb))
  ([ref korks cb] (listen-children (get-in ref korks) cb)))
