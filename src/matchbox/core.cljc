(ns matchbox.core
  (:refer-clojure :exclude [ref key set! swap! #?(:cljs -swap!) conj! get get-in read])
  #?(:cljs  (:require-macros [cljs.core.async.macros :as asnyc]))
  (:require
   #?(:cljs [cljsjs.firebase])
   [matchbox.impl :as impl]
   [matchbox.coerce :as c]
   [matchbox.utils :as u]
   #?(:clj [clojure.core.async :as async]
      :cljs  [cljs.core.async :as async])
   #?(:cljs [cljs.core.async.impl.channels :refer [ManyToManyChannel]]))
  #?(:clj
     (:import [com.firebase.client Firebase Query DataSnapshot ServerValue]
              [clojure.core.async.impl.channels ManyToManyChannel])))

#?(:cljs (def Firebase js/Firebase))

(defn ref [url]
  (Firebase. #?(:cljs url :clj (u/normalize-url url))))

(def server-timestamp
  #?(:clj  ServerValue/TIMESTAMP
     :cljs js/Firebase.ServerValue.TIMESTAMP))

;; hacks

(def nilify u/restore-nil)

#?(:clj (def <!! (comp u/restore-nil async/<!!)))

;; /end hacks


(defprotocol ITraversible
  (key    [_]      "Index value in parent for non-root thingy")
  (root   [_]      "Root thingy")
  (parent [_]      "Parent thingy")
  (get    [_ key]  "Descend one value")
  (get-in [_ path] "Descend path"))

(defprotocol IWriteable
  (set!          [_ value] [_ value priority])
  (set-priority! [_ priority])
  (merge!        [_ hsh])
  (conj!         [_ child])
  (-swap!        [_ f args local?])
  (remove!       [_ key])
  (delete!       [_]))

(defprotocol IReadable
  (snapshot [_]) ;; intermediate
  (raw      [_]) ;; as underlying client delivers it
  (read     [_]) ;; matchbox coertion applied
  (as-map   [_]) ;; .. in case you didn't want auto-vectorization
  (as-seq   [_]) ;; .. just the children [not sure about this - perhaps [k v]]
  (as-vec   [_]) ;; .. force vectorization
  (priority [_]) ;; metadata
  (export   [_])) ;; data + meta data

(defn read- [x] (read x))

(defprotocol IListenable
  (once [_ type_s]) ;; guess this does have some uses - eg. gimme the first deletion
  (on   [_ type_s])
  (on-children [_]) ;; sugar, and perf for jvm
  (off [_]))


;; implementations

(extend-type Firebase
  ITraversible
  (key    [ref]      (-> ref #?(:clj .getKey    :cljs .key) keyword))
  (root   [ref]      (-> ref #?(:clj .getRoot   :cljs .root)))
  (parent [ref]      (-> ref #?(:clj .getParent :cljs .parent)))
  (get    [ref key]  (.child ref (name key)))
  (get-in [ref path] (reduce get ref path)))

#?(:clj
(extend-type DataSnapshot
  ITraversible
  (key    [ss]      (-> ss #?(:clj .getKey    :cljs .key) keyword))
  (root   [ss]      (-> ss #?(:clj .getRoot   :cljs .root)))
  (parent [ss]      (-> ss #?(:clj .getParent :cljs .parent)))
  (get    [ss key]  (.child ss (name key)))
  (get-in [ss path] (reduce get ss path)))
)

;; consider moving to "sugar" namespace

(extend-type ManyToManyChannel
  ITraversible
  (key    [ch]      (u/fmap key ch))
  (root   [ch]      (u/fmap root ch))
  (parent [ch]      (u/fmap parent ch))
  (get    [ch key]  (u/fmap #(get % key) ch))
  (get-in [ch path] (u/fmap #(get-in % path) ch)))


;; write

;; TODO: blocking mutation
;; remove!! or overload?

(defn swap! [target f & args]
  (-swap! target f args true))

(defn swap-remote! [target f & args]
  (-swap! target f args false))

(extend-type Firebase
  IWriteable
  (set!
    ([ref value]
     (-> ref (#?(:clj .setValue :cljs .set) (c/serialize value))))
    ([ref value priority]
     (-> ref (#?(:clj .setValue :cljs .setWithPriority) (c/serialize value) priority))))

  (set-priority! [ref priority]
    (.setPriority ref priority))

  (merge! [ref updates]
    (.updateChildren ref (c/serialize updates)))

  (conj! [ref value]
    ;; overriding set! is not straightforward - special form
    #?(:clj  (matchbox.core/set! (.push ref) (c/serialize value))
       :cljs (.push ref (c/serialize value))))

  (-swap! [ref f args local?]
    ;; TODO: replace safe-prn with sensible or omitted callbacks
    #?(:clj
       (let [handler (impl/tx-handler #(apply f % args) u/safe-prn u/safe-prn)]
         (.runTransaction ref handler local?))))

  (remove! [ref key]
    (-> ref (get key) delete!))

  (delete! [ref]
    (-> ref #?(:clj .removeValue :cljs .remove))))


;; read

(extend-type Firebase
  IReadable
  (snapshot [ref] (once ref :value))
  (raw      [ref] (-> ref snapshot raw))
  (read     [ref] (-> ref snapshot read))
  (as-map   [ref] (-> ref snapshot as-map))
  (as-seq   [ref] (-> ref snapshot as-seq))
  (as-vec   [ref] (-> ref snapshot as-vec))
  (export   [ref] (-> ref snapshot export))
  (priority [ref] (-> ref snapshot priority)))

(extend-type ManyToManyChannel
  IReadable
  (snapshot [ch] (u/fmap snapshot ch))
  (read     [ch] (u/fmap read ch))
  (as-map   [ch] (u/fmap as-map ch))
  (as-seq   [ch] (u/fmap as-seq ch))
  (as-vec   [ch] (u/fmap as-vec ch))
  (export   [ch] (u/fmap export ch))
  (priority [ch] (u/fmap priority ch)))
#?(:clj
(extend-type DataSnapshot
  IReadable
  (snapshot [ss] ss)
  (raw      [ss] (.getValue ss))
  (read     [ss] (-> ss raw c/hydrate))
  (as-map   [ss] (-> ss read c/ensure-map))
  (as-seq   [ss] (map read (.getChildren ss)))
  (as-vec   [ss] (mapv read (.getChildren ss)))
  (export   [ss] (c/hydrate (.getValue ss true)))
  (priority [ss] (.getPriority ss)))
)


;; listen

(defn build-callbacks [events ch]
  (zipmap events (map (fn [e]
                        #(async/put! ch [(keyword (name e))
                                         (read %)]))
                      events)))

(extend-type Firebase
  IListenable
  (once [ref type_s]
    (let [ch (async/chan)]
      ;; TODO: assert types do not mix children- and value
      ;; TODO: strip child- prefix
      ;; TODO: move this ugliness down to impl
      #?(:clj
         (if (and (not (coll? type_s)) (= "value" (name type_s)))
           (.addListenerForSingleValueEvent
            ref
            (impl/value-listener #(do (async/put! ch %)
                                      (async/close! ch))
                                 #(do (prn 'err %))))
           (let [types (if (string? type_s) [type_s] type_s)]
             (.addChildEventListener ref
                                     (impl/child-listener
                                      (build-callbacks types ch))))))
      ch)))

;; re: off!
;; idea: have this overloaded for refs, queries and channels. perhaps even snapshots
;; idea: using for refs (and maybe snapshots): generic listener, or unsub all at that path
;; idea: using for query uses metadata to remove only that specific one
;; idea: for channel (that is a matchbox value channel), deactive just its producer(s)
;; idea: `off-all`, which disables all listeners for sub paths also
