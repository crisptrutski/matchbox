(ns pani.cljs.core
  (:refer-clojure :exclude [name get-in merge])
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

(defn- fb-call!
  "Set the value at the given root"
  ([push-fn root val]
   (let [as-js (clj->js val)]
     (push-fn root as-js)))

  ([push-fn root korks val]
   (fb-call! push-fn (walk-root root korks) val)))

(defn get-in
  "get-in style single shot get function, returns a channel which delivers the value"
  [root ks]
  (let [loc (clojure.string/join "/" (map name- ks))
        ref (.child root loc)
        ch  (async/chan)]
    (.once ref "value"
           #(if-let [v (.val %)]
              (put! ch (js->clj v :keywordize-keys true))
              (async/close! ch)))
    ch))

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
  (let [c (walk-root root korks)]
    (.transaction c #(clj->js (apply f (js->clj % :keywordize-keys true) args)) #() false)))

(defn- fb->chan
  "Given a firebase ref, an event and a transducer, binds and posts to returned channel"
  [fbref event td]
  (let [c (chan 1 td)]
    (.on fbref (name- event)
         #(put! c [event %]))
    c))

(defn listen<
  "Listens for events on the given firebase ref"
  [root korks]
  (let [root    (walk-root root korks)
        events  [:child_added :child_removed :child_changed]
        td      (map (fn [[evt snap]]
                       [evt (.key snap) (.val snap)]))
        chans   (map (fn [event]
                       (fb->chan root event td)) events)]
    (merge chans)))
