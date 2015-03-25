![Matchbox - Firebase bindings for Clojure(script)](https://cloud.githubusercontent.com/assets/881351/6807041/c6d72cbe-d254-11e4-896f-b75d2153d197.png)

[![Build Status](https://travis-ci.org/crisptrutski/matchbox.svg?branch=master)](https://travis-ci.org/crisptrutski/matchbox)
[![Dependency Status](https://www.versioneye.com/clojure/matchbox:matchbox/badge.svg)](https://www.versioneye.com/clojure/matchbox:matchbox)

# Current version

[![Clojars Project](http://clojars.org/matchbox/latest-version.svg)](http://clojars.org/matchbox)

# Features

Matchbox offers more than just bindings:

 * Atom/Zipper/Cursor-ish abstraction over Firebase references
 * Clojure data in/out
 * Uniform API for JVM and JS platforms
 * Optional core.async based API
 * Multiplexed children event channels and/or callbacks

# Usage

Take a look at the [quick intro](docs/quick_intro.cljx) to for a lightning tour.

## ClojureScript demos

There are some demos in the  `examples` directory.  

Those in the `cljs` folder can be compiled from the main project via `lein cljsbuild once <example-name>`, and then run by opening the 'index.html' found in the example directory in a browser.

There is also a stand-alone demo project, `reagent-example`. This example can be launched by executing `lein run`, and opening "http://localhost:3000" in a browser.

## Gotchas

1. Swap! takes callback in non-standard way

   Since we support passing additional arguments to an update function,
   we can't use an optional argument for the callback.
   
   Our solution draws inspiration from "kwargs" style signatures:

   ```clojure
   (eg. `(my-function :has "keyword" :style "arguments")`).
   ```

   Coming back to `swap!`, we support `:callback callback-fn` at end of arg list:

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

   This can be confusing when debugging with `prn` in callbacks, as
   `*out*` will not be to the REPL's writer. We provide `matchbox.utils/prn` as a simple
   helper to ensure output is visible. 

3. Serialization

  Data | Storage | Reads back as it writes?
  --- | --- | ---
  `{}`, nameable keys | JSON  | Not unless all keys are keywords (rest are coerced)
  `{}`, richer keys | Not supported | N/A
  `[]` | JSON with numeric keys | Yes
  `#{}` | JSON with numeric keys | No, reads back as a vector
  `"string"` | string | Yes
  `:a/keyword` | ":a/keyword" | Yes
  Number | Number | Pretty much, with nits for `java.math.*` types
  Record | JSON | No, reads back as vanilla map
  (def)Type | JSON | No, reads back as vanilla map
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
