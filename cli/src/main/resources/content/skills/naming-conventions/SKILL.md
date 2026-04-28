---
name: naming-conventions
description: Reference guide for Kotlin + Gradle naming conventions — language-level identifier rules, package/file naming, Gradle plugin IDs, task IDs, and this project's specific rules for plugin/extension/task/model/workflow/test class naming and convention-plugin prefix. Activates when creating any named artifact.
---

# Naming Conventions

> For related topics see:
> - `/package-structure` — where named artifacts live
> - `/kotlin-conventions` — project-wide Kotlin style rules
> - `/kotlin-lang` — language features (value classes, data objects, sealed)
> - `/gradle-plugins-basics` — plugin ID and apply plugin mechanics
> - `/konsist` — how these naming rules are enforced at build time

## Kotlin language-level rules

Kotlin's official style guide ([kotlinlang.org/docs/coding-conventions.html][kotlin]):

| Kind | Case | Example |
|---|---|---|
| Package | lowercase, no underscores | `com.example.gradle.myplugin` |
| Class / interface / typealias / annotation | PascalCase | `SymbolEntry`, `ChangeReader` |
| Object / `data object` | PascalCase | `Srcx`, `Wrkx` |
| Function (regular) | camelCase | `scanSources()` |
| Function (Composable / returns `@Composable`) | PascalCase | `UserAvatar()` |
| Function (test name in backticks) | natural language | `` `parses empty input`() `` |
| Property (val/var) | camelCase | `outputDir` |
| Compile-time constant (`const val`) | UPPER_SNAKE_CASE | `TASK_PROPOSE` |
| Top-level / companion `val` of immutable data | UPPER_SNAKE_CASE | `DEFAULT_TIMEOUT` |
| Enum entry | UPPER_SNAKE_CASE or PascalCase | `DATA_CLASS`, `Controller` |
| Generic type parameter | single uppercase letter or `T`-prefixed | `T`, `K`, `TResult` |
| Backing property | `_camelCase` | `_items` |
| Private property (optional) | `_camelCase` | `_cache` |

### File names

- **Single top-level class/object** — file name matches the declaration:
  `class SymbolEntry` → `SymbolEntry.kt`.
- **Multiple top-level declarations** — file name describes the topic in
  PascalCase: `Parsers.kt` with several `parse*` functions.
- **Extension-only files** — suffix `Extensions.kt` or `Dsl.kt` if the
  extensions are DSL accessors: `SrcxDsl.kt`, `StringExtensions.kt`.

### Backticked identifiers

Kotlin lets you put arbitrary characters in identifier names using
backticks. Reserve this for:

- Test function names — `` `returns empty list when input is blank`() ``.
- Java interop collisions — Java `fun` that conflicts with a Kotlin
  keyword.

Never use backticks to invent names in production code.

### Boolean naming

Booleans read like sentences:

```kotlin
val isActive: Boolean
val hasItems: Boolean
val canRetry: Boolean
val shouldCascade: Boolean
fun isValid(input: String): Boolean
```

Avoid negative names (`isNotReady`) — they compound awkwardly with `!`.

### Collections

Use plural: `items`, `users`, `sources`. Suffix with the collection type
only when disambiguation matters: `itemsByName: Map<String, Item>`,
`itemsQueue: ArrayDeque<Item>`.

### Function verbs

- Query (no side effect): `findUser`, `getUser`, `userOrNull`.
- Command (side effect): `saveUser`, `flush`, `writeFile`.
- Predicate: `isValid`, `hasAccess`.
- Conversion: `toList`, `toString`, `asSequence`.
- Factory: `createUser`, `newUser`, `userOf` (prefer `create*` over
  `new*` in Kotlin).

`as*` returns a **view** of the same data (`Sequence.asList()` may still
be lazy). `to*` creates a new copy. Respect the distinction when
naming your own.

### Package names

- All lowercase, no camelCase, no underscores.
- Reverse-DNS: `com.example.gradle.myplugin`.
- Singular nouns for domain packages: `model`, not `models`.
- Functional names (not layer names) when possible: `scan`, `report`,
  `parse` — what the code *does*, not *what it is*.

