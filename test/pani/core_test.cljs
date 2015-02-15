(ns pani.core-test
  (:require-macros [cemerick.cljs.test :refer [is deftest done]]
                   [cljs.core.async.macros :refer [go go-loop]])
  (:require [cemerick.cljs.test :as t]
            [cljs.core.async :refer [<!]]
            [pani.core :as p]))

(def firebase-url "https://blazing-fire-1915.firebaseio.com")

(defn random-ref []
  (let [ref (p/connect (str firebase-url "/" (rand-int 100000)))]
    ;; clear data once connection closed
    (.. ref onDisconnect remove)
    ref))

;; utils

(deftest serialize-hydrate-test
  (is (= {:a 1, :b ["b" "a"]}
         (p/hydrate
          (p/serialize {"a" 1, "b" #{:a :b}})))))

(deftest kebab->underscore-test
  (is (= "a_cromulent_name" (p/kebab->underscore :a-cromulent-name))))

(deftest underscore->kebab-test
  (is (= :a-tasty-skewer (p/underscore->kebab "a_tasty_skewer"))))

(deftest korks->path-test
  (is (= nil   (p/korks->path nil)))
  (is (= ""    (p/korks->path "")))
  (is (= ""    (p/korks->path [])))
  (is (= "a"   (p/korks->path :a)))
  (is (= "a"   (p/korks->path ["a"])))
  (is (= "a/b" (p/korks->path "a/b")))
  (is (= "a/b" (p/korks->path [:a :b]))))

(deftest key-parent-get-in-test
  (let [root (p/connect firebase-url)
        baby (p/get-in root [:a :b :c])]
    (is (nil? (p/key root)))
    (is (nil? (p/parent root)))
    (is (= "b" (p/key (p/parent baby))))
    (is (= ["b" "a" nil] (map p/key (p/parents baby))))))

(deftest ^:async reset!-test
  (let [ref (random-ref)]
    (p/reset! ref 34)
    (p/deref ref (fn [v] (is (= 34 v)) (done)))))

(deftest ^:async merge!-test
  (let [ref (random-ref)]
    (p/reset! ref {:a 1, :b 2})
    (p/merge! ref {:a 3, :c 9})
    (p/deref ref (fn [v] (is (= {:a 3, :b 2, :c 9} v)) (done)))))

(deftest ^:async conj!-test
  (let [ref (random-ref)
        ;; hack around not being online in test
        seen (atom #{})]
    (p/listen-to ref :child-added (fn [[key value]]
                                    (swap! seen conj value)
                                    (if (= @seen #{34 36}) (done))))

    (p/conj! ref 34)
    (p/conj! ref 36)

    ;; does not work without server connection
    ;; (p/deref ref (fn [v] (is (= [34 36] (vals v))) (done)))

    (js/setTimeout (fn [] (is (not "timeout")) (done)) 1000)))

(deftest ^:async swap!-test
  (let [ref (random-ref)]
    (p/reset! ref 2)
    (p/swap! ref * 9 2)
    (p/deref ref (fn [v] (is (= 36 v)) (done)))))

(deftest ^:async remove!-test
  (let [ref (random-ref)]
    (p/reset! ref 34)
    (p/remove! ref)
    (p/deref ref (fn [v] (is (nil? v)) (done)))))
