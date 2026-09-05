![Matchbox - Firebase bindings for Clojure(script)](https://cloud.githubusercontent.com/assets/881351/6866974/0c503f46-d486-11e4-9b88-6e0c833afeb0.png)

## Archived

Matchbox is archived and incompatible with current Firebase APIs.

For new projects:

* **ClojureScript:** use the official [Firebase JavaScript SDK](https://github.com/firebase/firebase-js-sdk).
* **Clojure:** use the official [Firebase Admin Java SDK](https://github.com/firebase/firebase-admin-java),
  or consider [fire](https://github.com/alekcz/fire) for Realtime Database access.

Matchbox is unlikely to be worth reviving:

* The client and Admin SDKs no longer have enough in common for one wrapper.
* The JavaScript SDK's function-based API works well directly from ClojureScript.

## Last release

```clojure
[matchbox "0.0.9"]
```

The rest of this README documents the final release.

## Features

Matchbox offers more than just bindings:

* Atom-, zipper-, and cursor-like abstractions over Firebase references
* Conversion to and from Clojure data
* A uniform API for Clojure and ClojureScript
* Optional sequence abstraction—work with lists rather than sorted maps
* Optional core.async-based API
* Multiplexed child-event channels and callbacks
* Listener registry for scoped or global cleanup

## Usage

Here's a quick taste:

```clojure
(require '[matchbox.core :as m])

(def root (m/connect "https://<app>.firebaseio.com"))

(m/auth-anon root)

(m/listen-children
  root [:users :mike :friends]
  (fn [[event-type data]] (prn data)))

(def mikes-friends (m/get-in root [:users :mike :friends]))
(m/reset! mikes-friends [{:name "Kid A"} {:name "Kid B"}])
(m/conj! mikes-friends {:name "Jean"})

(m/deref
  mikes-friends
  (fn [key value]
    (m/reset-in! root [:users :mike :num-friends]
                 (count value))))

(m/unauth root)
```

See the [quick introduction](docs/quick_intro.cljc) for a more complete tour.

## Overview

For brevity, this table compares Matchbox only with the JavaScript Firebase API.

Notes:

1. Almost all functions accept callbacks. Matchbox intercepts them so they receive hydrated data.
2. This table is not exhaustive. Matchbox also wraps connectivity, queries, authentication,
   logging, and more.

Matchbox | Firebase.js | Differences
-------- | -------- | ------
`connect` | `new Firebase` | Accepts a key or key path as an optional second argument
`get-in` | `.child` | Accepts a string, symbol, keyword, or key path
`parent` | `.parent` | —
`deref` | `.once` | Always uses a `value` subscription
`deref-list` | `.once` | Returns ordered values rather than a map; compatible with queries
`reset!` | `.set` | Automatically serializes data
`reset-with-priority!` | `.setWithPriority` | —
`merge!` | `.update` | —
`conj!` | `.push` | —
`swap!` | `.transaction` | Applies locally by default and accepts additional arguments
`dissoc!` or `remove!` | `.remove` | —
`set-priority!` | `.setPriority` | —
`listen-to` | `.on` | Stored in the registry for easy unsubscription
`listen-list` | `.on` | Like `deref-list` for `listen-to`
`listen-children` | `.on` | Multiplexes all child events through one callback

Additionally, there are up to three variations of most functions:

1. `*-in` variants accept a key or key path as their second argument to refer directly to a child.
   These exist for all functions ending in `!`, as well as `deref` and `deref-list`.
2. `*<` variants return a channel of results instead of taking a callback.
   These exist for all functions that would take a callback.
3. `*-in<` variants combine the first two forms where applicable.

The latter two, when available, are defined in `matchbox.async`, allowing Matchbox to be used
without a `core.async` dependency.

## Examples

There are ClojureScript demos for [Reagent](https://github.com/reagent-project/reagent) and
[Om](https://github.com/omcljs/om) in the `examples` directory. Run `boot dev` from that directory,
then open `http://localhost:3000` in a browser.

## Gotchas

1. `swap!` takes its callback in a non-standard position

   Since we support passing additional arguments to an update function,
   we can't use an optional argument for the callback.

   Our solution draws inspiration from keyword-argument-style signatures, such as
   `(my-function :has "keyword" :style "arguments")`.

   For `swap!`, pass `:callback callback-fn` at the end of the argument list:

   ```clojure
   (m/swap! r f)                  ;; call (f <val>),     no callback
   (m/swap! r f b c)              ;; call (f <val> b c), no callback
   (m/swap! r f :callback cb)     ;; call (f <val>),     with callback `cb`
   (m/swap! r f b c :callback cb) ;; call (f <val> b c), with callback `cb`
   ```

   `:callback` must appear as the second-last argument.

2. JVM callbacks may run on another thread

   Depending on your environment and Firebase configuration, callbacks may be triggered on
   another thread.

   This can be confusing when debugging with `prn`, as `*out*` may not point to the REPL's writer.
   `matchbox.utils/prn` ensures that output is visible.

3. Serialization

   Data | Storage | Reads back as it writes?
   --- | --- | ---
   `{}`, nameable keys | JSON | Not unless all keys are keywords (rest are coerced)
   `{}`, richer keys | Not supported | N/A
   `[]` | JSON with numeric keys | Yes
   `#{}` | JSON with numeric keys | No, reads back as a vector
   `"string"` | string | Yes
   `:a/keyword` | `":a/keyword"` | Yes
   Number | Number | Mostly; some `java.math.*` types differ
   `defrecord` instance | JSON | No, reads back as a vanilla map
   `deftype` instance | JSON | No, reads back as a vanilla map
   Other | Depends on platform | Expect useless strings (JS) or serious downcasting (JVM)

   See the [serialization documentation](docs/serialization.md) for more information.

## History

Matchbox began as a fork of [Pani](https://github.com/verma/pani).

## License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
