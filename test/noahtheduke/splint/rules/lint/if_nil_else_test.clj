; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns noahtheduke.splint.rules.lint.if-nil-else-test
  (:require
    [expectations.clojure.test :refer [defexpect]]
    [noahtheduke.splint-test :refer [check-alt]]))

(defexpect if-nil-else-test
  '(when-not x y)
  (check-alt "(if x nil y)"))
