(ns matchbox.quick-intro
  ;; Load and alias
  (:require [matchbox.core :as m]
            [matchbox.registry :as mr]
            ;; these are optional, can use matchbox without core.asyn in project
            [matchbox.async :as ma]
            #+clj [clojure.core.async :as async]
            #+cljs [cljs.core.async :as async]))


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



;; That's all for now, but here's a quick summary of what we haven't covered:
;;
;; TODO: lets update this ns to give light coverage to these cases
;;
;; `listen-to` can also refer directly to children, but no -in suffix required
;; `listen-to` can also handle children-events, which are like typed diffs
;; `listen-children` returns a listener multiplexing over all children-events
;;
;; `set-priority!` and `reset-with-priority!` tap into Firebase's metadata
;;    for explicit ordering of keys within a map
;;
;; `auth` provides credential based authentication
;; `auth-anon` provides anonymous authentication
;; `auth-info` returns a map with current identity
;;
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
      (safe-prn (and msg (not= "oh" (:message msg))))
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
