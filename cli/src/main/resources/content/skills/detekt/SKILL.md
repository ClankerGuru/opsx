---
name: detekt
description: >-
  Use when configuring, running, or extending detekt static analysis for
  Kotlin — Gradle plugin setup (`dev.detekt`, `toolVersion`, `source`,
  `config`, `buildUponDefaultConfig`, `allRules`, `parallel`, `basePath`,
  `ignoreFailures`, `failOnSeverity`, Android `ignoredBuildTypes`/
  `ignoredFlavors`/`ignoredVariants`), tasks (`detekt`, `detektMain`/
  `detektTest` with type resolution, `detektGenerateConfig`,
  `detektBaseline`), reports (`html`/`checkstyle`/`sarif`/`markdown`/
  `txt`), the YAML config surface (`build`, `config`, `processors`,
  `console-reports`, `output-reports`, per-rule `active`/`severity`/
  `aliases`/`excludes`/`includes`), suppression (`@Suppress`,
  `@file:Suppress`, baseline XML, config excludes), all built-in rule
  sets (complexity, coroutines, empty-blocks, exceptions, naming,
  performance, potential-bugs, style, comments, formatting), writing
  custom rules (`RuleSetProvider`, `Rule`, `visitKtXxx`, `Finding`,
  `Entity.from`, `by config()`), and packaging via `detektPlugins` or
  `META-INF/services`.
---

## When to use this skill

- Setting up detekt on a Kotlin Gradle project (JVM, Android, or KMP).
- Fixing or suppressing detekt violations.
- Tuning thresholds, adding/removing rules, introducing a baseline.
- Writing a custom rule or custom rule set.
- Diagnosing why a detekt task failed the build or produced no findings.

## When NOT to use this skill

- Formatting-only problems — use `ktlint` directly (or detekt's
  `detekt-formatting` wrapper if you want ktlint rules surfaced as
  detekt findings).
- Architectural assertions (package dependencies, layering) — use
  `konsist`.

## Version split (important)

- **`io.gitlab.arturbosch.detekt`** — the 1.x series. Still widely used.
- **`dev.detekt`** — the 2.x series (rebranded, new plugin id, new Java
  packages under `dev.detekt.*`).

Check the applied plugin first; the Gradle DSL and task class imports
differ between the two. Snippets below favor 2.x (`dev.detekt`); the
1.x form is noted where it matters.

## Gradle plugin setup

### 2.x (recommended for new projects)

```kotlin
plugins {
    id("dev.detekt") version "2.0.0-alpha.2"
}

repositories { mavenCentral() }

detekt {
    toolVersion = "2.0.0-alpha.2"
    source.setFrom("src/main/kotlin", "src/test/kotlin")
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true   // layer your config over the defaults
    allRules = false                // true enables every rule, even defaults-off
    parallel = true
    basePath.set(rootDir)           // makes report paths relative
    ignoreFailures = false
    failOnSeverity = dev.detekt.gradle.extensions.FailOnSeverity.Error
    baseline = file("config/baseline.xml")
}
```

### 1.x (legacy)

```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

detekt {
    toolVersion = "1.23.7"
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}
```

Key extension properties (both versions):

| Property                   | Purpose                                                  |
|----------------------------|----------------------------------------------------------|
| `toolVersion`              | Pin the analyzer version independently of the plugin.    |
| `source`                   | Source roots to scan.                                    |
| `config`                   | One or more YAML config files; later files override.     |
| `buildUponDefaultConfig`   | `true` layers your YAML on top of bundled defaults.      |
| `allRules`                 | Turn on every rule, including `active: false` defaults.  |
| `parallel`                 | Run rules in parallel.                                   |
| `basePath`                 | Relativize paths in reports (SARIF, XML).                |
| `ignoreFailures`           | Don't fail the build on findings. Avoid — defeats the point. |
| `failOnSeverity` *(2.x)*   | Min severity that fails the build. `Error` is strictest. |
| `baseline`                 | Baseline XML path (findings listed there are suppressed).|
| `disableDefaultRuleSets`   | Drop all built-in rules; keep only `detektPlugins`.      |
| `debug`                    | Verbose logging.                                         |
| `ignoredBuildTypes`/`Flavors`/`Variants` | Android: skip these.                       |

## Tasks

| Task                       | Does what                                                |
|----------------------------|----------------------------------------------------------|
| `detekt`                   | Fast scan, no type resolution (symbol-only analysis).    |
| `detektMain` / `detektTest`| Source-set-specific scan **with** type resolution. Required for rules that inspect types. |
| `detekt<Variant>`          | Android per-variant scan with type resolution.           |
| `detektGenerateConfig`     | Write default `detekt.yml` for you to edit.              |
| `detektBaseline`           | Generate `baseline.xml` containing current findings.     |
| `detektBaselineMain` / `detektBaselineTest` | Source-set-specific baseline.           |

