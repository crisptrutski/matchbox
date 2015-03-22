# Matchbox

Firebase bindings for Clojure(Script)

[![Build Status](https://travis-ci.org/crisptrutski/matchbox.svg?branch=master)](https://travis-ci.org/crisptrutski/matchbox)
[![Dependency Status](https://www.versioneye.com/clojure/matchbox:matchbox/badge.svg)](https://www.versioneye.com/clojure/matchbox:matchbox)

# Current version

[![Clojars Project](http://clojars.org/matchbox/latest-version.svg)](http://clojars.org/matchbox)


# Features

Matchbox offers more than just bindings:

 * Clojure data in/out
 * Cursor/Atom-like abstraction over Firebase references
 * Nested versions of all operations
 * Optional core.async based API
 * Multiplexed event channels and/or callbacks


# Usage

Require `matchbox`:

    (:require [matchbox.core :as p])
    (:require [matchbox.async :as pa]) ;; if wanting to use with core.async

Create a root object:

	(def r (p/connect "https://your-app.firebaseio.com/"))

Bind a callback to recieve callback notifications when a `value` notification occurs:

    (p/listen-to r :ago :value #(log %1))

The `listen-to` call accepts either a key or a seq of keys (`get-in` style):

	(p/listen-to r [:info :world] :value #(log %1))

You can also listen to other Firebase notification events, e.g. the `child-added` notification:

	(p/listen-to r :messages :child-added #(log %1))

If no callback is specified, the `listen-to` call returns an async channel:

    (let [c (p/listen-to r :messages :child_added)]
      (go-loop [msg (<! c)]
        (.log js/console "New message (go-loop):" (:message msg))
        (recur (<! c))))

Use the `reset!` call to set a value, like `bind` this function accepts either a single key or a seq of keys:

	(p/reset-in! r [:info :world] "welcome")
	(p/reset-in! r :age 100)

Use the `conj!` function to push values into a collection:

	(p/conj! r {:message "hello"})

Finally, use the `get-in` function to get a new child node:

	(def messages-root (p/get-in r :messages))
	(p/listen-to messages-root :child-added #(log %1))

## Clojurescript Examples
***Note that***, most examples will require you to add your Firebase app url to the example.  You'd most likely have to edit a line like the following in one of the source files (most likely `core.cljs`):

	;; TODO: Set this to a firebase app URL
	(def firebase-app-url "https://your-app.firebaseio.com/")


All examples are available under the `examples` directory.  To run a Clojurescript example just run the respective `lein` command to build it:

    lein cljsbuild once <example-name>

This should build and place a `main.js` file along with an `out` directory in the example's directory.

You should now be able to go to the example's directory and open the
`index.html` file in a web-browser.

## Gotchas

1. Java callbacks

   Depending on your (environment and
   config)[https://www.firebase.com/docs/java-api/javadoc/com/firebase/client/Config.html#setEventTarget(com.firebase.client.EventTarget)],
   callbacks may be triggered on another thread.

   This can be confusing if debugging with `prn` in callbacks, as
   `*out*` will not be to the REPL's writer. We define `matchbox.utils/prn` as a simple
   helper to ensure output is visible.

2. Serialization

   Since Firebase is a JSON-like store, we automatically convert keys in nested
   maps to strings, without any metadata about initial type. When hydrating,
   keys are always converted to keywords.

   There are further gotchas with values - and this may differ slightly between the CLJ
   and CLJS variants. Collections all become arrays (which hydrate to vectors),
   which is due to goes back to behaviour of`cljs->js` and matching against
   `java.util.Collection` of the ClojureScript and Clojure cases respectively.

   Strings and boolean are stable, but keywords are turned to strings in CLJS or
   maps in CLJ (`{:sym {"name" "b", "namespace" "a"}, :name "b", :namespace
   "a"}`, so you know.) This is likely to change. Perhaps magical treatment of
   strings with a colon prefix.

   Numbers are stable for the basic cases of Long and Double, although more
   exotic types like `java.math.BigDec` will be cast down. JavaScript only has
   one number type here so nothing to think about for ClojureScript.

   Records are saved as regular maps. Types defined with `deftype` also cast
   down to maps, but their attributes for some reason are `under_scored` rather
   than `kebab-styled`.

   For more advanced types, you're likely to have a bad time. JavaScript will
   probably do something very lossy, and Java to throw a type error.

   Potentially a feather should be plucked from EDN or Transit, and we should
   support user defined readers/writers added, because manually wrapping all your writes
   and callbacks (or putting a transducer on your channels) is not very practical.



## License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
