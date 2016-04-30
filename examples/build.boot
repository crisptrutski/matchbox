(set-env!
 :source-paths    #{"src/cljs" "../src"}
 :resource-paths  #{"resources"}
 :dependencies '[[adzerk/boot-cljs          "1.7.228-1" :scope "test"]
                 [adzerk/boot-cljs-repl     "0.3.0"     :scope "test"]
                 [adzerk/boot-reload        "0.4.7"     :scope "test"]
                 [pandeiro/boot-http        "0.7.3"     :scope "test"]
                 [org.clojure/clojurescript "1.8.51"    :scope "test"]
                 [org.clojure/tools.nrepl   "0.2.12"    :scope "test"]
                 [com.cemerick/piggieback   "0.2.1"     :scope "test"]
                 [weasel                    "0.7.0"     :scope "test"]
                 [reagent "0.5.0"]
                 [org.omcljs/om "0.8.6"]
                 [cljsjs/firebase "2.4.1-0"]
                 [sablono "0.3.4"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.boot-http    :refer [serve]])

(deftask build []
  (comp (speak)
        (cljs)
        ))

(deftask run []
  (comp (serve)
        (watch)
        (cljs-repl)
        (reload)
        (build)
        (target)))

(deftask production []
  (task-options! cljs {:optimizations :advanced
                       ;; pseudo-names true is currently required
                       ;; https://github.com/martinklepsch/pseudo-names-error
                       ;; hopefully fixed soon
                       :pseudo-names true})
  identity)

(deftask development []
  (task-options! cljs {:optimizations :none
                       :source-map true}
                 reload {:on-jsload 'matchbox-reagent.app/init})
  identity)

(deftask dev
  "Simple alias to run application in development mode"
  []
  (comp (development)
        (run)))
