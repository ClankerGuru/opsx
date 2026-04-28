---
name: kover
description: Reference guide for Kover (kotlinx-kover) Gradle plugin covering the kover { } DSL, report types (HTML/XML/binary/log), filters (classes/annotations/inheritance), verify rules with coverage units and aggregation, report variants, multi-module merging, Android variants, JaCoCo interop, plus this project's 90% threshold and thin-orchestrator strategy. Activates when configuring coverage or diagnosing verification failures.
---

# Kover

> For related topics see:
> - `/gradle` — plugin application, task wiring
> - `/gradle-plugins-basics` — `plugins { }` block, `pluginManagement`
> - `/testing-patterns` — how Kover fits into the test suite
> - `/kotest` — test framework whose runs feed Kover
> - `/detekt` — static analysis (complementary, not redundant)

## When to reach for this skill

- Applying and configuring `org.jetbrains.kotlinx.kover` in a Kotlin/JVM or
  multiplatform module.
- Deciding what to exclude (and what never to exclude).
- Reading an HTML report to find uncovered branches.
- Setting a verification threshold (`minBound` / `maxBound`).
- Merging coverage across a multi-module build.
- Choosing between `line`, `branch`, and `instruction` coverage units.
- Integrating with Android build variants.

## Plugin setup

```kotlin
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}
```

In multi-module builds, apply the plugin in every module that contributes
coverage and omit the version in subprojects (the root declares it).
`mavenCentral()` must be available — Kover's agent artifacts live there.

Kover instruments bytecode at test time via its own JVM agent (IntelliJ
Code Coverage). No `javaagent` wiring needed — the plugin handles it.

## The kover { } DSL — top-level shape

```kotlin
kover {
    currentProject {
        // Module-local: source sets, instrumentation, custom variants
    }
    reports {
        // Report generation, filters, verification rules
        total { /* total report across the module's sources */ }
        variant("debug") { /* Android / custom variant report */ }
        filters { /* shared across all variants */ }
        verify { /* shared verification */ }
    }
    useJacoco()        // opt into JaCoCo instead of the IntelliJ agent
}
```

- `currentProject { }` configures module-local concerns: source set
  inclusion/exclusion, instrumentation disable flags, custom variants.
- `reports { }` configures outputs and verification across variants.
- Kover creates a "total" variant automatically. Android builds add
  per-variant (flavor+buildType) reports.

## Report types

Each variant supports four outputs. Configure at `reports.total { }` or
`reports.variant("name") { }`.

### HTML report

```kotlin
html {
    onCheck = false
    title = "Coverage — myproject"
    charset = "UTF-8"
    htmlDir = layout.buildDirectory.dir("reports/kover/html")
}
```

Color coding: **green** = covered, **red** = missed, **yellow** =
partially covered branches.

### XML report

```kotlin
xml {
    onCheck = false
    title = "myproject"
    xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
}
```

JaCoCo-compatible schema. Feed to Codecov, SonarQube, Coveralls, etc.

### Binary report

```kotlin
binary {
    onCheck = false
    file = layout.buildDirectory.file("reports/kover/report.ic")
}
```

IntelliJ IC format. Useful when generating reports on a different
machine than where tests ran.

### Log report

```kotlin
log {
    onCheck = false
    header = "Coverage summary"
    format = "<entity> coverage: <value>%"
    groupBy = GroupingEntityType.APPLICATION
    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
    coverageUnits = CoverageUnit.LINE
}
```

Prints to the build log. Handy for CI when you want numbers without
cracking open an HTML report.

`onCheck = true` wires a report generation into the standard `check`
lifecycle so CI catches regressions automatically.

## Filters — what to exclude

Filters apply at three levels, each **replacing** (not merging) the
level below unless you use the `filtersAppend` variant:

1. Common — `reports.filters { }` applies to all variants.
2. Total — `reports.total { filters { } }` overrides for the total.
3. Variant — `reports.variant("debug") { filters { } }` per variant.

```kotlin
kover {
    reports {
        filters {
            excludes {
                classes("com.example.generated.*", "*.BuildConfig")
                packages("com.example.internal.debug")
                annotatedBy("org.gradle.api.tasks.TaskAction")
                inheritedFrom("com.example.BaseGenerated")
            }
            includes {
                classes("com.example.*")  // whitelist
            }
        }
    }
}
```

Filter targets:

- `classes("pattern")` — FQN, supports `*` (any chars) and `?` (one char).
  Inner classes use `$`: `com.example.Outer$Inner`.
- `packages("pattern")` — package-level globs.
- `annotatedBy("FQN")` — classes with the given annotation. Requires
  `@Retention(BINARY)` or `RUNTIME`; source-retention annotations don't
  survive compilation.
