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
  (hydrate [this x])
  (set-hydrate! [this val])
  (serialize [this x])
  (set-serialize! [this val]))

(deftype Serializer [#?(:clj ^:volatile-mutable hydrate :cljs ^:mutable hydrate)
                     #?(:clj ^:volatile-mutable serialize :cljs ^:mutable serialize)]
  ISerializer
  (hydrate [_ x] (hydrate x))
  (set-hydrate! [_ val] (set! hydrate val))
  (serialize [_ x] (serialize x))
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
