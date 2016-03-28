(defproject matchbox "0.0.8-SNAPSHOT"
  :description "Firebase bindings for Clojure(Script)"
  :url "http://github.com/crisptrutski/matchbox"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :authors ["verma", "crisptrutski"]

  :dependencies
  [[org.clojure/clojure "1.7.0" :scope "provided"]
   [org.clojure/clojurescript "1.7.228" :scope "provided"]
   [org.clojure/core.async "0.2.374" :scope "provided"]
   [com.firebase/firebase-client-jvm "2.5.2" :exclusions [org.apache.httpcomponents/httpclient]]
   [org.apache.httpcomponents/httpclient "4.5.2"]
   [cljsjs/firebase "2.4.1-0"]]

  :aot [matchbox.clojure.android-stub]
  :jar-exclusions [#"\.swp|\.swo|\.DS_Store"]
  :min-lein-version "2.5.2"

  :profiles {:dev {:plugins [[lein-cljsbuild "1.1.3" :scope "test"]
                             [com.jakemccrary/lein-test-refresh "0.6.0"]
                             [com.cemerick/clojurescript.test "0.3.3" :scope "test"]]
                   :aliases {"test-all" ["do" "clean,"
                                         "test,"
                                         "cljsbuild" "test"]}}}

  :cljsbuild {:builds [{:source-paths ["src" "test"]
                        ;;:notify-command ["phantomjs" :cljs.test/runner "target/cljs/test.js"]
                        :compiler {:output-to "target/cljs/test.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]
              :test-commands {"unit-tests" ["phantomjs" :runner
                                            "this.literal_js_was_evaluated=true"
                                            "target/cljs/test.js"]}})
