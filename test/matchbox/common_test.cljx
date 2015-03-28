(ns matchbox.common-test
  #+cljs (:require-macros [cemerick.cljs.test :refer [is deftest done block-or-done]])
  (:require #+cljs [cemerick.cljs.test :as t]
            #+clj [clojure.test :as t :refer [deftest is testing]]
            #+clj [cemerick.cljs.test :refer [block-or-done]]
            [matchbox.core :as m]
            [matchbox.async :as ma]
            [matchbox.registry :as mr]
            [matchbox.testing :refer [#+clj block-test random-ref is=]
             #+cljs :include-macros #+cljs true]
            [#+clj clojure.core.async
             #+cljs cljs.core.async
             :refer [chan <! >! go]]))

(defn people-fixtures []
  (let [r (random-ref)]
    (m/remove! r)
    (m/reset-with-priority-in! r :a {:name "Billy" :age 12} 4)
    (m/reset-with-priority-in! r :b {:name "Carly" :age 53} 7.0)
    (m/reset-with-priority-in! r :c {:name "Joel"  :age 18} 3)
    (m/reset-with-priority-in! r :d {:name "Frank" :age 22} 3)
    (m/reset-with-priority-in! r :e {:name "Timmy" :age  2} "b")
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

(def query-fixtures (people-fixtures))

(def query-fixtures-2 (number-fixtures))


(deftest ^:async order-by-value-test
  (testing "Null hypothesis"
    (is= [3.0 9 1 33.2]
         (ma/deref-list< query-fixtures-2)))
  (testing "Orders by value"
    (is= [1 3.0 9 33.2]
         (ma/deref-list< (m/order-by-value query-fixtures-2)))))

(deftest ^:async order-by-priority-test
  (testing "Orders by priority"
    (block-test
     (is (= ["Joel" "Frank" "Billy" "Carly" "Timmy"]
            (->> (m/order-by-priority query-fixtures)
                 (ma/deref-list<)
                 (<!)
                 (map :name)))))))

(deftest ^:async order-by-child-test
  (testing "Orders by child"
    (block-test
     (is (= ["Timmy" "Billy" "Joel" "Frank" "Carly"]
            (->> (m/order-by-child query-fixtures :age)
                 (ma/deref-list<)
                 (<!)
                 (map :name)))))))

(deftest ^:async order-by-key-test
  (testing "Orders by key"
    (block-test
     (is (= ["Billy" "Carly" "Joel" "Frank" "Timmy"]
            (->> (m/order-by-key query-fixtures)
                 (ma/deref-list<)
                 (<!)
                 (map :name)))))))

(deftest ^:async start-at-test
  (testing "Is inclusive"
    (is= [{:age 53, :name "Carly"} {:age 2, :name "Timmy"}]
         (ma/deref-list< (m/start-at query-fixtures 7.0))))
  (testing "Second parameter is a start"
    (is= [{:age 2, :name "Timmy"}]
         (ma/deref-list< (m/start-at query-fixtures 7.0 :c)))))

(deftest ^:async end-at-test
  (testing "Is inclusive"
    (is= [{:age 18, :name "Joel"}
          {:age 22, :name "Frank"}
          {:age 12, :name "Billy"}]
         (ma/deref-list< (m/end-at query-fixtures 4))))
  (testing "Second parameter is an end"
    (is= [{:age 18, :name "Joel"}]
         (ma/deref-list< (m/end-at query-fixtures 3 :c)))))

(deftest ^:async equal-to-test
  (testing "Includes all separate yet equal"
    (is= [{:age 18, :name "Joel"} {:age 22, :name "Frank"}]
         (ma/deref-list< (m/equal-to query-fixtures 3))))
  (testing "Second parameter is a start"
    (is= [{:age 22, :name "Frank"}]
         (ma/deref-list< (m/equal-to query-fixtures 3 :d)))))

(deftest ^:async take-test
  (testing "Takes from the start"
    (is= [{:age 18, :name "Joel"} {:age 22, :name "Frank"}]
         (ma/deref-list< (m/take query-fixtures 2)))))

(deftest ^:async take-last-test
  (testing "Takes from the end"
    (is= [{:age 53, :name "Carly"} {:age 2, :name "Timmy"}]
         (ma/deref-list< (m/take-last query-fixtures 2)))))

;; (deftest ^:async listen-list-test
;;   (testing "Gets called with all children / ordered query result"
;;     ;; Hangs, but only inside test runner. Will debug later
;;     (let [r  (random-ref)
;;           p  (promise)
;;           xs (atom [])
;;           cb (fn [val]
;;                (matchbox.utils/prn val)
;;                (swap! xs conj val)
;;                (matchbox.utils/prn (count @xs))
;;                (if (= (count @xs) 6)
;;                  (deliver p 1)))]
;;       (m/listen-list r cb)
;;       (go
;;         (<! (ma/reset-in!< r :a 1))
;;         (<! (ma/reset-in!< r :b 2))
;;         (<! (ma/reset-in!< r :c 3))
;;         (<! (ma/reset-in!< r :b 2.2))
;;         (<! (ma/dissoc-in!< r :b))
;;         (prn @xs))
;;       #_@p
;;       (is (= [[]
;;               [1]
;;               [1 2]
;;               [1 2 3]
;;               [1 2.2 3]
;;               [1 3]]
;;              @xs))
;;       (mr/disable-listeners!))))
