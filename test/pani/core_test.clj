(ns pani.core-test
  (:require [clojure.test :refer :all]
            [pani.clojure.core :as p]))

(def firebase-url "https://blazing-fire-1915.firebaseio.com")

(deftest serialize-test
  ;; keywords -> strings
  (let [orig {:hello "world" :bye "world"}
        tran (p/serialize orig)]
    (is (contains? tran "hello"))
    (is (not (contains? tran :hello))))
  ;; unchanged
  (doseq [x [true [1 2 3 4] 150 "hello"]]
    (is (= x (p/serialize x)))))

(deftest hydrate-test
  ;; strings -> keywords
  (let [orig {"hello" "world" "bye" "world"}
        tran (p/hydrate orig)]
    (is (contains? tran :hello))
    (is (not (contains? tran "hello"))))
  ;; unchanged
  (doseq [x [true [1 2 3 4] 150 "hello"]]
    (is (= x (p/hydrate x)))))

(deftest get-in-test
  (let [r (p/connect "https://some-app.firebaseio.com/")]
    (is (= (p/key (p/get-in r [:info :world])) "world"))
    (is (= (p/key (p/parent (p/get-in r [:info :world]))) "info"))
    (is (= (p/key (p/get-in r :age)) "age"))))

(deftest reset-test!
  (let [path (mapv str [(rand-int 500) (rand-int 500)])
        ref  (p/get-in (p/connect firebase-url) path)
        val  (rand-int 1000)
        p    (promise)]
    (p/listen-to ref :value #(deliver p %))
    (p/reset! ref val)
    (is (= [(last path) val] @p))))

(deftest conj-test!
  (let [path (mapv str [(rand-int 500) (rand-int 500)])
        ref  (p/get-in (p/connect firebase-url) path)
        val  (rand-int 1000)
        p1   (promise)
        p2   (promise)]
    (p/listen-to ref :child-added #(deliver p1 %))
    (p/conj! ref val)
    (is (= val (last @p1)))
    ;; FIXME times out.. probably also a persistence issue
    ;;
    ;; (p/listen-to ref (first @p1) :value #(deliver p2 %))
    ;; (is (= @p1 @p2))
    ))

(deftest swap!-test
  (let [path (mapv str [(rand-int 500) (rand-int 500)])
        ref  (p/get-in (p/connect firebase-url) path)
        val  (rand-int 1000)
        p    (promise)]
    (p/listen-to ref :value #(deliver p %))
    (p/swap! ref vector val 512)
    (is (= [(last path) [nil val 512]] @p))))
