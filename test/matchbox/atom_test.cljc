(ns matchbox.atom-test
  #?(:cljs (:require-macros [cemerick.cljs.test :refer [is deftest done]]))
  (:require
    #?(:cljs [cemerick.cljs.test]
       :clj [clojure.test :refer [deftest is testing]])
    [matchbox.core :as m]
    [matchbox.atom :as a :refer [#?@(:clj [-deref -swap!])]]
    [matchbox.registry :as mr]))

(def r
  (delay
    (let [r (m/connect "https://luminous-torch-5788.firebaseio.com" [(str (rand-int 1000)) "atom-test"])]
      (m/reset! r nil)
      r)))

(def c1 (delay (m/get-in @r "1")))
(def c2 (delay (m/get-in @r "2")))
(def c3 (delay (m/get-in @r "3")))
(def c4 (delay (m/get-in @r "4")))
(def c5 (delay (m/get-in @r "5")))
(def c6 (delay (m/get-in @r "6")))

(defmacro with-promise [binding & body]
  `(let [p# (promise)
         ~binding (fn [a#] (deliver p# a#))]
     ~@body
     @p#))

;; FIXME: port this trick so tests run on CLJS also
#?(:clj
   (defmacro once-synced
     [& body]
     ;; Assume round-trip is done once we can pull a root value then write counter
     `(do (with-promise c# (m/deref @r c#))
          (with-promise c# (m/reset-in! @r :counter (rand) c#))
          ~@body)))

#?(:clj
(deftest rough-n-ugly-test
  (testing "hammer a bunch of atoms haphazardly"
    (m/reset! @r nil)
    ;; we can't block in JS, so this empty form won't make sense there
    (once-synced)

    (def rnd (rand))

    (def atom-1 (a/sync-rw (atom nil) @c1))
    (def atom-2 (a/sync-rw (atom {:initial "state"}) @c2))
    (def atom-3 (a/wrap-atom (atom nil) @c3))
    (def atom-4 (a/wrap-atom (atom {:data rnd}) @c4))
    (def atom-5 (a/wrap-atom (atom nil) @c5))
    (def atom-6 (a/wrap-atom (atom {:some {:inital "data"}, :here "because"}) @c6))

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

    (m/reset-in! @c6 :b 40)

    (once-synced
     (is (= {:some {:inital "data"}, :here "because", :a "write", :b 40}
            (-deref atom-6))))

    (a/unlink! atom-1)
    (a/unlink! atom-2)
    (a/unlink! atom-3)
    (a/unlink! atom-4)
    (a/unlink! atom-5)
    (a/unlink! atom-6)

    (m/reset-in! @c6 :b 4)

    (once-synced
     (is (= {:some {:inital "data"}, :here "because", :a "write", :b 40}
            (-deref atom-6)))
     (let [p (promise)]
       (m/deref-in @c6 :b #(deliver p %))
       (is (= 4 @p))))

    (-swap! atom-6 assoc :b 50)

    (once-synced
     (is (= (:b (-deref atom-6)) 50))
     (let [p (promise)]
       (m/deref-in @c6 :b #(deliver p %))
       (is (= 4 @p))))

    ;; should rather assert no listeners
    (mr/disable-listeners!))))
