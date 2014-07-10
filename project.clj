(defproject pani "0.0.1"
  :description "Convenience library for using Firebase with Clojurescript"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :author "verma"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [com.firebase/firebase-client "1.0.7"]]
  :deploy-repositories [["releases" :clojars]]
  :cljsbuild {:builds []}


  :plugins [[lein-cljsbuild "1.0.3"]]
  :profiles {:dev {:dependencies [[om "0.6.4"]]}}

  :cljsbuild {:builds [{:id "om-value-changes"
                        :source-paths ["src-cljs" "examples/cljs/om-value-changes/src"]
                        :compiler {
                                 :output-to "examples/cljs/om-value-changes/main.js"
                                 :output-dir "examples/cljs/om-value-changes/out"
                                 :source-map true
                                 :optimizations :none }}]})
