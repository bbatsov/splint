(ns noahtheduke.spat.rules.lint.assoc-in-one-arg 
  (:require
    [noahtheduke.spat.rules :refer [defrule]]))

(defrule assoc-in-one-arg
  "`assoc-in` loops over the args, calling `assoc` for each key. If given a single key,
  just call `assoc` directly instead for performance and readability improvements.

  Examples:

  # bad
  (assoc-in coll [:k] 10)

  # good
  (assoc coll :k 10)
  "
  {:pattern '(assoc-in ?coll [?key] ?val)
   :message "Use `assoc` instead of recreating it."
   :replace '(assoc ?coll ?key ?val)})
