;; Matchbox 2 goals
;; ================

;; 1. Smaller API
;; 2. remove excessive sugar (esp. -in and overloading)
;; 3. channels-first
;; ==> summed up as SIMPLE
;; 4. provide all the laziness of snapshots
;; 5. reference counting
;; 6. amortize .val?
;; ==> summed up as EFFICIENT
;; 7. Simple introduction + demos
;; 8. Doc-string all the things
;; 9. Mention entire API on README
;; 10. Never swallow errors (JVM wrappers)

;; Note explicitly on what is not supported:
;; 1. Child listeners do not receive previous-child-name
;;    (check arity?) (specialized listener?)
;;
;;    tricky thing here is - how do we squeeze that into the channel
;;
;; 2. -- Disclose whether transaction committed [now they take this as second arg]