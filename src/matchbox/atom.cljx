(ns matchbox.atom
  (:require [matchbox.core :as m]
            [matchbox.registry :as mr]))

;; First, pay penance to the elder-platform gods

#+clj
(defprotocol ISwap
  (-swap!
    [_ f]
    [_ f a]
    [_ f a b]
    [_ f a b xs]))

#+clj
(defprotocol IDeref
  (-deref [_]))

#+clj
(extend-protocol IDeref
  clojure.lang.Atom
  (-deref [this] @this))

#+clj
(defprotocol IWatchable
  (-add-watch [_ k f])
  (-remove-watch [_ k]))

;; Update strategies

(defn- -reset-atom [atom]
  (fn [[key val]]
    (reset! atom val)))

(defn- -merge-atom [atom]
  (fn [[key val]]
    (swap! atom merge val)))

(defn- -reset-ref [ref]
  (fn [_ _ o n]
    (when (not= o n)
      (m/reset! ref n))))

(defn- -swap-failover [cache f args]
  ;; Fallback to local write if non syncing back
  (if-not (:matchbox-unsub (meta cache))
    (apply swap! cache f args)))

(defn- -swap-ref-local [ref cache]
  (fn [f & args]
    (or (-swap-failover cache f args)
        (m/reset! ref (apply f @cache args)))))

(defn- -swap-ref-remote [ref cache]
  (fn [f & args]
    (or (-swap-failover cache f args)
        (apply m/swap! ref f args))))

;; We have types too

(declare unlink!)

(defprotocol IUnlink
  (-unlink [_] "Remove any sync between local and firebase state"))

(deftype FireAtom [ref cache ->update]
  #+cljs IAtom
  ISwap
  (-swap! [_ f]
    (->update f))
  (-swap! [_ f a]
    (->update f a))
  (-swap! [_ f a b]
    (->update f a b))
  (-swap! [_ f a b xs]
    (apply ->update f a b xs))

  IDeref
  (-deref [_] (-deref cache))

  IUnlink
  (-unlink [_] (unlink! cache))

  IWatchable
  (-add-watch [_ k f]
    (-add-watch cache k f))
  (-remove-watch [_ k]
    (-remove-watch cache k)))

;; Watcher/Listener management

;; IDEA: replace or complement with global listener registry

;; FIXME: don't let more than atom sync with a ref, or solve coordination

(defn <-ref [ref atom f]
  (let [unsub (m/listen-to ref :value f)]
    (alter-meta! atom assoc :matchbox-unsub unsub)))

(defn ->ref [ref atom f]
  (let [id (gensym)]
    (alter-meta! atom assoc :matchbox-watch id)
    (add-watch atom id f)))

(defn unlink!
  "Stop synchronising atom with Firebase"
  [atom]
  (if (instance? FireAtom atom)
    (-unlink atom)
    (let [{id :matchbox-watch, unsub :matchbox-unsub} (meta atom)]
      (when id (remove-watch atom id))
      (when unsub (mr/disable-listener! unsub))
      (alter-meta! atom dissoc :matchbox-watch :matchbox-unsub))))

;; Atom factories / decorators

(defn- ensure-cache [& [atom-]]
  (or atom- (atom nil)))

(defn fire-atom [ref cache ->update <-update]
  (<-ref ref cache <-update)
  (FireAtom. ref cache ->update))

(defn brute-atom [ref & [atom-]]
  (let [cache (ensure-cache atom-)]
    (if atom- (m/reset! ref @atom-))
    (<-ref ref cache (-reset-atom cache))
    (->ref ref cache (-reset-ref ref))
    cache))

(defn reset-atom [ref & [atom-]]
  (let [cache (ensure-cache atom-)]
    (if atom- (m/reset! ref @atom-))
    (fire-atom ref cache
               (-swap-ref-local ref cache)
               (-reset-atom cache))))

;; IDEA: allow ignoring (or better yet, never receiving) first :value
;;       this allows us to be lazy about pulling and holding a deep tree,
;        just to sync some shallow read/writes

(defn merge-atom [ref & [atom-]]
  (let [cache (ensure-cache atom-)]
    (if atom- (m/merge! ref @atom-))
    (fire-atom ref cache
               (-swap-ref-remote ref cache)
               (-merge-atom cache))))

;; IDEA: minimal-merge-atom
;; Rather than single merges at the roots, use a diff to make
;; a deep-merge via focussed m/merge! calls

;; IDEA: fancy-pants-atom
;; Rather than use a :value listener, build up children listeners
;; on the various subnodes. Be smart about using vectors,
;; and read with prioties there. Add listeners based on initial
;; and local state updates, as well as clean up listeners when
;; data is removed (locally or remotely).
;;
;; Perhaps an option to automatically cascade (up to some limit?)
;; to track children's children as data gets synced.
