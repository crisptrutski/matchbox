(ns pani.cljs.core
  (:refer-clojure :exclude [name get-in merge set!])
  (:require cljsjs.firebase
            [cljs.core.async :as async :refer [<! >! chan put! merge]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def name- clojure.core/name)

;; Firebase listeners, modeled as maps of [:channel, :close-fn, :ref]
(defonce listeners (atom []))

(defn register-listener [ref type close-fn ]
  (swap! listeners conj
         {:close-fn close-fn
          :ref      ref
          :type     type}))

(defn disable-listeners-by! [match?]
  (let [xs @listeners]
    (swap! listeners #(vec (remove match? %)))
    (doseq [{:keys [close-fn]} (vec (filter match? xs))]
      (close-fn))))

(defn- ref-match
  "Curried equality check for refs"
  [a]
  (let [s (str a)]
    (fn [b] (= s (str b)))))

(defn disable-listeners!
  ([]
   (disable-listeners-by! (constantly true)))
  ([ref]
   (disable-listeners-by! (comp (ref-match ref) :ref)))
  ([ref type]
   (disable-listeners-by! (let [r= (ref-match ref)
                                t= (let [n (name- type)] #(= n %))]
                            (fn [{t :type r :ref}]
                              (and (r= r) (t= t)))))))

(defn- clj-val [v]
  (js->clj (.val v) :keywordize-keys true))

;; Make a firebase object ouf the given URL
(defn get-ref [url]
  "Makes a root reference for firebase"
  (js/Firebase. url))

(def root get-ref)

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye] refers to hello.world.bye
(defn walk-root [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (let [p (if (sequential? korks)
            (apply str (interpose "/" (map name- korks)))
            (when korks (name- korks)))]
    (if-not (seq p)
      root
      (.child root p))))

(defn name [r]
  "Get the name of the given root"
  (.key r))

(defn parent [r]
  "Get the parent of the given root"
  (let [p (.parent r)]
    (if (nil? (js->clj p))
      nil
      p)))

(defn value
  "Returns a channel which delivers value of ref just once"
  [ref]
  (let [ch (async/chan)]
    (.once ref "value"
           (fn [js-val]
             (if-let [v (clj-val js-val)]
                (put! ch v))
             (async/close! ch)))
    ch))

(def ^{:doc "Similar to `value`, but takes a relative path"}
  get-in (comp value walk-root))

;; A function set the value on a root [korks]
;;
(defn set!
  "Set the value at the given root"
  ([root val]
   (.set root (clj->js val)))

  ([root korks val]
   (.set (walk-root root korks) (clj->js val))))

(defn push!
  "Set the value at the given root"
  ([root val]
   (.push root (clj->js val)))

  ([root korks val]
   (push! (walk-root root korks) val)))

(defn remove!
  "Remove the value at the given location"
  ([r f]
   (.remove r f))
  ([r]
   (let [c (chan)]
     (remove! r #(if %
                   (async/onto-chan c [%])
                   (async/close! c)))
     c)))

(defn bind
  "Bind to a certain property under the given root"
  ([root type korks]
   (let [bind-chan (chan)
         close-fn (bind root type korks
                        #(go (>! bind-chan %)))]
     [bind-chan close-fn]))

  ([root type korks cb]
   (let [type (name- type)
         child (walk-root root korks)
         callback #(when-let [v (clj-val %1)]
                     (cb {:val v, :name (name %1)}))
         unsub #(.off child type callback)]
     (.on child type callback)
     (register-listener child type unsub)
     unsub)))

(defn transact!
  "Use the firebase transaction mechanism to update a value atomically"
  [root korks f & args]
  (let [c (walk-root root korks)
        t (fn [current]
            (let [js-val (js->clj current :keywordize-keys true)
                  js-new (apply f js-val args)]
              (clj->js js-new)))]
    (.transaction c t)))

(defn listen<
  "Listens for events on the given firebase ref"
  [root korks]
  (let [c    (chan)
        root (walk-root root korks)]
    (doto root
      (.on "child_added" #(put! c [:child_added (.key %) (.val %)]))
      (.on "child_changed" #(put! c [:child_changed (.key %) (.val %)]))
      (.on "child_removed" #(put! c [:child_removed (.key %) (.val %)])))
    c))
