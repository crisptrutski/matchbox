(ns sunog.utils
  (:require [clojure.string :as str]))

(defn- kebab->underscore [keyword]
  (-> keyword name (str/replace "-" "_")))

(defn- underscore->kebab [string]
  (-> string (str/replace "_" "-") keyword))

(defn- korks->path [korks]
  (if (sequential? korks)
    (str/join "/" (map name korks))
    (when korks (name korks))))

(defn no-op [& _])

(defn extract-cb [args]
  (if (and (>= 2 (count args))
           (= (first (take-last 2 args)) :callback))
    [(last args) (drop-last 2 args)]
    [nil args]))
