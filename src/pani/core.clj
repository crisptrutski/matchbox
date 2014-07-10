(ns pani.core
  (:require [clojure.core.async :refer [<! >! chan go go-loop]]))

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
  (com.firebase.client.Firebase. url))

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye ] refers to hello.world.bye
;;
(defn walk-root [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (let [path (if (sequential? korks)
               (clojure.string/join "/" (map name korks))
               (name korks))]
    (.child root path)))

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
     (pani.core/set! node sval)))

  ([root korks val]
   (let [r    (walk-root root korks)
         node (.push r)
         sval (app->fb val)]
     (pani.core/set! node sval))))

(defn bind
  "Bind to a certain property under the given root"
  ([root type korks]
   (let [bc (chan)]
     (pani.core/bind root type korks #(go (>! bc %)))
     bc))

  ([root type korks cb]
   (let [node (walk-root root korks)]
     (cond
       (= type :value) 
       (.addValueEventListener node
                               (reify com.firebase.client.ValueEventListener
                                 (onDataChange [this v]
                                   (cb (.getValue v)))))

       (= type :child_added)
       (.addChildEventListener node
                               (reify com.firebase.client.ChildEventListener
                                 (onChildAdded [this v _]
                                   (cb (.getValue v)))))

       (= type :child_removed)
       (.addChildEventListener node
                               (reify com.firebase.client.ChildEventListener
                                 (onChildRemoved [this v]
                                   (cb (.getValue v)))))

       :else (throw (Exception. (str type " is not supported")))))))
