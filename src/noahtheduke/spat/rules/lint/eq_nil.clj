(ns noahtheduke.spat.rules.lint.eq-nil
  (:require
    [noahtheduke.spat.rules :refer [defrule]]))

(defrule eq-nil
  "`nil?` exists so use it.

  Examples:

  # bad
  (= nil x)
  (= x nil)

  # good
  (nil? x)
  "
  {:patterns ['(= nil ?x)
              '(= ?x nil)]
   :message "Use `nil?` instead of recreating it."
   :replace '(nil? ?x)})
