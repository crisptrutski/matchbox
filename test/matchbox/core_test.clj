(ns matchbox.core-test
  (:require [clojure.test :refer :all]
            [matchbox.core :as m]))

(def firebase-url "https://luminous-torch-5788.firebaseio.com/")

(deftest serialize-test
  ;; map keys from keywords -> strings
  (let [orig {:hello "world" :bye "world"}
        tran (m/serialize orig)]
    (is (contains? tran "hello"))
    (is (not (contains? tran :hello))))
  ;; values are unchanged
  (testing "bool, long, float, vector, string, keyword, set, list"
    (doseq [x [true [1 2 3 4] 150.0 "hello" :a #{1 2 3} '(3 2 1)]]
      (is (= x (m/serialize x))))))

(deftest hydrate-test
  ;; map keys from strings -> keywords
  (let [orig {"hello" "world" "bye" "world"}
        tran (m/hydrate orig)]
    (is (contains? tran :hello))
    (is (not (contains? tran "hello"))))
  ;; values are unchanged
  (testing "bool, long, float, vector, string, keyword, set, list"
    (doseq [x [true [1 2 3 4] 150.0 "hello" :a #{1 2 3} '(3 2 1)]]
      (is (= x (m/hydrate x))))))

(deftest get-in-test
  (let [r (m/connect "https://some-app.firebaseio.com/")]
    (is (= (m/key (m/get-in r [:info :world])) "world"))
    (is (= (m/key (m/parent (m/get-in r [:info :world]))) "info"))
    (is (= (m/key (m/get-in r :age)) "age"))))

(deftest reset-test!
  (let [ref (rand-ref)
        val (rand-int 1000)
        p   (promise)]
    (m/listen-to ref :value #(deliver p %))
    (m/reset! ref val)
    (is (= [(m/key ref) val] @p))))

(deftest deref-test
  (let [path (mapv str [(rand-int 500) (rand-int 500)])
        ref  (m/get-in (m/connect firebase-url) path)
        val  (rand-int 1000)
        p    (promise)]
    (m/deref ref #(deliver p %))
    (m/reset! ref val)
    (is (= [(last path) val] @p))))

(deftest conj-test!
  (let [path (mapv str [(rand-int 500) (rand-int 500)])
        ref  (m/get-in (m/connect firebase-url) path)
        val  (rand-int 1000)
        p1   (promise)
        p2   (promise)]
    (m/listen-to ref :child-added #(deliver p1 %))
    (m/conj! ref val)
    (is (= val (last @p1)))
    (m/listen-to ref (first @p1) :value #(deliver p2 %))
    (is (= @p1 @p2))))

(deftest swap!-test
  (let [path (mapv str [(rand-int 500) (rand-int 500)])
        ref  (m/get-in (m/connect firebase-url) path)
        val  (rand-int 1000)
        p    (promise)]
    (m/listen-to ref :value #(deliver p %))
    (m/swap! ref vector val 512)
    (is (= [(last path) [nil val 512]] @p))))