Run: `./gradlew detekt` for the fast check, `./gradlew detektMain
detektTest` when type-resolved rules need to fire.

## Reports

```kotlin
tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.html"))
        checkstyle.required.set(true)          // XML; call "xml" in 1.x
        sarif.required.set(true)               // GitHub code-scanning
        markdown.required.set(true)
    }
}
```

- **HTML** — human review.
- **checkstyle / XML** — CI tooling (CodeClimate, Jenkins, SonarQube).
- **SARIF** — GitHub Advanced Security / code scanning upload.
- **Markdown** — PR comments.
- **TXT** — plain text (1.x); dropped in 2.x.

In 1.x the XML key is `xml`; in 2.x it is `checkstyle`. The content is
the same Checkstyle dialect.

## Type resolution

Many rules (e.g. `UnsafeCast`, `IgnoredReturnValue`,
`RedundantSuspendModifier`) only fire when the analyzer has a
typed-AST. The plain `detekt` task runs without it — use
`detektMain`/`detektTest`:

```kotlin
tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = "17"
    jdkHome.set(file(System.getProperty("java.home")))
}
```

Type resolution is slower — typically only wired into CI. Locally,
plain `detekt` is fine for quick feedback.

## The `detekt.yml` surface

```yaml
build:
  maxIssues: 0                 # (1.x) any finding fails the build
  excludeCorrectable: false
  weights:
    complexity: 2              # multiply severity for these rule sets

config:
  validation: true             # validate the YAML structure itself
  warningsAsErrors: true
  checkExhaustiveness: false   # in 2.x, enforce every rule is present
  excludes: ''                 # comma-separated glob patterns to drop from validation

processors:
  active: true
  exclude:
    - 'DetektProgressListener'

console-reports:
  active: true
  exclude:
    - 'ProjectStatisticsReport'
    - 'ComplexityReport'

output-reports:
  active: true
  exclude: []

# Rule sets follow:
complexity:
  CyclomaticComplexMethod:
    active: true
    severity: error
    threshold: 15
    aliases: ['CyclomaticComplex']
    excludes: ['**/generated/**']
    includes: []
```

### Per-rule keys

- `active` — enable/disable.
- `severity` — `info` | `warning` | `error`. Resolution order: rule >
  ruleset > default (`error`). With `failOnSeverity = Error`, only
  errors fail the build.
- `aliases` — alternate ids accepted in `@Suppress`.
- `excludes` / `includes` — glob patterns against file paths.
- Rule-specific knobs (thresholds, allowlists).

## Built-in rule sets

| Rule set        | Catches                                                             |
|-----------------|---------------------------------------------------------------------|
| `complexity`    | Long methods, deep nesting, too many functions/conditions, high cyclomatic/cognitive complexity. |
| `coroutines`    | `GlobalScope` usage, suspend functions returning `Flow`, redundant suspend modifier. |
| `empty-blocks`  | Empty `catch`, `if`, `for`, `init`, `finally`, etc.                 |
| `exceptions`    | `printStackTrace`, throwing without a message, rethrowing caught exceptions, catching `NPE`/`Error`/`Throwable`. |
| `naming`        | Class/function/variable/package/constant naming patterns.           |
| `performance`   | `forEach` on Range, spread operator, unnecessary `let`, array primitives. |
| `potential-bugs`| `UnsafeCast`, `ExplicitGarbageCollectionCall`, `IgnoredReturnValue` (typed), `UnreachableCode`. |
| `style`         | `MagicNumber`, `WildcardImport`, `MaxLineLength`, `ReturnCount`, `ForbiddenComment`, `UnusedImports`. |
| `comments`      | Comment conventions on public API, KDoc requirements, outdated tags.|
| `formatting`    | ktlint rules wrapped as detekt rules (requires `detekt-formatting`).|

### Selected `complexity` rules (most often tuned)

| Rule                      | Knob                                                            |
|---------------------------|-----------------------------------------------------------------|
| `CognitiveComplexMethod`  | `allowedComplexity` (SonarSource cognitive, default 15).        |
| `CyclomaticComplexMethod` | `allowedComplexity` / `threshold` (McCabe, default 14).         |
| `LongMethod`              | `allowedLines` / `threshold` (default 60).                      |
| `LongParameterList`       | `allowedFunctionParameters` (5), `allowedConstructorParameters` (6). |
| `LargeClass`              | `allowedLines` (600).                                           |
| `NestedBlockDepth`        | `allowedDepth` (4).                                             |
| `TooManyFunctions`        | `thresholdInFiles`/`InClasses`/`InInterfaces`/`InObjects`/`InEnums` (11). |
| `ComplexCondition`        | `allowedConditions` (3).                                        |
| `StringLiteralDuplication`| `allowedDuplications` (2).                                      |

