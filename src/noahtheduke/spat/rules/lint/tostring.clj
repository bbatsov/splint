(ns noahtheduke.spat.rules.lint.tostring
  (:require
    [noahtheduke.spat.rules :refer [defrule]]))

(defrule tostring
  "Convert (.toString) to (str)

  Examples:

  # bad
  (.toString x)

  # good
  (str x)
  "
  {:pattern '(.toString ?x)
   :message "Use `str` instead of interop."
   :replace '(str ?x)})
