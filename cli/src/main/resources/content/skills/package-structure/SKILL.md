---
name: package-structure
description: Reference guide for Kotlin package design — general principles (cohesion, acyclic dependencies, feature vs layer), package-by-feature layout, and this project's specific Gradle settings plugin layout under {group}.{plugin} with model/task/workflow/scan/report/parse/analysis/skill sub-packages and enforced dependency direction. Activates when creating plugins, adding packages, or placing new classes.
---

# Package Structure

> For related topics see:
> - `/naming-conventions` — what to call the packages and files
> - `/konsist` — the tool that enforces package boundaries
> - `/kotlin-conventions` — project Kotlin rules
> - `/gradle-plugins-basics` — plugin ID and class layout
> - `/kotlin-functional-first` — leaf-first, pure-first design that maps naturally to this layout

## Generic principles

### Cohesion over size

A package is a pile of things that change together. Put things in the
same package when **changing one of them usually means changing the
others**. Split a package when two clusters start drifting.

A five-class package that always moves as a unit beats a fifteen-class
package that's really three disjoint concerns.

### Stable dependencies point inward

Draw an arrow from *depends on* to *depended upon*. Packages that are
depended on by many must be stable (rarely change). Packages that
depend on many must be unstable (safe to change).

Consequence: **the most stable thing in the codebase is your model /
domain**. Put `data class`, `enum class`, and `value class` definitions
at the bottom of the graph. No imports from anywhere.

### No cycles

If `a` imports `b` and `b` imports `a`, either they're the same package
or you've confused two concerns. Konsist catches these
(`assertHasNoCircularDependencies`).

### Package-by-feature (mostly)

Two common organizing axes:

- **By layer** — `controller/`, `service/`, `repository/`. Easy to
  explain; scatters each feature across several packages.
- **By feature** — `ordering/`, `billing/`, `notifications/`. Each
  feature is a self-contained unit.

For most JVM apps, **package-by-feature** wins: features change as a
unit; onboarding a dev means learning one feature folder end-to-end.

For **Gradle plugins** (and this project), the functional / pipeline
split is stronger than either pure layer or pure feature. A plugin is
a pipeline: parse → analyze → scan → report → task. Each stage is a
package; dependencies flow one direction.

### Internal vs public

- `internal` restricts visibility to the module (Gradle source set). Use
  it liberally — a class that's not part of the plugin's public API
  should be `internal`.
- `public` is the default; apply it only to what downstream modules or
  plugin users consume.
- Files without a top-level `public` class should use `internal
  class`.

### Extension functions and their packages

Put extension functions in the package that owns the **receiver**, not
the package that owns the return type. If an extension is only used by
one module, make it `internal`.

DSL accessors are the exception — put them in a `*Dsl.kt` file at the
plugin root so `settings.myplugin { }` resolves without an extra import.

## This project's standard layout

Each Gradle settings plugin follows this layout under `{group}.{plugin}`:

```
{group}/{plugin}/
    {Plugin}.kt          # data object + SettingsPlugin + SettingsExtension
    {Plugin}Dsl.kt       # Settings.{plugin} { } accessor (optional)
    model/               # Data classes, enums, value classes, configs
    task/                # One file per task, each extends DefaultTask
    workflow/            # Business logic utilities
    scan/                # Scanner / traversal logic
    report/              # Renderers, writers, formatters
    parse/               # Source parsing (e.g. PSI-based)
    analysis/            # Analyzers, detectors
    skill/               # Skill / command generation
```

Not every plugin uses every package. Use only what is needed — a
workflow-only plugin has `model/`, `workflow/`, `task/` and nothing
else.

## Per-package rules

### Root package — `{Plugin}.kt`

Contains exactly three things:

1. **`data object {Plugin}`** — the plugin's identity, constants, and
   task registration logic.
2. **`class SettingsPlugin : Plugin<Settings>`** — nested inside the
   data object, thin `apply()` method.
3. **`abstract class SettingsExtension`** — nested inside the data
   object, Gradle-managed properties.

```kotlin
data object MyPlugin {
    const val TASK_PROPOSE = "myplugin-propose"

    class SettingsPlugin : Plugin<Settings> {
        override fun apply(settings: Settings) { ... }
    }

    abstract class SettingsExtension {
        abstract val outputDir: Property<String>
    }
}
```

No logic in this file — delegate to `workflow/` / `scan/` / `task/`.

### `model/`

- Data classes, value classes, enums.
- **Leaf nodes** — `model` imports nothing from other internal
  packages.
- No business logic; only data representation and invariant validation
  (`init { require(...) }`).
- Often the most stable, most reused part of the plugin.

### `task/`

- One file per task class.
- Each task class extends `DefaultTask`.
- `@TaskAction fun run()` is a thin orchestrator; all logic in
  `internal` methods.
- May import from any other internal package — sits at the top of the
  dependency graph.

### `workflow/`

- Business logic utilities: readers, writers, dispatchers, prompt
  builders.
- May import from `model` only (not `task`, not `skill`).
- Pure-ish functions where possible — easier to test, cheaper to
  reuse.

### `scan/`

- Scanner and traversal logic.
- May import from `model`, `analysis`, `parse`.
- Never imports from `task` or `report`.

