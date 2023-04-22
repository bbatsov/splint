; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns noahtheduke.spat.parser
  (:require
    [edamame.core :as e]
    [edamame.impl.read-fn :as read-fn]
    [noahtheduke.spat.ns-parser :refer [derive-aliases unquote-if-quoted]]))

(set! *warn-on-reflection* true)

(defn- parse-ns [obj]
  (when (list? obj)
    (case (first obj)
      ns {:current (second obj)
          :aliases (derive-aliases obj)}
      in-ns {:current (unquote-if-quoted (second obj))}
      (alias require use import) {:aliases (derive-aliases obj)}
      ; else
      nil)))

(defn make-edamame-opts [ns-state]
  {:all true
   :row-key :line
   :col-key :column
   :end-location true
   :features #{:clj :cljs}
   :read-cond :allow
   :readers (fn reader [r] (fn reader-value [v] (list 'splint/tagged-literal (list r v))))
   :auto-resolve (fn auto-resolve [ns-str]
                   (if-let [resolved-ns (@ns-state ns-str)]
                     resolved-ns
                     (if (= :current ns-str)
                       "splint-auto-current_"
                       (str "splint-auto-alias_" (name ns-str)))))
   :postprocess (fn postprocess [{:keys [obj loc]}]
                  (when-let [{:keys [current aliases]} (parse-ns obj)]
                    (when current
                      (reset! ns-state {:current current}))
                    (when aliases
                      (swap! ns-state (fnil merge {}) aliases)))
                  ;; Gotta apply location data here as using `:postprocess` skips automatic
                  ;; location data
                  (if (e/iobj? obj)
                    (vary-meta obj merge loc)
                    obj))
   :uneval (fn [{:keys [uneval next]}]
             (cond
               (identical? uneval :splint/disable)
               (vary-meta next assoc :splint/disable true)
               (and (seqable? (:splint/disable uneval))
                    (seq (:splint/disable uneval)))
               (vary-meta next assoc :splint/disable (seq (:splint/disable uneval)))
               :else
               next))
   ; All reader macros should be a splint-specific symbol wrapping the expression
   :dispatch {; @x
              \@ (fn [expr] (list 'splint/deref expr))
              ; `(+ 1 2)
              ; This is a deliberate decision to not expand syntax quotes. Easier to parse/manipulate the entire tree.
              \` (fn [expr] (list 'splint/syntax-quote expr))
              \~ {; ~x unquote
                  :default (fn [expr] (list 'splint/unquote expr))
                  ; ~@(map inc [1 2 3])
                  \@ (fn [expr] (list 'splint/unquote-splicing expr))}
              \# {; #'x
                  \' (fn [expr] (list 'splint/var expr))
                  ; #=(+ 1 2)
                  \= (fn [expr] (list 'splint/read-eval expr))
                  ; #()
                  \( (fn [expr]
                       (let [sexp (read-fn/read-fn expr)]
                         (apply list (cons 'splint/fn (next sexp)))))
                  ; #".*"
                  \" (fn [expr] (list 'splint/re-pattern expr))}}})

(defn parse-string
  ([s] (parse-string s (atom {})))
  ([s ns-state] (e/parse-string s (make-edamame-opts ns-state))))

(defn parse-string-all
  ([s] (parse-string-all s (atom {})))
  ([s ns-state] (e/parse-string-all s (make-edamame-opts ns-state))))
