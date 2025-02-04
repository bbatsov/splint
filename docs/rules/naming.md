# Naming

## naming/conventional-aliases

| Enabled by default | Version Added | Version Updated |
| ------------------ | ------------- | --------------- |
| true               | 1.3.0         | 1.3.0           |

Through community and core practices over the years, various core libraries have gained standard or expected aliases. To better align with the community, it's best to use those aliases in favor of alternatives.

### Examples

```clojure
# bad
(:require [clojure.string :as string])

# good
(:require [clojure.string :as str])
```

### Reference

* https://guide.clojure.style/#use-idiomatic-namespace-aliases

---

## naming/conversion-functions

| Enabled by default | Version Added | Version Updated |
| ------------------ | ------------- | --------------- |
| true               | 0.1.94        | 0.1.94          |

Use `->` instead of `to` in the names of conversion functions.

### Examples

```clojure
# bad
(defn f-to-c ...)

# good
(defn f->c ...)
```

### Reference

* https://guide.clojure.style/#naming-conversion-functions

---

## naming/lisp-case

| Enabled by default | Version Added | Version Updated |
| ------------------ | ------------- | --------------- |
| true               | 1.3.0         | 1.3.0           |

Use lisp-case for function and variable names. (Replacement is generated with `camel-snake-kebab`.)

### Examples

```clojure
# bad
(def someVar ...)
(def some_fun ...)

# good
(def some-var ...)
(defn some-fun ...)
```

### Reference

* https://guide.clojure.style/#naming-functions-and-variables

---

## naming/predicate

| Enabled by default | Version Added | Version Updated |
| ------------------ | ------------- | --------------- |
| true               | 0.1.69        | 0.1.94          |

Functions that return a boolean should end in a question mark.

Doesn't verify the kind of function, just checks for anti-patterns in the
names. Also doesn't actually check the classic Common Lisp convention as we
have no way to know when a function name uses a word that naturally ends in
a 'p' (such as `map`).

### Examples

```clojure
# bad
(defn palindrome-p ...)
(defn is-palindrome ...)

# good
(defn palindrome? ...)
```

### Reference

* https://guide.clojure.style/#naming-predicates

---

## naming/record-name

| Enabled by default | Version Added | Version Updated |
| ------------------ | ------------- | --------------- |
| true               | 0.1.69        | 1.0             |

Records should use PascalCase.

### Examples

```clojure
# bad
(defrecord foo [a b c])

# good
(defrecord Foo [a b c])
```

### Reference

* https://guide.clojure.style/#naming-protocols-records-structs-and-types

---

## naming/single-segment-namespace

| Enabled by default | Version Added | Version Updated |
| ------------------ | ------------- | --------------- |
| true               | 1.3.0         | 1.3.0           |

Namespaces exist to disambiguate names. Using a single segment namespace puts you in direct conflict with everyone else using single segment namespaces, thus making it more likely you will conflict with another code base.

### Examples

```clojure
# bad
(ns simple)

# good
(ns noahtheduke.simple)
```

### Reference

* https://guide.clojure.style/#no-single-segment-namespaces
