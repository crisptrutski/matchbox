(ns sunog.clojure.core
  (:refer-clojure :exclude [get-in set! reset! conj! swap! dissoc! deref parents key])
  (:require [clojure.core.async :refer [<! >! chan go go-loop]]
            [clojure.string :as str]
            [clojure.walk :as walk])
  (:import [com.firebase.client
            ServerValue
            Firebase
            FirebaseError
            MutableData
            DataSnapshot
            ValueEventListener
            ChildEventListener
            Transaction
            Transaction$Handler]))

(defn serialize [v]
  (if (map? v)
    (walk/stringify-keys v)
    v))

(defn hydrate [v]
  (if (map? v)
    (walk/keywordize-keys v)
    v))

(defn- wrap-cb [cb]
  (reify com.firebase.client.Firebase$CompletionListener
    (^void onComplete [this ^FirebaseError err ^Firebase ref]
      (prn err ref)
      (if err
        (throw err)
        (cb ref)))))
;;

(defn connect [url]
  (Firebase. url))

(defn get-in [root korks]
  (let [path (if (sequential? korks)
               (str/join "/" (map name korks))
               (name korks))]
    (.child root path)))

(defn key [ref] (.getKey ref))

(defn parent [ref] (.getParent ref))

(defn value [ref cb]
  (.addListenerForSingleValue
   ref
   (reify ValueEventListener
     (^void onDataChange [_ ^DataSnapshot snapshot]
       (cb (hydrate (.getValue snapshot))))
     (^void onCancelled [_ ^FirebaseError err]
       (throw err)))))

(defn reset! [ref val & [cb]]
  (if-not cb
    (.setValue ref (serialize val))
    (.setValue ref (serialize val) (wrap-cb cb))))

(defn conj!
  ([ref val & [cb]]
   (reset! (.push ref) val cb)))

;; FIXME not sure how to add callback here

(defn- build-tx-handler [f & args]
  (reify Transaction$Handler
    (#_com.firebase.client.Transaction$Result doTransaction [_ #_MutableData d]
      (let [current (hydrate (.getValue d))]
        (prn current)
        (reset! d (apply f current args))
        (Transaction/success d)))
    (onComplete [_ _ _ _])))

(defn swap!
  "Update value atomically, with local optimistic writes"
  [ref f & args]
  (.runTransaction ref (build-tx-handler f) true))

(defn merge! [ref val]
  (.updateChildren ref (serialize val)))

(defn dissoc! [ref]
  (.removeValue ref))

(def remove! dissoc)

(defn set-priority [ref priority]
  (.setPriority ref priority))

;;

(defn reset-in! [ref korks val]
  (reset! (get-in ref korks) val))

(defn conj-in! [ref korks val]
  (conj! (get-in ref korks) val))

(defn swap-in! [ref korks f & args]
  (apply swap! (get-in ref korks) f args))

(defn merge-in! [ref korks val]
  (merge! (get-in ref korks) val))

(defn dissoc-in! [ref korks]
  (dissoc! (get-in ref korks)))

(def remove-in! dissoc-in!)

(defmacro bind-handlers [btype node cb & specs]
  (let [pcount {:value 2, :child-added 3, :child-removed 2}]
    `(cond
       ~@(mapcat (fn [[matchtype iface handler]]
                   (let [params (vec (repeatedly (pcount matchtype) gensym))
                         attacher (if (= matchtype :value)
                                    'addValueEventListener
                                    'addChildEventListener)]
                     (list
                      `(= ~btype ~matchtype)
                      `(. ~node
                          ~attacher
                          (reify ~iface
                            (~handler ~params
                              (~cb [(key ~(second params))
                                    (.getValue ~(second params))])))))))
                 specs)
       :else (throw (Exception. (str ~type " is not supported"))))))

(defn listen-to
  ([ref type cb]
   (bind-handlers type ref cb
                  [:value         ValueEventListener onDataChange]
                  [:child-added   ChildEventListener onChildAdded]
                  [:child-removed ChildEventListener onChildRemoved]))
  ([ref korks type cb]
   (listen-to (get-in ref korks) type cb)))

(defn listen-to< [ref type]
  (let [ch (chan)]
    (listen-to ref type #(go (>! ch %)))
    ch))
