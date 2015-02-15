(ns pani.registry)

(defonce unsubs (atom {}))

(defn register-listener [ref type unsub!]
  (swap! unsubs update-in [(str ref) type] #(set (conj % unsub!))))

(defn- flatten-vals [xs]
  (if-not (map? xs)
    xs
    (reduce into (map flatten-vals (vals xs)))))

(defn- call-all! [fs] (doseq [f fs] (f)))

(defn disable-listeners! [& [ref type unsub! :as path]]
  (let [ref (if ref (str ref))]
    (case (count path)
      0 (do (call-all! (flatten-vals @unsubs))
            (reset! unsubs {}))
      1 (do (call-all! (flatten-vals (get @unsubs ref)))
            (swap! unsubs dissoc ref))
      2 (do (call-all! (flatten-vals (get-in @unsubs [ref type])))
            (swap! unsubs update ref #(dissoc % type)))
      3 (do (unsub!)
            (swap! unsubs update-in [ref type] #(disj % unsub!))))))
