(ns matchbox.atom-test
  #+cljs (:require-macros [cemerick.cljs.test :refer [is deftest done]])
  (:require #+cljs [cemerick.cljs.test :as t]
            #+clj [clojure.test :as t :refer [deftest is testing]]
            [matchbox.core :as m]
            [matchbox.atom :as a :refer [#+clj -deref #+clj -swap!]]
            [matchbox.registry :as mr]))

(def r (m/connect "https://luminous-torch-5788.firebaseio.com/atom-test"))

(def c1 (m/get-in r "1"))
(def c2 (m/get-in r "2"))
(def c3 (m/get-in r "3"))
(def c4 (m/get-in r "4"))
(def c5 (m/get-in r "5"))
(def c6 (m/get-in r "6"))

;; TODO: tests (by type)
;; 1. data read implicitly on creating (with/without cache)
;; 2. date writen implicitly on creating (with/without cache)
;; 3. remote updates (deep, shallow) sync localy
;; 4. local updates (depp, shallow) sync remotely
;; 5. unlinking works

;; FIXME: figure out agnostic pattern for async tests (promise vs ^:async done)
;; FIXME: figure out agnostic pattern for running after next sync (promise vs cps?)
#+clj
(defmacro once-synced
  [& body]
  ;; Assume round-trip is done once we can pull a root value
  `(let [p# (promise)]
     (m/deref r (fn [_#] (deliver p# 1)))
     @p#
     ~@body))

#+clj
(deftest rough-n-ugly-test
  (testing "hammer a bunch of atoms haphazardly"
    (m/reset! r nil)
    ;; we can't block in JS, so this empty form won't make sense there
    (once-synced)

    (def rnd (rand))

    (def atom-1 (a/brute-atom c1))
    (def atom-2 (a/brute-atom c2 (atom {:initial "state"})))
    (def atom-3 (a/reset-atom c3))
    (def atom-4 (a/reset-atom c4 (atom {:data rnd})))
    (def atom-5 (a/merge-atom c5))
    (def atom-6 (a/merge-atom c6 (atom {:some {:inital "data"}, :here "because"})))

    (once-synced
     ;; white swapping around helped with alignment, makes error messages confusing
     (is (= (-deref atom-1) nil))
     (is (= (-deref atom-2) {:initial "state"}))
     (is (= (-deref atom-3) nil))
     (is (= (-deref atom-4) {:data rnd}))
     (is (= (-deref atom-5) nil))
     (is (= (-deref atom-6) {:some {:inital "data"}, :here "because"})))

    (swap! atom-1 assoc :a "write")
    (swap! atom-2 assoc :a "write")
    (-swap! atom-3 assoc :a "write")
    (-swap! atom-4 assoc :a "write")
    (-swap! atom-5 assoc :a "write")
    (-swap! atom-6 assoc :a "write")

    (once-synced
     (is (= (-deref atom-1) {:a "write"}))
     (is (= (-deref atom-2) {:initial "state", :a "write"}))
     (is (= (-deref atom-3) {:a "write"}))
     (is (= (-deref atom-4) {:data rnd, :a "write"}))
     (is (= (-deref atom-5) {:a "write"}))
     (is (= (-deref atom-6) {:some {:inital "data"}, :here "because", :a "write"})))

    (m/reset-in! c6 :b 40)

    (once-synced
     (is (= (-deref atom-6) {:some {:inital "data"}, :here "because", :a "write", :b 40})))

    (a/unlink! atom-1)
    (a/unlink! atom-2)
    (a/unlink! atom-3)
    (a/unlink! atom-4)
    (a/unlink! atom-5)
    (a/unlink! atom-6)

    (m/reset-in! c6 :b 4)

    (once-synced
     (is (= (-deref atom-6) {:some {:inital "data"}, :here "because", :a "write", :b 40}))
     (let [p (promise)]
       (m/deref-in c6 :b #(deliver p (second %)))
       (is (= 4 @p))))

    (-swap! atom-6 assoc :b 50)

    (once-synced
     (is (= (:b (-deref atom-6)) 50))
     (let [p (promise)]
       (m/deref-in c6 :b #(deliver p (second %)))
       (is (= 4 @p))))

    ;; should rather assert no listeners
    (mr/disable-listeners!)))
