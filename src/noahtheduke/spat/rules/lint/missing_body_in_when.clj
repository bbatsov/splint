; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns noahtheduke.spat.rules.lint.missing-body-in-when
  (:require
    [noahtheduke.spat.rules :refer [defrule ->violation]]))

(defrule missing-body-in-when
  "`when` calls should have at least 1 expression after the condition.

  Examples:

  ; bad
  (when true)
  (when (some-func))

  ; good
  (when true (do-stuff))
  (when (some-func) (do-stuff))
  "
  {:pattern '(when _)
   :message "Missing body in when"
   :on-match (fn [rule form _] (->violation rule form))})
