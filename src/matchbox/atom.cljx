(ns matchbox.atom
  (:require [clojure.walk :refer [postwalk]]
            [matchbox.core :as m]
            [matchbox.registry :as mr]))

;; Shim CLJS-style prototypes into CLJ

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

;; Watch strategies

(def ^:dynamic *local-sync*)

(def ^:dynamic *remote-sync*)

(defn- strip-nils [data]
  (postwalk
   (fn [x]
     (if (map? x)
       (into (empty x) (remove (comp nil? second) x))
       x))
   data))

(defn- reset-atom
  "Generate ref listener to sync values back to atom"
  [atom & [xform]]
  (fn [[key val]]
    (when-not (= atom *local-sync*)
      (binding [*remote-sync* atom]
        (reset! atom (if xform (xform val) val))))))

(defn- reset-in-atom
  "Generate ref listener to sync values back to atom. Scoped to path inside atom."
  [atom path]
  (fn [[key val]]
    (when-not (= atom *local-sync*)
      (binding [*remote-sync* atom]
        (let [val (if (nil? val)
                    (let [old (get-in @atom path)]
                      (if (coll? old) (empty old)))
                    val)]
          (swap! atom assoc-in path val))))))

(defn- cascade
  "Cascade deletes locally, but do not remove the root (keep type and existence stable)"
  [o n]
  (or (strip-nils n)
      (if (coll? n) (empty n))
      (if (coll? o) (empty o))))

(defn- reset-ref
  "Generate atom-watch function to sync values back to ref"
  [ref]
  (fn [_ atom o n]
    (when-not (or (= o n) (= atom *remote-sync*))
      (let [cascaded (cascade o n)]
        (if-not (= cascaded n)
          (binding [*remote-sync* atom]
            (reset! atom cascaded))))

      (binding [*local-sync* atom]
        (m/reset! ref n)))))

(defn- reset-in-ref
  "Generate atom-watch function to sync values back to ref. Scoped to path inside atom."
  [ref path]
  (fn [_ atom o n]
    (let [o (get-in o path)
          n (get-in n path)]
      (when-not (or (= o n) (= atom *remote-sync*))
        (let [cascaded (cascade o n)]
          (if-not (= cascaded n)
            (binding [*remote-sync* atom]
              (swap! atom assoc-in path cascaded))))

        (binding [*local-sync* atom]
          (m/reset! ref n))))))

;; Proxy strategies

(defn- swap-failover [cache f args]
  (if-not (:matchbox-unsubs (meta cache))
    (apply swap! cache f args)))

(defn- swap-ref-local [ref cache]
  (fn [f & args]
    (or (swap-failover cache f args)
        (m/reset! ref (apply f @cache args)))))

(defn- swap-ref-remote [ref cache]
  (fn [f & args]
    (or (swap-failover cache f args)
        (apply m/swap! ref f args))))

;; Wrapper type

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

(defn- attach-unsub [atom unsub]
  (alter-meta! atom update-in [:matchbox-unsubs] #(conj % unsub)))

(defn <-ref
  "Track changes in ref back to atom, via update function f."
  [ref atom f]
  (attach-unsub atom (m/listen-to ref :value f)))

(defn ->ref
  "Track changes in atom back to ref, via update function f."
  [ref atom f]
  (let [id (gensym)]
    (alter-meta! atom assoc :matchbox-watch id)
    (add-watch atom id f)))

;; Atom factories / decorators

(defn- init-ref!
  "Set ref with value, if value is not empty or nil."
  [ref value update? update!]
  (when-not (or (nil? value) (and (coll? value) (empty? value)))
    (m/deref ref
             ;; don't update if the ship has already sailed
             #(when (update? value)
                (if (nil? %)
                  (m/reset! ref value)
                  (update! %))))))

;; Atom co-ordination

(defn sync-r
  "Set up one-way sync of atom tracking ref changes. Useful for queries."
  [atom query & [xform]]
  (<-ref query atom (reset-atom atom xform))
  atom)

(defn sync-list
  "Set up one-way sync of atom tracking ordered list of elements. Useful for queries."
  [atom query & [xform]]
  (attach-unsub atom (m/listen-list query #(reset! atom (if xform (xform %) %))))
  atom)

(defn sync-rw
  "Set up two-way data sync between ref and atom."
  [atom ref]
  (init-ref! ref @atom #(reset! atom %) #(= % @atom))
  (<-ref ref atom (reset-atom atom))
  (->ref ref atom (reset-ref ref))
  atom)

(defn- update-path [atom path & [xform]]
  #(swap! atom assoc-in path (if xform (xform %) %)))

(defn sync-r-in [atom path query & [xform]]
  (m/listen-to query :value (update-path atom path xform))
  atom)

(defn sync-list-in [atom path query & [xform]]
  (attach-unsub atom (m/listen-list query (update-path atom path xform)))
  atom)

(defn sync-rw-in [atom path ref]
  (init-ref! ref (get-in @atom path)
             (update-path atom path nil)
             #(= % (get-in @atom path)))
  (<-ref ref atom (reset-in-atom atom path))
  (->ref ref atom (reset-in-ref ref path))
  atom)

(defn atom-wrapper
  "Build atom-proxy with given sync strategies."
  [atom ref ->update <-update]
  (<-ref ref atom <-update)
  (FireAtom. ref atom ->update))

(defn wrap-atom
  "Build atom-proxy which synchronises with ref via brute force."
  [atom ref]
  (init-ref! ref @atom #(reset! atom %) #(= % @atom))
  (atom-wrapper atom ref
                (swap-ref-local ref atom)
                (reset-atom atom)))

(defn unlink!
  "Stop synchronising atom with Firebase."
  [atom]
  (if (instance? FireAtom atom)
    (-unlink atom)
    (let [{id :matchbox-watch, unsubs :matchbox-unsubs} (meta atom)]
      (when id (remove-watch atom id))
      (doseq [unsub unsubs]
        (mr/disable-listener! unsub))
      (alter-meta! atom dissoc :matchbox-watch :matchbox-unsubs))))
