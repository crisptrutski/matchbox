(defproject matchbox "0.0.8"
  :description "Firebase for Clojure(Script)"
  :url "http://github.com/crisptrutski/matchbox"
  :authors ["crisptrutski"]
  :min-lein-version "2.5.0"
  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]
                 [org.clojure/clojurescript "0.0-3308" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha" :scope "provided"]
                 [com.firebase/firebase-client-jvm "2.3.1"]
                 [crisptrutski/android-context-stub "0.0.1"]
                 [cljsjs/firebase "2.2.7-0"]]

  :jar-exclusions [#"\.swp|\.swo|\.DS_Store"]

  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]]
                   :repl-options {:nrepl-middleware
                                  [cemerick.piggieback/wrap-cljs-repl]}
                   :plugins      [[quickie "0.3.6"]
                                  [lein-cljsbuild "1.0.3" :scope "test"]
                                  [com.jakemccrary/lein-test-refresh "0.6.0"]
                                  [com.cemerick/clojurescript.test "0.3.3" :scope "test"]
                                  [com.cemerick/piggieback "0.2.1"]]

                   :aliases      {"test-all" ["do" "test,"
                                              "cljsbuild" "once" "test"]}}}

  :cljsbuild {:builds        [{:id             "test"
                               :source-paths   ["src", "test", "target/classes", "target/test-classes"]
                               :notify-command ["phantomjs" :cljs.test/runner "target/cljs/test.js"]
                               :compiler       {:output-to     "target/cljs/test.js"
                                                :optimizations :whitespace
                                                :pretty-print  true}}]
              :test-commands {"unit-tests" ["phantomjs" :runner
                                            "this.literal_js_was_evaluated=true"
                                            "target/cljs/test.js"]}})
