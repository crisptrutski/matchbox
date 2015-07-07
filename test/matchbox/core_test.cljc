(ns matchbox.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [matchbox.core :as m]))

(def base "http://luminous-torch-5788.firebaseio.com/")

(defmacro with-ref [binding & body]
  `(let [~binding (m/ref (str base (rand-int 1000)))]
     ~@body
     (m/delete! ~binding)))

(deftest ref-test
  (is (= "https://abc" (.toString (m/ref "abc")))))

(deftest traverse-test
  (let [root (m/ref base)
        root2 (m/ref (str base "/d"))
        child (m/get root :a)
        deep (m/get-in root [:a "b" 'c])
        redeep (-> child (m/get 'b) (m/get :c))
        parent (m/parent deep)
        reroot (m/root deep)
        reroot2 (m/root root2)]
    (is (= nil (m/key root)))
    (is (= :a (m/key child)))
    (is (= :c (m/key deep)))
    (is (= (.toString deep) (.toString redeep)))
    (is (= :b (m/key parent)))
    (is (= (.toString root) (.toString reroot2)))
    (is (= (.toString root) (.toString reroot)))))

(deftest set!-test
  (with-ref ref
    (m/set! ref {"key" 432})

    (is (= 432 (-> ref (m/get :key) (m/read) (m/<!!))))
    (is (= 432 (-> ref (m/get :key) (m/snapshot) (m/<!!) (m/read))))
    (is (= 432 (-> ref (m/snapshot) (m/get :key) (m/<!!) (m/read))))
    (is (= 432 (-> ref (m/snapshot) (m/<!!) (m/get :key) (m/read))))))

(deftest conj!-test
  (with-ref ref
    (m/conj! ref 1)
    (m/conj! ref 2)
    (m/conj! ref 3)

    (is (= [1 2 3] (-> ref (m/as-vec) (m/<!!))))
    (is (= [1 2 3] (-> ref (m/snapshot) (m/<!!) (m/as-vec))))
    (is (= [1 2 3] (-> ref (m/snapshot) (m/<!!) (m/as-vec))))
    (is (= [1 2 3] (-> ref (m/snapshot) (m/<!!) (m/as-vec))))

    (is (= #{1 2 3} (into #{} (vals (-> ref m/export m/<!!)))))
    (is (nil? (-> ref m/priority m/<!!)))))

(deftest swap!-test
  (with-ref ref
    (m/set! ref 1)
    (m/swap! ref inc)
    (is (= 2 (-> ref m/read m/<!!)))

    (m/swap! ref inc)
    (is (= 3 (-> ref m/read m/<!!)))))

(deftest swap-remote!-test
  (with-ref ref
    (m/set! ref 1)
    (m/swap-remote! ref inc)
    ;; TODO: block on success rather than magic time. Also will work
    ;; with CLJS
    #?(:clj [(Thread/sleep 900)
             (is (= 2 (-> ref m/read m/<!!)))])))

(deftest test-priority
  (with-ref ref
    (m/set! (m/get ref :a) 1 3)
    (m/set! (m/get ref :b) 2 2)
    (m/set! (m/get ref :c) 3 1)

    (is (= [3 2 1] (-> ref m/as-vec m/<!!)))

    (is (= {:a {:.priority 3.0, :.value 1}
            :b {:.priority 2.0, :.value 2}
            :c {:.priority 1.0, :.value 3}}
           (-> ref m/export m/<!!)))

    (m/set-priority! (m/get ref :b) 100)

    (is (= [3 1 2] (-> ref m/as-vec m/<!!)))))

(deftest test-merge!
  (with-ref ref
    (m/set! ref {:a 1 :b 2 :c 3})
    (m/merge! ref {:b 3 :d 5})

    (is (= {:a 1 :b 3 :c 3 :d 5} (-> ref m/read m/<!!)))))

(deftest test-remove!
  (with-ref ref
    (m/set! ref {:a 1 :b 2 :c 3})
    (m/remove! ref :b)

    (is (= {:a 1 :c 3} (-> ref m/read m/<!!)))))

(deftest test-as-map!
  (with-ref ref
    ;; (m/set! ref {0 :a 1 :b 2 :c})
    (m/set! ref {"0" :a "1" :b "2" :c})
    (is (= [:a :b :c] (-> ref m/read m/<!!)))
    (is (= {0 :a 1 :b 2 :c} (-> ref m/as-map m/<!!)))))

(deftest test-once!
  (with-ref ref
    (let [listener (m/once ref [:added :removed])]
      (m/set! (m/get ref :a) 1)
      (m/set! (m/get ref :b) 2)
      (m/remove! ref :a)
      (is (= [:added 1] (m/<!! listener)))
      (is (= [:added 2] (m/<!! listener)))
      (is (= [:removed 1] (m/<!! listener))))))

(deftest test-query
  (with-ref ref
    (prn
      (.getWireProtocolParams
        (.getParams
          (.getSpec (.limitToLast (.orderByValue ref) 30)))))))
