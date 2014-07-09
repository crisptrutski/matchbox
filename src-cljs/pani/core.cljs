(ns pani.core
  (:require [cljs.core.async :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- clj-val [v]
  (js->clj (.val v) :keywordize-keys true))

;; Make a firebase object ouf the given URL
;;
(defn root [url]
  "Makes a root reference for firebase"
  (js/Firebase. url))

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye ] refers to hello.world.bye
;;
(defn walk-root [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (cond
    (sequential? korks) (reduce #(.child %1 (name %2)) root korks)
    :else (.child root (name korks))))

(defn- fb-call!
  "Set the value at the given root"
  ([push-fn root val]
   (let [as-js (clj->js val)]
     (push-fn root as-js)))

  ([push-fn root korks val]
   (fb-call! push-fn (walk-root root korks) val)))

;; A function set the value on a root [korks]
;;
(defn set!
  "Set the value at the given root"
  ([root val]
   (fb-call! #(.set %1 %2) root val))

  ([root korks val]
   (fb-call! #(.set %1 %2) root korks val)))

(defn push!
  "Set the value at the given root"
  ([root val]
   (fb-call! #(.push %1 %2) root val))

  ([root korks val]
   (fb-call! #(.push %1 %2) root korks val)))

(defn bind
  "Bind to a certain property under the given root"
  ([root type korks]
   (let [c (walk-root root korks)
         bind-chan (chan)]
     (.on c (name type) #(go (>! bind-chan (clj-val %1))))
     bind-chan))

  ([root type korks cb]
   (let [c (walk-root root korks)]
     (.on c (name type) #(cb (clj-val %1))))))

