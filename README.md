![Matchbox - Firebase bindings for Clojure(script)](https://cloud.githubusercontent.com/assets/881351/6866974/0c503f46-d486-11e4-9b88-6e0c833afeb0.png)


[![Build Status](https://travis-ci.org/crisptrutski/matchbox.svg?branch=master)](https://travis-ci.org/crisptrutski/matchbox)
[![Dependency Status](https://www.versioneye.com/clojure/matchbox:matchbox/badge.svg)](https://www.versioneye.com/clojure/matchbox:matchbox)
[![Join the chat at https://gitter.im/crisptrutski/matchbox](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/crisptrutski/matchbox?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Current version

[![Clojars Project](http://clojars.org/matchbox/latest-version.svg)](http://clojars.org/matchbox)

# Features

Matchbox offers more than just bindings:

 * Atom/Zipper/Cursor-ish abstraction over Firebase references
 * Clojure data in/out
 * Uniform API for JVM and JS platforms
 * Optional sequence abstraction - work with lists not sorted maps
 * Optional core.async based API
 * Multiplexed children event channels and/or callbacks
 * Registry for listeners simplifies scoped or global cleanup

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
                 (count value))))

(m/unauth)
```

Take a look at the [quick intro](docs/quick_intro.cljx) to for a lightning tour.

# Overview

For brevity, comparing to the JS Firebase API only.

Notes:

1. Almost all functions accept callbacks, and those callbacks will be intercepted to receive hydrated data.
2. This list is not complete, notably it does not cover connectivity control and hooks, queries, auth, logging and other aspects also wrapped by Matchbox.

Matchbox | Firebase.js | Differences
-------- | -------- | ------
`connect` | `Firebase.` | Supports optional korks as second parameter
`get-in` | `.child` | Supports symbols, keywords and sequences also (korks)
`parent` | `.parent` | -
`deref` | `.once` | Fixed to a "value" subscription
`deref-list` | `.once` | Returns ordered values rather than a map. Query compatible.
`reset!` | `.set` | Data automatically serialized in a sensible manner
`reset-with-priority!` | `.setWithPriority` | ..
`merge!` | `.update` | ..
`conj!` | `.push` | ..
`swap!` | `.transaction` | Always applied locally, supports additional arguments.
`dissoc!` or `remove!` | `.remove` | -
`set-priority!` | `.setPriority` | -
`listen-to` | `.on` | Stored in registry for easy unsubscription
`listen-list` | `.on` | Like `deref-list` for `listen-to`
`listen-children` | `.on` | Listen to all child events, multiplexed.

Additionally, there are up to three variations of most functions:

1. `*-in` variants take an optional second parameter of `korks`, to refer directly to a child.
   These exist for all "ending-with-a-bang" functions, as well as `deref` and `deref-list`.
2. `*<` variants return a channel of results instead of taking a callback.
   These exist for all functions that would take a callback.
3. `*-in<` combine decoration of (1) and (2), and exist where applicable.

The last two, if they exist are defined in `matchbox.async`. This is so that
Matchbox can be used without a `core.async` dependency.

# Examples

There are some ClojureScript demos in the  `examples` directory.

Those in the `cljs` folder can be compiled from the main project via
`lein cljsbuild once <example-name>`, and then run by opening the 'index.html'
found in the example directory in a browser.

There is also a stand-alone demo project, `reagent-example`. This example can be
launched by executing `lein run`, and opening "http://localhost:3000" in a browser.

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
