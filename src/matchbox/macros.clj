(ns matchbox.macros
  (:require [matchbox.core :as m]
            [matchbox.async :as ma]))

(defn in-arglists? [sym arglists]
  (let [pred #{sym}]
    (every? (fn [arglist] (some pred arglist))
            arglists)))

(defn not-in-arglists? [sym arglists]
  (let [pred #{sym}]
    (every? (fn [arglist] (not (some pred arglist)))
            arglists)))

(defmacro def-in
  "Generate -in variety of function, which takes korks"
  [fn]
  (let [{:keys [name arglists]} (-> fn resolve meta)]
    (assert (in-arglists? 'ref arglists)
            "Cannot work on arglists without `ref`")
    (assert (not-in-arglists? 'korks arglists)
            "Cannot work on arglists with `korks`")
    `(do
       ~@(for [arglist arglists]
           (let [var-arg? (some #{'&} arglist)
                 reg-args (take-while (partial not= '&) arglist)
                 pre-args (take-while (partial not= 'ref) reg-args)
                 aft-args (drop (inc (count pre-args)) reg-args)
                 new-args (vec (concat pre-args '[ref korks] aft-args
                                       (if var-arg? '[& args])))]
             `(defn ~(symbol (str name "-in")) ~new-args
                (~@(if var-arg? ['apply fn] [fn])
                 ~@pre-args
                 (matchbox.core/get-in ~'ref ~'korks)
                 ~@aft-args
                 ~@(if var-arg? '[args]))))))))

(defmacro def-in*
  "Generate -in variety of function, which takes korks.
   For simplicity assumes:
   1. Only one dispatch arity
   2. That `ref` appears first in arglist
   3. Are not passing in a function which already takes korks"
  [fn]
  (let [{:keys [name arglists]} (-> fn resolve meta)
        arglist  (first arglists)
        var-arg? (some #{'&} arglist)
        reg-args (rest (take-while (partial not= '&) arglist))
        new-args (vec (concat '[ref korks] reg-args (if var-arg? '[& args])))]
    `(defn ~(symbol (str name "-in")) ~new-args
      (~@(if var-arg? ['apply fn] [fn])
       (matchbox.core/get-in ~'ref ~'korks)
       ~@reg-args
       ~@(if var-arg? '[args])))))

(defmacro def-in**
  "Generate -in variety of function, which takes korks.
   For simplicity assumes:
   1. Only one dispatch arity
   2. That `ref` appears first in arglist
   3. Are not passing in a function which already takes korks
   4. Function not variadic"
  [fn]
  (let [{:keys [name arglists]} (-> fn resolve meta)
        arglist  (first arglists)]
    `(defn ~(symbol (str name "-in")) ~(vec (concat '[ref korks] arglist))
       (~fn (matchbox.core/get-in ~'ref ~'korks)
         ~@(rest arglist)))))

(defmacro def-<
  "Generate -< variety of function, which return channels.
   Right off the bat, just implementing the simple version"
  [fn]
  (let [{:keys [name arglists]} (-> fn resolve meta)
        arglist (first arglists)
        cb? (#{'[cb] 'cb} (last arglist))
        regargs (take-while #(#{'& 'cb} %) arglist)
        newargs (vec (if cb? regargs arglist))
        cb  (gensym)]
    `(defn ~(symbol (str name "-in")) ~newargs
       (let [ch# '(matchbox.async/chan)
             ~cb (matchbox.async/chan->cb-once ch#)]
         (~fn ~@regargs ~(if cb?
                           cb
                           (list 'into 'args [:callback cb])))
         ch#))))

(macroexpand '(def-in m/deref))
(macroexpand '(def-in* m/deref))
(macroexpand '(def-in** m/deref))

(macroexpand '(def-in m/reset!))
(macroexpand '(def-in* m/reset!))
(macroexpand '(def-in** m/reset!))

(macroexpand '(def-in m/swap!))
(macroexpand '(def-in* m/swap!))
(macroexpand '(defn a [x e] f))

(macroexpand '(def-< m/deref))
(macroexpand '(def-< m/reset!))
(macroexpand '(def-< m/swap!))

(def-< m/deref)
(def-< m/reset!)
(def-< m/swap!)
