(ns pani.core-test
  (:require-macros [cemerick.cljs.test :refer [is deftest with-test run-tests testing test-var done]]
                   [cljs.core.async.macros :refer [go go-loop]])
  (:require [cemerick.cljs.test :as t]
            [cljs.core.async :refer [<!]]
            [pani.cljs.core :as pani]))

(enable-console-print!)

; never trust data at this URL, may not work, change this if it doesn't
(def firebase-url "https://blazing-fire-1915.firebaseio.com")

(deftest creates-root
  (let [r (pani/root firebase-url)]
    (is (not (nil? r)))))

(defn random-root []
  (let [r (pani/root (str firebase-url "/" (rand-int 100000)))]
    (.remove (.onDisconnect r))
    r))

(deftest can-walk
  (let [r (pani/root firebase-url)
        c (pani/walk-root r [:info :world])
        d (pani/walk-root r :age)
        e (pani/walk-root r [])]
    (is (= (pani/name c) "world"))
    (is (= (-> c pani/parent pani/name) "info"))
    (is (= (pani/name d) "age"))
    (is (= e r))
    (is (nil? (pani/parent r)))))

(deftest ^:async value-turnaround
  (let [r (random-root)]
    (pani/bind r :value :age (fn [v]
                               (is (= (:name v) "age"))
                               (is (= (:val v) 10))
                               (done)))
    (pani/set! r :age 10)))

(deftest ^:async value-turnaround-chans
  (let [r (random-root)
        [c _] (pani/bind r :value :age)]
    (go (let [v (<! c)]
          (is (= (:name v) "age")
              (= (:val v) 10)))
        (done))
    (pani/set! r :age 10)))

(deftest ^:async push-turnaround
   (let [r (random-root)]
     (pani/bind r :child_added :messages
                (fn [v]
                  (is (= (:val v) "hello-world"))
                  (done)))
     (pani/push! r :messages "hello-world")))

(deftest ^:async push-turnaround-chans
  (let [r (random-root)
        [c _] (pani/bind r :child_added :messages)]
    (go (let [v (<! c)]
          (is (= (:val v) "hello-world")))
        (done))
    (pani/push! r :messages "hello-world")))


(deftest ^:async listen-emits-lifetime-events
  (let [r (random-root)
        c (pani/listen< r :messages)]
    (go-loop [m (<! c)
              r []]
             (let [r (conj r (first m))]
               (when (= (count r) 3)
                 (is (= r [:child_added :child_changed :child_removed]))
                 (done))
               (recur (<! c) r)))
    (let [rf (pani/push! r :messages "hello world")]
      (pani/set! rf "world hello")
      (pani/remove! rf))))

(deftest ^:async transact-works
  (let [r (random-root)
        r (pani/walk-root r :stuff)
        [v _] (pani/bind r :value [])]
    (go-loop [m (<! v) c []]
             (let [c (conj c (:val m))]
               (when (= (count c) 2)
                 (is (= c [10 11]))
                 (done))
               (recur (<! v) c)))
    (pani/set! r 10)
    (pani/transact! r [] inc)))
