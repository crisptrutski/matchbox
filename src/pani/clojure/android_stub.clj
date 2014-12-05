;; Avoid reflection-triggered exception when Android SDK not in classpath
(ns pani.clojure.android-stub
  (require [clojure.reflect :refer [resolve-class]]))

(defn class-exists? [c]
  (resolve-class (.getContextClassLoader (Thread/currentThread)) c))

(if-not (class-exists? 'android.content.Context)
  (gen-class :name "android.content.Context"))
