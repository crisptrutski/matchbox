(ns sunog.clojure.core
  (:refer-clojure :exclude [get-in set! reset! conj! swap! dissoc! deref parents key])
  (:require [clojure.core.async :refer [chan put!]]
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

(def child-events
  [:child-added
   :child-changed
   :child-moved
   :child-removed])

(def all-events
  (conj child-events :value))

;; TODO: review + unsubscribe listeners
;; TODO: server time
;; TODO: parents (inherit)
;; TODO: connect/discconet/on-disconnect
;; TODO: auth
;; TODO: listen-children

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
      (if err (throw err) (cb ref)))))
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

(defn reify-value-listener [cb]
  (reify ValueEventListener
    (^void onDataChange [_ ^DataSnapshot ds]
      (cb [(.getKey ds) (hydrate (.getValue ds))]))
    (^void onCancelled [_ ^FirebaseError err]
      (throw err))))

(defn value [ref cb]
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

(def remove! dissoc)

(defn set-priority [ref priority & [cb]]
  (if-not cb
    (.setPriority ref priority)
    (.setPriority ref priority (wrap-cb cb))))

;;

(defn reset-in! [ref korks val & [cb]]
  (reset! (get-in ref korks) val cb))

(defn conj-in! [ref korks val & [cb]]
  (conj! (get-in ref korks) val cb))

(defn swap-in! [ref korks f & args]
  (apply swap! (get-in ref korks) f args))

(defn merge-in! [ref korks val & [cb]]
  (merge! (get-in ref korks) val cb))

(defn dissoc-in! [ref korks & [cb]]
  (dissoc! (get-in ref korks) cb))

(def remove-in! dissoc-in!)

(defn- wrap-snapshot [^DataSnapshot d]
  [(.getKey d) (hydrate (.getValue d))])

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

(defn listen-to< [ref type]
  (let [ch (chan)]
    (listen-to ref type #(if % (put! ch %)))
    ch))

(defn listen-children< [ref]
  (let [ch (chan)]
    (listen-children ref #(if % (put! ch %)))
    ch))
