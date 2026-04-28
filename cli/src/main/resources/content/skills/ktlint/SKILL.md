---
name: ktlint
description: Reference guide for ktlint covering code style presets (ktlint_official/intellij_idea/android_studio), the .editorconfig property surface, standard rule catalog, individual rule disable syntax, @Suppress suppression, the jlleitschuh Gradle plugin tasks, baseline files, and this project's 4-space/120-char/trailing-comma config. Activates when writing Kotlin code or fixing formatting violations.
---

# ktlint

> For related topics see:
> - `/detekt` — static analysis; wildcard-imports handled there, not ktlint
> - `/kotlin-conventions` — project conventions enforced on top of ktlint
> - `/kotlin-lang` — language features the formatter handles
> - `/gradle-plugins-basics` — how Gradle plugins are applied
> - `/gradle-build-conventions` — convention plugins that apply ktlint

## When to reach for this skill

- Configuring or understanding the `.editorconfig` that drives ktlint.
- Deciding which rules to enable, disable, or tune.
- Suppressing a legitimate violation with `@Suppress`.
- Fixing a ktlint build failure.
- Adding ktlint to a new module.
- Choosing between ktlint-level and detekt-level rules when they overlap.

## What ktlint is

ktlint is a Kotlin linter + formatter. It enforces a consistent code
style defined by `.editorconfig` properties. Every rule is **both** a
checker and a formatter: `ktlintCheck` reports violations, `ktlintFormat`
auto-fixes the ones that can be fixed mechanically.

Key properties:

- **`.editorconfig` driven** — no separate config file. Honors IntelliJ
  IDEA-specific `ij_kotlin_*` properties too.
- **Three code styles** — `ktlint_official` (strictest), `intellij_idea`,
  `android_studio`.
- **Rule sets** — `standard` (built-in) and user-authored custom sets.
- **Suppression** — `@Suppress("ktlint:standard:<rule-id>")` on a
  declaration; no more `// ktlint-disable` comments (removed in 1.0).

## Installation

### Gradle (jlleitschuh plugin, used here)

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

