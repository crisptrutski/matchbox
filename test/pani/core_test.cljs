(ns pani.core-test
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var)])
  (:require [cemerick.cljs.test :as t]
            [pani.core :as pani]))

(enable-console-print!)

(deftest creates-root
  (let [r (pani/root "https://some-app.firebaseio.com/")]
    (is (not (nil? r)))))

(deftest can-walk
  (let [r (pani/root "https://some-app.firebaseio.com/")
        c (pani/walk-root r [:info :world])
        d (pani/walk-root r :age)]
    (is (= (pani/name c) "world"))
    (is (= (-> c pani/parent pani/name) "info"))
    (is (= (pani/name d) "age"))
    (is (nil? (pani/parent r)))))
