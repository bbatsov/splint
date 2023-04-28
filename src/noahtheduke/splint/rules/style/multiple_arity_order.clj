; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.splint.rules.style.multiple-arity-order
  (:require
    [noahtheduke.splint.diagnostic :refer [->diagnostic]]
    [noahtheduke.splint.rules :refer [defrule]]
    [noahtheduke.splint.rules.helpers :refer [parse-defn]]))

(set! *warn-on-reflection* true)

(defrule style/multiple-arity-order
  "Sort the arities of a function from fewest to most arguments.

  Examples:

  # bad
  (defn foo
    ([x] (foo x 1))
    ([x y & more] (reduce foo (+ x y) more))
    ([x y] (+ x y)))

  # good
  (defn foo
    ([x] (foo x 1))
    ([x y] (+ x y))
    ([x y & more] (reduce foo (+ x y) more)))
  "
  {:pattern '(%defn??%-?defn ?name &&. ?args)
   :message "defn arities should be sorted fewest to most arguments."
   :on-match (fn [ctx rule form {:syms [?defn ?name ?args]}]
               (let [defn-form (parse-defn ?name ?args)]
                 (when (not= (:arglists defn-form)
                             (sort-by count (:arglists defn-form)))
                   (let [new-arities (sort-by (comp count first) (:arities defn-form))
                         new-form (list* ?defn ?name new-arities)]
                     (->diagnostic rule form {:replace-form new-form})))))})