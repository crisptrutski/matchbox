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

Quick spin to get some basic flavour:

```clojure
(require '[matchbox.core :as m])

(def root (m/connect "https://<app>.firebaseio.com"))

(m/auth-anon root)

(m/listen-children
  root [:users :mike :friends]
  (fn [event-type data] (prn data)))

(def mikes-friends (m/get-in root [:users :mike :friends]))
(m/reset! mikes-friends [{:name "Kid A"} {:name "Kid B"}])
(m/conj! mikes-friends {:name "Jean"})

(m/deref
  mikes-friends
  (fn [key value]
    (m/reset-in! root [:users :mike :num-friends]
                 (count value))))]

(m/unauth)
```

Take a look at the [quick intro](docs/quick_intro.cljx) to for a lightning tour.

## ClojureScript demos

There are some demos in the  `examples` directory.  

Those in the `cljs` folder can be compiled from the main project via `lein cljsbuild once <example-name>`, and then run by opening the 'index.html' found in the example directory in a browser.

There is also a stand-alone demo project, `reagent-example`. This example can be launched by executing `lein run`, and opening "http://localhost:3000" in a browser.

## Gotchas

1. `swap!` takes callback in non-standard way

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

  See more info [here](docs/serialization.md)

## License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
