(ns pani.cljs.core
  (:require [cljs.core.async :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn- clj-val [v]
  (js->clj (.val v) :keywordize-keys true))

;; Make a firebase object ouf the given URL
;;
(defn root [url]
  "Makes a root reference for firebase"
  (js/Firebase. url))

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye ] refers to hello.world.bye
;;
(defn walk-root [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (let [p (if (sequential? korks)
            (apply str (interpose "/" (map name korks)))
            (name korks))]
    (.child root p)))


(defn name [r]
  "Get the name of the given root"
  (.name r))

(defn parent [r]
  "Get the parent of the given root"
  (let [p (.parent r)]
    (if (nil? (js->clj p))
      nil
      p)))

(defn- fb-call!
  "Set the value at the given root"
  ([push-fn root val]
   (let [as-js (clj->js val)]
     (push-fn root as-js)))

  ([push-fn root korks val]
   (fb-call! push-fn (walk-root root korks) val)))

;; A function set the value on a root [korks]
;;
(defn set!
  "Set the value at the given root"
  ([root val]
   (fb-call! #(.set %1 %2) root val))

  ([root korks val]
   (fb-call! #(.set %1 %2) root korks val)))

(defn push!
  "Set the value at the given root"
  ([root val]
   (fb-call! #(.push %1 %2) root val))

  ([root korks val]
   (fb-call! #(.push %1 %2) root korks val)))

(defn bind
  "Bind to a certain property under the given root"
  ([root type korks]
   (let [bind-chan (chan)]
     (bind root type korks #(go (>! bind-chan %)))
     bind-chan))

  ([root type korks cb]
   (let [c (walk-root root korks)]
     (.on c (clojure.core/name type) 
          #(when-let [v (clj-val %1)]
             (cb v))))))

(defn transact!
  "Use the firebase transaction mechanism to update a value atomically"
  [root korks f & args]
  (let [c (walk-root root korks)]
    (.transaction c #(apply f % args) #() false)))