ktlint {
    version.set("1.5.0")     // ktlint runtime version
}
```

The plugin auto-detects the Kotlin plugin and wires tasks into the
`check` lifecycle.

### CLI (occasional use)

```bash
# Install via Homebrew / curl / asdf
brew install ktlint
ktlint "src/**/*.kt"
ktlint -F "src/**/*.kt"      # auto-format
```

### Tasks (jlleitschuh)

| Task | Purpose |
|---|---|
| `ktlintCheck` | Lint all Kotlin + `.kts` files; fails on violations |
| `ktlintFormat` | Auto-fix what can be fixed |
| `ktlint<SourceSet>SourceSetCheck` | Per-source-set lint |
| `ktlint<SourceSet>SourceSetFormat` | Per-source-set format |
| `ktlintKotlinScriptCheck` | Lint `*.gradle.kts` |
| `ktlintKotlinScriptFormat` | Format `*.gradle.kts` |
| `ktlintBaselineGenerate` | Write a baseline of current violations |

### Other integrations

- **Spotless** (`com.diffplug.spotless`) — ktlint as a backend; favor it
  in multi-language builds.
- **kotlinter-gradle** (`org.jmailen.kotlinter`) — alternative wrapper
  with incremental builds.
- **Maven** — via `exec-maven-plugin`.
- **Pre-commit** — `pre-commit-hooks` catalog.
- **IntelliJ / Android Studio** — install the Ktlint plugin; configure
  "Format on save".

## Code styles

```editorconfig
[*.{kt,kts}]
ktlint_code_style = ktlint_official   # default, strictest
# or
ktlint_code_style = intellij_idea
ktlint_code_style = android_studio
```

- `ktlint_official` — ktlint-opinionated, deviates from IntelliJ in a few
  places (trailing commas everywhere, specific wrapping rules).
- `intellij_idea` — mirrors the default IntelliJ Kotlin style.
- `android_studio` — Android Kotlin Style Guide.

Pick `ktlint_official` unless you have a strong reason not to.

## Rule sets and enabling individual rules

ktlint groups rules into sets. The built-in set is `standard`.

**Enable / disable a whole set:**

```editorconfig
ktlint_standard = enabled        # default
ktlint_experimental = enabled    # preview rules (opt-in)
```

**Enable / disable a specific rule (takes precedence over the set):**

```editorconfig
ktlint_standard_no-wildcard-imports = disabled
ktlint_standard_final-newline = enabled
ktlint_standard_some-experimental-rule = enabled
```

Format is `ktlint_<ruleset>_<rule-id>`. Look up rule IDs in the
[standard rules docs][rules].

## Standard rule catalog (selection)

Rules in `ktlint_official`. Most auto-fix; a few (`max-line-length`,
`function-signature` in certain cases) require manual edits.

### Imports

- `no-wildcard-imports` — forbid `import foo.*`.
- `import-ordering` — alphabetical; layout controlled by
  `ij_kotlin_imports_layout`.
- `no-unused-imports` — remove unreferenced imports.

### Naming

- `function-naming` — camelCase; Composable / `@Test` functions can
  bypass via `ktlint_function_naming_ignore_when_annotated_with`.
- `class-naming` — PascalCase.
- `property-naming` — lowercase for `var`, camelCase or
  `UPPER_SNAKE_CASE` for `val`.
- `package-name` — `^[a-z][a-zA-Z\d]*(\.[a-z][a-zA-Z\d]*)*$`.
- `enum-entry-name-case` — `UPPER_UNDERSCORE` or PascalCase.
- `filename` — file holding a single public top-level class must match
  the class name.

### Spacing

- `colon-spacing`, `comma-spacing`, `curly-spacing`,
  `parenthesis-spacing`, `keyword-spacing`, `operator-spacing`,
  `unary-op-spacing`, `angle-bracket-spacing`.
- `no-multi-spaces` — collapse consecutive internal spaces.
- `no-trailing-spaces`.

### Layout

- `final-newline` — newline at EOF.
- `indent` — `indent_size` / `indent_style` from `.editorconfig`.
- `max-line-length` — enforced; exceptions for package/import lines,
  string literals, backticked names.
- `no-consecutive-blank-lines`.
- `no-blank-line-before-rbrace`.
- `no-empty-class-body` — `class Foo` over `class Foo { }`.
- `blank-line-before-declaration` — blank line before top-level and
  class-level declarations.

### Wrapping

- `function-signature` — one-line if it fits, else multiline with
  trailing comma.
- `class-signature` — same idea for class declarations.
- `parameter-list-wrapping`, `argument-list-wrapping`.
- `chain-method-chain` / `chain-method-continuation` — `.` at the start
  of continuation lines.
- `binary-expression-wrapping` — wrap before the operator.
- `function-literal` — lambda params + arrow on the same line.

### Syntax cleanup

- `no-semi` — no trailing `;`.
- `no-unit-return` — omit `: Unit`.
- `string-template` — no braces on simple `${x}` references.
- `annotation` — multiple annotations on separate lines from the
  declaration.
- `modifier-order` — canonical modifier sequence (`public open suspend
  inline`).
- `multiline-if-else` — require braces if any branch has them.
- `mixed-condition-operators` — parenthesize mixed `&&` and `||`.
- `discouraged-comment-location` — don't stick comments in weird spots.

### Trailing commas

- `trailing-comma-on-declaration-site` — declarations (`fun foo(a,)`).
- `trailing-comma-on-call-site` — call sites (`foo(a, b,)`).

Both are wired to the IntelliJ flags below.

### KDoc / comments

- `kdoc` — KDoc only on elements that actually produce documentation.
- `block-comment-initial-star-alignment`.
- `no-consecutive-comments` — blank line between KDoc / block / EOL
  comments.
- `no-single-line-block-comment` — prefer EOL comments.

## .editorconfig property reference

### Universal

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_size = 4
indent_style = space
insert_final_newline = true
max_line_length = 120
trim_trailing_whitespace = true
```

