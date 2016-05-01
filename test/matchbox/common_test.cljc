(ns matchbox.common-test
  #?(:cljs
     (:require-macros
       [cljs.test :refer [deftest is testing]]
       [cljs.core.async.macros :refer [go]]))
  (:require
    #?(:cljs [cljs.test]
       :clj [clojure.test :refer [deftest is testing]])
            [matchbox.core :as m]
            [matchbox.async :as ma]
            [matchbox.testing :refer [db-uri random-ref #?@(:clj [is= block-test])]
             #?@(:cljs [:refer-macros [is= block-test] :include-macros true])]
            [#?(:clj  clojure.core.async
                :cljs cljs.core.async)
             :refer [chan <! >! #?(:clj go)]]))

(deftest reset-test!
  (testing "Set value with reset!"
    (let [ref (random-ref)
          val (rand-int 1000)]
      (m/reset! ref val)
      (is= val (ma/deref< ref)))))

;; Rely on implicit testing via other tests for cljs
#?(:clj
   (deftest deref-test
     (testing "Read value with deref"
       (let [ref (random-ref)
             val (rand-int 1000)
             p (promise)]
         (m/deref ref #(deliver p %))
         (m/reset! ref val)
         (is (= val @p))))))

;; Must still be ported to cljs
#?(:clj
   (deftest conj-test!
     (let [ref (random-ref)
           val (rand-int 1000)
           p1 (promise)
           p2 (promise)]
       (m/listen-to ref :child-added #(deliver p1 %))
       (is (string? (m/conj! ref val)))
       (is (= val (last @p1)))
       (m/listen-to ref (first @p1) :value #(deliver p2 %))
       (is (= @p1 @p2)))))

(deftest swap!-test
  (testing "Transact value with swap"
    (let [ref (random-ref)
          val (rand-int 1000)]
      (m/swap! ref vector val 512)
      (is= [nil val 512] (ma/deref< ref)))))

(deftest key-parent-get-in-test
  (let [root (m/connect db-uri)
        baby (m/get-in root [:a :b :c])]
    (is (nil? (m/key root)))
    (is (nil? (m/parent root)))
    (is (= "b" (m/key (m/parent baby))))
    (is (= "z" (m/key (m/get-in root :z))))
    (is (= ["b" "a" nil] (map m/key (m/parents baby))))))

(defn people-fixtures []
  (let [r (random-ref)]
    (m/remove! r)
    (m/reset-with-priority-in! r :a {:name "Billy" :age 12} 4)
    (m/reset-with-priority-in! r :b {:name "Carly" :age 53} 7.0)
    (m/reset-with-priority-in! r :c {:name "Joel" :age 18} 3)
    (m/reset-with-priority-in! r :d {:name "Frank" :age 22} 3)
    (m/reset-with-priority-in! r :e {:name "Timmy" :age 2} "b")
    r))

(defn number-fixtures []
  (let [r (random-ref)]
    (m/remove! r)
    (m/conj! r 3.0)
    (m/conj! r 9)
    (m/conj! r 1)
    (m/conj! r 33.2)
    r))

;; create read-only fixtures statically

(def query-fixtures (delay (people-fixtures)))

(def query-fixtures-2 (delay (number-fixtures)))

#?(:cljs (enable-console-print!))

(deftest export-test
  (testing "Includes metadata"
    (let [r (random-ref)]
      (m/reset-with-priority-in! r :a "A" 32.3)
      (m/reset-with-priority-in! r [:b :c] "BC" "prior")
      (is= {:a {:.priority 32.3
                :.value "A"},
            :b {:c {:.priority "prior"
                    :.value "BC"}}}
           (ma/export< r)))))

(deftest priority-test
  (testing "Includes metadata"
    (let [r (random-ref)]
      (m/reset-with-priority! r {:some 'data} "s3krit")
      (is= "s3krit" (ma/priority< r)))))

(deftest order-by-value-test-a
  (testing "Null hypothesis"
    (is= [3.0 9 1 33.2] (ma/deref-list< @query-fixtures-2))))

(deftest order-by-value-test-b
  (testing "Orders by value"
    (is= [1 3.0 9 33.2] (ma/deref-list< (m/order-by-value @query-fixtures-2)))))

(deftest order-by-priority-test
  (testing "Orders by priority"
    (is= ["Joel" "Frank" "Billy" "Carly" "Timmy"]
       (->> (m/order-by-priority @query-fixtures)
            (ma/deref-list<) (<!)
            (map :name) (go)))))

(deftest order-by-child-test
  (testing "Orders by child"
    (is= ["Timmy" "Billy" "Joel" "Frank" "Carly"]
         (->> (m/order-by-child @query-fixtures :age)
              (ma/deref-list<) (<!)
              (map :name) (go)))))

(deftest order-by-key-test
  (testing "Orders by key"
    (is= ["Billy" "Carly" "Joel" "Frank" "Timmy"]
         (->> (m/order-by-key @query-fixtures)
              (ma/deref-list<) (<!)
              (map :name) (go)))))

(deftest start-at-test-a
  (testing "Is inclusive"
    (is= [{:age 53, :name "Carly"} {:age 2, :name "Timmy"}]
         (ma/deref-list< (m/start-at @query-fixtures 7.0)))))

(deftest start-at-test-b
  (testing "Second parameter is a start"
    (is= [{:age 2, :name "Timmy"}]
         (ma/deref-list< (m/start-at @query-fixtures 7.0 :c)))))

(deftest end-at-test-a
  (testing "Is inclusive"
    (is= [{:age 18, :name "Joel"}
          {:age 22, :name "Frank"}
          {:age 12, :name "Billy"}]
         (ma/deref-list< (m/end-at @query-fixtures 4)))))

(deftest end-at-test-b
  (testing "Second parameter is an end"
    (is= [{:age 18, :name "Joel"}]
         (ma/deref-list< (m/end-at @query-fixtures 3 :c)))))

(deftest equal-to-test-a
  (testing "Includes all separate yet equal"
    (is= [{:age 18, :name "Joel"} {:age 22, :name "Frank"}]
         (ma/deref-list< (m/equal-to @query-fixtures 3)))))

(deftest equal-to-test-b
  (testing "Second parameter is a start"
    (is= [{:age 22, :name "Frank"}]
         (ma/deref-list< (m/equal-to @query-fixtures 3 :d)))))

(deftest take-test
  (testing "Takes from the start"
    (is= [{:age 18, :name "Joel"} {:age 22, :name "Frank"}]
         (ma/deref-list< (m/take @query-fixtures 2)))))

(deftest take-last-test
  (testing "Takes from the end"
    (is= [{:age 53, :name "Carly"} {:age 2, :name "Timmy"}]
         (ma/deref-list< (m/take-last @query-fixtures 2)))))
