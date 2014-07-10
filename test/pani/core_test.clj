(ns pani.core-test
  (:require [clojure.test :refer :all]
            [pani.core :as pani]))

(deftest app->fb-works
  (do
    (let [orig {:hello "world" :bye "world"}
          tran (pani/app->fb orig)]
      (is (contains? tran "hello"))
      (is (not (contains? tran :hello))))

    (let [orig [1 2 3 4]
          tran (pani/app->fb orig)]
      (is (= orig tran)))

    (let [orig true
          tran (pani/app->fb orig)]
      (is (= orig tran)))

    (let [orig 150
          tran (pani/app->fb orig)]
      (is (= orig tran)))
    
    (let [orig "hello"
          tran (pani/app->fb orig)]
      (is (= orig tran)))))


(deftest fb->app-works
  (do
    (let [orig {"hello" "world" "bye" "world"}
          tran (pani/fb->app orig)]
      (is (contains? tran :hello))
      (is (not (contains? tran "hello"))))

    (let [orig [1 2 3 4]
          tran (pani/fb->app orig)]
      (is (= orig tran)))

    (let [orig true
          tran (pani/fb->app orig)]
      (is (= orig tran)))

    (let [orig 150
          tran (pani/fb->app orig)]
      (is (= orig tran)))
    
    (let [orig "hello"
          tran (pani/fb->app orig)]
      (is (= orig tran)))))

(deftest walk-root-works
  (let [r (pani/root "https://some-app.firebaseio.com/")]
    (is (= (pani/name (pani/walk-root r [:info :world])) "world"))
    (is (= (pani/name (pani/parent (pani/walk-root r [:info :world]))) "info"))
    (is (= (pani/name (pani/walk-root r :age)) "age"))))

