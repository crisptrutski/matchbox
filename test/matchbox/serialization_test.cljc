(ns matchbox.serialization-test
  #?(:cljs
     (:require-macros
       [cljs.test :refer [deftest is testing async]]
       [cljs.core.async.macros :refer [go]]))
  (:require
    [matchbox.core :as m]
    [matchbox.testing :refer [db-uri random-ref]]
    [matchbox.testing #?@(:clj  [:refer [is= round-trip= round-trip<]]
                          :cljs [:refer-macros [is= round-trip= round-trip<] :include-macros true])]
    #?(:clj [clojure.test :refer [deftest is testing]] :cljs [cljs.test])
    [#?(:clj clojure.core.async :cljs cljs.core.async) :refer [chan <! >! #?(:clj go)]]
    [matchbox.serialization.plain :as plain]
    [matchbox.serialization.keyword :as keyword]
    [matchbox.serialization.sorted :as sorted]
    [matchbox.serialization.stable :as stable])
#?(:clj (:import (java.util HashMap))))

;; serialize / deserialize (specs)

(deftest serialize-test
  ;; map keys from keywords -> strings
  (let [orig {:hello "world" :bye "world"}
        tran #?(:clj (m/serialize orig)
                :cljs (js->clj (m/serialize orig)))]
    (is (contains? tran "hello"))
    (is (not (contains? tran :hello))))
  ;; values are unchanged
  (testing "bool, long, float, vector, string, set, list"
    (doseq [x [true [1 2 3 4] 150.0 "hello" #{1 2 3} '(3 2 1)]]
      (is (= #?(:clj x :cljs (js->clj (clj->js x)))
             #?(:clj (m/serialize x)
                :cljs (js->clj (m/serialize x)))))))
  (testing "keyword"
    (is (= ":a" (m/serialize :a)))))

(deftest hydrate-test
  ;; map keys from strings -> keywords
  (let [orig {"hello" "world" "bye" "world"}
        tran (m/hydrate orig)]
    (is (contains? tran :hello))
    (is (not (contains? (set (keys tran)) "hello"))))
  ;; values are unchanged
  (testing "bool, long, float, vector, string, keyword, set, list"
    (doseq [x [true [1 2 3 4] 150.0 "hello" :a #{1 2 3} '(3 2 1)]]
      (is (= x (m/hydrate x))))))

(deftest serialize-hydrate-test
  (is (= {:a 1, :b #?(:cljs [:b :a] :clj #{:a :b})}
         (m/hydrate
           (m/serialize {"a" 1, "b" #{:a :b}})))))


;; round trip (integration)

(defrecord ARecord [a-field])
(deftype AType [an-attr])

(deftest roundtrip-test
  (testing "numbers"
    (round-trip= 42 42)
    (round-trip= 41.3 41.3)
    ;; Floating point survives "JSON" phase
    (round-trip= 41.0 41.0)
    ;; Cast down from extended types though
    (round-trip= 3.0 3N)
    (round-trip< 4M value
      #?(:clj  (is (instance? Double value))
         :cljs (is (= js/Number (type value)))))
    ;; Unless the decimal portion is explicit..
    (round-trip< 4.0M value
      #?(:clj  (is (instance? Double value))
         :cljs (is (= js/Number (type value)))))
    #?(:clj
       (round-trip= 4.3E90 4.3E90M)))

  (testing "strings"
    (round-trip= "feeling myself" "feeling myself"))

  (testing "keyword"
    (round-trip= :a :a)
    (round-trip= :ns/key :ns/key))

  (testing "map"
    (round-trip= {:a 1, :b 2}
                 {"a" 1, :b 2})
    (round-trip= {:nested {:data 4}}
                 {"nested" {"data" 4}}))

  (testing "list"
    (round-trip= [1 2 3 4] '(1 2 3 4)))

  (testing "vector"
    (round-trip= [1 2 3 4] [1 2 3 4]))

  (testing "set"
    (round-trip< #{1 2 3 4} result
      (is (vector? result))
      (is (= [1 2 3 4] (sort result)))))

  (testing "richer data"
    (round-trip= {:a-field [2]} (ARecord. [2]))
    (round-trip= {:an_attr [3]} (AType. #{3}))))


(def ref-map
  (reduce
    (fn [#?(:clj ^HashMap acc :cljs acc) [k v]]
      #?(:clj (.put acc k v) :cljs (aset acc k v))
      acc)
    (let [base {"a" {"b" {"c" [{"d" "e" "f" ":f"}]}}}]
      #?(:clj (HashMap. base) :cljs (clj->js base)))
    (map vector (map (comp str char) (range 98 123)) (range 25))))

(deftest custom-serializers-test
  (testing "Keys as expected"
    (is (every? string? (keys (plain/hydrate ref-map))))
    (is (every? keyword? (keys (keyword/hydrate ref-map))))
    (is (every? keyword? (keys (sorted/hydrate ref-map))))
    (is (every? keyword? (keys (stable/hydrate ref-map)))))
  (testing "Values as expected"
    (is (= ":f" (get-in (plain/hydrate ref-map) ["a" "b" "c" 0 "f"])))
    (is (= :f (get-in (keyword/hydrate ref-map) [:a :b :c 0 :f])))
    (is (= :f (get-in (sorted/hydrate ref-map) [:a :b :c 0 :f])))
    (is (= :f (get-in (stable/hydrate ref-map) [:a :b :c 0 :f]))))
  (testing "Sorted as expected"
    (is (= (sort (keys (sorted/hydrate ref-map))) (keys (sorted/hydrate ref-map))))
    (is (= (sort (keys (stable/hydrate ref-map))) (keys (stable/hydrate ref-map))))))