Note: 1.x used `threshold:`; 2.x renamed most knobs to `allowed*`.
Both names are often accepted during the transition.

## Enabling the formatting pack

```kotlin
dependencies {
    detektPlugins("dev.detekt:detekt-formatting:2.0.0-alpha.2")
    // 1.x: detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}
```

Then in `detekt.yml`:

```yaml
formatting:
  active: true
  android: false
  autoCorrect: true            # detekt --auto-correct rewrites files
  MaximumLineLength:
    active: true
    maxLineLength: 120
```

ktlint rules only report at file scope — suppress them with
`@file:Suppress("ktlint:...")`, not a per-function `@Suppress`.

## Suppression

### In source

```kotlin
@Suppress("LongMethod")                    // rule id
@Suppress("style:MagicNumber")              // ruleset:rule
@Suppress("detekt:LongParameterList")       // detekt:rule
@Suppress("all")                            // nuke every detekt finding

// File-scope (required for some rules like TooManyFunctions, ktlint wrappers)
@file:Suppress("TooManyFunctions")
```

Kotlin's `@Suppress` takes precedence over Java's `@SuppressWarnings`
if both are present.

### In config

```yaml
style:
  MagicNumber:
    excludes: ['**/test/**', '**/*Test.kt']
    ignoreNumbers: ['-1', '0', '1', '2']
    ignoreHashCodeFunction: true
    ignorePropertyDeclaration: true
```

Use `excludes` for path-based silencing, `ignore*` options for
rule-specific carve-outs.

### Baseline

Baseline captures *current* findings so only new violations fail
builds — useful when adopting detekt mid-project.

```bash
./gradlew detektBaseline
```

Produces `baseline.xml`:

```xml
<SmellBaseline>
    <ManuallySuppressedIssues/>
    <CurrentIssues>
        <ID>LongMethod:App.kt$fun main()</ID>
        <ID>MagicNumber:Parser.kt$42</ID>
    </CurrentIssues>
</SmellBaseline>
```

Subsequent runs ignore anything listed there. Regenerate periodically
and delete entries as you fix them. Don't hand-edit — `<ID>` format is
position-sensitive.

## Custom rules

A minimal custom rule set:

### 1. Gradle module

```kotlin
plugins { kotlin("jvm") }

dependencies {
    compileOnly("dev.detekt:detekt-api:2.0.0-alpha.2")
    testImplementation("dev.detekt:detekt-test:2.0.0-alpha.2")
}
```

### 2. Provider

```kotlin
package com.example.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetProvider

class CustomRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "custom"
    override fun instance(config: Config): RuleSet =
        RuleSet(
            ruleSetId,
            listOf(
                NoPrintlnRule(config),
            ),
        )
}
```

### 3. Rule

```kotlin
package com.example.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression

class NoPrintlnRule(config: Config) : Rule(
    config,
    description = "Disallow println; use a logger instead.",
) {
    private val allowedFiles: List<String> by config(defaultValue = emptyList())

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee == "println") {
            report(Finding(Entity.from(expression), "println is banned"))
        }
    }
}
```

Rule lifecycle:

- Subclass `Rule`. Override the `visitKtXxx` for the PSI node you care
  about — `visitKtFile`, `visitClass`, `visitNamedFunction`,
  `visitCallExpression`, etc. Always call `super.visitXxx(...)` so
  descent continues.
- Read config with `by config(defaultValue = ...)`; detekt supports
  `String`, `Int`, `Boolean`, `List<String>`, `Regex` out of the box.
- Report via `report(Finding(Entity.from(psi), message))` or a
  subclass (`CorrectableCodeSmell` for `autoCorrect`-capable rules).

In 1.x the API lived under `io.gitlab.arturbosch.detekt.api.*` and the
`Finding` class was `CodeSmell` with a separate `Issue` object:

```kotlin
// 1.x
class NoPrintln(config: Config) : Rule(config) {
    override val issue = Issue("NoPrintln", Severity.Style, "println banned", Debt.FIVE_MINS)
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text == "println") {
            report(CodeSmell(issue, Entity.from(expression), "println is banned"))
        }
    }
}
```

### 4. Register

Either service-loader:

```
src/main/resources/META-INF/services/dev.detekt.api.RuleSetProvider
```

