(defproject pani "0.0.4-SNAPSHOT"
  :description "Convenience library for using Firebase with Clojure and Clojurescript"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :url "http://github.com/verma/pani"
  :author "verma"
  :min-lein-version "2.5.0"
  :dependencies [[org.clojure/clojure "1.6.0" :scope "provided"]
                 [org.clojure/clojurescript "0.0-2755" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.firebase/firebase-client-jvm "2.2.0"]

                 [cljsjs/firebase "2.1.2-1"]]
  :deploy-repositories [["releases" :clojars]]

  :plugins [[lein-cljsbuild "1.0.3" :scope "test"]
            [com.cemerick/clojurescript.test "0.3.1" :scope "test"]]

  :profiles {:dev {:dependencies [[om "0.6.4"]]}}

  :aliases {"test-all"  ["do" "test," "cljsbuild" "once" "test"]
            "auto-test" ["do" "clean," "cljsbuild" "auto" "test"]}

  :aot [pani.clojure.android-stub]

  :cljsbuild {:builds [{:id "om-value-changes"
                        :source-paths ["examples/cljs/om-value-changes/src" "src"]
                        :compiler {:output-to "examples/cljs/om-value-changes/main.js"
                                   :output-dir "examples/cljs/om-value-changes/out"
                                   :source-map true
                                   :optimizations :none }}
                       {:id "test"
                        :source-paths ["src", "test"]
                        :notify-command ["phantomjs" :cljs.test/runner "target/cljs/test.js"]
                        :compiler {:output-to "target/cljs/test.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]
              :test-commands {"unit-tests" ["phantomjs" :runner
                                            "this.literal_js_was_evaluated=true"
                                            "target/cljs/test.js"]}})
