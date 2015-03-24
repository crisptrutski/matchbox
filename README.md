![Matchbox - Firebase bindings for Clojure(script)](https://cloud.githubusercontent.com/assets/881351/6807041/c6d72cbe-d254-11e4-896f-b75d2153d197.png)

[![Build Status](https://travis-ci.org/crisptrutski/matchbox.svg?branch=master)](https://travis-ci.org/crisptrutski/matchbox)
[![Dependency Status](https://www.versioneye.com/clojure/matchbox:matchbox/badge.svg)](https://www.versioneye.com/clojure/matchbox:matchbox)

# Current version

[![Clojars Project](http://clojars.org/matchbox/latest-version.svg)](http://clojars.org/matchbox)

# Features

Matchbox offers more than just bindings:

 * Clojure data in/out
 * Cursor-like abstraction over Firebase references
 * Nested versions of all operations
 * Optional core.async based API
 * Multiplexed event channels and/or callbacks

# Example usage

```clojure
;; Load and alias
(require '[matchbox.core :as m])

;; Create a printer (this is platform dependent)

;; cljs
(enable-console-print!)
(def safe-prn (partial prn "> "))

;; clj
(def safe-prn (partial matchbox.utils/prn "> "))

;; Get a reference the root (update the URI with your app's name)
(def r (m/connect "https://<your-app>.firebaseio.com/"))

;; Let's write some data
(m/reset! r {:deep {:route "secret"}})

;; Let's read that data back
;; Note: We'll get a tuple with the parent key too, or nil if root
(m/deref r safe-prn) ;; => [nil {:deep {:route "secret"}}]

;; We can also pass a callback, eg. to know once the value is persisted
;; The callback will receive the reference written to.
(m/reset! r {:deep {:route "better-secret"}} safe-prn)

;; We can get child references, and read just that portion
;; Note: going forward I'm omitting key portion in output comments
(def child (m/get-in r [:deep :route]))
(m/deref child safe-prn) ;; => "better-secret"

;; Where it makes sense, you can add `-in` to the operation name to
;; operate on children directly through their parent reference.
;;
;; These variants take an extra argument, in second position, for the path
(m/reset-in! r [:deep :route] "s3krit")
(m/deref-in r [:deep :route] safe-prn)

;; Note that paths are very forgiving, keywords and strings work also:
(m/deref-in r :deep safe-prn)        ;; => {:route "s3krit"}
(m/deref-in r "deep/route" safe-prn) ;; => "s3krit"

;; Now lets add a persistent listener, so we can keep abreast of changes to our root:
(m/listen-to r :value (partial safe-prn "listener: "))

;; Lets see how else we can mutate data
(m/reset! r {:something "else"}) ;; => Note that :deep was lost
(m/merge! r {:less "extreme"})   ;; => This time :something is retained
(m/conj! r "another value")      ;; => A "strange" key was generated
(def child (m/get-in r :less))
(defn rev-str [x] (apply str (reverse x)))
(m/swap! child rev-str)          ;; => Like an atom - value is now "emertxe"
(m/remove! child)                ;; => Only :less was removed

;; Note that `remove!` has an alias `dissoc!`, to fit with `-in` case:
(m/dissoc-in! r [:something]) ;; => Only :something was removed

;; If you're wondering about the random key created by `conj!`, you may want to
;; read this: https://www.firebase.com/blog/2014-04-28-best-practices-arrays-in-firebase.html

;; Coming from writes back to observers, let's take a deeper look at `listen-to`
;; TODO: add note and example about other event types and how it hadnles -in case

;; There is also listen-children, which multiplexes across all children events
;; TODO: add an example

;; TODO: provide notes and examples around using "priority"

;; TODO: provide notes and examples around using auth

;; Lets look quickly at the core.async variants of the various functions
(:require [matchbox.async :as ma])

;; TODO: mention which ops are in async namespace, the naming convention (`<`, `-in<`)

;; TODO: update this example to fit into narrative, and add some around other ops
(let [c (ma/listen-to< r :messages :child_added)]
  (go-loop [msg (<! c)]
    (.log js/console "New message (go-loop):" (:message msg))
    (recur (<! c))))

```

## ClojureScript Examples

All examples are available under the `examples` directory.  To run a Clojurescript example just run the respective `lein` command to build it:

```clojure
lein cljsbuild once <example-name>
```


This should build and place a `main.js` file along with an `out` directory in the example's directory.

You should now be able to go to the example's directory and open the
`index.html` file in a web-browser.

## Gotchas

1. Swap! takes callback in non-standard way

   Since it's common to pass additional arguments to an update function,
   a pragmatic choice was made. We try so support both, by aluding to the
   common inline-keywords style supported by `& {...}`  destructuring:

   ```clojure
   (eg. `(my-function :has "keyword" :style "arguments")`).
   ```

   In our case, we allow `:callback callback-fn` at the end of the args, eg:

   ```clojure
   (m/swap! r f)                  ;; call (f <val>),     no callback
   (m/swap! r f b c)              ;; call (f <val> b c), no callback
   (m/swap! r f :callback cb)     ;; call (f <val>),     with callback `cb`
   (m/swap! r f b c :callback cb) ;; call (f <val> b c), with callback `cb`
   ```

   Note that `:callback` MUST appear as the second-last argument.

2. JVM callbacks on side thread

   Depending on your [environment and
   config](https://www.firebase.com/docs/java-api/javadoc/com/firebase/client/Config.html#setEventTarget(com.firebase.client.EventTarget)),
   callbacks may be triggered on another thread.

   This can be confusing if debugging with `prn` in callbacks, as
   `*out*` will not be to the REPL's writer. We define `matchbox.utils/prn` as a simple
   helper to ensure output is visible.

3. Serialization

  Data | Storage | Stable? 
  --- | --- | ---
  `{}`, nameable keys | JSON  | Not unless all keys are keywords (rest are coerced)
  `{}`, richer keys | Not supported | N/A
  `[]` | JSON with numeric keys | Yes
  `#{}` | JSON with numeric keys | No, comes back as a vector
  `"string"` | string | Yes
  `:a/keyword` | ":a/keyword" | Yes
  Number | Number | Yes
  Record | JSON | No, reads vanilla map
  (def)Type | JSON | No, reads vanilla map
  Other | Depends on platform | Expect useless strings (JS) or serious downcasting (JVM)

   Since Firebase is a JSON-like store, we automatically convert keys in nested
   maps to strings. No metadata about initial type is stored, and keys are
   always read as keywords.

   Maps are not restricted to just keywords and strings though, but the case of
   rich keys has not been handled. On the JVM passing using such values will
   result in a `ClassCastException`, and in JS you can always cast, so you'll
   pull back a keyword, like `:32` or perhaps `:[object Object]`.

   Coming to values, you're at the mercy of the platform and little work has
   been done to manage the semantics. For basic data types they are at least
   consistent between platforms, and mostly lossless or "almost-lossless".

   We leverage `java.util.Collection` and `cljs->js` respectively between the
   JVM and JS. That means that almost everything becomes an array, and most
   primitives stay the same. The most notable difference is that `cljs->js`
   turns keywords into strings, but we're making no such cast on the JVM.

   Strings, booleans and keywords are stable, and stored either as the 
   associated JSON type, or as an EDN string in the case of keywords. We 
   may support symbols, dates etc also as EDN in future.

   Numbers are mostly stable. For JS, floats-only is a gotcha but par for the course. 
   On the JVM Numbers are stable for the core cases of Long and Double, although  more 
   exotic types like `java.math.BigDec` will be cast down. One strange behaviour is that while
   `4.0` will read back as a Double, `4M` will read back as a Long.

   Records are saved as regular maps. Types defined with `deftype` also cast
   down to maps, but their attributes for some reason are `under_scored` rather
   than `kebab-styled`.

   For more advanced types, you're likely to have a bad time. JavaScript will
   probably do something very lossy, and Java to throw a type error.

   If there's interest, some extensible system for adding readers/writers for
   custom types could be added, probably looking something like those found in
   the EDN or Transit libraries.

   Otherwise you can manually wrap your writes and callbacks / channels.

## License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
