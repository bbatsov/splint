(ns noahtheduke.spat.rules.lint.first-next
  (:require
    [noahtheduke.spat.rules :refer [defrule]]))

(defrule first-next
  "fnext is succinct and meaningful.

  Examples:

  # bad
  (first (next coll))

  # good
  (fnext coll)
  "
  {:pattern '(first (next ?coll))
   :message "Use `fnext` instead of recreating it."
   :replace '(fnext ?coll)})
