(ns matchbox.quick-intro
  ;; Load and alias
  (:require [matchbox.core :as m]
            [matchbox.registry :as mr]
            ;; who can live without this guy
            [clojure.string :as str]
            ;; these are optional, can use matchbox without core.asyn in project
            [matchbox.async :as ma]
            [#+clj clojure.core.async
             #+cljs cljs.core.async
             :as async]))


;; Create thread-safe pretty printer, with clear separation

(def prn-chan (async/chan))

(defn safe-prn [& msgs]
  (async/put! prn-chan msgs))

(async/go-loop []
  (let [msgs (async/<! prn-chan)]
    (doseq [msg msgs]
      (if (string? msg)
        (println msg)
        (clojure.pprint/pprint msg)))
    (println " ")
    (recur)))


;; JVM only - you can adjust log level, but only before first API call
;; Let's set it to the most verbose settings
#+clj (try (m/set-logger-level! :debug)
           (catch Throwable e "Too late to set log level"))

;; Supported logger levels
#+clj (prn (keys m/logger-levels))


;; Get a reference the root (update the URI with your app's name)
(def base-uri "https://luminous-torch-5788.firebaseio.com/")

;; We get a reference to firebase, starting in a random keyspace
(def r (m/connect base-uri (str (rand-int 100))))

;; Let's write some data
(m/reset! r {:deep {:route "secret"}})

;; Let's read that data back
;; Note: We'll get a tuple of [<key> value], where <key> is the name
;; of the immediate parent, or `nil` if you're reading from the root.
(m/deref r safe-prn)
;; => [<key> {:deep {:route "secret"}}]

;; We can also pass a callback, eg. to know once the value is persisted
;; The callback will receive the reference written to.
(m/reset! r {:deep {:route "better-secret"}} safe-prn)

;; We can get child references, and read just that portion
;; Note: going forward I'm omitting key portion in output comments
(def child (m/get-in r [:deep :route]))
(m/deref child safe-prn)
;; => ["route" "better-secret"]

;; We can also get back to parents (unlike a cursor)
(= r (-> child m/parent m/parent))

;; And get the whole ancestor chain in one go
(for [ref (m/parents child)]
  (str/replace (str ref) #"^.*?firebaseio\.com" ""))

;; Where it makes sense, you can add `-in` to the operation name to
;; operate on children directly through their parent reference.
;;
;; These variants take an extra argument, in second position, for the path
(m/reset-in! r [:deep :route] "s3krit")
(m/deref-in r [:deep :route] safe-prn)

;; Note that paths are very forgiving, keywords and strings work also:
(m/deref-in r :deep safe-prn)        ;; => {:route "s3krit"}
(m/deref-in r "deep/route" safe-prn) ;; => "s3krit"

;; Now lets add a persistent listener:
(def listener
  (m/listen-to r :value (partial safe-prn ['listen-to :value])))
;; (note that it immediately prints the current value)

;; And get a sub-reference and helper function ready
(def child (m/get-in r :less))
(defn rev-str [x] (apply str (reverse x)))


;; Now lets see all the basic mutating verbs:

(m/reset! r {:something "else"}) ;; => Note that :deep was lost
(m/merge! r {:less "extreme"})   ;; => This time :something is retained
(m/conj! r "another value")      ;; => A "strange" key was generated
(m/swap! child rev-str)          ;; => Like an atom - value is now "emertxe"
(m/remove! child)                ;; => Only :less was removed
;; Note that `remove!` has an alias `dissoc!`, to fit with `-in` case:
(m/dissoc-in! r [:something])    ;; => Only :something was removed

;; If you're wondering about the random key created by `conj!`
;; You should probably read this:
;; https://www.firebase.com/blog/2014-04-28-best-practices-arrays-in-firebase.html

;; Now lets close that listener again
(mr/disable-listener! listener)

;; Note that we can unsubscribe *all* listeners created with Matchbox
(mr/disable-listeners!)

;; You can also remove listeners by reference, or reference and type
;; See (clojure.repl/doc mr/disable-listeners!)


;; Now lets look at working with ordered data and priorites

(def c (m/get-in r [:todos]))
(m/remove! c)
(m/reset-with-priority-in! c :c "Release next version" 4)
(m/reset-with-priority-in! c :b "Clean up docs" 2)
(m/reset-with-priority-in! c :a "Finish this intro" 3)
(m/reset-with-priority-in! c :d "Go to sleep" 1)

;; lets try read that..
(m/deref c safe-prn)

;; oh darn, map lost the order.. let's try another getter
(println "Original order\n\n")
(m/deref-list c safe-prn)

(m/set-priority-in! c :a -500)

(println "Reordered\n\n")
(m/deref-list c safe-prn)

;; There's also a listener version of `deref-list`, and lets
;; throw in
(def listener (m/listen-list (m/take c 3) safe-prn))

;; lets make a change and see the listener get called
(m/set-priority-in! c :a 5)

;; Note that it was called twice - since two child-events
;; were processed by Firebase to update the state of the associated
;; query.

;; let's add a value at the end, note that it isn't called
(m/reset-with-priority-in! c :f "Mow the lawn" 20)

;; and a value at the start to shunt another value out
(m/reset-with-priority-in! c :f "Wrap up this example" -1000)

(mr/disable-listener! listener)

;; Let's look at one more way we can order things:

(m/deref-list (m/order-by-priority c) safe-prn)
(m/deref-list (m/order-by-value c) safe-prn)
(m/deref-list (m/order-by-key c) safe-prn)

;; And one more example, that requires map data for children

(def c (m/get-in r [:potential-dates]))
(m/remove! c)
(m/conj! c {:name "James", :age 32})
(m/conj! c {:name "Jamie", :age 12})
(m/conj! c {:name "Jebediah", :age 3212})

(m/deref-list (m/order-by-child c :age) safe-prn)
(m/deref-list (m/order-by-child c :name) safe-prn)


;; Let's go back to the observer, `listen-to`

;; It can also refer directly to deep children, but no "-in" suffix is used:
(m/listen-to r [:a :b :c :d :e] :value safe-prn)
(m/reset-in! r [:a :b :c :d :e] "boo")
(mr/disable-listeners!)

;; It can also listen to more granular children events, for example:
(m/listen-to r :abcde :child-added safe-prn)
(doseq [i [1 3 3 7]]
  (m/conj-in! r :abcdee i))
(mr/disable-listeners!)

;; The listen of child events handled:
(prn m/child-events)

;; You can also listen to all child-events with a single callback,
;; receiving [type value] pairs instead of just values.
(def c (m/get-in r :edcba))
(m/listen-children c safe-prn)
(m/conj! c 42)
(m/merge! c {:d 45})
(m/set-priority-in! c :d -10)
(m/dissoc-in! c :d)
(mr/disable-listeners!)


;; Let's look at sessions now

;; We'll create an anonymous session.
;; You could also log in with `(m/auth r <email> <password)`,
;; and in future we'll support the other options too.
(m/auth-anon r (fn [err auth-data]
                 (safe-prn auth-data
                           ;; Once authenticated, this info is also availabel
                           ;; via any ref on the app
                           (m/auth-info r))))

(m/auth r "jeffpalentine@gmail.com" "KwfDwHPr*xvrmr4AhK+JTD8smFphhsE8zbUCND" safe-prn)

;; Let's close the session, not going into security here
(m/unauth r)


;; `disconnect!` and `reconnect!` allow control of whether data should sync
;; `connected?` returns whether data should sync (but does not test connection)
;; `on-disconnect` adds callbacks for when connection drops
;; `cancel` can remove these callbacks


;; core.async flavoured interface
;; ------------------------------

;; Lets look quickly at the core.async variants of the various functions

;; We can get channel flavoured listeners
(def baby-chan (ma/listen-to< r :messages :child-added))

(def baby-sitter (ma/listen-children< r :household))

(async/go
  ;; Mutation functions can return channels, which deliver once confirmed
  (async/<! (ma/reset!< r {:tabula "rasa"}))
  (println "< done >\n\n")

  ;; Also, note how `-in` can compose with `<`
  (async/<! (ma/reset-in!< r [:a :deep :forest] {:super "spooky"}))
  (println "(This deep forest is super spooky for sure)\n\n")

  ;; Lets send out some child-like updates
  (doseq [word ["like" "baby" "baby" "baby" "oh"]]
    (ma/conj-in!< r :messages {:message word}))

  ;; Without blocking for a change
  (ma/reset-in!< r [:household :first-child] {:name "john"}) ;; kid got home
  (ma/merge-in!< r [:household :first-child] {:name "j-dog"}) ;; ... kids
  ;; But we can block later, since Firebase knows these must be ordered
  (async/<! (ma/dissoc-in!< r [:household :first-child])) ;; kid went back to school

  ;; And finally let's drain those listener channels
  (loop []
    (let [[key msg] (async/<! baby-chan)]
      (safe-prn '[listen-to<] (:message msg))
      (if (and msg (not= "oh" (:message msg)))
        (recur))))

  (loop []
    (let [[type body] (async/<! baby-sitter)]
      (safe-prn '[listen-children<] type body)
      (if (and body (not= :child-removed type))
        (recur))))

  ;; It's now safe to remove the listeners
  ;; TODO: API for removing listener by channel
  (mr/disable-listeners!))


(comment
  (remove-ns 'matchbox.quick-intro))
