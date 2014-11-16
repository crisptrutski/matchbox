(defproject pani "0.0.3"
  :description "Convenience library for using Firebase with Clojure and Clojurescript"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "http://github.com/verma/pani"
  :author "verma"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.firebase/firebase-client-jvm "1.1.1"]]
  :deploy-repositories [["releases" :clojars]]


  :plugins [[lein-cljsbuild "1.0.3"]
            [com.cemerick/clojurescript.test "0.3.1"]]
  :profiles {:dev {:dependencies [[om "0.6.4"]
                                  [com.cemerick/piggieback "0.1.3"]]
                   :plugins [[com.cemerick/austin "0.1.5"]]}}

  :aliases {"auto-test" ["do" "clean," "cljsbuild" "auto" "test"]}


  :aot [pani.clojure.android-stub]

  :cljsbuild {:builds [{:id "om-value-changes"
                        :source-paths ["examples/cljs/om-value-changes/src" "src"]
                        :compiler {
                                 :output-to "examples/cljs/om-value-changes/main.js"
                                 :output-dir "examples/cljs/om-value-changes/out"
                                 :source-map true
                                 :optimizations :none }}
                       {:id "test"
                        :source-paths ["test"]
                        :notify-command ["phantomjs" :cljs.test/runner "vendor/firebase-1.1.3.js" "target/cljs/test.js"]
                        :compiler {:output-to "target/cljs/test.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]
              :test-commands {"unit-tests" ["phantomjs" :runner
                                            "this.literal_js_was_evaluated=true"
                                            "vendor/firebase-1.1.3.js"
                                            "target/cljs/test.js"]}})
