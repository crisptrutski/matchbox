(ns pani.clojure.core
  (:require [clojure.core.async :refer [<! >! chan go go-loop]])
  (:import [com.firebase.client
            Firebase
            ValueEventListener
            ChildEventListener
            Transaction
            Transaction$Handler]))

(defn app->fb [v]
  (if (map? v)
    (clojure.walk/stringify-keys v)
    v))

(defn fb->app [v]
  (if (map? v)
    (clojure.walk/keywordize-keys v)
    v))

;; Make a firebase object ouf the given URL
;;
(defn root [url]
  "Makes a root reference for firebase"
  (Firebase. url))

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye ] refers to hello.world.bye
;;
(defn walk-root [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (let [path (if (sequential? korks)
               (clojure.string/join "/" (map name korks))
               (name korks))]
    (.child root path)))

(defn name [root]
  "Get the name of the given root"
  (.getName root))

(defn parent [root]
  "Get the parent of the given root"
  (.getParent root))

;; A function set the value on a root [korks]
;;
(defn set!
  "Set the value at the given root"
  ([root val]
   (.setValue root (app->fb val)))

  ([root korks val]
   (.setValue (walk-root root korks) (app->fb val))))

(defn push!
  "Push a value under the given root"
  ([root val]
   (let [node (.push root)
         sval (app->fb val)]
     (pani.clojure.core/set! node sval)))

  ([root korks val]
   (let [r    (walk-root root korks)
         node (.push r)
         sval (app->fb val)]
     (pani.clojure.core/set! node sval))))

(defmacro bind-handlers [btype node cb & specs]
  (let [pcount {:value 2 :child_added 3 :child_removed 2}]
    `(cond
       ~@(mapcat (fn [[matchtype iface handler]]
                   (let [params (vec (take (pcount matchtype) (repeatedly gensym)))
                         attacher (if (= matchtype :value) 'addValueEventListener 'addChildEventListener)]
                     (list
                       `(= ~btype ~matchtype )
                       `(. ~node ~attacher (reify ~iface
                                             (~handler ~params
                                               (~cb (.getValue ~(second params))))))))) specs)
       :else (throw (Exception. (str ~type " is not supported"))))))

(defn bind
  "Bind to a certain property under the given root"
  ([root type korks]
   (let [bc (chan)]
     (pani.clojure.core/bind root type korks #(go (>! bc %)))
     bc))

  ([root type korks cb]
   (let [node (walk-root root korks)]
     (bind-handlers type node cb
        [:value         ValueEventListener onDataChange]
        [:child_added   ChildEventListener onChildAdded]
        [:child_removed ChildEventListener onChildRemoved]))))


(defn transact!
  "Use the firebase transaction mechanism to update a value atomically"
  [root korks f & args]
  (let [r (walk-root root korks)]
    (.runTransaction r (reify Transaction$Handler
                         (doTransaction [this d]
                           (let [cv (.getValue d)
                                 nv (apply f cv args)]
                             (.setValue d nv)
                             (Transaction/success d)))
                         (onComplete [_ _ _ _])))))
