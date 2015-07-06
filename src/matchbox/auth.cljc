(ns matchbox.auth
  (:require [matchbox.impl :as impl]
            [matchbox.coerce :as coerce]))

#?(:cljs
   (defn build-opts [session-only?]
     (if session-only?
       #js {:remember "sessionOnly"}
       undefined)))

(defn- wrap-auth-cb [f]
  #?(:cljs 
     ;; evil node-convention call style
     (if cb
       (fn [err info] (f err (coerce/hydrate info)))
       identity)
     :clj
     (let [g (if f (comp coerce/hydrate f) identity)]
       (impl/auth-handler g impl/err-handler))))

(defn auth [ref email password handler #?(:cljs session-only?)]
  (.authWithPassword ref
    #?(:cljs {:email email, :password password})
    #?@(:clj [email password])
    (wrap-auth-cb handler)
    #?(:cljs (build-opts session-only?))))

(defn auth-anon [ref handler #?(:cljs session-only?)]
  (.authAnonymously ref
    (wrap-auth-cb handler)
    #?(:cljs (build-opts session-only?))))

(defn auth-info
  "Return map of {uid, provider, token, expires}, if there is a session"
  [ref]
  (some-> ref .getAuth impl/parse-auth))

;; onAuth and offAuth are not wrapped yet

;; this is a super worthwhile wrapper..
(defn unauth [ref]
  (.unauth ref))


;; TODO:
;; 0. wrap java auth
;; 1. cover other methods of auth
;; 2. better error handling


;; TIL: macros have access to (static) scope around their call..
;; TIL: actually no, they have access to something magical, that is the dynamic scope
;; TIL: it's pretty wierd: env (no, )&env (no), `~env (no), `~&env (yesy).. what is it?