content:
```
com.example.detekt.CustomRuleSetProvider
```

…or let Gradle do it via `detektPlugins` in consuming projects:

```kotlin
dependencies {
    detektPlugins(project(":detekt-custom-rules"))
}
```

### 5. Activate

```yaml
custom:
  NoPrintlnRule:
    active: true
    allowedFiles: []
```

Every custom rule is off by default — you must turn it on.

## Testing custom rules

```kotlin
class NoPrintlnRuleTest : BehaviorSpec({
    val env = KotlinCoreEnvironmentTest()
    given("a file with println") {
        val code = """fun main() { println("hi") }"""
        `when`("rule runs") {
            val findings = NoPrintlnRule(Config.empty).lint(code)
            then("one finding") {
                findings.size shouldBe 1
                findings[0].message shouldBe "println is banned"
            }
        }
    }
})
```

`detekt-test` exposes `Rule.lint(code)` and `Rule.compileAndLint(code)`
for type-resolved rules.

## CI integration

```yaml
# GitHub Actions
- name: detekt
  run: ./gradlew detekt detektMain detektTest

- name: Upload SARIF
  uses: github/codeql-action/upload-sarif@v3
  if: always()
  with:
    sarif_file: build/reports/detekt/detekt.sarif
```

For PR-only failures on changes, set `basePath.set(rootDir)` and point
GitHub code-scanning at the SARIF report. Don't use `ignoreFailures =
true` as a shortcut — prefer a baseline.

## Anti-patterns

```kotlin
// WRONG — blanket @Suppress at class level
@Suppress("all")
class Everything { ... }

// WRONG — silencing via config to "fix" a finding
style:
  MagicNumber:
    active: false              // you now have no magic-number enforcement

// WRONG — ignoreFailures = true in production config
detekt { ignoreFailures = true }   // findings are still collected, but ignored

// WRONG — running only `detekt` and assuming type-resolved rules ran
./gradlew detekt                 // skips IgnoredReturnValue, UnsafeCast, ...

// WRONG — using @Suppress on a ktlint-wrapper finding at function scope
@Suppress("ktlint:standard:no-wildcard-imports")
fun foo() { ... }                // ktlint rules require @file:Suppress

// WRONG — custom rule forgetting super.visitXxx
override fun visitClass(klass: KtClass) {
    // no super.call → children never visited → silent false negatives
}

// WRONG — returning an Issue per rule on 2.x
override val issue = Issue(...)  // 2.x uses `description` in constructor, not Issue
```

## Pitfalls

| Symptom                                         | Cause / fix                                                        |
|-------------------------------------------------|--------------------------------------------------------------------|
| Rule fires on generated code                    | Add glob to rule's `excludes:` (e.g. `**/generated/**`).           |
| Custom rule never runs                          | Forgot to enable in YAML (`active: true`) or missed the `META-INF/services` entry. |
| Type-resolution rules silent                    | Using `detekt` task, not `detektMain`/`detektTest`.                |
| `detekt` task succeeds but CI shows failures    | Different config layered in CI — check `buildUponDefaultConfig`.   |
| Baseline keeps growing                          | Regenerate from scratch after refactors; don't let stale entries rot. |
| `warningsAsErrors: true` fails on new rule default | Pin `toolVersion` and upgrade in discrete steps.                |
| Upgrading 1.x → 2.x breaks config               | Package rename (`io.gitlab.arturbosch.detekt.*` → `dev.detekt.*`), report key rename (`xml` → `checkstyle`), knob rename (`threshold` → `allowedComplexity`). Review the 2.0 migration guide. |
| `detektGenerateConfig` overwrote my config      | The task writes to the `config.setFrom(...)` path. Delete the task's output location or point it elsewhere. |
| `@Suppress("TooManyFunctions")` ignored         | Must be `@file:Suppress`, not on the class.                        |
| Report paths absolute and ugly                  | Set `basePath.set(rootDir)` so SARIF/XML paths are repo-relative.  |

## Reference points

- https://detekt.dev/ — top-level docs
- https://detekt.dev/docs/gettingstarted/gradle
- https://detekt.dev/docs/introduction/configurations
- https://detekt.dev/docs/introduction/suppressing-rules
- https://detekt.dev/docs/introduction/baseline
- https://detekt.dev/docs/introduction/extensions — custom rules
- https://detekt.dev/docs/rules/complexity — one page per rule set
- https://detekt.dev/kdoc/ — API reference for `Rule`, `Finding`, `Entity`, `Config`
- https://github.com/detekt/detekt — source, issue tracker
- Default config: `detekt-core/src/main/resources/default-detekt-config.yml`
