# opsx

[![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/plugin-opsx?label=Maven%20Central)](https://central.sonatype.com/search?q=zone.clanker.plugin-opsx)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.1-green)](https://gradle.org)
[![Coverage](https://img.shields.io/badge/Coverage-%E2%89%A590%25-brightgreen)](https://github.com/ClankerGuru/opsx)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)

**Spec-driven development workflows, skill generation, and AI agent orchestration for Gradle workspaces.**

Define changes as structured proposals, apply them with any AI agent, verify correctness, and archive when done. opsx generates agent-specific skill files for Claude, Copilot, Codex, and OpenCode so every agent understands your project the same way.

## Usage

**settings.gradle.kts**

```kotlin
plugins {
    id("zone.clanker.gradle.opsx") version "<version>"
}

opsx {
    outputDir = "opsx"           // default
    defaultAgent = "claude"      // claude | copilot | codex | opencode
    changesDir = "changes"       // default
    specsDir = "specs"           // default
    projectFile = "project.md"   // default
}
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| [wrkx](https://github.com/ClankerGuru/wrkx) | 0.40.0 | Multi-repo workspace management |
| [srcx](https://github.com/ClankerGuru/srcx) | 0.46.0 | Source symbol extraction for LLM context |

## Tasks

Six tasks are visible in `./gradlew tasks`:

| Task | Description |
|---|---|
| `opsx-propose` | Propose a new change (`-Pzone.clanker.opsx.prompt="..."`) |
| `opsx-apply` | Apply a change to the codebase (`-Pzone.clanker.opsx.change="..."`) |
| `opsx-verify` | Verify a change was applied correctly (`-Pzone.clanker.opsx.change="..."`) |
| `opsx-archive` | Archive a completed change (`-Pzone.clanker.opsx.change="..."`) |
| `opsx-status` | Show all changes grouped by status |
| `opsx-sync` | Generate agent skills for Claude, Copilot, Codex, OpenCode |

Additional tasks are available but hidden from `./gradlew tasks`:

| Task | Description |
|---|---|
| `opsx-continue` | Continue work on an in-progress change |
| `opsx-explore` | Explore the codebase with an AI agent |
| `opsx-feedback` | Provide feedback on a change |
| `opsx-ff` | Fast-forward a change to the latest state |
| `opsx-onboard` | Onboard a new contributor |
| `opsx-bulk-archive` | Archive all completed changes |
| `opsx-list` | List all changes |
| `opsx-clean` | Remove generated skill files |

## Common Flags

All agent tasks support:

- `-Pzone.clanker.opsx.agent=copilot` -- Override the default agent
- `-Pzone.clanker.opsx.model=opus` -- Override the model

## Change Lifecycle

```
draft -> active -> in-progress -> completed/done -> verified -> archived
```

Changes live in `opsx/changes/<name>/` with:

- `.opsx.yaml` -- Change metadata (name, status, depends)
- `proposal.md` -- The change proposal
- `design.md` -- Technical design
- `tasks.md` -- Implementation checklist

## Agent Skills

`opsx-sync` generates slash commands for each agent:

- **Claude**: `.claude/commands/` + `CLAUDE.md`
- **Copilot**: `.github/prompts/` + `AGENTS.md` + `.github/copilot-instructions.md`
- **Codex**: `~/.codex/prompts/`
- **OpenCode**: `.opencode/commands/`

## Architecture

### Package structure

```text
zone.clanker.opsx/
├── model/       <- Domain value types (Change, ChangeStatus, OpsxConfig, TaskDefinition, etc.)
├── task/        <- Gradle task classes (one per lifecycle action)
├── workflow/    <- Orchestration logic (AgentDispatcher, TaskExecutor, ChangeReader, etc.)
└── skill/       <- Agent skill file generation (SkillGenerator, TaskInfo)
```

### Dependency direction

```
task -> model      (tasks consume models)
task -> workflow   (tasks delegate to workflows)
workflow -> model  (workflows consume models)
model -> (nothing) (models are leaf nodes)
workflow -> (no tasks)
```

Models are pure data. Tasks are thin entry points that delegate to workflows. Workflows contain the business logic.

## Contributing

### Requirements

- JDK 17+ (JetBrains Runtime recommended)
- Gradle 9.4.1 (included via wrapper)

### Build

```bash
./gradlew build
```

This single command runs everything:

| Step | Task | What it checks |
|------|------|---------------|
| Compile | `compileKotlin` | Kotlin source compiles |
| Detekt | `detekt` | Static analysis against `config/detekt.yml` |
| ktlint | `ktlintCheck` | Code formatting against `.editorconfig` |
| Unit tests | `test` | Model, task, workflow, and plugin behavior |
| Architecture tests | `slopTest` | Konsist: naming, packages, annotations, forbidden patterns |
| Coverage | `koverVerify` | Line coverage >= 90% enforced |
| Plugin validation | `validatePlugins` | Gradle plugin descriptor is valid |

### Common commands

```bash
./gradlew build                    # full build (everything)
./gradlew assemble                 # just compile
./gradlew test                     # unit tests
./gradlew detekt                   # static analysis only
./gradlew ktlintCheck              # formatting check only
./gradlew ktlintFormat             # auto-fix formatting
./gradlew slopTest                 # architecture tests (Konsist)
./gradlew check                    # all verification tasks
./gradlew publishToMavenLocal      # publish to ~/.m2 for local testing
```

### Test suites

**Unit tests** (`src/test/`) -- model validation, task behavior, workflow logic, plugin lifecycle. Kotest BehaviorSpec.

**Architecture tests** (`src/slopTest/`) -- Konsist structural rules enforced via the `slopTest` source set:

| Test | Enforces |
|------|----------|
| `PackageBoundaryTest` | Models never import from tasks or workflow. Workflow never imports from tasks. |
| `NamingConventionTest` | Task classes end with `Task`. No generic suffixes (Helper, Manager, Util, etc.). |
| `TaskAnnotationTest` | Every task class has `@DisableCachingByDefault` or `@CacheableTask` annotation. |
| `ForbiddenPackageTest` | No junk-drawer packages (utils, helpers, managers, misc, base). |
| `ForbiddenPatternTest` | No try-catch (use runCatching). No standalone constant files. No wildcard imports. |

**Coverage threshold**: 90% minimum line coverage via [Kover](https://github.com/Kotlin/kotlinx-kover). `@TaskAction` methods and `SettingsPlugin` classes are excluded (require real Gradle execution context).

```bash
./gradlew koverVerify              # check coverage threshold
./gradlew koverHtmlReport          # generate HTML report
open build/reports/kover/html/index.html
```

### Convention plugins (build-logic)

All build configuration is managed through precompiled script plugins:

| Plugin | Provides |
|--------|----------|
| `clkx-conventions` | Applies all conventions below |
| `clkx-module` | `java-library` + Kotlin JVM + JUnit Platform |
| `clkx-toolchain` | JDK 17 toolchain (JetBrains vendor) |
| `clkx-plugin` | `java-gradle-plugin` + Maven publish setup |
| `clkx-publish` | Maven Central publishing via Vanniktech |
| `clkx-testing` | Kotest + Gradle TestKit dependencies |
| `clkx-cover` | Kover coverage with 90% minimum + exclusions |
| `clkx-konsist` | Konsist + slopTest source set + `slopTest` task |
| `clkx-detekt` | Detekt static analysis with `config/detekt.yml` |
| `clkx-ktlint` | ktlint formatting (v1.5.0) |

The main `build.gradle.kts` is one line:

```kotlin
plugins {
    id("clkx-conventions")
}
```

### Project structure

```text
opsx/
├── config/
│   └── detekt.yml               <- Detekt static analysis rules
├── build-logic/                 <- Convention plugins (clkx-*)
│   ├── build.gradle.kts         <- Plugin dependencies
│   ├── settings.gradle.kts
│   └── src/main/kotlin/         <- 10 convention plugin scripts
├── src/
│   ├── main/kotlin/zone/clanker/opsx/
│   │   ├── Opsx.kt             <- SettingsPlugin + SettingsExtension + OpsxConfig + constants
│   │   ├── model/
│   │   │   ├── Change.kt              <- Change data class
│   │   │   ├── Agent.kt               <- Agent enum (CLAUDE, COPILOT, CODEX, OPENCODE)
│   │   │   ├── ChangeConfig.kt        <- Change configuration
│   │   │   ├── ChangeStatus.kt        <- Status enum (draft, active, etc.)
│   │   │   ├── OpsxConfig.kt           <- Serializable settings for config cache
│   │   │   ├── TaskDefinition.kt      <- Task definition model
│   │   │   └── TaskStatus.kt          <- Task status enum
│   │   ├── skill/
│   │   │   ├── SkillGenerator.kt      <- Agent skill file generator
│   │   │   └── TaskInfo.kt            <- Task metadata for skill generation
│   │   ├── task/
│   │   │   ├── ApplyTask.kt           <- Apply a change via agent
│   │   │   ├── ArchiveTask.kt         <- Archive a completed change
│   │   │   ├── BulkArchiveTask.kt     <- Archive all completed changes
│   │   │   ├── CleanTask.kt           <- Remove generated files
│   │   │   ├── ContinueTask.kt        <- Continue in-progress change
│   │   │   ├── ExploreTask.kt         <- Explore codebase via agent
│   │   │   ├── FeedbackTask.kt        <- Provide feedback on a change
│   │   │   ├── FfTask.kt              <- Fast-forward a change
│   │   │   ├── ListTask.kt            <- List all changes
│   │   │   ├── OnboardTask.kt         <- Onboard a contributor
│   │   │   ├── ProposeTask.kt         <- Propose a new change
│   │   │   ├── StatusTask.kt          <- Show change status
│   │   │   ├── SyncTask.kt            <- Generate agent skills
│   │   │   └── VerifyTask.kt          <- Verify change correctness
│   │   └── workflow/
│   │       ├── AgentDispatcher.kt      <- Dispatch commands to agents
│   │       ├── ChangeLogger.kt         <- Log change lifecycle events
│   │       ├── ChangeReader.kt         <- Read change files from disk
│   │       ├── ChangeWriter.kt         <- Write change files to disk
│   │       ├── PromptBuilder.kt        <- Build agent prompts
│   │       ├── TaskExecutor.kt         <- Execute atomic tasks with dependency ordering
│   │       └── TaskParser.kt           <- Parse task definitions
│   ├── test/kotlin/             <- Unit tests (Kotest BehaviorSpec)
│   └── slopTest/kotlin/         <- Architecture tests (Konsist)
│       ├── PackageBoundaryTest.kt
│       ├── NamingConventionTest.kt
│       ├── TaskAnnotationTest.kt
│       ├── ForbiddenPackageTest.kt
│       └── ForbiddenPatternTest.kt
├── build.gradle.kts             <- One line: id("clkx-conventions")
├── settings.gradle.kts          <- build-logic (named opsx-build-logic), clkx-settings, root name
├── gradle.properties            <- Version, Maven coordinates, POM metadata
└── LICENSE                      <- MIT
```

## License

[MIT](LICENSE)
