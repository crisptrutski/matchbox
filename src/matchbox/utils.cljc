(ns matchbox.utils
  (:require
    #?(:clj [clojure.core.async :as async]))
  #?(:clj (:import [clojure.core.async.impl.channels ManyToManyChannel])))

#?(:clj  (def -out- *out*))

(def ^{:doc "Placeholder value to send `nil` through channel."}
  -nil- (gensym))

(defn restore-nil
  "Recover nil values from channel output."
  [x]
  (when (not= -nil- x) x))

(defn safe-prn
  "Print to primary thread, only useful on JVM."
  [& xs]
  (binding [*out* -out-]
    (apply prn xs)))

(defn fmap
  "Apply transform to values coming off channel, returns new channel."
  [f in]
  (let [out (async/chan)]
    (async/go-loop []
      (let [v (async/<! in)]
        (if (nil? v)
          (async/close! out)
          (let [o (f v)
                oo (if (nil? o) -nil- o)]
            (async/>! out oo)
            (recur)))))
    out))

#?(:clj (defn normalize-url
          "Ensure that URL has a valid schema."
          [url]
          (if (re-find #"\w+://" url) url (str "https://" url))))

