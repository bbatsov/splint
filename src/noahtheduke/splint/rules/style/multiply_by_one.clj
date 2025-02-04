; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.splint.rules.style.multiply-by-one
  (:require
    [noahtheduke.splint.rules :refer [defrule]]))

(set! *warn-on-reflection* true)

(defrule style/multiply-by-one
  "Checks for (* x 1).

  Examples:

  ; bad
  (* x 1)
  (* 1 x)

  ; good
  x
  "
  {:patterns ['(* ?x 1)
              '(* 1 ?x)]
   :message "Multiplying by 1 is a no-op."
   :replace '?x})
