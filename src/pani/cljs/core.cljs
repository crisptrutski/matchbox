(ns pani.cljs.core
  (:refer-clojure :exclude [name get-in merge])
  (:require cljsjs.firebase
            [cljs.core.async :as async :refer [<! >! chan put! merge]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; Firebase listeners, modeled as maps of [:channel, :close-fn, :ref]
(defonce listeners (atom []))

(defn register-listener [ref type close-fn ]
  (swap! listeners conj
         {:close-fn close-fn
          :ref      ref
          :type     type}))

(defn disable-listeners!
  ;; close all listeners
  ([]
   (let [xs @listeners]
     (reset! listeners [])
     (doseq [{:keys [close-fn]} xs]
       (close-fn))))
  ;; close all listeners on ref
  ([ref]
   (let [xs @listeners
         p? (comp #{ref} :ref)]
     (swap! listeners #(vec (remove p? %)))
     (doseq [{:keys [close-fn]} (filter p? xs)]
       (close-fn))))
  ;; close all listeners on ref with given type
  ([ref type]
   (let [xs @listeners
         p? (fn [{t :type r :ref}] (and (= r ref) (= t type)))]
     (swap! listeners #(vec (remove p? %)))
     (doseq [{:keys [close-fn]} (filter p? xs)]
       (close-fn)))))

(defn- clj-val [v]
  (js->clj (.val v) :keywordize-keys true))

;; Make a firebase object ouf the given URL
;;
(defn get-ref [url]
  "Makes a root reference for firebase"
  (js/Firebase. url))

(def root get-ref)

;; A utility function to traverse through korks and get ref to a child object
;; [:hello :world :bye] refers to hello.world.bye
;;
(defn walk-root [root korks]
  "Takes korks and reduces it to a root on which we can perform direct actions"
  (let [p (if (sequential? korks)
            (apply str (interpose "/" (map clojure.core/name korks)))
            (when korks (clojure.core/name korks)))]
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
  (let [c (chan)]
    (.once root "value" #(let [v (-> (.val %)
                                     (js->clj :keywordize-keys true)
                                     (clojure.core/get-in (if (sequential? ks) ks [ks])))]
                           (put! c v)))
    c))

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
   (let [type (clojure.core/name type)
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
    (.on fbref (clojure.core/name event)
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
