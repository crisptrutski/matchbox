# Pani

A convenience library to access Firebase from Clojurescript.

The goal of this library is not to provide access to the entire functionality offered by Firebase, but to make it convenient to use Firebase as a data store from within Clojurescript.

# Current version

The library is in its infancy.  The current version is `0.0.1`.  This library is not on clojars yet.


# Features

Pani offers several benefits over raw JS interop:
 
 * Idiomatic constructs
 * Async channels or callbacks for change notifications
 * Javascript objects abstraction
 
This library, for now, is entirely based on how I intend to use this library (may be with Om etc.) and would grow as I discover more things I'd like it to do.  Pull requests welcome!

# Usage

Require `pani`:

    (:require [pani.core])
    
Create a root object:

	(def r (pani.core/root "https://your-app.firebaseio.com/"))

Bind a callback to recieve callback notifications when a `value` notification occurs:

    (pani.core/bind r :value :age #(log %1))
    
The `bind` call accepts either a key or a seq of keys (`get-in` style):

	(pani.core/bind r :value [:info :world] #(log %1))

You can also bind to other Firebase notification events, e.g. the `child_added` notification:

	(pani.core/bind r :child_added :messages #(log %1))
	
If no callback is specified, the `bind` call returns an async channel:

    (let [c (pani.core/bind r :child_added :messages)]
      (go-loop [msg (<! c)]
        (.log js/console "New message (go-loop):" (:message msg))
        (recur (<! c))))

Use the `set!` call to set a value, like `bind` this function accepts either a single key or a seq of keys:

	(pani.core/set! r [:info :world] "welcome")
	(pani.core/set! r :age 100)

Use the `push!` function to push values into a collection:

	(pani.core/push! r :messages {:message "hello"})
	
Finally, use the `walk-root` function to get a new child node:

	(def messages-root (pani.core/walk-root r :messages))
	(pani.core/bind messages-root :child_added [] #(log %1))


## License

Copyright © 2014 Uday Verma

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
