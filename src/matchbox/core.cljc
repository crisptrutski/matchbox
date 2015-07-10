(ns matchbox.core
  (:refer-clojure :exclude [ref key set! swap! #?(:cljs -swap!) conj! get get-in read])
  #?(:cljs (:require-macros [cljs.core.async.macros :as asnyc]))
  (:require
    #?(:cljs [cljsjs.firebase])
    [clojure.string :as str]
    [matchbox.impl :as impl]
    [matchbox.coerce :as c]
    [matchbox.utils :as u #?(:clj :refer :cljs :refer-macros) [with-let]]
    #?(:clj
    [clojure.core.async :as async]
       :cljs [cljs.core.async :as async])
    #?(:cljs [cljs.core.async.impl.channels :refer [ManyToManyChannel]]))
  #?(:clj
     (:import [com.firebase.client Firebase Query DataSnapshot ServerValue Firebase$CompletionListener]
              [clojure.core.async.impl.channels ManyToManyChannel])))

#?(:cljs (def Firebase js/Firebase))

(defn ref [url]
  (Firebase. #?(:cljs url :clj (u/normalize-url url))))

(def server-timestamp
  #?(:clj  ServerValue/TIMESTAMP
     :cljs js/Firebase.ServerValue.TIMESTAMP))

;; hacks

(def nilify u/restore-nil)
(defn no-op [_])
;; TODO: likely need another case for transactions, to indicate whether it committed
(defn throw [err & [committed?]] (throw err))

#?(:clj (def <!! (comp u/restore-nil async/<!!)))


(defn- take-once [ch]
  (fn [value]
    (async/put! ch value)
    (async/close! ch)))

(defn- put [ch]
  (fn [value]
    (async/put! ch value)))

(defn- err-once [ch]
  (fn [err]
    (async/put! ch {:error err})
    (async/close! ch)))

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
  (delete!       [_])

  ;; channel variants
  (set!!          [_ value] [_ value priority])
  (set-priority!! [_ priority])
  (merge!!        [_ hsh])
  (conj!!         [_ child])
  (-swap!!        [_ f args local?])
  (remove!!       [_ key])
  (delete!!       [_]))

(defprotocol IReadable
  (snapshot [_])
  (raw      [_])
  (read     [_])
  (as-map   [_])
  (as-seq   [_])
  (as-vec   [_])
  (priority [_])
  (export   [_]))

;; Work around for (protocol-fn (<! ..))
(defn read- [x] (read x))

(defprotocol IListenable
  (once        [_ type_s])
  (on          [_ type_s])
  (on-children [_])
  (off         [_]))

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
  (get-in [ss path] (reduce get ss path))))

;; consider moving to "sugar" namespace

(extend-type ManyToManyChannel
  ITraversible
  (key    [ch]      (u/fmap key ch))
  (root   [ch]      (u/fmap root ch))
  (parent [ch]      (u/fmap parent ch))
  (get    [ch key]  (u/fmap #(get % key) ch))
  (get-in [ch path] (u/fmap #(get-in % path) ch)))

;; write

(defn swap!         [target f & args] (-swap!  target f args true))
(defn swap!!        [target f & args] (-swap!! target f args true))
(defn swap-remote!  [target f & args] (-swap!  target f args false))
(defn swap-remote!! [target f & args] (-swap!! target f args false))

(defn- partial-right [f args]
  (if (seq args) #(apply f % args) f))

(extend-type Firebase
  IWriteable
  (set!
    ([ref value]
     #?(:clj (.setValue ref (c/serialize value))
       :cljs (.set ref (c/serialize value))))
    ([ref value priority]
     #?(:clj (.setValue ref (c/serialize value) priority)
        :cljs (.set ref (c/serialize value) priority))))

  (set-priority! [ref priority]
    (.setPriority ref priority))

  (merge! [ref updates]
    (.updateChildren ref (c/serialize updates)))

  (conj! [ref value]
    ;; set! is a special form
    #?(:clj  (matchbox.core/set! (.push ref) (c/serialize value))
       :cljs (.push ref (c/serialize value))))

  (-swap! [ref f args local?]
    #?(:clj (let [handler (impl/tx-handler (partial-right f args) no-op throw)]
              (.runTransaction ref handler local?))
       :cljs (.transaction ref (partial-right f args))))

  (remove! [ref key]
    (-> ref (get key) delete!))

  (delete! [ref]
    (-> ref #?(:clj .removeValue :cljs .remove)))

  ;; channel variants

  (set!!
    ([ref value]
     (with-let [ch (async/chan)]
       #?(:clj  (.setValue ref
                           (c/serialize value)
                           ;; required to dispatch correctly (not as priority)
                           ;; not required as naked form.. maybe conditional reader macro?
                           ^Firebase$CompletionListener
                           (impl/completion-listener (put ch) throw))
          :cljs (.set ref (c/serialize value) (put ch)))))
    ([ref value priority]
     (with-let [ch (async/chan)]
       #?(:clj  (.setValue ref
                           (c/serialize value)
                           priority
                           (impl/completion-listener (put ch) throw))
          :cljs (.setWithPriority ref (c/serialize value) priority (put ch))))))

  (set-priority!! [ref priority]
    (with-let [ch (async/chan)]
      (.setPriority ref priority (put ch))))

  (merge!! [ref updates]
    (with-let [ch (async/chan)]
      (.updateChildren ref (c/serialize updates) (put ch))))

  (conj!! [ref value]
    #?(:clj  (matchbox.core/set!! (.push ref) (c/serialize value))
       :cljs (.push ref (c/serialize value) (put ch))))

  (-swap!! [ref f args local?]
    #?(:clj (with-let [ch (async/chan)]
              (let [handler (impl/tx-handler #(apply f % args) (put ch) throw)]
                (.runTransaction ref handler local?)))
       :cljs (.transaction ref (partial-right f args) (put ch))))

  (remove! [ref key]
    (-> ref (get key) delete!!))

  (delete!! [ref]
    (with-let [ch (async/chan)]
      #?(:clj  (.removeValue ref (impl/completion-listener (put ch) throw))
         :cljs (.remove ref (put ch))))))

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
  (priority [ss] (.getPriority ss))))

;; listen

(defn build-callbacks [events ch]
  (assoc (zipmap (map (fn [e] (keyword (last (str/split (name e) "-")))) events)
                 (map (fn [e] #(async/put! ch [e (read %)])) events))
    :cancelled #(async/put! ch {:error %})))

(defn- normalize-types [type_s]
  (->> (if (coll? type_s) type_s [type_s])
       (mapv (fn [s] (if (keyword? s) s (keyword s))))
       (distinct)))

(extend-type Firebase
  IListenable
  (once [ref type_s]
    (let [ch (async/chan)
          types (normalize-types type_s)
          value? (some #{:value} types)]
      (assert (or (not value?) (= 1 (count types))) "Cannot mix :value and :child listeners")
      (if value?
        (impl/add-value-listener ref (take-once ch) (err-once ch))
        (impl/add-child-listeners ref (build-callbacks types ch)))
      ch)))