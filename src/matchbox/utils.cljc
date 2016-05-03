(ns matchbox.utils
  (:refer-clojure :exclude [prn])
  (:require [clojure.string :as str]))

(defn kebab->underscore [keyword]
  (-> keyword name (str/replace "-" "_")))

(defn underscore->kebab [string]
  (-> string (str/replace "_" "-") keyword))

(defn korks->path [korks]
  (if (sequential? korks)
    (str/join "/" (map name korks))
    (when korks (name korks))))

(defn no-op [& _])

(defn extract [kw args]
  (if (and (>= (count args) 2)
           (= (first (take-last 2 args)) kw))
    [(last args) (drop-last 2 args)]
    [nil args]))

;;

#?(:clj (def repl-out *out*))

#?(:cj
    (defn prn
      "Like clojure.core/prn, but always bound to root thread's *out*"
      [& args]
      (binding [*out* repl-out]
        (apply clojure.core/prn args))))