### `report/`

- Renderers: Markdown, HTML, text.
- May import from `model` and `scan`.
- Never imports from `task`, `parse`, or `analysis` — `report`
  consumes pre-analyzed models, doesn't re-parse.

### `parse/`

- Source parsing (PSI, ANTLR, regex).
- May import from `model` only.
- Never imports from `task`, `report`, `scan`.

### `analysis/`

- Source analysis: anti-pattern detection, classification, dependency
  analysis.
- May import from `model` only.
- Never imports from `task`, `report`, `scan`.

### `skill/`

- Skill file and command generation (opsx-specific).
- May import from `model` only.
- Never imports from `workflow`.

## Package dependency graphs

### Source-analysis plugin (srcx)

```
model      → (nothing)
parse      → model
analysis   → model
scan       → model, analysis, parse
report     → model, scan
task       → model, parse, analysis, report, scan
```

### Workflow / skill plugin (opsx)

```
model      → (nothing)
skill      → model
workflow   → model
task       → model, workflow, skill
```

Arrows indicate "depends on / imports from". The graph is a DAG — any
cycle fails the architecture test.

These boundaries are enforced by a `PackageBoundaryTest` in the
architecture-test source set (see `/konsist`).

## Reading a layout

When you land on a plugin you haven't seen, read in this order:

1. **`{Plugin}.kt`** — understand the plugin ID, extension schema, and
   registered tasks. Five minutes.
2. **`model/`** — learn the domain vocabulary. The data classes define
   everything the plugin talks about.
3. **`task/`** — the top of the pipeline. Each task tells you an
   end-to-end story (inputs → outputs).
4. **Working backwards from task methods** — follow each internal
   helper into `workflow/`, `scan/`, etc.

This order mirrors the dependency graph: bottom-up from model, then
top-down from tasks.

## Examples

### New feature: changelog generator

```
{group}/myplugin/
    MyPlugin.kt                    # Add TASK_CHANGELOG constant
    model/
        ChangelogEntry.kt          # Data class for a changelog entry
    workflow/
        ChangelogBuilder.kt        # Builds changelog from changes
    task/
        ChangelogTask.kt           # Wires ChangelogBuilder
```

Dependency flow:

```
ChangelogTask → ChangelogBuilder → ChangelogEntry
(task)          (workflow)         (model)
```

### New feature: dependency-graph analyzer

```
{group}/srcx/
    Srcx.kt                        # Add TASK_DEPGRAPH constant
    model/
        DependencyEdge.kt          # Data class for an edge
        DependencyGraph.kt         # Data class for the full graph
    parse/
        GradleFileParser.kt        # Parses build files
    analysis/
        DependencyAnalyzer.kt      # Builds the graph
    scan/
        ProjectScanner.kt          # Walks the multi-project build
    report/
        DependencyGraphRenderer.kt # Renders DOT / Markdown
    task/
        DepGraphTask.kt            # Wires everything
```

## Anti-patterns

```kotlin
// WRONG: task class outside task/
// src/main/kotlin/com/example/myplugin/MyCoolTask.kt
// Correct
// src/main/kotlin/com/example/myplugin/task/MyCoolTask.kt

// WRONG: standalone Constants.kt
// src/main/kotlin/com/example/myplugin/Constants.kt
// object Constants { const val GROUP = "myplugin" }
// Correct
// constants live on MyPlugin data object

// WRONG: utils/helpers package
// src/main/kotlin/com/example/myplugin/utils/StringUtils.kt
// Correct: extension functions in the package of the receiver
// src/main/kotlin/com/example/myplugin/workflow/StringExtensions.kt
// or fold single-use helpers into the using class

// WRONG: model imports from anywhere internal
// package com.example.myplugin.model
// import com.example.myplugin.parse.PsiParser   // model should be a leaf
// Correct: model has zero internal imports

// WRONG: report imports from parse or analysis
// package com.example.myplugin.report
// import com.example.myplugin.parse.PsiParser
// Correct: report consumes pre-analyzed models

// WRONG: business logic in {Plugin}.kt root file
// data object MyPlugin {
//     fun scanSources(...) = ... // 200 lines
// }
// Correct: delegate to workflow/ or scan/
```

## Common pitfalls

- **Adding a new package by copying an old one** — picks up stale
  dependencies. Start empty; import only what the first file in the
  package actually needs.
- **Circular import between `workflow/` and `scan/`** — usually means
  a piece of `scan/` logic belongs in `workflow/`, or vice versa. Find
  which one owns the data flow and move.
- **`task/` class with inline logic** — drops coverage and kills
  reuse. Keep `@TaskAction` thin; push logic into `internal` methods
  that unit tests can call.
- **`model/` class with an imported collection of `PsiElement`s** —
  `model/` must be framework-free. Use a plain data class and convert
  in `parse/`.
- **Package explosion** — 15 packages each with one class is a symptom
  of over-eager splitting. Collapse until each package has a real
  cluster of related types.

## References

- Kotlin coding conventions (package structure) —
  https://kotlinlang.org/docs/coding-conventions.html#source-file-organization
- Effective Java / Kotlin — Bloch's guidance on cohesion
- Uncle Bob on stable dependencies — "Clean Architecture" Ch 14
