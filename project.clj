(defproject cljs-firebase "0.0.1"
  :description "Convenience library for using Firebase with Clojure and Clojurescript"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :author "verma"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]]
  :deploy-repositories [["releases" :clojars]]
  :cljsbuild {:builds []}
  :plugins [[com.keminglabs/cljx "0.4.0"]]

  :aliases {"cljs-build" ["do" "cljx," "with-profile" "cljs" "cljsbuild" "once" "dev"]
            "clj-test" ["do" "cljx," "with-profile" "clj" "test"]}

  :cljx {:builds [{:source-paths ["src-cljx"]
                   :output-path "target/gen/clj"
                   :rules :clj}
                  {:source-paths ["src-cljx"]
                   :output-path "target/gen/cljs"
                   :rules :cljs}]}


  :profiles {:cljs {:dependencies []
                    :plugins [[lein-cljsbuild "1.0.3"]]
                    :hooks [leiningen.cljsbuild]
                    :cljsbuild {:builds [{:id "dev"
                                          :source-paths ["target/gen/cljs"]
                                          :compiler {:output-to "target/main.js"
                                                     :optimizations :whitespace
                                                     :pretty-print true}}]}
                    :source-paths ["target/gen/cljs"]}
             :clj {:dependencies []
                   :source-paths ["target/gen/clj"]
                   :test-paths ["test"]
                   :repl-options {:nrepl-middleware [cljx.repl-middleware/wrap-cljx]}}})
