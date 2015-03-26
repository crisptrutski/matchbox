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

(defn- -swap-atom [atom]
  (fn [[key val]]
    (swap! atom merge val)))

(defn- -reset-ref [ref]
  (fn [_ _ o n]
    (when (not= o n)
      (m/reset! ref n))))

(defn- -swap-ref-local [ref cache]
  (fn [f & args]
    (m/reset! ref (apply f @cache args))))

(defn- -swap-ref-remote [ref]
  (fn [f & args]
    (apply m/swap! ref f args)))

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
               (-swap-ref-remote ref)
               (-swap-atom cache))))

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


;; quick repl tests

(comment

  (def r (m/connect "https://luminous-torch-5788.firebaseio.com/atom-test"))

  (m/reset! r nil)

  (def c1 (m/get-in r "1"))
  (def c2 (m/get-in r "2"))
  (def c3 (m/get-in r "3"))
  (def c4 (m/get-in r "4"))
  (def c5 (m/get-in r "5"))
  (def c6 (m/get-in r "6"))

  (def atom-1 (brute-atom c1))
  (def atom-2 (brute-atom c2 (atom {:initial "state"})))
  (def atom-3 (reset-atom c3))
  (def atom-4 (reset-atom c4 (atom {:data (rand)})))
  (def atom-5 (merge-atom c5))
  (def atom-6 (merge-atom c6 (atom {:some {:inital "data"}, :here "because"})))

  (swap! atom-1 assoc :a 'write)
  (swap! atom-2 assoc :a 'write)
  (-swap! atom-3 assoc :a 'write)
  (-swap! atom-4 assoc :a 'write)
  (-swap! atom-5 assoc :a 'write)
  (-swap! atom-6 assoc :a 'write)

  (-deref atom-1)
  (-deref atom-2)
  (-deref atom-3)
  (-deref atom-4)
  (-deref atom-5)
  (-deref atom-6)

  (m/reset-in! c6 :b 40)

  (unlink! atom-1)
  (unlink! atom-2)
  (unlink! atom-3)
  (unlink! atom-4)
  (unlink! atom-5)
  (unlink! atom-6)

  (m/reset-in! c6 :b 4)

  (mr/disable-listeners!))
