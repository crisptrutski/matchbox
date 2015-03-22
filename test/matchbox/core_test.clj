(ns matchbox.core-test
  (:require [clojure.test :refer :all]
            [matchbox.core :as m]))

;; helpers

(def firebase-url "https://luminous-torch-5788.firebaseio.com/")

(defn- rand-ref []
  (let [path (mapv str [(rand-int 500) (rand-int 500)])]
    (m/get-in (m/connect firebase-url) path)))

(defn round-trip [data]
  (let [p   (promise)
        ref (rand-ref)]
    (m/deref ref #(deliver p (second %)))
    (m/reset! ref data)
    @p))

;;

(deftest serialize-test
  ;; map keys from keywords -> strings
  (let [orig {:hello "world" :bye "world"}
        tran (m/serialize orig)]
    (is (contains? tran "hello"))
    (is (not (contains? tran :hello))))
  ;; values are unchanged
  (testing "bool, long, float, vector, string, set, list"
    (doseq [x [true [1 2 3 4] 150.0 "hello" #{1 2 3} '(3 2 1)]]
      (is (= x (m/serialize x)))))
  (testing "keyword"
    (is (= ":a" (m/serialize :a)))))

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

(defrecord ARecord [a-field])
(deftype AType [an-attr])

(deftest roundtrip-test
  (testing "numbers"
    (is (= 42 (round-trip 42)))
    (is (= 41.3 (round-trip 41.3)))
    ;; Floating point survives "JSON" phase
    (is (= 41.0 (round-trip 41.0)))
    ;; Cast down from extended types though
    (is (= 3 (round-trip 3N)))
    ;; Strangely BigDecimal can cast all the way down to a long
    (is (instance? java.lang.Long (round-trip 4M)))
    ;; Unless the decimal portion is explicit..
    (is (instance? java.lang.Double (round-trip 4.0M)))
    (is (= 4.3E90 (round-trip 4.3E90M))))

  (testing "strings"
    (is (= "feeling myself" (round-trip "feeling myself"))))

  (testing "keyword"
    (is (= :a (round-trip :a)))
    (is (= :ns/key (round-trip :ns/key))))

  (testing "map"
    (is (= {:a 1, :b 2}
           (round-trip {"a" 1, :b 2})))
    (is (= {:nested {:data 4}}
           (round-trip {"nested" {"data" 4}}))))

  (testing "list"
    (is (= [1 2 3 4]
           (round-trip '(1 2 3 4)))))

  (testing "vector"
    (is (= [1 2 3 4]
           (round-trip [1 2 3 4]))))

  (testing "set"
    (let [result (round-trip #{1 2 3 4})]
      (is (vector? result))
      (is (= [1 2 3 4] (sort result)))))

  (testing "richer data"
    (is (= {:a-field [2]} (round-trip (ARecord. [2]))))
    (is (= {:an_attr [3]} (round-trip (AType. #{3}))))))

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