## Gradle naming

### Plugin IDs

- Reverse-DNS, lowercase, hyphen-separated: `com.example.myplugin`.
- For Gradle-specific plugins, the convention is
  `{group}.gradle.{name}`: `com.example.gradle.myplugin`.
- IDs appear in `plugins { id("...") }`, the `pluginManagement`
  resolution strategy, and Gradle Plugin Portal. Keep them stable
  across versions.

### Task names

- Kebab-case: `myplugin-context`, not `mypluginContext`.
- `{plugin}-{verb}` pattern — the plugin prefix namespaces the task,
  the verb says what runs.
- Lifecycle tasks are nouns (inherited): `build`, `check`, `clean`.
- Publishing tasks follow Gradle conventions: `publishMaven...`.
- Task name constants live on the plugin's `data object`:

```kotlin
data object MyPlugin {
    const val TASK_CONTEXT = "myplugin-context"
    const val TASK_PROPOSE = "myplugin-propose"
}
```

### Configuration names

Gradle configurations default to camelCase: `implementation`,
`testImplementation`, `runtimeOnly`. Custom configurations follow the
same case: `myPluginRuntime`.

### Source-set names

Kotlin source sets are camelCase: `main`, `test`, `integrationTest`,
`functionalTest`.

### Convention plugin files

- Precompiled script plugins: `{prefix}-{concern}.gradle.kts`.
- Pick one prefix and stick with it across the repo: `conv-`, `clkx-`,
  `myorg-`.
- Each file = one concern: `conv-detekt.gradle.kts`,
  `conv-testing.gradle.kts`.

## This project's rules

### Plugin naming

- ID format: `{group}.gradle.{name}` or `{group}.{name}`.
- Stable reverse-DNS group across all plugins.

### Task naming

- `{group}-{action}` with a hyphen.
- Group = plugin name, lowercase; action = verb.
- Examples: `myplugin-context`, `myplugin-clean`, `myplugin-propose`,
  `myplugin-apply`.
- Constants on the data object:
  `const val TASK_CONTEXT = "myplugin-context"`.

### Class naming

**Task classes**

- Format: `{Action}Task` (PascalCase).
- Matches the action in the task name.
- Examples: `ContextTask`, `CleanTask`, `ProposeTask`, `ApplyTask`,
  `VerifyTask`, `ArchiveTask`.
- Must extend `DefaultTask`.
- Must live in `task/`.

**Data object**

- Name = plugin's short name, PascalCase.
- Contains `SettingsPlugin` and `SettingsExtension` as nested types.
- Holds task-name constants.

**Extension class**

- Always `SettingsExtension`.
- Always nested inside the data object.
- Example: `MyPlugin.SettingsExtension`.

**Model classes**

- Data classes: descriptive nouns — `ProjectSummary`, `SymbolEntry`,
  `ChangeConfig`.
- Value classes: wrap primitives with validation — `SymbolName`,
  `PackageName`, `FilePath`.
- Enums: singular nouns — `SymbolKind`, `ChangeStatus`, `ReferenceKind`.

**Workflow / business-logic classes**

- Descriptive nouns ending in an agent noun: `ChangeReader`,
  `ChangeWriter`, `PromptBuilder`, `AgentDispatcher`, `SourceScanner`,
  `PsiParser`, `ReportRenderer`.

### Test naming

**Unit tests (`src/test/`)**

- Format: `{Class}Test` in the same package as the class under test.
- Examples: `ProposeTaskTest`, `SymbolEntryTest`, `ChangeReaderTest`.

**Architecture tests (separate source set)**

- Format: `{Pattern}Test` describing the rule.
- Live in the plugin's root package.
- Examples: `ForbiddenPatternTest`, `PackageBoundaryTest`,
  `TaskAnnotationTest`.

**Functional tests (`src/functionalTest/`)**

- Format: `{Plugin}PluginTest` or `{Feature}Test`.

### Convention plugin prefix

- Use a consistent prefix for convention plugins: `conv-`, `clkx-`,
  `wrkx-`.
