; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.splint.rules.lint.let-do
  (:require
    [noahtheduke.splint.rules :refer [defrule]]))

(defrule lint/let-do
  "`let` has an implicit `do`, so use it.

  Examples:

  ; bad
  (let [a 1 b 2] (do (println a) (println b)))

  ; good
  (let [a 1 b 2] (println a) (println b))
  "
  {:pattern '(let ?binding (do &&. ?exprs))
   :message "Unnecessary `do` in `let` body."
   :replace '(let ?binding &&. ?exprs)})
