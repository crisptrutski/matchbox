(ns sunog.impl
  (:refer-clojure :exclude [get-in set! reset! conj! swap! dissoc! deref parents key])
  (:import [com.firebase.client
            ServerValue
            Firebase
            FirebaseError
            MutableData
            DataSnapshot
            ValueEventListener
            ChildEventListener
            Transaction
            Transaction$Handler])
  (:require [clojure.core.async :refer [chan put!]]
            [clojure.string :as str]
            [clojure.walk :as walk]))

;; who doesn't like circular refs?

(def all-events sunog.core/all-events)
(def child-events sunog.core/child-events)

;; TODO: review + unsubscribe listeners
;; TODO: connect/discconet/on-disconnect
;; TODO: auth

(defn- wrap-cb [cb]
  (reify com.firebase.client.Firebase$CompletionListener
    (^void onComplete [this ^FirebaseError err ^Firebase ref]
      (if err (throw err) (cb ref)))))
;;

(def SERVER_TIMESTAMP ServerValue/TIMESTAMP)

(defn hydrate [v]
  (if (map? v)
    (walk/keywordize-keys v)
    v))

(defn serialize [v]
  (if (map? v)
    (walk/stringify-keys v)
    v))

(defn key [ref] (.getKey ref))

(defn value [snapshot]
  (hydrate (.getValue snapshot)))

(defn wrap-snapshot [^DataSnapshot d]
  [(key d) (value d)])

(defn connect [url]
  (Firebase. url))

(defn get-in [root korks]
  (let [path (if (sequential? korks)
               (str/join "/" (map name korks))
               (name korks))]
    (.child root path)))

(defn parent [ref] (.getParent ref))

(defn- reify-value-listener [cb]
  (reify ValueEventListener
    (^void onDataChange [_ ^DataSnapshot ds]
      (cb (wrap-snapshot ds)))
    (^void onCancelled [_ ^FirebaseError err]
      (throw err))))

(defn deref [ref cb]
  (.addListenerForSingleValue ref (reify-value-listener cb)))

(defn reset! [ref val & [cb]]
  (if-not cb
    (.setValue ref (serialize val))
    (.setValue ref (serialize val) (wrap-cb cb))))

(defn conj!
  ([ref val & [cb]]
   (reset! (.push ref) val cb)))

(defn- build-tx-handler [f args cb]
  (reify Transaction$Handler
    (^com.firebase.client.Transaction$Result doTransaction [_ ^MutableData d]
      (let [current (hydrate (.getValue d))]
        (reset! d (apply f current args))
        (Transaction/success d)))
    (^void onComplete [_ ^FirebaseError error, ^boolean committed, ^DataSnapshot d]
      (if (and cb (not error) committed)
        (cb (hydrate (.getValue d)))))))

(defn swap!
  "Update value atomically, with local optimistic writes"
  [ref f & args]
  (let [cb nil #_"extract this like in CLJS case"]
    (.runTransaction ref (build-tx-handler f args cb) true)))

(defn merge! [ref val & [cb]]
  (if-not cb
    (.updateChildren ref (serialize val))
    (.updateChildren ref (serialize val) (wrap-cb cb))))

(defn dissoc! [ref & [cb]]
  (if-not cb
    (.removeValue ref)
    (.removeValue ref (wrap-cb cb))))

(defn set-priority [ref priority & [cb]]
  (if-not cb
    (.setPriority ref priority)
    (.setPriority ref priority (wrap-cb cb))))

(defn reify-child-listener [{:keys [added changed moved removed]}]
  (reify ChildEventListener
    (^void onChildAdded [_ ^DataSnapshot d ^String previous-child-name]
      (if added (added (wrap-snapshot d))))
    (^void onChildChanged [_ ^DataSnapshot d ^String previous-child-name]
      (if changed (changed (wrap-snapshot d))))
    (^void onChildMoved [_ ^DataSnapshot d ^String previous-child-name]
      (if moved (moved (wrap-snapshot d))))
    (^void onChildRemoved [_ ^DataSnapshot d]
      (if removed (removed (wrap-snapshot d))))
    (^void onCancelled [_ ^FirebaseError err]
      (throw err))))

(defn- strip-prefix [type]
  (-> type name (str/replace #"^.+\-" "") keyword))

(defn listen-to
  ([ref type cb]
   (assert (some #{type} all-events) (str "Unknown type: " type))
   (if-not (some #{type} child-events)
     (.addValueEventListener ref (reify-value-listener cb))
     (.addChildEventListener ref (reify-child-listener
                                  (hash-map (strip-prefix type) cb)))))
  ([ref korks type cb]
   (listen-to (get-in ref korks) type cb)))

(defn listen-children
  ([ref cb]
   (let [bases (map strip-prefix child-events)
         cbs (zipmap bases (->> child-events
                                (map (fn [type] #(vector type %)))
                                (map #(comp cb %))))]
     (.addChildEventListener ref (reify-child-listener cbs)))))
