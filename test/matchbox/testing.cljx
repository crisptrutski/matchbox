(ns matchbox.testing
  #+cljs (:require-macros [cemerick.cljs.test :refer [block-or-done]])
  (:require [matchbox.core :as m]
            #+clj [cemerick.cljs.test :refer [block-or-done]]
            [#+clj clojure.core.async
             #+cljs cljs.core.async
             :refer [chan <! >! go]]))

(def db-uri "https://luminous-torch-5788.firebaseio.com/")

(defn random-ref []
  (let [ref (m/connect db-uri (str (rand-int 100000)))]
    ;; clear data once connection closed, having trouble on JVM with reflection
    #+cljs
    (-> ref m/on-disconnect m/remove!)
    ref))

;; #+clj
;; (defmacro deliver-cb [& body]
;;   `(let [p# (promise)
;;          ~'cb #(deliver p# %)]
;;      ~@body
;;      @p#))

;; #+clj
;; (defmacro eval-async
;;   "Support running expectations against exactly one async value"
;;   [expr & expectations]
;;   `(let [#+clj p# #+clj (promise)
;;          ~'*cb*
;;          #+cljs #(let [~'*res* %]
;;                    ~@expectations
;;                    (done))
;;          #+clj #(deliver p# %)]
;;      ~expr
;;      #+clj (let [~'*res* @p#]
;;              ~@expectations)))

#+clj
(defmacro block-test [& body]
  `(let [complete# (chan)]
     (go (let [res# (or (try ~@body (catch #+cljs js/Object #+clj Exception e# e#))
                        true)]
           (~'>! complete# res#)))
     (block-or-done complete#)))

(defmacro is= [expect expr]
  `(block-test
    (~'is (= ~expect (<! ~expr)))))
