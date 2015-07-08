(set-env!
 :resource-paths #{"src"}
 :dependencies '[[adzerk/boot-cljs      "0.0-3308-0" :scope "test"]
                 [adzerk/boot-cljs-repl "0.1.10-SNAPSHOT"      :scope "test"]
                 [adzerk/boot-reload    "0.3.1"      :scope "test"]
                 [adzerk/boot-test      "1.0.4"      :scope "test"]
                 [adzerk/bootlaces      "0.1.11"              :scope "test"]
                 [pandeiro/boot-http    "0.6.3-SNAPSHOT"      :scope "test"]

                 [doo "0.1.2-SNAPSHOT"]

                 [org.clojure/tools.nrepl "0.2.10" :scope "provided"]

                 [org.clojure/clojurescript        "0.0-3308" :scope "provided"]
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
 '[pandeiro.boot-http    :refer [serve]]

 '[doo.core              :as doo])

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
    (javac)
    (jar)
    (install)))

(defn add-android-stub []
  (set-env! :dependencies #(conj % '[crisptrutski/android-context-stub "0.0.1"])))

(deftask dev []
  (set-env! :source-paths (constantly #{"src" "test"}))
  (add-android-stub)
  (comp
    (serve :dir "target/")
    (watch)
    (speak)
    (reload)
    (cljs-repl)
    (cljs :source-map true :optimizations :none)))


(require 'doo.core)
(require 'cljs.build.api)

(deftask test-cljs
  "Run cljs.test tests in a pod.

   The --namespaces option specifies the namespaces to test. The default is to
   run tests in all namespaces found in the project.

   The --filters option specifies Clojure expressions that are evaluated with %
   bound to a Var in a namespace under test. All must evaluate to true for a Var to
   be considered for testing by clojure.test/test-vars."
  [e js-env     VAL kw     "The environment to run tests within, eg. slimer, phantom, node, or rhino"
   n namespaces NS  #{sym} "Namespaces whose tests will be run. All tests will be run if ommitted."
   x exit?          bool   "Exit immediately with reporter's exit code."]
  (let [js-env     (or js-env :phantom)
        tmp-file   "testable.js"
        main-ns    'example.runner
        inputs     (into (get-env :source-paths)
                         (get-env :resource-paths))
        cljs-paths (apply cljs.build.api/inputs inputs)]
    (fn [next-task]
      (fn [fileset]
        (cljs.build.api/build cljs-paths
                              {:output-to     tmp-file
                               :main          main-ns
                               :optimizations :whitespace})
        (let [{:keys [exit]} (doo.core/run-script js-env tmp-file)]
          (if exit?
            (System/exit exit)
            (next-task fileset)))))))

(deftask testing []
  (add-android-stub)
  (set-env! :source-paths #(conj % "test"))
  identity)

(deftask test-all []
  (comp
   (testing)
   (watch)
   (test)
   (test-cljs)))
