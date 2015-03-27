(ns matchbox.common-test
  #+cljs (:require-macros [cemerick.cljs.test :refer [is deftest done]])
  (:require #+cljs [cemerick.cljs.test :as t]
            #+clj [clojure.test :as t :refer [deftest is testing]]
            [matchbox.core :as m]
            [matchbox.registry :as mr]))

(def db-uri "https://luminous-torch-5788.firebaseio.com/")

(defn random-ref []
  (let [ref (m/connect db-uri (str (rand-int 100000)))]
    ;; clear data once connection closed, having trouble on JVM with reflection
    #+cljs
    (-> ref m/on-disconnect m/remove!)
    ref))

;; setup some read-only fixtures to share between tests
(def query-fixtures (random-ref))
(m/remove! query-fixtures)
(m/reset-with-priority-in! query-fixtures :a {:name "Billy" :age 12} 4)
(m/reset-with-priority-in! query-fixtures :b {:name "Carly" :age 53} 7.0)
(m/reset-with-priority-in! query-fixtures :c {:name "Joel"  :age 18} 3)
(m/reset-with-priority-in! query-fixtures :d {:name "Frank" :age 22} 3)
(m/reset-with-priority-in! query-fixtures :e {:name "Timmy" :age  2} "b")

(def query-fixtures-2 (random-ref))
(m/remove! query-fixtures-2)
(m/conj! query-fixtures-2 3.0)
(m/conj! query-fixtures-2 9)
(m/conj! query-fixtures-2 1)
(m/conj! query-fixtures-2 33.2)

;; This won't work for CLJS, but still struggling for better solution

#+clj
(defmacro deliver-cb [& body]
  `(let [p# (promise)
         ~'cb #(deliver p# %)]
     ~@body
     @p#))

(deftest order-by-value-test
  (testing "Null hypothesis"
    (is (= [3.0 9 1 33.2]
           (deliver-cb
            (m/deref-list query-fixtures-2 cb)))))
  (testing "Orders by value"
    (is (= [1 3.0 9 33.2]
           (deliver-cb
            (m/deref-list (m/order-by-value query-fixtures-2) cb))))))

(deftest order-by-priority-test
  (testing "Orders by priority"
    (is (= ["Joel" "Frank" "Billy" "Carly" "Timmy"]
           (map :name
                (deliver-cb
                 (m/deref-list (m/order-by-priority query-fixtures) cb)))))))

(deftest order-by-child-test
  (testing "Orders by child"
    (is (= ["Timmy" "Billy" "Joel" "Frank" "Carly"]
           (map :name
                (deliver-cb
                 (m/deref-list (m/order-by-child query-fixtures :age) cb)))))))

(deftest order-by-key-test
  (testing "Orders by key"
    (is (= ["Billy" "Carly" "Joel" "Frank" "Timmy"]
           (map :name
                (deliver-cb
                 (m/deref-list (m/order-by-key query-fixtures) cb)))))))

(deftest start-at-test
  (testing "Is inclusive"
    (is (= [{:age 53, :name "Carly"} {:age 2, :name "Timmy"}]
           (deliver-cb
            (m/deref-list (m/start-at query-fixtures 7.0) cb)))))
  (testing "Second parameter is a start"
    (is (= [{:age 2, :name "Timmy"}]
           (deliver-cb
            (m/deref-list (m/start-at query-fixtures 7.0 :c) cb))))))

(deftest end-at-test
  (testing "Is inclusive"
    (is (= [{:age 18, :name "Joel"}
            {:age 22, :name "Frank"}
            {:age 12, :name "Billy"}]
           (deliver-cb
            (m/deref-list (m/end-at query-fixtures 4) cb)))))
  (testing "Second parameter is an end"
    (is (= [{:age 18, :name "Joel"}]
           (deliver-cb
            (m/deref-list (m/end-at query-fixtures 3 :c) cb))))))

(deftest equal-to-test
  (testing "Includes all separate yet equal"
    (is (= [{:age 18, :name "Joel"} {:age 22, :name "Frank"}]
           (deliver-cb
            (m/deref-list (m/equal-to query-fixtures 3) cb)))))
  (testing "Second parameter is a start"
    (is (= [{:age 22, :name "Frank"}]
           (deliver-cb
            (m/deref-list (m/equal-to query-fixtures 3 :d) cb))))))

(deftest take-test
  (testing "Takes from the start"
    (is (= [{:age 18, :name "Joel"} {:age 22, :name "Frank"}]
           (deliver-cb
            (m/deref-list (m/take query-fixtures 2) cb))))))

(deftest take-last-test
  (testing "Takes from the end"
    (is (= [{:age 53, :name "Carly"} {:age 2, :name "Timmy"}]
           (deliver-cb
            (m/deref-list (m/take-last query-fixtures 2) cb))))))

(deftest listen-list-test
  (testing "Gets called with all children / ordered query result"
    ;; Hangs, but only inside test runner. Will debug later
    #_(let [r  (random-ref)
            p  (promise)
            xs (atom [])
            cb (fn [val]
                 (matchbox.utils/prn @xs)
                 (swap! xs conj val)
                 (if (= (count @xs) 6)
                   (deliver p 1)))]
        (m/listen-list r cb)
        (m/reset-in! r :a 1)
        (m/reset-in! r :b 2)
        (m/reset-in! r :c 3)
        (m/reset-in! r :b 2.2)
        (m/dissoc-in! r :b)
        @p
        (is (= [[]
                [1]
                [1 2]
                [1 2 3]
                [1 2.2 3]
                [1 3]]
               @xs))
        (mr/disable-listeners!))))
