; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.splint.rules.style.filter-complement
  (:require
    [noahtheduke.splint.rules :refer [defrule]]))

(set! *warn-on-reflection* true)

(defrule style/filter-complement
  "Check for `(filter (complement pred) coll)`.

  Examples:

  ; bad
  (filter (complement even?) coll)

  ; good
  (remove even? coll)
  "
  {:patterns ['(filter (complement ?pred) ?coll)
              '(filter ((? _ fn??) [?arg] (not (?pred ?arg))) ?coll)
              '(filter (comp not ?pred) ?coll)]
   :message "Use `remove` instead of recreating it."
   :replace '(remove ?pred ?coll)})
