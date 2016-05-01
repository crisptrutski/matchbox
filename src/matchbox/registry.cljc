(ns matchbox.registry
  (:require [clojure.walk :as walk]))

;; nested maps of {ref {fn wrapped-fn}}
(defonce auth-listeners (atom nil))

;; nested maps of {ref {type #{fns}}}
(defonce unsubs (atom nil))

;; flat map of {fn [ref type]}
(defonce unsubs-flat (atom nil))

(defn register-auth-listener [ref cb wrapped-cb]
  #?(:clj  (.addAuthStateListener ref wrapped-cb)
     :cljs (.onAuth ref wrapped-cb))
  (swap! auth-listeners update ref #(assoc % cb wrapped-cb)))

(defn- -disable-auth-listener! [ref cb]
  (let [passed-cb (get-in @auth-listeners [ref cb] #?(:cljs cb))]
    #?(:clj  (when passed-cb (.removeAuthStateListener ref passed-cb))
       :cljs (.offAuth ref passed-cb))))

(defn disable-auth-listener! [ref cb]
  (-disable-auth-listener! ref cb)
  (swap! auth-listeners update ref #(dissoc % cb)))

(defn disable-auth-listeners!
  ([]
   (doseq [[ref cbs] @auth-listeners]
     (doseq [[_ wrapped-cb] cbs]
       (-disable-auth-listener! ref wrapped-cb)))
   (reset! auth-listeners {}))
  ([ref]
    (doseq [[_ wrapped-cb] (get @auth-listeners ref)]
      (-disable-auth-listener! ref wrapped-cb))
    (swap! auth-listeners dissoc ref)))

(defn register-listener [ref type unsub!]
  (swap! unsubs update-in [(str ref) type] #(set (conj % unsub!)))
  (swap! unsubs-flat assoc unsub! [(str ref) type]))

(defn- flatten-vals [xss]
  (if-not (map? xss)
    xss
    (if-let [xs (seq (map flatten-vals (vals xss)))]
      (reduce into xs))))

(defn- disable-all! [fs]
  (apply swap! unsubs-flat dissoc fs)
  (doseq [f fs] (f)))

(defn- -cleanup! [data]
  (walk/postwalk
    (fn [x]
      (when-not (and (coll? x) (empty? x))
        (if (map? x)
          (if-let [remains (seq (filter second x))]
            (into (empty x) remains))
          x)))
    data))

(defn- cleanup!
  "Remove empty branches in `unsubs`"
  []
  (swap! unsubs -cleanup!))

(defn disable-listener! [unsub!]
  (when-let [[ref type] (get @unsubs-flat unsub!)]
    (unsub!)
    (swap! unsubs-flat dissoc unsub!)
    (swap! unsubs update-in [ref type] #(disj % unsub!))
    (cleanup!)))

(defn disable-listeners!
  "Remove all known listeners within appropriate scope.

  By known listeners, we mean listeners that were added with Matchbox.

  The scope is determined by the args:

  0-arity: remove all listeners
  1-arity: remove all listeners on a given `ref`
  2-arity: remove all listeners of `type` on a given `ref`

  For removing a single listener, see `disable-listener!`"
  [& [ref type :as path]]
  (let [ref (when ref (str ref))]
    (case (count path)
      0 (do (disable-all! (flatten-vals @unsubs))
            (reset! unsubs {}))
      1 (do (disable-all! (flatten-vals (get @unsubs ref)))
            (swap! unsubs dissoc ref))
      2 (do (disable-all! (flatten-vals (get-in @unsubs [ref type])))
            (swap! unsubs update-in [ref] #(dissoc % type))))
    (cleanup!)))
