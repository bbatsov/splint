; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns noahtheduke.spat.rules.style.prefer-condp
  (:require
    [noahtheduke.spat.rules :refer [defrule ->violation]]))

(defn find-violation [?pairs]
  (when (and (even? (count ?pairs))
             (< 2 (count ?pairs))
             (list? (first ?pairs))
             (= 3 (count (first ?pairs))))
    (let [[test-expr] ?pairs
          [pred-f _ expr] test-expr
          all-pairs (partition 2 ?pairs)
          last-pred-f (first (last all-pairs))
          default? (or (keyword? last-pred-f)
                       (true? last-pred-f))
          ;; trim final pred if it's a keyword or `true`
          all-pairs (if default?
                      (butlast all-pairs)
                      all-pairs)
          test-exprs
          (reduce
            (fn [acc [cur-pred cur-branch]]
              (if (and (list? cur-pred)
                       (= pred-f (first cur-pred))
                       (= expr (last cur-pred)))
                (conj acc (second cur-pred) cur-branch)
                (reduced nil)))
            []
            all-pairs)]
      (when test-exprs ; short circuit
        (let [test-exprs (if default?
                           (conj test-exprs (last ?pairs))
                           test-exprs)]
          (list* 'condp pred-f expr test-exprs))))))

(defrule prefer-condp
  "`cond` checking against the same value in every branch is a code smell.

  This rule uses the first test-expr as the template to compare against each
  other test-expr. It has a number of conditions it checks as it runs:

  * The `cond` is well-formed (aka even number of args).
  * The `cond` has more than 1 pair.
  * The first test-expr is a list with 3 forms.
  * The function of every test-expr must match the test-expr of the first
    test-expr.
    * The last test-expr isn't checked if it is `true` or a keyword.
  * The last argument of every test-expr must match the last argument of the
    first test-expr.

  Provided all of that is true, then the middle arguments of the test-exprs are
  gathered and rendered into a `condp`.

  Examples:

  # bad
  (cond
    (= 1 x) :one
    (= 2 x) :two
    (= 3 x) :three
    (= 4 x) :four)

  # good
  (condp = x
    1 :one
    2 :two
    3 :three
    4 :four)

  # bad
  (cond
    (= 1 x) :one
    (= 2 x) :two
    (= 3 x) :three
    :else :big)

  # good
  (condp = x
    1 :one
    2 :two
    3 :three
    :big)
  "
  {:pattern '(cond &&. ?pairs)
   :on-match (fn [rule form {:syms [?pairs]}]
               (when-let [new-form (find-violation ?pairs)]
                 (let [message "Prefer condp when predicate and arguments are the same"]
                   (->violation rule form {:replace-form new-form
                                           :message message}))))})