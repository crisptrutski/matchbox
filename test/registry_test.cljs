(ns pani.registry-test
  (:require-macros [cemerick.cljs.test :refer [is deftest done]])
  (:require [cemerick.cljs.test :as t]
            [pani.registry :as r]))

(reset! r/unsubs {})

(def a-1 (atom nil))
(def a-2 (atom nil))
(def a-3 (atom nil))

(def callback-1 #(swap! a-1 inc))
(def callback-2 #(swap! a-2 inc))
(def callback-3 #(swap! a-3 inc))

(deftest lifecycle-test
  (is (empty? @r/unsubs))

  (reset! a-1 0)
  (reset! a-2 0)
  (reset! a-3 0)

  (r/register-listener "ref-like" "value" callback-1)
  (r/register-listener "ref-like" "value" callback-2)
  (r/register-listener "ref-like" "child_added" callback-1)
  (r/register-listener "ref-ness" "child_removed" callback-3)

  (is (= {"ref-like" {"value" #{callback-1 callback-2}
                      "child_added" #{callback-1}}
          "ref-ness" {"child_removed" #{callback-3}}}
         @r/unsubs))

  (r/disable-listeners! "ref-like" "value" callback-1)
  (is (= {"ref-like" {"value" #{callback-2}
                      "child_added" #{callback-1}}
          "ref-ness" {"child_removed" #{callback-3}}}
         @r/unsubs))

  (r/disable-listeners! "ref-like" "child_added")
  (is (= {"ref-like" {"value" #{callback-2}}
          "ref-ness" {"child_removed" #{callback-3}}}
         @r/unsubs))

  (r/disable-listeners! "ref-ness")
  (is (= {"ref-like" {"value" #{callback-2}}}
         @r/unsubs))

  (r/disable-listeners!)
  (is (= {}
         @r/unsubs))

  (is (= 2 @a-1))
  (is (= 1 @a-2))
  (is (= 1 @a-3)))
