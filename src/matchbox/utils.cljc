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

(defn no-op ([_]) ([_ _]) ([_ _ _]) ([_ _ _ & _]))

(defn extract-cb [args]
  (if (and (>= (count args) 2)
           (= (first (take-last 2 args)) :callback))
    [(last args) (drop-last 2 args)]
    [nil args]))

;;

(defprotocol ISerializer
  (get-hydrate [this])
  (set-hydrate! [this val])
  (get-serialize [this])
  (set-serialize! [this val]))

(deftype Serializer [#?(:clj ^:volatile-mutable hydrate :cljs ^:mutable hydrate)
                     #?(:clj ^:volatile-mutable serialize :cljs ^:mutable serialize)]
  ISerializer
  (get-hydrate [_] hydrate)
  (set-hydrate! [_ val] (set! hydrate val))
  (get-serialize [_] serialize)
  (set-serialize! [_ val] (set! serialize val)))

(defn set-date-config! [hydrate serialize]
  (-> ^Serializer
      #?(:clj @(resolve 'matchbox.core/data-config)
         :cljs matchbox.core/data-config)
      (set-hydrate! hydrate)
      (set-serialize! serialize)))

#?(:clj (def repl-out *out*))

#?(:clj
    (defn prn
      "Like clojure.core/prn, but always bound to root thread's *out*"
      [& args]
      (binding [*out* repl-out]
        (apply clojure.core/prn args))))
