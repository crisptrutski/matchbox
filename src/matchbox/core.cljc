(ns matchbox.core
  (:refer-clojure :exclude [get-in ref key])
  #?(:cljs (:require [cljsjs.firebase]))
  #?(:clj (:import [com.firebase.client Firebase Query])))

#?(:cljs (def Firebase js/Firebase))

#?(:clj
   (defn normalize-url [url]
     (if (re-find #"\w+://" url) url (str "https://" url))))

(defn ref [url]
  (Firebase. #?(:cljs url :clj (normalize-url url))))

;; TODO: docstrings
(defprotocol ITraversible
  (key    [_])
  (root   [_])
  (parent [_])
  (get    [_ key])
  (get-in [_ path]))

;; TODO: interop here
(extend-type Firebase
  ITraversible
  (key    [ref]      (.getKey ref))
  (root   [ref]      (.getRoot ref))
  (parent [ref]      (.getParent ref))
  (get    [ref key]  (.child ref key))
  (get-in [ref path] (reduce get ref path)))

;; + we can get equality
;; + we can coerce back from queries, snapshots. also urls (good idea?)
;; + no extra functions required.. (but which ones would be used - not that many)
;; - java requires a wrapper class (to implement clojure interfaces)
;; - can't use this same interface for other traversible things (eg snapshots)
;; - tighter coupling to platforms (and their current implmentation)


;; mutation
;; ========

(defprotocol IWriteable
  (set          [_ value]
                [_ value priority])
  (set-priority [_ priority])
  (merge        [_ hsh])
  (conj         [_ child])
  (remove       [_ key])
  (delete       [_])
  (swap         [_ f args])
  (swap-remote  [_ f args]))

(defprotocol IReadable
  (as-js       [_]) ;; sometime raw is best (eg. large, fast changing lists)
  (as-default  [_]) ;; sometimes you are OK with the duck typing
  (as-map      [_]) ;; sometimes you're not OK with the duck typing
  (as-seq      [_]) ;; not sure if this should be [k v] or [v]
  (as-vec      [_]) ;; sometimes you want to force the ducks hand
  (as-snapshot [_]) ;; - here you go twiggy -
  (export      [_])
  (priority    [_]))

;; reads
;; =====
;; . raw (generally map, but may auto-coerce)
;; . map (no vectors, bro - but fail if not a suitable collection)
;; . seq (super generic. key as meta maybe too) [perhaps this is diff to rest]
;; . vec (worth it? useful for om. also perf)

;; novel
;; . snapshot (which then is also compat with these reads again)

;; metadata
;; . export
;; . priority

;; general: have all these methods work on refs, queries and snapshots (async)
;;          have another (sync) version, that asserts snapshot


;; ie. these would all return the same: (and in probably incr order of perf)
;;
;; (-> ref-or-query (m/snapshot) (m/get :a)   (m/get :b)   (m/vec))
;; (-> ref-or-query (m/get :a)   (m/snapshot) (m/get :b)   (m/vec))
;; (-> ref-or-query (m/get :a)   (m/get :b)   (m/snapshot) (m/vec))
;; (-> ref-or-query (m/get :a)   (m/get :b)   (m/vec))

;; cool idea: make channels readable also, there's your composition
;; another idea: have a ! (like core.async blocking) version for the sync
;; (ie not returning channel)

;; weird idea maybe: transducers
;; [nope, pretty sure you want return values most of the time, can always
;;  express with 1 chan only by calling reader last]


(defprotocol IListenable
  (once [_]) ;; guess this does have some uses - eg. gimme the first deletion
  (on [_])
  (on-children [_]) ;; sugar, and perf for jvm
  (off [_]))

;; idea: have this overloaded for refs, queries and channels. perhaps even snapshots
;; idea: using for refs (and maybe snapshots): generic listener, or unsub all at that path
;; idea: using for query uses metadata to remove only that specific one
;; idea: for channel (that is a matchbox value channel), deactive just its producer(s)
;; idea: `off-all`, which disables all listeners for sub paths also


;; sync
;; ====
;; dumb-atom
;; lazy-atom (can this be made reactive too? custom type?)
;; dequeued-atom
;; graft-atom (ie. multiple subs, normalize)


;; Matchbox 2 goals
;; ================
;; 1. Smaller API
;; 2. remove excessive sugar (esp. -in and overloading)
;; 3. channels-first
;; ==> summed up as SIMPLE
;; 4. provide all the laziness of snapshots
;; 5. reference counting
;; 6. amortize .val?
;; ==> summed up as EFFICIENT
;; 7. Simple introduction + demos
;; 8. Doc-string all the things
;; 9. Mention entire API on README
;; 10. Never swallow errors (JVM wrappers)

;; Note explicitly on what is not supported:
;; 1. Child listeners do not receive previous-child-name
;;    (check arity?) (specialized listener?)
;;
;;    tricky thing here is - how do we squeeze that into the channel
;;
;; 2. -- Disclose whether transaction committed [now they take this as second arg]



;; things to benchmark:
;; transient, vs voliatile, vs js
