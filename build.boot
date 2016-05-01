(set-env!
 :dependencies
 '[[org.clojure/clojure "1.7.0" :scope "provided"]
   [org.clojure/clojurescript "1.7.228" :scope "provided"]
   [org.clojure/core.async "0.2.374" :scope "provided"]
   [adzerk/boot-cljs "1.7.228-1" :scope "test"]
   [adzerk/bootlaces "0.1.13" :scope "test"]
   [crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT"]
   [com.firebase/firebase-client-jvm "2.5.2" :exclusions [org.apache.httpcomponents/httpclient]]
   [org.apache.httpcomponents/httpclient "4.5.2"]
   [reagent "0.6.0-alpha" :scope "provided"]
   [cljsjs/firebase "2.4.1-0"]]
 :source-paths   #{"src"})

(require
  '[adzerk.bootlaces :refer :all]
  '[adzerk.boot-cljs :refer :all]
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

(deftask run-test
  "Test"
  []
  (merge-env! :source-paths #{"test"})
  (comp
    (test-cljs)))

(deftask build
  "Build matchbox for deployment"
  []
  (comp
   (cljs :optimizations :none)
   (target :dir #{"target"})
   ))

(deftask pack
  "Pack matchbox for deployment"
  []
  (comp
   (pom)
   (aot)
   (jar)))

(deftask dev
  "Build matchbox for local development."
  []
  (comp
   (watch)
   (build)))

(deftask prod
  "Build matchbox for production deployment."
  []
  (comp
   (build)
   (pack)))