`max_line_length = off` disables the rule globally.

### Kotlin-specific

```editorconfig
[*.{kt,kts}]
ktlint_code_style = ktlint_official

# Per-rule toggles
ktlint_standard_no-wildcard-imports = disabled
ktlint_standard_function-signature = disabled
ktlint_standard_argument-list-wrapping = disabled

# IntelliJ-compatible properties ktlint honors
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
ij_kotlin_imports_layout = *,java.**,javax.**,kotlin.**,^

# Composable / DSL exceptions
ktlint_function_naming_ignore_when_annotated_with = Composable,ComposeView

# Tuning knobs
ktlint_chain_method_rule_force_multiline_when_chain_operator_count_greater_or_equal_than = 4
ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than = 3

# Enable experimental rules
ktlint_experimental = enabled
```

### Per-directory overrides

```editorconfig
[api/*.{kt,kts}]
ktlint_standard_indent = disabled

[**/generated/**.{kt,kts}]
ktlint_standard = disabled
```

**Gotcha:** IntelliJ sometimes reformats `.editorconfig` and inserts
spaces into glob patterns, silently breaking them. Check the raw file
after IDE edits.

## Suppression

ktlint 1.0+ uses `@Suppress` **only**. `// ktlint-disable` comments are
removed.

### Declaration / file scope

```kotlin
@Suppress("ktlint:standard:max-line-length")
fun longName() = "and the usual disclaimers ............................................."

@file:Suppress("ktlint:standard:filename")
package foo.bar
```

### Whole-rule-set suppression

```kotlin
@Suppress("ktlint")                    // all rules
@Suppress("ktlint:standard")           // standard rule set
@Suppress("ktlint:custom-ruleset")
```

Prefer suppressing the narrowest scope (one declaration, one rule).

### Baselines

Capture the current set of violations so you can gradually clean them
up without blocking the build:

```bash
./gradlew ktlintBaselineGenerate
```

Produces `build/ktlint/baseline.xml`. Subsequent `ktlintCheck` runs
ignore listed violations. Delete entries as you fix them.

## jlleitschuh Gradle DSL

```kotlin
ktlint {
    version.set("1.5.0")
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(false)
    disabledRules.set(emptySet())     // prefer .editorconfig
    baseline.set(file("config/baseline.xml"))

    filter {
        exclude("**/generated/**")
        exclude { it.file.path.contains("build/") }
    }

    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.SARIF)
    }
}
```

Prefer driving rule toggles via `.editorconfig` — keeps IDE, CLI, and
Gradle in sync. `disabledRules` bypasses `.editorconfig` and can hide
violations from the CLI.

## ktlint vs detekt

Both tools overlap on some rules. Split the labor:

- **ktlint** — formatting (indent, spacing, imports ordering, trailing
  commas, wrapping). Things a formatter can fix.
- **detekt** — static analysis (complexity, naming suffixes, forbidden
  patterns, potential bugs, wildcard imports in this project).

To avoid double-reporting:

```editorconfig
# This project: wildcard imports belong to detekt
ktlint_standard_no-wildcard-imports = disabled
```

Run both in CI; they complement each other.

## This project's configuration

Convention plugin `clkx-ktlint.gradle.kts`:

```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    version.set("1.5.0")
}
```

`.editorconfig` at the repo root:

```editorconfig
root = true

[*]
charset = utf-8
end_of_line = lf
indent_size = 4
indent_style = space
insert_final_newline = true
max_line_length = 120
trim_trailing_whitespace = true

[*.{kt,kts}]
ktlint_code_style = ktlint_official
ktlint_function_naming_ignore_when_annotated_with = Composable

# Wildcard imports handled by detekt, disabled here to avoid double-reporting
ktlint_standard_no-wildcard-imports = disabled
ktlint_standard_package-name = enabled

# Allow SAM constructor style
ktlint_standard_function-signature = disabled
ktlint_standard_argument-list-wrapping = disabled

# Always trailing commas (cleaner diffs)
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true

[*.md]
trim_trailing_whitespace = false

[*.{yml,yaml}]
indent_size = 2

[Makefile]
indent_style = tab
```

