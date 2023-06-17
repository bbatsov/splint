# Splint

[![Clojars Project](https://img.shields.io/clojars/v/io.github.noahtheduke/splint.svg)](https://clojars.org/io.github.noahtheduke/splint)
[![cljdoc badge](https://cljdoc.org/badge/io.github.noahtheduke/splint)](https://cljdoc.org/d/io.github.noahtheduke/splint)

**Splint** is a Clojure linter focused on style and code shape. It aims to warn about many of the guidelines in the [Clojure Style Guide][style guide]. It is inspired by the Ruby linter [Rubocop][rubocop] and the Clojure linter [Kibit][kibit].

[style guide]: https://guide.clojure.style

## Why?

Why another Clojure linter? We have [clj-kondo][clj-kondo], [eastwood][eastwood], and [kibit][kibit], in addition to [clojure-lsp][clojure-lsp]'s capabilities built on top of clj-kondo. I have contributed to most of these, and recently took over maintenance of kibit. However, most of them aren't built to be easily modifiable, and while kibit's rules are simple, the underlying engine (built on [core.logic][core.logic]) is quite slow. This means that adding or updating the various linting rules can be quite frustrating and taxing.

Inspired by [RuboCop][rubocop], I decided to try something new: A "fast enough" linting engine based on linting code shape, built to be easily extended.

[clj-kondo]: https://github.com/clj-kondo/clj-kondo
[eastwood]: https://github.com/jonase/eastwood
[kibit]: https://github.com/clj-commons/kibit
[clojure-lsp]: https://clojure-lsp.io/
[core.logic]: https://github.com/clojure/core.logic
[rubocop]: https://rubocop.org/

## Non-goals

For speed and simplicity, Splint doesn't run any code, it only works on the provided code as text. As such, it doesn't understand macros or perform any macro-expansion (unlike Eastwood) so it can only lint a given macro call, not the resulting code.

clj-kondo performs lexical analysis and can output usage and binding information, such as unused or incorrectly defined vars. At this time, Splint makes no such efforts. It is only focused on code shape, not code intent or meaning.

Versions are not semantic, they are incremental. Splint is not meant to be infrastructure, so don't rely on it like infrastructure; it is a helpful development tool. It should not be relied on as a library. I make no guarantees about public API, visibility of functions, consistency of data shape, or otherwise. I hope to maintain consistent `json` and `edn` output so users can expect that to stay the same, but everything inside of Splint should be considered implementation detail.

## Speed comparison

```text
$ tokei
===============================================================================
 Language            Files        Lines         Code     Comments       Blanks
===============================================================================
 Clojure               169       113620       102756         4266         6598
 ClojureC                4         1012          850           36          126
 ClojureScript          48        12666        11649          142          875

$ time lein kibit
...
real    34m30.395s
user    35m4.952s
sys     0m2.995s

$ time splint .
...
Linting took 4606ms, 749 style warnings

real    0m6.891s
user    0m37.746s
sys     0m0.419s
```

## License

Copyright © Noah Bogart

Distributed under the Mozilla Public License version 2.0.
