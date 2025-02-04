; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.splint.rules.style.minus-one
  (:require
    [noahtheduke.splint.rules :refer [defrule]]))

(set! *warn-on-reflection* true)

(defrule style/minus-one
  "Checks for simple -1 that should use `clojure.core/dec`.

  Examples:

  ; bad
  (- x 1)

  ; good
  (dec x)
  "
  {:pattern '(- ?x 1)
   :message "Use `dec` instead of recreating it."
   :replace '(dec ?x)})
