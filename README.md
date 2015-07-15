Matchbox 2 goals
================

1. Smaller API
2. remove excessive sugar (esp. -in and overloading)
3. channels-first
4. provide all the laziness of snapshots
5. reference counting
6. amortize .val?
7. Simple introduction + demos
8. Doc-string all the things
9. Mention entire API on README
10. Never swallow errors (JVM wrappers)

Note explicitly on what is not supported:
1. Child listeners do not receive previous-child-name
   (check arity?) (specialized listener?)
   
   tricky thing here is - how do we squeeze that into the channel
   
2. -- Disclose whether transaction committed [now they take this as second arg]


re: off!
idea: have this overloaded for refs, queries and channels. perhaps even snapshots
idea: using for refs (and maybe snapshots): generic listener, or unsub all at that path
idea: using for query uses metadata to remove only that specific one
idea: for channel (that is a matchbox value channel), deactive just its producer(s)
idea: `off-all`, which disables all listeners for sub paths also
