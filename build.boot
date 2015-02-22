(set-env!
 :source-paths   #{"src"}
 :resource-paths #{}
 :dependencies '[[adzerk/boot-cljs      "0.0-2814-1" :scope "test"]
                 [adzerk/boot-cljs-repl "0.1.9"      :scope "test"]
                 [adzerk/boot-reload    "0.2.0"      :scope "test"]
                 [pandeiro/boot-http    "0.3.0"      :scope "test"]
                 [deraen/boot-cljx      "0.2.2"      :scope "test"]

                 [adzerk/bootlaces    "0.1.10"       :scope "test"]
                 [org.clojure/clojurescript "0.0-2755" :scope "provided"]

                 [org.clojure/core.async           "0.1.346.0-17112a-alpha"]
                 [cljsjs/firebase                  "2.1.2-1"]
                 [com.firebase/firebase-client-jvm "2.2.0"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.0.5")

(task-options!
  pom {:project     'crisptrutski/matchbox
       :version     +version+
       :description "Convenience library for using Firebase with Clojure and Clojurescript"
       :url         "https://github.com/crisptrutski/matchbox"
       :scm         {:url "https://github.com/crisptrutski/matchbox"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.http         :refer [serve]]
 '[deraen.boot-cljx      :refer [cljx]])

(deftask dev []
  (comp
    (serve :dir "target/")
    (aot :namespace #{'matchbox.clojure.android-stub})
    (watch)
    (speak)
    (reload)
    (cljx)
    (cljs-repl)
    (cljs :unified-mode true :source-map true :optimizations :none)))

(defn define-node-repl-launcher []
  (fn [handler]
    (fn [fileset]
      (defn node-repl []
        (require 'cemerick.piggieback 'cljs.repl.node)
        ((resolve 'cemerick.piggieback/cljs-repl)
         :repl-env ((resolve 'cljs.repl.node/repl-env))
         :output-dir ".noderepl"
         :optimizations :none
         :cache-analysis true
         :source-map true))
      (handler fileset))))

(deftask cider-server
  "After launching the cider-server, attach via a separate nrepl client
  and run boot.user/node-repl to launch a clojurescript repl"
  []
  (swap! boot.repl/*default-dependencies*
         concat '[[com.cemerick/piggieback "0.1.5"]
                  [cider/cider-nrepl "0.9.0-SNAPSHOT"]])

  (swap! boot.repl/*default-middleware*
         into '[cider.nrepl/cider-middleware
                      cemerick.piggieback/wrap-cljs-repl])

  (comp
   (aot :namespace #{'matchbox.clojure.android-stub})
   (watch)
   (cljx)
   (build-jar)
   (repl :server true)
   (define-node-repl-launcher)
   (wait)))
