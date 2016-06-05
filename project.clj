(defproject matchbox "0.0.10-SNAPSHOT"
  :description "Firebase bindings for Clojure(Script)"
  :url "http://github.com/crisptrutski/matchbox"
  :license {:name "Eclipse Public License" :url "http://www.eclipse.org/legal/epl-v10.html"}
  :authors ["verma", "crisptrutski"]

  :dependencies
  [[org.clojure/clojure "1.8.0" :scope "provided"]
   [org.clojure/clojurescript "1.8.34" :scope "provided"]
   [org.clojure/core.async "0.2.374" :scope "provided"]
   [reagent "0.6.0-alpha" :scope "provided"]
   [frankiesardo/linked "1.2.6" :scope "provided"]
   [com.firebase/firebase-client-jvm "2.5.2" :exclusions [org.apache.httpcomponents/httpclient]]
   [org.apache.httpcomponents/httpclient "4.5.2"]
   [cljsjs/firebase "2.4.1-0"]
   [org.clojure/tools.namespace "0.2.11" :scope "test"]]

  :aot [matchbox.clojure.android-stub]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.1.3"]
                             [lein-doo "0.1.6"]
                             [com.jakemccrary/lein-test-refresh "0.6.0"]]}}

  :cljsbuild {:builds [{:id "test"
                        :source-paths ["src" "test"]
                        :compiler {:output-to "target/cljs/test.js"
                                   :main matchbox.runner
                                   :optimizations :none}}]})