### Key rules in force

- `ktlint_official` style.
- 4-space indent; tabs only in `Makefile`.
- Max line length 120.
- Trailing commas required on multi-line parameter + argument lists.
- LF line endings, final newline.
- `@Composable` functions may use PascalCase.
- Wildcard imports handled by detekt, off here.
- `function-signature` and `argument-list-wrapping` off — permit the
  SAM-constructor / DSL style this project uses heavily.

## Running

```bash
./gradlew ktlintCheck         # lint (fails on violations)
./gradlew ktlintFormat        # auto-fix

# Scoped to a single source set / file pattern
./gradlew ktlintMainSourceSetCheck
./gradlew ktlintKotlinScriptCheck
```

CI runs `ktlintCheck` via the `check` lifecycle.

## Illustrative formatting rules

### Chain continuation

```kotlin
// Correct — ktlint_official
val result = listOf(1, 2, 3)
    .filter { it > 1 }
    .map { it * 2 }

// Wrong — dot on previous line
val result = listOf(1, 2, 3).
    filter { it > 1 }.
    map { it * 2 }
```

### Trailing commas

```kotlin
// Correct
data class Foo(
    val a: String,
    val b: Int,
)

fun bar(
    x: Int,
    y: Int,
) { }
```

### No blank lines in single-line blocks

```kotlin
// Correct
val x = run { compute() }

// Wrong
val x = run {

    compute()

}
```

### Import order

Sorted lexicographically; `java`, `javax`, `kotlin` grouped per
`ij_kotlin_imports_layout`. Don't edit imports by hand — run
`ktlintFormat`.

## Anti-patterns

```kotlin
// WRONG: @file:Suppress to bypass a fixable rule
@file:Suppress("ktlint:standard:trailing-comma-on-declaration-site")

// WRONG: fighting ktlintFormat's output
// If ktlintFormat changes your code, accept it. Don't revert and
// add a suppression.

// WRONG: local .editorconfig to loosen rules
// workspace/module/.editorconfig overrides the root silently.
// Keep all ktlint config in the root .editorconfig.

// WRONG: tab indentation in .kt files
fun process() {
	val x = 1       // tab
}

// WRONG: manually sorted imports
import z.last.A
import a.first.B
// Run ktlintFormat; don't guess the order.

// WRONG: disabling rules through the Gradle DSL
ktlint {
    disabledRules.set(setOf("trailing-comma-on-call-site"))
}
// Correct: .editorconfig so IDE and CLI see the same rules.
```

## Common pitfalls

- **`.editorconfig` in a subdirectory** overrides the root silently.
  Grep for stray `.editorconfig` files when behavior is unexpected.
- **IntelliJ reformatting `.editorconfig`** — spaces inside glob
  patterns break matching. Review after the IDE touches it.
- **ktlint 1.x vs 0.x rule IDs** — several rules were renamed. The `1.x`
  prefix is `ktlint_standard_<id>`; older configs using `ktlint_<id>`
  may be ignored.
- **`ktlintFormat` can't fix everything** — `max-line-length` violations
  and some wrapping cases require manual edits. Fix them or rethink the
  line.
- **Running via IntelliJ only** — the IDE plugin may lag behind the
  configured runtime version. Truth lives in `./gradlew ktlintCheck`.
- **Disable a rule via Gradle, forget `.editorconfig`** — the IDE still
  warns. Prefer `.editorconfig`.

## References

- Home — https://pinterest.github.io/ktlint/
- Rules — https://pinterest.github.io/ktlint/latest/rules/standard/
- Configuration — https://pinterest.github.io/ktlint/latest/rules/configuration-ktlint/
- Integrations — https://pinterest.github.io/ktlint/latest/install/integrations/
- Gradle plugin — https://github.com/JLLeitschuh/ktlint-gradle
- Ktlint repo — https://github.com/pinterest/ktlint

[rules]: https://pinterest.github.io/ktlint/latest/rules/standard/
