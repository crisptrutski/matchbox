(ns firebase.main
  (:require [cljs.core.async :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; Make a firebase object ouf the given URL
;;
(defn make-root [url]
  "Makes a root reference for firebase"
  (js/Firebase. url))

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye ] refers to hello.world.bye
;;
(defn- reduce-to-element [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (cond
    (sequential? korks) (reduce #(.child %1 (name %2)) root korks)
    :else (.child root (name korks))))

;; A function set the value on a root [korks]
;;
(defn firebase-set!
  "Set the value at the given root"
  ([root val]
   (let [as-js (clj->js val)]
     (.set root as-js)))

  ([root korks val]
   (firebase-set! (reduce-to-element root korks) val)))

;; Bind a callback to a root [korks]
;;
(defn firebase-bind [root korks cb]
  "Bind to change notifications"
  (let [c (reduce-to-element root korks)]
    (.on c "value" #(cb (.val %1)))))

;; Bind a chan to a root [korks]
;;
(defn firebase-bind-chan [root korks]
  "Bind and return an async channel to which any value changes are posted"
  (let [c (reduce-to-element root korks)
        bind-chan (chan)]
    (.on c "value" #(go (>! bind-chan (.val %1))))
    bind-chan))

;; ;;;;;;;;;;;;;;;;;;;
;; Example usage
;;
;; Create a root which represents a firebase root
(def root (make-root "https://<your-app>.firebaseio.com/"))

;; A simple utility function that prints the value posted to it
;;
(defn- print-val [val]
  (.log js/console "Value:" val))

;; Bind a function to a few values inside our root, the binding is modeled after core/get-in
;;
(firebase-bind root [:info :world] print-val)
(firebase-bind root [:info :test] print-val)
(firebase-bind root :age print-val)

;; get a channel instead of passing a callback
;;
(let [my-chan (firebase-bind-chan root [:info :test])]
  (go-loop [val (<! my-chan)]
           (.log js/console "Got new value inside go loop:" val)
           (recur (<! my-chan))))

;; Finally set a few values
;;
(firebase-set! root {:name "James Bond"
                     :age 94
                     :info {:test "hello"
                            :world "bye"}}) 

(firebase-set! root [:info :world] "welcome")
(firebase-set! root [:info :test] {:another "object"})
(firebase-set! root :age 100)