- `inheritedFrom("FQN")` — classes extending / implementing the given
  type.

**Excludes override includes.** If both match, the class is excluded.

### Source-set inclusion

```kotlin
kover {
    currentProject {
        sources {
            excludedSourceSets.addAll("slopTest", "functionalTest")
            includedSourceSets.addAll("main", "extra")
        }
    }
}
```

Test source sets are excluded from coverage by default.

## Instrumentation control

```kotlin
kover {
    currentProject {
        instrumentation {
            excludedClasses.add("com.example.Unstable*")
            disableForTestTasks.add("performanceTest")
            // disableForAll = true    // nuclear option
        }
    }
}
```

Use this when instrumentation itself is the problem — e.g. a class with
bytecode tricks that the coverage agent can't handle, or a perf-critical
test task where instrumentation overhead skews measurements.

## Verification rules

```kotlin
kover {
    reports {
        verify {
            rule {
                minBound(90)             // default: LINE / COVERED_PERCENTAGE / APPLICATION
            }
            rule("branch-coverage") {
                bound {
                    minValue = 75
                    coverageUnits = CoverageUnit.BRANCH
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                }
                groupBy = GroupingEntityType.APPLICATION
            }
        }
    }
}
```

### CoverageUnit

- `LINE` (default) — source lines.
- `BRANCH` — conditional branches (both arms of `if`, each `when`
  branch).
- `INSTRUCTION` — JVM bytecode instructions. Fine-grained but noisy.

### AggregationType

- `COVERED_PERCENTAGE` (default) — covered / total × 100.
- `MISSED_PERCENTAGE` — 100 − covered percentage.
- `COVERED_COUNT` — absolute covered units.
- `MISSED_COUNT` — absolute missed units; use with `maxBound` to enforce
  "no more than N uncovered lines".

### GroupingEntityType

- `APPLICATION` (default) — single value for the whole module.
- `CLASS` — per-class thresholds (fails if any class is below).
- `PACKAGE` — per-package thresholds.

Per-variant thresholds live at the variant level:

```kotlin
reports {
    total { verify { rule { minBound(50) } } }
    variant("release") { verify { rule { minBound(80) } } }
}
```

## Report variants

- **Total** — aggregates all source sets and tests in the module.
  Available automatically.
- **Named** — per Android build variant (`debug`, `release`,
  `freeDebug`), or user-defined custom variants.
- **Custom** — combine several variants:

```kotlin
kover {
    currentProject {
        createVariant("ci") {
            add("debug")
            add("jvm")
        }
    }
}
```

Tasks: `:koverHtmlReportCi`, `:koverVerifyCi`, etc.

## Multi-module merging

Pick one module (usually the root) to aggregate coverage from others:

```kotlin
// root build.gradle.kts
plugins { id("org.jetbrains.kotlinx.kover") }

dependencies {
    kover(project(":core"))
    kover(project(":api"))
    kover(project(":service"))
}
```

Running `:koverHtmlReport` on the root triggers every dependent
module's tests and merges results. Classes from one module can be
measured by tests in another — essential when integration tests in
`:service` exercise code in `:core`.

In multi-project builds **all participating modules must use the same
coverage type** — either all Kover or all JaCoCo.

## Generated tasks

| Task | Purpose |
|---|---|
| `koverHtmlReport` | HTML output |
| `koverXmlReport` | XML output |
| `koverBinaryReport` | IC format |
| `koverLog` | Log output |
| `koverVerify` | Run all verification rules |
| `koverHtmlReport<Variant>` | Per-variant HTML |
| `koverVerify<Variant>` | Per-variant verification |

Every report task transitively depends on the test tasks in the module
(and merged modules). Running `koverHtmlReport` runs the tests.

## JaCoCo mode

```kotlin
kover {
    useJacoco()                // latest JaCoCo
    useJacoco("0.8.14")        // pinned version
}
```

Use JaCoCo only when you need JaCoCo-specific output formats or tool
integrations. Caveats:

- Annotation-based filters (`annotatedBy`) are **not supported** — JaCoCo
  has no annotation filter.
- Feature parity not guaranteed; prefer the default IntelliJ agent.
- All merged modules must use JaCoCo too.

## This project's configuration

Two convention plugins cover the repo:

### srcx / clkx-testing — strict

```kotlin
kover {
    reports {
        filters {
            excludes {
                // It is prohibited to add exclusions
            }
        }
        verify {
            rule { minBound(90) }
        }
    }
}
```

No exclusions permitted. All untestable code must be refactored behind
an `internal` method that **is** testable — see below.

### opsx — minimal exclusions

