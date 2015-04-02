(ns matchbox.testing
  (:require [matchbox.core :as m]))

(def db-uri "https://luminous-torch-5788.firebaseio.com/")

(defn random-ref []
  (let [ref (m/connect db-uri (str (rand-int 100000)))]
    ;; clear data once connection closed, having trouble on JVM with reflection
    #+cljs
    (-> ref m/on-disconnect m/remove!)
    ref))

#+clj
(defn cljs-env?
  "Take the &env from a macro, and tell whether we are expanding into cljs."
  [env]
  (boolean (:ns env)))

#+clj
(defmacro if-cljs
  "Return then if we are generating cljs code and else for Clojure code.
   https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
  [then else]
  (if (cljs-env? &env) then else))

#+clj
(defmacro block-test
  "Ensuring blocking or continuation, run forms within a go block"
  [& body]
  `(let [complete# (~'chan)]
     (~'go (let [res# (or (try ~@body
                               (if-cljs
                                  '(catch js/Object e# e#)
                                  '(catch Exception e# e#)))
                          true)]
             (~'>! complete# res#)))
     (~'block-or-done complete#)))

(defmacro is=
  "Test next value delivered from channel matches expectation"
  [expect expr]
  `(block-test
    (~'is (= ~expect (~'<! ~expr)))))
