; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns noahtheduke.splint.rules.style.set-literal-as-fn-test
  (:require
    [expectations.clojure.test :refer [defexpect]]
    [noahtheduke.splint.test-helpers :refer [expect-match]]))

(defexpect set-literal-as-fn-test
  (expect-match
    '[{:alt (case elem (a b c) elem nil)}]
    "(#{'a 'b 'c} elem)")
  (expect-match
    '[{:alt (case elem (nil 1 :b c) elem nil)}]
    "(#{nil 1 :b 'c} elem)")
  (expect-match nil "(#{'a 'b c} elem)")
  (expect-match nil "(#{'a 'b 'c '(1 2 3)} elem)"))
