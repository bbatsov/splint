; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns noahtheduke.spat
  (:require
   [clj-kondo.hooks-api :as hapi]
   [clj-kondo.impl.rewrite-clj.parser :as p]
   [clj-kondo.impl.utils :as u]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [clojure.pprint :as pprint]
   [clojure.java.io :as io])
  (:import
    (java.io File)))

(set! *warn-on-reflection* true)

(defn s-type [sexp]
  (cond
    ; literals
    (or (nil? sexp)
        (boolean? sexp)
        (char? sexp)
        (number? sexp)) :literal
    (keyword? sexp) :keyword
    (string? sexp) :string
    (symbol? sexp) :symbol
    ; reader macros
    (map? sexp) :map
    (set? sexp) :set
    (vector? sexp) :vector
    (seq? sexp) :list
    :else (class sexp)))

(comment
  (s-type {:a 1})
  (s-type (Object.)))

(defn read-dispatch [sexp _form _retval]
  (let [type (s-type sexp)]
    (case type
      :symbol (let [s-name (name sexp)]
                (cond
                  (= '_ sexp) :any
                  (= \% (first s-name)) :pred
                  (= \? (first s-name)) :var
                  (= "&&." (subs s-name 0 (min (count s-name) 3))) :rest
                  :else :symbol))
      :list (if (= 'quote (first sexp))
              :quote
              :list)
      ;; else
      type)))

(defmulti read-form #'read-dispatch)

(defmacro pattern
  "Must be wrapped in a function to be useful."
  [sexp]
  (let [form (gensym "form-")
        retval (gensym "retval-")]
    `(fn [~form]
       (let [~retval (atom {})]
       (when ~(read-form sexp form retval)
         @~retval)))))

(defmethod read-form :default [sexp form retval]
  `(do (throw (ex-info "default" {:type (read-dispatch ~sexp ~form ~retval)}))
       false))

(defmethod read-form :quote [sexp form retval]
  (read-form (first (next sexp)) form retval))

(defmethod read-form :literal [sexp form _retval]
  `(= ~sexp (:value ~form)))

(defmethod read-form :keyword [sexp form _retval]
  `(= ~sexp (:k ~form)))

(defmethod read-form :string [sexp form _retval]
  `(when-let [lines# (:lines ~form)]
     (= ~(str/split-lines sexp) lines#)))

(defn get-simple-val [form]
  (or (:value form)
      (:k form)
      (some->> (:lines form) (str/join "\n"))))

(defn get-val [form]
  (or (get-simple-val form)
      (:children form)))

(defmethod read-form :any [_sexp _form _revtal] nil)

(defmethod read-form :pred [sexp form retval]
  ;; pred has to be unquoted and then quoted to keep it a simple keyword,
  ;; not fully qualified, as it needs to be resolved in the calling namespace
  ;; to allow for custom predicate functions
  (let [[pred bind] (str/split (name sexp) #"%-")
        pred (requiring-resolve (symbol (str *ns*) (subs pred 1)))
        bind (when bind (symbol bind))]
    `(when-let [result# (~pred (get-val ~form))]
       (when (and result# '~bind)
         (swap! ~retval assoc '~bind (hapi/sexpr ~form)))
       result#)))

(def ^:private sentinel (Object.))

(defmethod read-form :var [sexp form retval]
  `(do (swap! ~retval assoc '~sexp {:sentinel sentinel
                                    :val (hapi/sexpr ~form)})
       true))

(defmethod read-form :symbol [sexp form _retval]
  `(= '~sexp (:value ~form)))

(defmethod read-form :rest [sexp form retval]
  ;; drop &&.
  (let [sym (symbol (subs (name sexp) 3))]
    `(do (swap! ~retval assoc '~sym {:sentinel sentinel
                                     :vals (map hapi/sexpr ~form)})
         true)))

(defn- read-form-seq [sexp form retval tag]
  (let [children-form (gensym (str (name tag) "-form-"))
        rest? (volatile! false)
        preds (loop [idx 0
                     sexp sexp
                     acc []]
                (if (seq sexp)
                  (let [item (first sexp)]
                    ;; flag that we've hit a special rest binding
                    (if (= '&&. item)
                      (do (vreset! rest? true)
                          (let [ret (read-form
                                      (symbol (str "&&." (name (second sexp))))
                                      `(drop ~idx ~children-form)
                                      retval)]
                            (conj acc ret)))
                      (let [res (read-form item `(nth ~children-form ~idx) retval)]
                        (recur (inc idx)
                               (next sexp)
                               (if res (conj acc res) acc)))))
                  acc))
        ;; If there's a rest arg, then count of given will be less than or equal
        size-pred (if @rest?
                    `(<= ~(- (count sexp) 2) (count ~children-form))
                    `(= ~(count sexp) (count ~children-form)))
        new-form (gensym (str (name tag) "-new-form-"))]
    `(let [~new-form ~form]
       (and (= ~tag (:tag ~new-form))
            (let [~children-form (:children ~new-form)]
              (and ~size-pred
                   ~@preds))))))

(comment
  (declare check-all-rules)
  (check-all-rules
    (p/parse-string "(loop [] (do 1))"))
  ((pattern '(loop ?binding &&. ?x)) (p/parse-string "(loop [] (do 1))"))
  )

(defmethod read-form :list [sexp form retval]
  (read-form-seq sexp form retval :list))

(defmethod read-form :vector [sexp form retval]
  (read-form-seq sexp form retval :vector))

(def simple? #{:literal :keyword :string :symbol :token})

(defmethod read-form :map [sexp form retval]
  {:pre [(every? (comp simple? s-type) (keys sexp))]}
  (let [children-form (gensym "map-form-children-")
        c-as-map (gensym "map-form-as-map")
        simple-keys (filterv #(simple? (s-type %)) (keys sexp))
        simple-keys-preds (->> (select-keys sexp simple-keys)
                               (mapcat (fn [[k v]]
                                         [(list `contains? c-as-map k)
                                          (read-form v (list `get c-as-map k) retval)])))
        new-form (gensym "map-new-form-")]
    `(when-let [~new-form ~form]
       (and (= :map (:tag ~new-form))
            (let [~children-form (:children ~new-form)]
              (and (every? #(simple? (u/tag %)) (take-nth 2 ~children-form))
                   (let [~c-as-map (->> ~children-form
                                        (partition 2)
                                        (map (fn [[k# v#]] [(get-val k#) v#]))
                                        (into {}))]
                     (and (<= ~(count simple-keys) (count ~c-as-map))
                          ~@simple-keys-preds))))))))

(defn vec-remove
  "remove elem in coll
  from: https://stackoverflow.com/a/18319708/3023252"
  [pos coll]
  (vec (concat (subvec coll 0 pos) (subvec coll (inc pos)))))

(defmethod read-form :set [sexp form retval]
  (let [children-form (gensym "set-form-children-")
        simple-vals-set (gensym "set-form-simple-vals-")
        [simple-vals complex-vals] (reduce (fn [acc cur]
                                             (if (simple? (s-type cur))
                                              (update acc 0 conj cur)
                                              (update acc 1 conj cur)))
                                           [[] []]
                                           sexp)
        simple-keys-preds (map (fn [k] (list `contains? simple-vals-set k)) simple-vals)
        current-child (gensym "set-current-child-")
        complex-keys-preds (mapv (fn [k]
                                   `(fn [~current-child]
                                      ~(read-form k current-child retval)))
                                complex-vals)
        new-form (gensym "set-new-form-")]
    `(when-let [~new-form ~form]
       (and (= :set (:tag ~new-form))
            (let [~children-form (vec (:children ~new-form))]
              (and (<= ~(count sexp) (count ~children-form))
                   (let [~simple-vals-set (set (keep get-simple-val ~children-form))]
                     (and (or ~(empty? simple-keys-preds)
                              (and ~@simple-keys-preds))
                          (or ~(empty? complex-keys-preds)
                              ;; loop over both the predicates and the children.
                              ;; for each predicate, compare it against each child
                              ;; until it finds a match, and then remove the child
                              ;; from the list of children and recur.
                              (loop [complex-keys-preds# (seq ~complex-keys-preds)
                                     complex-children#
                                     (vec (for [child# ~children-form
                                                :when (not (contains? ~simple-vals-set (get-simple-val child#)))]
                                            child#))]
                                (or (empty? complex-keys-preds#)
                                    (when-let [cur-pred# (first complex-keys-preds#)]
                                      (let [idx#
                                            (loop [idx# 0]
                                              (when-let [cur-child# (nth complex-children# idx# nil)]
                                                (if (cur-pred# cur-child#)
                                                  idx#
                                                  (recur (inc idx#)))))]
                                        (when idx#
                                          (recur (next complex-keys-preds#) (vec-remove idx# complex-children#))))))))))))))))

(defmacro defrule
  [rule-name & opts]
  (let [docs (when (string? (first opts)) (first opts))
        opts (if (string? (first opts)) (next opts) opts)
        {:keys [pattern replace replace-fn]} opts]
    (assert (simple-symbol? rule-name) "defrule name cannot be namespaced")
    (assert (not (and replace replace-fn))
            "defrule cannot define both replace and replace-fn")
    `(def ~rule-name
       {:name ~(str rule-name)
        :docstring ~docs
        :pattern-raw '~pattern
        :replace-raw '~replace
        :pattern (pattern ~pattern)
        :replace (when ~replace
                   (fn ~(symbol (str rule-name "-replacer-fn"))
                     [form#]
                     (when (not-empty form#)
                       (walk/postwalk-replace form# ~replace))))
        :replace-fn ~replace-fn})))

(defrule plus-x-1
  {:pattern '(+ ?x 1)
   :replace '(inc ?x)})

(defrule plus-1-x
  {:pattern '(+ 1 ?x)
   :replace '(inc ?x)})

(defrule minus-x-1
  {:pattern '(- ?x 1)
   :replace '(dec ?x)})

(defrule nested-muliply
  {:pattern '(* ?x (* &&. ?xs))
   :replace '(* ?x &&. ?xs)})

(defrule nested-addition
  {:pattern '(+ ?x (+ &&. ?xs))
   :replace '(+ ?x &&. ?xs)})

(defrule plus-0
  {:pattern '(+ ?x 0)
   :replace '?x})

(defrule minus-0
  {:pattern '(- ?x 0)
   :replace '?x})

(defrule multiply-by-1
  {:pattern '(* ?x 1)
   :replace '?x})

(defrule divide-by-1
  {:pattern '(/ ?x 1)
   :replace '?x})

(defrule multiply-by-0
  {:pattern '(* ?x 0)
   :replace '0})

(def math-rules
  [plus-x-1
   plus-1-x
   minus-x-1
   nested-muliply
   nested-addition
   plus-0
   minus-0
   multiply-by-1
   divide-by-1
   multiply-by-0])

(defrule str-to-string
  "(.toString) to (str)"
  {:pattern '(.toString ?x)
   :replace '(str ?x)})

(defrule str-apply-interpose
  "(apply str (interpose)) to (str/join)"
  {:pattern '(apply str (interpose ?x ?y))
   :replace '(clojure.string/join ?x ?y)})

(defrule str-apply-reverse
  "(apply str (reverse)) to (str/reverse)"
  {:pattern '(apply str (reverse ?x))
   :replace '(clojure.string/reverse ?x)})

(defrule str-apply-str
  "(apply str) to (str/join)"
  {:pattern '(apply str ?x)
   :replace '(clojure.string/join ?x)}) 

(def string-rules
  "All str and clojure.string related rules"
  [str-to-string
   str-apply-interpose
   str-apply-reverse
   str-apply-str])

(defrule mapcat-apply-apply
  {:pattern '(apply concat (apply map ?x ?y))
   :replace '(mapcat ?x ?y)})

(defrule mapcat-concat-map
  {:pattern '(apply concat (map ?x &&. ?y))
   :replace '(mapcat ?x &&. ?y)})

(defrule filter-complement
  {:pattern '(filter (complement ?pred) ?coll)
   :replace '(remove ?pred ?coll)})

(defrule filter-seq
  {:pattern '(filter seq ?coll)
   :replace '(remove empty? ?coll)})

(defrule filter-fn*-not-pred
  {:pattern '(filter (fn* [?x] (not (?pred ?x))) ?coll)
   :replace '(remove ?pred ?coll)})

(defrule filter-fn-not-pred
  {:pattern '(filter (fn [?x] (not (?pred ?x))) ?coll)
   :replace '(remove ?pred ?coll)})

(defrule filter-vec-filter
  {:pattern '(vec (filter ?pred ?coll))
   :replace '(filterv ?pred ?coll)})

(def sequence-rules
  [mapcat-apply-apply
   mapcat-concat-map
   filter-complement
   filter-seq
   filter-fn*-not-pred
   filter-fn-not-pred
   filter-vec-filter])

(defrule first-first
  {:pattern '(first (first ?coll))
   :replace '(ffirst ?coll)})

(defrule first-next
  {:pattern '(first (next ?coll))
   :replace '(fnext ?coll)})

(defrule next-next
  {:pattern '(next (next ?coll))
   :replace '(nnext ?coll)})

(defrule next-first
  {:pattern '(next (first ?coll))
   :replace '(nfirst ?coll)})

(def first-next-rules
  [first-first
   first-next
   next-first
   next-next])

(defrule fn*-wrapper
  {:pattern '(fn* [?arg] (?fun ?arg))
   :replace '?fun})

(defrule fn-wrapper
  {:pattern '(fn [?arg] (?fun ?arg))
   :replace '?fun})

(def fn-rules
  [fn*-wrapper
   fn-wrapper])

(defrule thread-first-no-arg
  "(-> x) to x"
  {:pattern '(-> ?x)
   :replace '?x})

(defn symbol-or-keyword-or-list? [sexp]
  (or (symbol? sexp)
      (keyword? sexp)
      (list? sexp)
      (and (sequential? sexp) (not (vector? sexp)))))

(defrule thread-first-1-arg
  "(-> x y) to (y x)
  (-> x (y)) to (y x)"
  {:pattern '(-> ?arg %symbol-or-keyword-or-list?%-?form)
   :replace-fn (fn [{:syms [?arg ?form]}]
                 (if (list? ?form)
                   (list* (first ?form) ?arg (rest ?form))
                   (list ?form ?arg)))})

(defrule thread-last-no-arg
  "(->> x) to x"
  {:pattern '(->> ?x)
   :replace '?x})

(defrule thread-last-1-arg
  "(->> x y) to (y x)
  (->> x (y)) to (y x)"
  {:pattern '(->> ?arg %symbol-or-keyword-or-list?%-?form)
   :replace-fn (fn [{:syms [?arg ?form]}]
                 (if (list? ?form)
                   (list* (concat ?form [?arg]))
                   (list ?form ?arg)))})

(def threading-rules
  [thread-first-no-arg
   thread-first-1-arg
   thread-last-no-arg
   thread-last-1-arg])

(defrule not-some-pred
  {:pattern '(not (some ?pred ?coll))
   :replace '(not-any? ?pred ?coll)})

(defrule with-meta-f-meta
  {:pattern '(with-meta ?x (?f (meta ?x) &&. ?args))
   :replace-fn (fn [{:syms [?x ?f ?args]}]
                 (list* 'vary-meta ?x ?f ?args))})

(def misc-rules
  [not-some-pred
   with-meta-f-meta])

;;vector
(defrule conj-vec
  {:pattern '(conj [] &&. ?x)
   :replace '(vector &&. ?x)})

(defrule into-vec
  {:pattern '(into [] ?coll)
   :replace '(vec ?coll)})

(defrule assoc-assoc-key-coll
  {:pattern '(assoc ?coll ?key0 (assoc (?key0 ?coll) ?key1 ?val))
   :replace '(assoc-in ?coll [?key0 ?key1] ?val)})

(defrule assoc-assoc-coll-key
  {:pattern '(assoc ?coll ?key0 (assoc (?coll ?key0) ?key1 ?val))
   :replace '(assoc-in ?coll [?key0 ?key1] ?val)})

(defrule assoc-assoc-get
  {:pattern '(assoc ?coll ?key0 (assoc (get ?coll ?key0) ?key1 ?val))
   :replace '(assoc-in ?coll [?key0 ?key1] ?val)})

(defrule assoc-fn-key-coll
  {:pattern '(assoc ?coll ?key (?fn (?key ?coll) &&. ?args))
   :replace '(update-in ?coll [?key] ?fn &&. ?args)})

(defrule assoc-fn-coll-key
  {:pattern '(assoc ?coll ?key (?fn (?coll ?key) &&. ?args))
   :replace '(update-in ?coll [?key] ?fn &&. ?args)})

(defrule assoc-fn-get
  {:pattern '(assoc ?coll ?key (?fn (get ?coll ?key) &&. ?args))
   :replace '(update-in ?coll [?key] ?fn &&. ?args)})

(defrule update-in-assoc
  {:pattern '(update-in ?coll ?keys assoc ?val)
   :replace '(assoc-in ?coll ?keys ?val)})

;; empty?
(defrule not-empty?
  {:pattern '(not (empty? ?x))
   :replace '(seq ?x)})

(defrule when-not-empty?
  {:pattern '(when-not (empty? ?x) &&. ?y)
   :replace '(when (seq ?x) &&. ?y)})

;; set
(defrule into-set
  {:pattern '(into #{} ?coll)
   :replace '(set ?coll)})

(defrule take-repeatedly
  {:pattern '(take ?n (repeatedly ?coll))
   :replace '(repeatedly ?n ?coll)})

(defrule dorun-map
  {:pattern '(dorun (map ?fn ?coll))
   :replace '(run! ?fn ?coll)})

(def coll-rules
  [conj-vec
   into-vec
   assoc-assoc-key-coll
   assoc-assoc-coll-key
   assoc-assoc-get
   assoc-fn-key-coll
   assoc-fn-coll-key
   assoc-fn-get
   update-in-assoc
   not-empty?
   when-not-empty?
   into-set
   take-repeatedly
   dorun-map])

(defrule if-else-nil
  {:pattern '(if ?x ?y nil)
   :replace '(when ?x ?y)})

(defrule if-nil-else
  {:pattern '(if ?x nil ?y)
   :replace '(when-not ?x ?y)})

(defrule if-then-do
  {:pattern '(if ?x (do &&. ?y))
   :replace '(when ?x &&. ?y)})

(defrule if-not-x-y-x
  {:pattern '(if (not ?x) ?y ?z)
   :replace '(if-not ?x ?y ?z)})

(defrule if-x-x-y
  {:pattern '(if ?x ?x ?y)
   :replace '(or ?x ?y)})

(defrule when-not-x-y
  {:pattern '(when (not ?x) &&. ?y)
   :replace '(when-not ?x &&. ?y)})

(defrule do-x
  {:pattern '(do ?x)
   :replace '?x})

(defrule if-let-else-nil
  {:pattern '(if-let ?binding ?expr nil)
   :replace '(when-let ?binding ?expr)})

(defrule when-do
  {:pattern '(when ?x (do &&. ?y))
   :replace '(when ?x &&. ?y)})

(defrule when-not-do
  {:pattern '(when-not ?x (do &&. ?y))
   :replace '(when-not ?x &&. ?y)})

(defrule if-not-do
  {:pattern '(if-not ?x (do &&. ?y))
   :replace '(when-not ?x &&. ?y)})

(defrule if-not-not
  {:pattern '(if-not (not ?x) ?y ?z)
   :replace '(if ?x ?y ?z)})

(defrule when-not-not
  {:pattern '(when-not (not ?x) &&. ?y)
   :replace '(when ?x &&. ?y)})

(defrule loop-empty-when
  {:pattern '(loop [] (when ?test &&. ?exprs (recur)))
   :replace '(while ?test &&. ?exprs)})

(defrule let-do
  {:pattern '(let ?binding (do &&. ?exprs))
   :replace '(let ?binding &&. ?exprs)})

(defrule loop-do
  {:pattern '(loop ?binding (do &&. ?exprs))
   :replace '(loop ?binding &&. ?exprs)})

(def control-flow-rules
  [if-else-nil
   if-nil-else
   if-then-do
   if-not-x-y-x
   if-x-x-y
   when-not-x-y
   do-x
   if-let-else-nil
   when-do
   when-not-do
   if-not-do
   if-not-not
   when-not-not
   loop-empty-when
   let-do
   loop-do])

(defrule not-eq
  {:pattern '(not (= &&. ?args))
   :replace '(not= &&. ?args)})

(defrule eq-0-x
  {:pattern '(= 0 ?x)
   :replace '(zero? ?x)})

(defrule eq-x-0
  {:pattern '(= ?x 0)
   :replace '(zero? ?x)})

(defrule eqeq-0-x
  {:pattern '(== 0 ?x)
   :replace '(zero? ?x)})

(defrule eqeq-x-0
  {:pattern '(== ?x 0)
   :replace '(zero? ?x)})

(defrule lt-0-x
  {:pattern '(< 0 ?x)
   :replace '(pos? ?x)})

(defrule gt-x-0
  {:pattern '(> ?x 0)
   :replace '(pos? ?x)})

(defrule lt-x-0
  {:pattern '(< ?x 0)
   :replace '(neg? ?x)})

(defrule gt-0-x
  {:pattern '(> 0 ?x)
   :replace '(neg? ?x)})

(defrule eq-true
  {:pattern '(= true ?x)
   :replace '(true? ?x)})

(defrule eq-false
  {:pattern '(= false ?x)
   :replace '(false? ?x)})

(defrule eq-x-nil
  {:pattern '(= ?x nil)
   :replace '(nil? ?x)})

(defrule eq-nil-x
  {:pattern '(= nil ?x)
   :replace '(nil? ?x)})

(defrule not-nil?
  {:pattern '(not (nil? ?x))
   :replace '(some? ?x)})

(def equality-rules
  [not-eq
   eq-0-x
   eq-x-0
   eqeq-0-x
   eqeq-x-0
   lt-0-x
   lt-x-0
   gt-0-x
   gt-x-0
   eq-true
   eq-false
   eq-x-nil
   eq-nil-x
   not-nil?])

(def all-rules
  (vec (concat string-rules
               sequence-rules
               first-next-rules
               fn-rules
               threading-rules
               misc-rules
               math-rules
               coll-rules
               control-flow-rules
               equality-rules)))

(defn check-rule [rule form]
  (let [pattern (:pattern rule)
        replace (or (:replace rule) (:replace-fn rule))]
    (when-let [result (pattern form)]
      (if replace
        (replace result)
        result))))

(defn check-multiple-rules [rules form]
  (reduce
    (fn [_ rule] (when-let [alt (check-rule rule form)] (reduced alt)))
    nil
    rules))

(defn check-all-rules [form]
  (check-multiple-rules all-rules form))

(defn print-find [filename form alt]
  (let [line (:row (meta form))]
    (printf "At %s:%s%nConsider using:%n" filename line)
    (pprint/pprint alt)
    (println "instead of:")
    (pprint/pprint (hapi/sexpr form))
    (newline)
    (flush)))

(defn check-subforms [[filename form]]
  (let [alt (check-all-rules form)]
    (when alt
      (print-find filename form alt))
    (doall (map #(check-subforms [filename %]) (:children form)))
    nil))

(comment
  (check-all-rules
    (p/parse-string "(loop [] (do 1))"))

  (require '[criterium.core :as cc])
  (let [form (p/parse-string "(next (first (range 10)))")]
    (cc/quick-bench (check-all-rules form)))
  ,)

(defn -main [& args]
  (let [[dir] args]
    (->> (io/file dir)
         (file-seq)
         (filter #(and (.isFile ^File %)
                       (str/ends-with? % ".clj")))
         (map #(do [(str %) (p/parse-file-all %)]))
         (map check-subforms)
         doall)))
