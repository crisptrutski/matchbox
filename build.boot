(set-env!
 :dependencies
 '[[org.clojure/clojure "1.7.0" :scope "provided"]
   [org.clojure/clojurescript "1.7.228" :scope "provided"]
   [org.clojure/core.async "0.2.374" :scope "provided"]
   [adzerk/boot-cljs "1.7.228-1" :scope "test"]
   [adzerk/bootlaces "0.1.13" :scope "test"]
   [com.firebase/firebase-client-jvm "2.5.2" :exclusions [org.apache.httpcomponents/httpclient]]
   [org.apache.httpcomponents/httpclient "4.5.2"]
   [reagent "0.6.0-alpha" :scope "provided"]
   [cljsjs/firebase "2.4.1-0"]]
 :source-paths   #{"src"})

(require
 '[adzerk.bootlaces :refer :all]
 '[adzerk.boot-cljs :refer :all])

 (def +version+ "0.0.10-SNAPSHOT")
 (bootlaces! +version+)

(task-options!
 pom {:project 'matchbox
      :version +version+
      :description ""
      :url         ""
      :scm {:url ""}}
 aot {:namespace #{'matchbox.clojure.android-stub}})

(deftask run-test
  "Test"
  []
  clojure.core/identity)

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
