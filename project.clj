(defproject pani "0.0.2"
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


  :plugins [[lein-cljsbuild "1.0.3"]
            [com.cemerick/clojurescript.test "0.3.1"]]
  :profiles {:dev {:dependencies [[om "0.6.4"]]}}

  :cljsbuild {:builds [{:id "om-value-changes"
                        :source-paths ["examples/cljs/om-value-changes/src"]
                        :compiler {
                                 :output-to "examples/cljs/om-value-changes/main.js"
                                 :output-dir "examples/cljs/om-value-changes/out"
                                 :source-map true
                                 :optimizations :none }}
                       {:id "test"
                        :source-paths ["test"]
                        :compiler {:output-to "target/cljs/test.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]
              :test-commands {"unit-tests" ["phantomjs" :runner
                                            "this.literal_js_was_evaluated=true"
                                            "vendor/firebase-1.0.17.js"
                                            "target/cljs/test.js"]}})
