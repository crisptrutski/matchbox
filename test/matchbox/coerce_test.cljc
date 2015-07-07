(ns matchbox.coerce-test
  (:require #?(:clj [clojure.test :refer :all]
               :cljs [cljs.test :as t])
            [matchbox.coerce :as mc])
  #?(:cljs (:require-macros [cljs.test :refer [deftest testing is]])))

;; test.check could be interesting here

(deftest test-roundtrip
  (is (= 4 (-> 4 mc/serialize mc/hydrate)))
  (is (= {:a 1} (-> {:a 1} mc/serialize mc/hydrate)))
  (is (= {:a 1} (-> {"a" 1} mc/serialize mc/hydrate)))
  (is (= [:a "a"] (-> [:a "a"] mc/serialize mc/hydrate)))
  (is (= [1 2 3] (-> '(1 2 3) mc/serialize mc/hydrate))))

(deftest test-ensure-map
  (is (= 421             (mc/ensure-map 421)))
  (is (= {0 3, 1 2, 2 1} (mc/ensure-map '(3 2 1))))
  (is (= {0 3, 1 2, 2 1} (mc/ensure-map [3 2 1])))
  (is (= {:a 1, :b 2}    (mc/ensure-map {:a 1, :b 2}))))
