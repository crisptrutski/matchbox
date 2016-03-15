(ns matchbox.runner
  (require [doo.core :as doo]
           [cljs.build.api :as cljs-build]))

(def compiler  {:output-to     "testable.js"
                :main          'example.runner
                :optimizations :whitespace})

(def source-paths #{"src" "test"})

(defn compile! [compiler source-paths]
  (cljs-build/build
   (apply cljs-build/inputs source-paths) compiler))

(defn run-tests! [env out-file]
  (doo/run-script env out-file))

(comment
  (compile! compiler source-paths)
  (run-tests! :phantom (:output-to compiler)))
