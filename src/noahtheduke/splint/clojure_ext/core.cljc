; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns noahtheduke.splint.clojure-ext.core
  (:require
    [noahtheduke.splint.utils :refer [simple-type]])
  (:import
    (java.util.concurrent Executors Future)
    #?@(:bb []
        :clj ([clojure.lang LazilyPersistentVector]))))

(set! *warn-on-reflection* true)

(defn ->list
  "Efficient thread-first friendly concrete list creation.

  (apply list (range 1000)) => 40.8 µs
  (apply list (vec (range 1000))) => 43.8 µs

  (->list (range 1000)) => 28.5 µs
  (->list (vec (range 1000))) => 15.2 µs
  "
  {:inline (fn [coll] `(clojure.lang.PersistentList/create ~coll))}
  [coll]
  #?(:bb (apply list coll)
     :clj (clojure.lang.PersistentList/create coll)))

(comment
  (let [coll (range 1000)
        voll (vec (range 1000))]
    (doall coll)
    (println "apply list")
    (user/quick-bench (apply list nil))
    (user/quick-bench (apply list coll))
    (user/quick-bench (apply list voll))
    (user/quick-bench
      (do (apply list coll)
          (apply list voll)))
    (println "->list")
    (user/quick-bench (->list nil))
    (user/quick-bench (->list coll))
    (user/quick-bench (->list (vec (range 1000))))
    (user/quick-bench
      (do (->list coll)
          (->list voll)))
    ))

(defn mapv*
  "Efficient version of mapv which operates directly on the sequence
  instead of Clojure's reduce abstraction.

  (into [] (map inc) nil) => 75 ns
  (into [] (map inc) (range 1000)) => 17 us
  (into [] (map inc) (vec (range 1000))) => 17 us

  (mapv inc nil) => 70 ns
  (mapv inc (range 1000)) => 21 us
  (mapv inc (vec (range 1000))) => 19 us

  (mapv* inc nil) => 3 ns
  (mapv* inc (range 1000)) => 22 us
  (mapv* inc (vec (range 1000))) => 19 us
"
  [f coll]
  #?(:bb (mapv f coll)
     :clj (let [cnt (count coll)]
            (if (zero? cnt) []
              (let [new-coll (object-array cnt)
                    iter (.iterator ^Iterable coll)]
                (loop [n 0]
                  (when (.hasNext iter)
                    (aset new-coll n (f (.next iter)))
                    (recur (unchecked-inc n))))
                (LazilyPersistentVector/createOwning new-coll))))))

(comment
  (let [coll (range 1000)
        voll (vec coll)]
    (println "doall map")
    (user/quick-bench (doall (map inc nil)))
    (user/quick-bench (doall (map inc coll)))
    (user/quick-bench (doall (map inc voll)))
    (println "into []")
    (user/quick-bench (into [] (map inc) nil))
    (user/quick-bench (into [] (map inc) coll))
    (user/quick-bench (into [] (map inc) voll))
    (println "mapv")
    (user/quick-bench (mapv inc nil))
    (user/quick-bench (mapv inc coll))
    (user/quick-bench (mapv inc voll))
    (println "mapv*")
    (user/quick-bench (mapv* inc nil))
    (user/quick-bench (mapv* inc coll))
    (user/quick-bench (mapv* inc voll))
    ))

(defn run!*
  "Efficient version of run! which operates directly on the sequence
  instead of Clojure's reduce abstraction. Does not respond to `reduced`.

  (run! inc (range 1000)) => 7 µs
  (run!* inc (range 1000)) => 950 ns"
  [f coll]
  #?(:bb (run! f coll)
     :clj (let [cnt (count coll)]
            (if (zero? cnt) []
              (let [iter (.iterator ^Iterable coll)]
                (while (.hasNext iter)
                  (f (.next iter)))
                nil)))))

#_{:clj-kondo/ignore [:unused-value]}
(comment
  (let [coll (range 1000)]
    (println "run!")
    (user/quick-bench
      (run! inc coll))
    (println "run!*")
    (user/quick-bench
      (run!* inc coll))
    nil))

(defn pmap*
  "Efficient version of pmap which avoids the overhead of lazy-seq.

  (doall (pmap (fn [_] (Thread/sleep 100)) coll)) => 3.34 secs
  (pmap* (fn [_] (Thread/sleep 100)) coll) => 202 ms"
  [f coll]
  (let [executor (Executors/newCachedThreadPool)
        futures (mapv #(.submit executor (reify Callable (call [_] (f %)))) coll)
        ret (mapv #(.get ^Future %) futures)]
    (.shutdownNow executor)
    ret))

(comment
  (let [coll (range 1000)]
    (println "doall pmap")
    (user/quick-bench
      (doall (pmap (fn [_] (Thread/sleep 100)) coll)))
    (println "pmap*")
    (user/quick-bench
      (pmap* (fn [_] (Thread/sleep 100)) coll))
    nil))

(defn with-meta*
  "Same as clojure.core/with-meta except it doesn't error if the obj doesn't
  support meta."
  [obj meta]
  (if (instance? clojure.lang.IObj obj)
    (with-meta obj meta)
    obj))

(defprotocol Walk
  (walk* [form inner outer]
    "Protocol version of postwalk"))

(extend-protocol Walk
  ; literals
  nil (walk* [form _inner outer] (outer form))
  Boolean (walk* [form _inner outer] (outer form))
  Character (walk* [form _inner outer] (outer form))
  Number (walk* [form _inner outer] (outer form))
  String (walk* [form _inner outer] (outer form))
  clojure.lang.Keyword (walk* [form _inner outer] (outer form))
  clojure.lang.Symbol (walk* [form _inner outer] (outer form))
  ; reader macros
  clojure.lang.IPersistentMap
  (walk* [form inner outer]
    (with-meta*
      (outer
        (->> form
             (reduce-kv
               (fn [m k v]
                 (assoc! m (inner k) (inner v)))
               (transient {}))
             (persistent!)))
      (meta form)))
  clojure.lang.IPersistentSet
  (walk* [form inner outer]
    (with-meta* (outer (into #{} (map inner) form)) (meta form)))
  clojure.lang.IPersistentVector
  (walk* [form inner outer]
    (with-meta* (outer (mapv* inner form)) (meta form)))
  clojure.lang.ISeq
  (walk* [form inner outer]
    (with-meta* (outer (->list (mapv* inner form))) (meta form)))
  ; else
  Object (walk* [form _inner _outer]
           (throw (ex-info "Unimplemented type: " {:type (simple-type form)
                                                   :form form}))))

(defn postwalk*
  "More efficient and meta-preserving clojure.walk/postwalk.
  Only handles types returned from simple-type.
  All ISeqs return concrete lists.

  (clojure.walk/postwalk identity big-map) => 72 us
  (postwalk* identity big-map) => 25 us"
  [f form]
  (walk* form #(postwalk* f %) f))
