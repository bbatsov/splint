; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.splint.rules.lint.warn-on-reflection
  (:require
    [noahtheduke.splint.diagnostic :refer [->diagnostic]]
    [noahtheduke.splint.rules :refer [defrule]]))

(set! *warn-on-reflection* true)

(defrule lint/warn-on-reflection
  "Because we can't (or won't) check for interop, `*warn-on-reflection*` should
  be at the top of every file out of caution.

  Examples:

  # bad
  (ns foo.bar)
  (defn baz [a b] (+ a b))

  # good
  (ns foo.bar)
  (set! *warn-on-reflection* true)
  (defn baz [a b] (+ a b))
  "
  {:pattern '[(ns ?*ns-args) ?warn ?*rest-of-file]
   :init-type :file
   :message "*warn-on-reflection* should be immediately after ns declaration."
   :ext :clj
   :on-match (fn [ctx rule form {:syms [?warn ?rest-of-file] :as binds}]
               (when-not (= '(set! *warn-on-reflection* true) ?warn)
                 (->diagnostic ctx rule
                               nil
                               {:form-meta (meta ?warn)
                                :filename (:filename (meta form))})))})