```kotlin
kover {
    reports {
        filters {
            excludes {
                // @TaskAction run() methods need real Gradle execution context
                annotatedBy("org.gradle.api.tasks.TaskAction")
                // Settings plugin apply() needs a real Settings instance
                classes("*\$SettingsPlugin", "*\$SettingsPlugin\$*")
            }
        }
        verify {
            rule { minBound(90) }
        }
    }
}
```

Only two exclusions, both justified by Gradle's plugin model — a real
`Settings` / `Project` / task execution graph can't be constructed in a
unit test. TestKit covers them from a forked JVM that Kover can't
instrument.

### Threshold — 90% minimum

The build fails if line coverage for the module drops below 90%. Do not
lower this. If coverage falls, refactor production code to be testable.

### Thin-orchestrator strategy

`@TaskAction` methods must be thin orchestrators. All logic lives in
`internal` methods that tests exercise directly:

```kotlin
class ProposeTask : DefaultTask() {
    // All logic in internal methods — fully testable
    internal fun resolveChangeName(spec: String?, prompt: String?): String { ... }
    internal fun buildProposalPrompt(context: String, spec: String, ...): String { ... }

    @TaskAction
    fun run() {
        // Thin orchestration — excluded from coverage
        val name = resolveChangeName(spec, prompt)
        val body = buildProposalPrompt(...)
        // ...
    }
}
```

Same principle for `SettingsPlugin.apply()` — register tasks, don't
compute. Logic belongs in the enclosing `data object`'s
`registerTasks(...)` method, tested directly.

### Source sets and Kover

| Source set | Purpose | Feeds Kover? |
|---|---|---|
| `test` | Unit tests (Kotest, ProjectBuilder) | **Yes** |
| `slopTest` | Konsist architecture tests | No |
| `functionalTest` | Gradle TestKit integration tests | No |

Kover only measures coverage from the `test` source set. TestKit tests
run in a forked JVM that Kover cannot instrument; Konsist tests inspect
source, not runtime behavior.

## Running

```bash
./gradlew koverVerify              # fail if coverage < 90%
./gradlew koverHtmlReport          # generate HTML
./gradlew koverXmlReport           # generate XML (CI)

# Report locations:
# build/reports/kover/html/index.html
# build/reports/kover/report.xml
```

To diagnose a failure:

```bash
./gradlew koverHtmlReport
open build/reports/kover/html/index.html
```

Look for red lines in your changed classes. Often the fix is a missing
test or a refactor: move untested logic into an `internal` method
called from `@TaskAction`.

## Anti-patterns

```kotlin
// WRONG: lower the threshold to "unblock" CI
kover { reports { verify { rule { minBound(80) } } } }

// WRONG: broad exclusion to hide untested code
kover {
    reports {
        filters {
            excludes { classes("com.example.service.*") }
        }
    }
}

// WRONG: business logic in @TaskAction
@TaskAction
fun run() {
    val changes = discoverChanges()
    val filtered = changes.filter { it.type == "feature" }
    val prompt = buildString { ... 30 lines ... }
    writeOutput(prompt)
}
// Correct: thin orchestration, logic in internal methods
@TaskAction
fun run() {
    val prompt = buildProposalPrompt(discoverChanges())
    writeOutput(prompt)
}

// WRONG: rely on functional tests for coverage
// TestKit runs in a forked JVM — Kover cannot instrument it.
// Always add unit-test coverage for new logic.

// WRONG: mixing Kover and JaCoCo across modules in a merged build
// Pick one.
```

## Common pitfalls

- **`annotatedBy` doesn't work** — check the annotation's retention. It
  must be `BINARY` or `RUNTIME`. `SOURCE`-retention annotations are gone
  by the time Kover sees the bytecode.
- **Class not excluded** — for inner classes, use `$`:
  `*$SettingsPlugin` matches a nested `SettingsPlugin`, not a top-level
  one.
- **Coverage drops when adding a class** — even a class with a single
  property counts toward the denominator. Add a test or accept the
  slight drop.
- **Includes + excludes interaction** — excludes always win.
- **Report shows stale data** — Kover caches binary reports. Run
  `clean` when diagnosing weird numbers.
- **Multi-module tests not in report** — forgot `kover(project(":m"))`
  in the aggregating module.

## References

- GitHub — https://github.com/Kotlin/kotlinx-kover
- Docs — https://kotlin.github.io/kotlinx-kover/gradle-plugin/
- Migration guide — https://kotlin.github.io/kotlinx-kover/gradle-plugin/migrations/
- CLI (`kover-cli`) — https://kotlin.github.io/kotlinx-kover/cli/
- Release notes — https://github.com/Kotlin/kotlinx-kover/releases
