(defproject pani "0.0.1"
  :description "Convenience library for using Firebase with Clojurescript"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :author "verma"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]]
  :deploy-repositories [["releases" :clojars]]
  :cljsbuild {:builds []}


  :plugins [[lein-cljsbuild "1.0.3"]]
  :hooks [leiningen.cljsbuild]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :compiler {:output-to "target/main.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]})
