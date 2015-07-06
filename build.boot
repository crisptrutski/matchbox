(set-env!
 :resource-paths #{"src" "lib/android-context"}
 :dependencies '[[adzerk/boot-cljs      "0.0-3308-0" :scope "test"]
                 [adzerk/boot-cljs-repl "0.1.9"      :scope "test"]
                 [adzerk/boot-reload    "0.3.1"      :scope "test"]
                 [adzerk/boot-test      "1.0.4"      :scope "test"]
                 [adzerk/bootlaces      "0.1.11"     :scope "test"]
                 ;;[pandeiro/boot-http    "0.6.0"      :scope "test"]

                 [crisptrutski/android-context-stub "0.0.1"]

                 ;[pandeiro/boot-test-cljs "0.1.0" :scope "test"]

                 [org.clojure/clojurescript "0.0-3308" :scope "provided"]

                 [org.clojure/core.async           "0.1.346.0-17112a-alpha"]
                 [cljsjs/firebase                  "2.2.7-0"]
                 [com.firebase/firebase-client-jvm "2.3.1"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0-SNAPSHOT")

(task-options!
  pom {:project     'crisptrutski/matchbox
       :version     +version+
       :description "Use Firebase with flair from Clojure and Clojurescript"
       :url         "https://github.com/crisptrutski/matchbox"
       :scm         {:url "https://github.com/crisptrutski/matchbox"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-test      :refer :all]
 '[boot.pod              :refer [make-pod]]
 ;'[pandeiro.http         :refer [serve]]
 #_'[pandeiro.boot-test-cljs :refer [test-cljs]]
 )

(deftask build-android-stub []
  (set-env! :src-paths nil
            :resource-paths #{"lib/android-context"}
            :dependencies [])
  (comp
    (pom :project     'crisptrutski/android-context-stub
         :version     "0.0.1"
         :description "Workaround for https://groups.google.com/forum/#!searchin/firebase-talk/jvm/firebase-talk/XLbpLpqCdDI/mbXk1AMmOY8J"
         :url         "https://github.com/crisptrutski/matchbox/lib/android-context"
         :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"})
    (jar)
    (install)))

(deftask dev []
  (comp
    ;(serve :dir "target/")
    (watch)
    (speak)
    (reload)
    (cljs-repl)
    (cljs :unified-mode true :source-map true :optimizations :none)))

(deftask autotest []
  (set-env! :source-paths #(conj % "test"))
  (watch)
  (test))

;(deftask autotest-cljs []
  ;(set-env! :source-paths #(conj % "test"))
  ;(watch)
  ;(test-cljs :namespaces #{'matchbox.core-test}))
