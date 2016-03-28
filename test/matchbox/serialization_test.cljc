(ns matchbox.serialization-test
  #?(:cljs
     (:require-macros
       [cemerick.cljs.test :refer [deftest is testing done]]
       [cljs.core.async.macros :refer [go]]))
  (:require
    #?(:clj [clojure.test :refer [deftest is testing]]
        :cljs [cemerick.cljs.test])
    [matchbox.core :as m]
    [matchbox.testing :refer [db-uri random-ref]]
    [matchbox.testing
     #?@(:clj [:refer [is=
                       round-trip=
                       round-trip<
                       block-test]]
         :cljs [:refer-macros [is=
                               round-trip=
                               round-trip<
                               block-test]
                :include-macros true])]
    [#?(:clj clojure.core.async
        :cljs cljs.core.async)
     :refer [chan <! >! #?(:clj go)]]))

;; serialize / deserialize (specs)

#?(:clj
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
    (is (= ":a" (m/serialize :a))))))

#?(:clj
(deftest hydrate-test
  ;; map keys from strings -> keywords
  (let [orig {"hello" "world" "bye" "world"}
        tran (m/hydrate orig)]
    (is (contains? tran :hello))
    (is (not (contains? tran "hello"))))
  ;; values are unchanged
  (testing "bool, long, float, vector, string, keyword, set, list"
    (doseq [x [true [1 2 3 4] 150.0 "hello" :a #{1 2 3} '(3 2 1)]]
      (is (= x (m/hydrate x)))))))

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
                 #?(:clj (is (instance? Double value))
                    :cljs (is (= js/Number (type value)))))
    ;; Unless the decimal portion is explicit..
    (round-trip< 4.0M value
                 #?(:clj (is (instance? Double value))
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
