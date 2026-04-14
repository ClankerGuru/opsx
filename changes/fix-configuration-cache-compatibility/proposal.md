# Proposal: Fix Configuration Cache Compatibility in All Opsx Tasks

## Problem

Every opsx task accesses `project.rootDir`, `project.findProperty()`, or `project.gradle.includedBuilds` inside `@TaskAction` methods at **execution time**. Gradle's configuration cache requires that tasks be fully serializable after configuration — any `project` access during execution breaks the cache and causes build failures when `--configuration-cache` is enabled.

Affected tasks (14 total in `zone.clanker.opsx.task`):

| Task | `project.rootDir` | `project.findProperty()` | `project.gradle` |
|------|-------------------|--------------------------|-------------------|
| ProposeTask | yes | SPEC, PROMPT, CHANGE_NAME, AGENT, MODEL | - |
| ApplyTask | yes | CHANGE, AGENT, MODEL | - |
| VerifyTask | yes | CHANGE, AGENT, MODEL | - |
| ArchiveTask | yes | CHANGE | - |
| ContinueTask | yes | CHANGE, AGENT, MODEL | - |
| ExploreTask | yes | PROMPT, AGENT, MODEL | - |
| FeedbackTask | yes | CHANGE, PROMPT, AGENT, MODEL | - |
| OnboardTask | yes | PROMPT, AGENT, MODEL | - |
| FfTask | yes | CHANGE, AGENT, MODEL | - |
| BulkArchiveTask | yes | - | - |
| StatusTask | yes | - | - |
| ListTask | yes | - | - |
| SyncTask | yes (via SkillGenerator) | - | includedBuilds |
| CleanTask | yes | - | includedBuilds |

Additionally, `SkillGenerator` takes a `Project` reference and accesses `rootProject.tasks`, `rootProject.gradle.includedBuilds`, and `rootProject.rootDir` — all of which are project state that must not leak into execution.

## Scope

- All 14 task classes in `opsx/src/main/kotlin/zone/clanker/opsx/task/`
- `SkillGenerator` constructor and usages
- Task registration in `Opsx.kt` `registerTasks()`
- Dead inner classes in `Opsx.kt` (`SyncTask`, `CleanTask`, `StatusTask`, `ListTask`) — remove since the package versions supersede them

## Out of Scope

- The `SettingsExtension` property — already captured at configuration time via the registration lambda and passed as a plain data object (not a Gradle model type). This is safe.
- Any functional behavior changes — all tasks must produce identical results.

## Constraints

- Must follow the same pattern as srcx `ContextTask`: abstract `Property<T>` fields annotated `@Internal`, set during task registration, read via `.get()` / `.orNull` in `@TaskAction`.
- `-P` flags are optional and may be absent — use `Property<String>` with `.orNull` rather than `.get()`.
- `SkillGenerator` must be refactored to accept plain data instead of `Project`.