- Format: `{prefix}-{concern}.gradle.kts`.
- Examples: `conv-detekt.gradle.kts`, `conv-ktlint.gradle.kts`,
  `conv-konsist.gradle.kts`, `conv-cover.gradle.kts`,
  `conv-testing.gradle.kts`.

### Source-set naming

| Source set | Purpose |
|---|---|
| `main` | Production code |
| `test` | Unit tests |
| `archTest` | Architecture tests (e.g. Konsist) |
| `functionalTest` | Integration tests (Gradle TestKit) |

### Forbidden class-name suffixes

Enforced by Konsist `NamingConventionTest`:

- `*Helper`
- `*Manager`
- `*Util`, `*Utils`
- `*Service` (unless the domain genuinely uses the term)
- `*Handler` (prefer specific agent nouns)

Pick a domain-specific agent noun: `SymbolScanner`, `ConfigReader`,
`ReportWriter`, `PromptBuilder` — each says exactly what the class
does. `ConfigManager` says nothing.

## Examples

Creating a new task that generates a feedback report:

```
Plugin constant:  const val TASK_FEEDBACK = "myplugin-feedback"
Task class:       FeedbackTask (in task/ package)
Test class:       FeedbackTaskTest (in test, same package)
```

Creating a new model:

```
Data class:       FeedbackSummary (in model/ package)
Test class:       FeedbackSummaryTest (in test, model/ package)
```

Creating a new workflow helper:

```
Class:            FeedbackCollector (in workflow/ package)
Test class:       FeedbackCollectorTest
```

## Anti-patterns

```kotlin
// WRONG: camelCase task name
tasks.register("mypluginContext") { }
// Correct
tasks.register("myplugin-context") { }

// WRONG: SettingsPlugin outside the data object
class MyPluginSettingsPlugin : Plugin<Settings> { ... }
// Correct
data object MyPlugin {
    class SettingsPlugin : Plugin<Settings> { ... }
}

// WRONG: Extension with a non-standard name
abstract class MyPluginConfig { ... }
// Correct
abstract class SettingsExtension { ... }

// WRONG: generic suffix
class ConfigManager { }
class ReportHelper { }
class StringUtils { }
// Correct
class ConfigReader { }
class ReportRenderer { }
class StringFormatter { }

// WRONG: architecture test in src/test/
src/test/kotlin/.../ForbiddenPatternTest.kt
// Correct
src/slopTest/kotlin/.../ForbiddenPatternTest.kt

// WRONG: test class without Test suffix
class ProposeTaskSpec : BehaviorSpec({ ... })
// Correct
class ProposeTaskTest : BehaviorSpec({ ... })

// WRONG: mixed convention prefixes
conv-detekt.gradle.kts
clkx-ktlint.gradle.kts
myorg-testing.gradle.kts
// Correct: pick one and stick to it
conv-detekt.gradle.kts
conv-ktlint.gradle.kts
conv-testing.gradle.kts

// WRONG: constants file
// Constants.kt
object Constants { const val GROUP = "myplugin" }
// Correct
data object MyPlugin { const val GROUP = "myplugin" }

// WRONG: camelCase package
package com.example.gradle.myPlugin
// Correct
package com.example.gradle.myplugin
```

## Common pitfalls

- **Renaming tasks** — task names are part of the plugin's public API.
  CI pipelines, documentation, user scripts reference them. Deprecate
  with a new name + old name alias before removing.
- **Plugin ID changes** — break every consumer's `plugins { }` block.
  Pick carefully up front.
- **Generic class suffixes drift in** — `NewConfigHelper` sneaks past
  review if Konsist rules aren't watching. Let the tool catch it.
- **Test class doesn't match class under test** — `ProposeTaskTests`
  (plural) or `ProposeTaskSpec` fails the naming test. Use `*Test`.
- **Hyphen vs underscore in task IDs** — Gradle technically accepts
  both; the convention is hyphen. Konsist catches this.

## References

- Kotlin coding conventions — https://kotlinlang.org/docs/coding-conventions.html
- Gradle plugin naming — https://docs.gradle.org/current/userguide/custom_plugins.html
- Google Kotlin style guide — https://developer.android.com/kotlin/style-guide

[kotlin]: https://kotlinlang.org/docs/coding-conventions.html
