(ns pani.clojure.core
  (:refer-clojure :exclude [get-in set! reset! conj! swap! dissoc! deref parents key])
  (:require [clojure.core.async :refer [<! >! chan go go-loop]]
            [clojure.string :as str]
            [clojure.walk :as walk])
  (:import [com.firebase.client
            Firebase
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

(defn connect [url]
  (Firebase. url))

(defn get-in [root korks]
  (let [path (if (sequential? korks)
               (str/join "/" (map name korks))
               (name korks))]
    (.child root path)))

(defn key [ref] (.getKey ref))

(defn parent [ref] (.getParent ref))

(defn value [ref] (hydrate (.getValue ref)))

(defn reset!
  ([ref val]
   (.setValue ref (serialize val))))

(defn conj!
  ([ref val]
   (reset! (.push ref) val)))

(defn swap!
  "Use the firebase transaction mechanism to update a value atomically"
  [ref f & args]
  (.runTransaction
   ref
   (reify Transaction$Handler
     (doTransaction [this d]
       (let [old (value d)
             new (apply f old args)]
         (reset! d new)
         (Transaction/success d)))
     (onComplete [_ _ _ _]))
   true))

;; TODO: merge!
;; TODO: dissoc!

;;

(defn reset-in! [ref korks val]
  (reset! (get-in ref korks) val))

(defn conj-in! [ref korks val]
  (conj! (get-in ref korks) val))

(defn swap-in! [ref korks f & args]
  (apply swap! (get-in ref korks) f args))

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
