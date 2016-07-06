(set-env!
 :dependencies
 '[[org.clojure/clojure "1.7.0" :scope "provided"]
   [org.clojure/clojurescript "1.7.228" :scope "provided"]
   ;; packaged dependencies
   [com.firebase/firebase-client-jvm "2.5.2" :exclusions [org.apache.httpcomponents/httpclient]]
   [cljsjs/firebase "3.0.5-rc2-0"]
   [degree9/firebase-cljs "0.3.0"]
   [org.apache.httpcomponents/httpclient "4.5.2"]
   ;; optional namespace dependencies
   [org.clojure/core.async "0.2.374" :scope "provided"]
   [reagent "0.6.0-alpha" :scope "provided"]
   [frankiesardo/linked "1.2.6" :scope "provided"]
   ;; build tooling
   [adzerk/boot-cljs "1.7.228-1" :scope "test"]
   [adzerk/bootlaces "0.1.13" :scope "test"]
   [adzerk/boot-test "1.1.1" :scope "test"]
   [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT" :scope "test"]]
 :source-paths #{"src"})

(require
  '[adzerk.bootlaces :refer :all]
  '[adzerk.boot-cljs :refer :all]
  '[adzerk.boot-test :refer :all]
  '[crisptrutski.boot-cljs-test :refer [test-cljs]])

(def +version+ "0.0.10-SNAPSHOT")
(bootlaces! +version+)

(task-options!
  pom {:project 'matchbox
       :version +version+
       :description "Firebase bindings for Clojure(Script)"
       :url "http://github.com/crisptrutski/matchbox"
       :scm {:url "http://github.com/crisptrutski/matchbox"}}
  aot {:namespace #{'matchbox.clojure.android-stub}}
  test-cljs {:js-env :phantom})

(deftask deps [] identity)

(deftask testing []
  (merge-env! :source-paths #{"test"})
  identity)

(deftask watch-js [] (comp (testing) (watch) (test-cljs)))

(deftask watch-jvm [] (comp (aot) (testing) (watch) (test)))

(deftask ci []
  (task-options!
    test {:junit-output-to "junit-out"}
    test-cljs {:exit? true})
  (comp (aot) (testing) (test) (test-cljs)))

(deftask build []
  (comp (pom) (aot) (jar)))
