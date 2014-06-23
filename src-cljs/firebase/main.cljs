(ns firebase.main
  (:require [cljs.core.async :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; Make a firebase object ouf the given URL
;;
(defn make-root [url]
  "Makes a root reference for firebase"
  (js/Firebase. url))

(defn- clj-val [v]
  (js->clj (.val v) :keywordize-keys true))

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye ] refers to hello.world.bye
;;
(defn- reduce-to-element [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (cond
    (sequential? korks) (reduce #(.child %1 (name %2)) root korks)
    :else (.child root (name korks))))

(defn- firebase-call!
  "Set the value at the given root"
  ([push-fn root val]
   (let [as-js (clj->js val)]
     (push-fn root as-js)))

  ([push-fn root korks val]
   (firebase-call! push-fn (reduce-to-element root korks) val)))

;; A function set the value on a root [korks]
;;
(defn firebase-set!
  "Set the value at the given root"
  ([root val]
   (firebase-call! #(.set %1 %2) root val))

  ([root korks val]
   (firebase-call! #(.set %1 %2) root korks val)))

(defn firebase-push!
  "Set the value at the given root"
  ([root val]
   (firebase-call! #(.push %1 %2) root val))

  ([root korks val]
   (firebase-call! #(.push %1 %2) root korks val)))

(defn firebase-bind
  "Bind to a certain property under the given root"
  ([root type korks]
   (let [c (reduce-to-element root korks)
         bind-chan (chan)]
     (.on c (name type) #(go (>! bind-chan [type (clj-val %1)])))
     bind-chan))

  ([root type korks cb]
   (let [c (reduce-to-element root korks)]
     (.on c (name type) #(cb type (clj-val %1))))))

;; ;;;;;;;;;;;;;;;;;;;
;; Example usage
;;
;; Create a root which represents a firebase root
(def root (make-root "https://<your app>.firebaseio.com/"))

(defn print-val [& xs]
  (.log js/console (pr-str xs)))

;; Bind a function to a few values inside our root, the binding is modeled after core/get-in
;;
(firebase-bind root :value [:info :world] print-val)
(firebase-bind root :value [:info :test] print-val)
(firebase-bind root :value :age print-val)

;; get a channel instead of passing a callback
;;
(let [my-chan (firebase-bind root :value [:info :test])]
  (go-loop [val (<! my-chan)]
           (.log js/console "Got new value inside go loop:" (pr-str val))
           (recur (<! my-chan))))

(firebase-bind root :value :messages print-val)
(firebase-bind root :child_added :messages print-val)

;; Finally set a few values
;;
(firebase-set! root {:name "James Bond"
                     :age 94
                     :messages {}
                     :info {:test "hello"
                            :world "bye"}}) 

(firebase-set! root [:info :world] "welcome")
(firebase-set! root [:info :test] {:another "object"})
(firebase-set! root :age 100)

(firebase-push! root :messages {:message "hello"})



