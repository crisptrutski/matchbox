(ns firebase.core
  #+clj (:require [clojure.core.async :refer [<! >! chan go go-loop]])
  #+cljs (:require [cljs.core.async :refer [<! >! chan]])
  #+cljs (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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

;; ;;;;;;;;;;;;;;;;;;;
;; Example usage
;;
;; Create a root which represents a firebase root
(def r (firebase.core/root "https://blazing-fire-1915.firebaseio.com/"))

(defn print-val [& xs]
  (.log js/console (pr-str xs)))

;; Bind a function to a few values inside our root, the binding is modeled after core/get-in
;;
(firebase.core/bind r :value [:info :world] print-val)
(firebase.core/bind r :value [:info :test] print-val)
(firebase.core/bind r :value :age print-val)

;; get a channel instead of passing a callback
;;
(let [my-chan (firebase.core/bind r :value [:info :test])]
  (go-loop [val (<! my-chan)]
           (.log js/console "Got new value inside go loop:" (pr-str val))
           (recur (<! my-chan))))

(firebase.core/bind r :value :messages print-val)
(firebase.core/bind r :child_added :messages print-val)

(firebase.core/bind r :child_added :messages
                    (fn [msg]
                      (.log js/console (pr-str msg))
                      (.log js/console "Message: " (:message msg))))

;; Finally set a few values
;;
(firebase.core/set! r {:name "James Bond"
                     :age 94
                     :messages {}
                     :info {:test "hello"
                            :world "bye"}}) 

(firebase.core/set! r [:info :world] "welcome")
(firebase.core/set! r [:info :test] {:another "object"})
(firebase.core/set! r :age 100)

(firebase.core/push! r :messages {:message "hello"})

(def messages-root (walk-root r :messages))

(firebase.core/bind messages-root :child_added []
               (fn [msg]
                 (.log js/console "New message:" (:message msg))))

(let [c (firebase.core/bind messages-root :child_added [])]
  (go-loop [msg (<! c)]
           (.log js/console "New message (go-loop):" (:message msg))
           (recur (<! c))))
