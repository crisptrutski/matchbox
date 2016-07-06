(ns matchbox.core-test
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]
    [cljs.test :refer [is deftest async]])
  (:require
    [cljs.core.async :refer [<!]]
    [cljs.test]
    [matchbox.core :as m]
    [matchbox.testing :refer [random-ref]]))

(deftest reset!-test
  (async done
    (let [ref (random-ref)]
      (m/reset! ref 34)
      (m/deref ref (fn [v] (is (= 34 v)) (done))))))

(deftest merge!-test
  (async done
    (let [ref (random-ref)]
      (m/reset! ref {:a 1, :b 2})
      (m/merge! ref {:a 3, :c 9})
      (m/deref ref (fn [v] (is (= {:a 3, :b 2, :c 9} v)) (done))))))

(deftest conj!-test
  (async done
    (let [ref (random-ref)
          ;; hack around not being online in test
          seen (atom #{})]
      (m/listen-to ref :child-added (fn [[key value]]
                                      (swap! seen conj value)
                                      (if (= @seen #{34 36}) (done))))

      (m/conj! ref 34)
      (m/conj! ref 36)

      ;; does not work without server connection
      ;; (m/deref ref (fn [v] (is (= [34 36] (vals v))) (done)))

      (js/setTimeout (fn [] (when (= @seen #{34 36}) (done))) 2000))))

(deftest swap!-test
  (async done
    (let [ref (random-ref)]
      (m/reset! ref 2)
      (m/swap! ref * 9 2)
      (m/deref ref (fn [v] (is (= 36 v)) (done))))))

(deftest remove!-test
  (async done
    (let [ref (random-ref)]
      (m/reset! ref 34)
      (m/remove! ref)
      (m/deref ref (fn [v] (is (nil? v)) (done))))))

(deftest set-priority!-test
  (async done
    (let [ref (random-ref)
          child-1 (m/get-in ref "a")
          child-2 (m/get-in ref "b")
          child-3 (m/get-in ref "c")]
      (m/reset! child-1 1)
      (m/reset! child-2 2)
      (m/reset! child-3 3)
      ;; order is:
      ;; 1st: no priority
      ;; 2nd: number as priority
      ;; 3rd: string as priority
      ;; (sorts by name on equality)
      (m/set-priority! child-1 "a")
      (m/set-priority-in! ref (m/key child-2) 0)
      (m/deref-list ref (fn [v] (is (= [3 2 1] v)) (done))))))

(deftest reset-with-priority!-test
  (async done
    (let [ref (random-ref)]
      (m/reset-with-priority-in! ref "a" 1 "a")
      (m/reset-with-priority-in! ref "b" 2 0)
      (m/reset-in! ref "c" 3)
      (m/deref-list ref (fn [v] (is (= [3 2 1] v)) (done))))))

(deftest disconnect!-reconnect!-test
  ;; default is connected
  (is (m/connected?))
  ;; do things in twos to show idempotent
  (m/disconnect!)
  (is (not (m/connected?)))
  (m/disconnect!)
  (is (not (m/connected?)))
  (m/reconnect!)
  (is (m/connected?))
  (m/reconnect!)
  (is (m/connected?)))

(deftest auth-anon-test
  (async done
    (let [ref (random-ref)]
      (is (nil? (m/auth-info ref)))
      (m/auth-anon ref
        (fn [error auth-data]
          (is (not error))
          (is (= "anonymous" (:provider auth-data)))
          (is (= (m/auth-info ref) auth-data))
          (m/unauth ref)
          (is (nil? (m/auth-info ref)))
          (done))))))
