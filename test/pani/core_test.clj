(ns pani.core-test
  (:require [clojure.test :refer :all]
            [pani.clojure.core :as p]))

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
