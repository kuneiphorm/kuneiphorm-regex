[![CI](https://img.shields.io/github/actions/workflow/status/kuneiphorm/kuneiphorm-regex/ci.yml?branch=master&label=CI)](https://github.com/kuneiphorm/kuneiphorm-regex/actions)
[![kuneiphorm-regex](https://img.shields.io/github/v/release/kuneiphorm/kuneiphorm-regex?include_prereleases&label=kuneiphorm-regex)](https://github.com/kuneiphorm/kuneiphorm-regex/releases)
![License](https://img.shields.io/badge/License-Apache_2.0-blue)
![Java](https://img.shields.io/badge/Java-21-blue)

# kuneiphorm-regex

Regex parser and tokenizer specification builder for the kuneiphorm toolkit.

This module parses regular expressions into `Expression<IntRange>` trees (from `kuneiphorm-daedalus`), and provides a builder that compiles named regex rules into a `RegexTokenizerSpec` -- a compiled, fragmented DFA. The resulting spec is a pure data description of a regex-based tokenizer, suitable for both runtime interpretation and code generation.

## Package overview

```
org.kuneiphorm.regex
├── RegexParser                    Recursive-descent regex parser
├── RegexTokenizerSpec             Compiled tokenizer specification (FragmentedAutomaton wrapper)
├── RegexTokenizerSpecBuilder      Builds a spec from named regex rules
└── UnknownPosixClassException     Thrown for unrecognized POSIX class names
```

## Key classes

| Class | Description |
|---|---|
| `RegexParser` | `parse(CharFlow)` is the primary API; `parse(String)` is a convenience wrapper. Produces `Expression<IntRange>` trees. Supports alternation, concatenation, quantifiers (including `{n}`, `{n,}`, `{n,m}`), groups, character classes (with negation), dot, shorthand escapes (`\d`, `\w`, `\s` and inverses), POSIX classes (`[[:alpha:]]`), and escape sequences (`\n`, `\t`, `\r`, `\xHH`, `\uHHHH`). `defineClass(name, ranges)` overrides POSIX classes. |
| `RegexTokenizerSpec<L>` | Record wrapping a `FragmentedAutomaton<L>` and a `Set<L> labels` of all recognized token labels. The compiled output of the builder -- a pure data description consumed by runtime interpreters or code generators. |
| `RegexTokenizerSpecBuilder<L>` | Collects `(label, regex)` pairs via `add`, customizes POSIX classes via `defineClass`, then builds: parse -> Thompson NFA -> `RangeDeterminizer` -> `Trimmer` -> `Minimizer` -> `AlphabetFragmenter`. First rule added wins on priority conflict. |
| `UnknownPosixClassException` | Extends `SyntaxException`. Thrown for unrecognized POSIX class names in `[[:name:]]`. |

## Supported regex syntax

| Syntax | Description |
|---|---|
| `a\|b` | Alternation |
| `ab` | Concatenation (juxtaposition) |
| `?`, `+`, `*` | Quantifiers (optional, one-or-more, zero-or-more) |
| `{n}`, `{n,}`, `{n,m}` | Repetition bounds (exact, at-least, range) |
| `(...)` | Grouping |
| `[a-z]`, `[^...]` | Character classes, with negation |
| `.` | Any character except `\n` |
| `\d`, `\D`, `\w`, `\W`, `\s`, `\S` | Shorthand character classes |
| `[[:alpha:]]`, `[[:digit:]]`, ... | POSIX character classes (customizable via `defineClass`) |
| `\n`, `\t`, `\r`, `\xHH`, `\uHHHH` | Escape sequences |

## Key design decisions

- **`parse(CharFlow)` is primary.** The parser consumes one regex from the flow and stops, allowing callers to parse regexes embedded in larger grammars. `parse(String)` additionally enforces full-input consumption.
- **Output is `Expression<IntRange>`.** The same expression algebra from daedalus, with `IntRange` as the unit label (character ranges).
- **`RegexTokenizerSpecBuilder` is two-phase.** Collection (`add`) is separate from computation (`build`). All rules are collected first, then the full pipeline runs once.
- **First rule wins on priority.** Lower insertion index = higher priority, matching `RangeDeterminizer`'s priority map.
- **POSIX classes are customizable.** `defineClass(name, ranges)` overrides any built-in class.

## Dependencies

- `kuneiphorm-daedalus` -- expression algebra, automata, algorithms
- `kuneiphorm-runtime` -- `CharFlow`, `SyntaxException`

## Requirements

- Java 21+
