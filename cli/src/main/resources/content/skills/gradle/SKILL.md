---
name: gradle
description: >-
  Use when orienting in a Gradle build or explaining what Gradle does —
  the three build phases (initialization → configuration → execution),
  the `Gradle`/`Settings`/`Project` object model, `settings.gradle.kts`
  vs `build.gradle.kts` vs `gradle.properties`, the Gradle wrapper
  (`gradlew`, `gradle/wrapper/gradle-wrapper.properties`), CLI
  invocation (`-q`, `-i`, `-d`, `-P`, `-D`, `--no-daemon`,
  `--configuration-cache`, `--offline`, `--refresh-dependencies`,
  `--rerun-tasks`, `--scan`, `--write-locks`), lifecycle callbacks
  (`beforeSettings`, `settingsEvaluated`, `beforeProject`,
  `afterProject`, `projectsEvaluated`, `buildFinished`), how task
  selection works, and when configuration cache skips configuration.
  Use this skill as the entry point — hand off to `gradle-tasks`,
  `gradle-plugins-basics`, `gradle-custom-plugins`,
  `gradle-providers-properties`, `gradle-dependency-injection`, or
  `gradle-init-scripts` for depth.
---

## When to use this skill

- Explaining "what Gradle does" or why a build behaves the way it does.
- Orienting in an unfamiliar Gradle project.
- Deciding where a piece of logic belongs (init script / settings /
  project / plugin).
- Reading build scan timing output and making sense of the phase split.

## When NOT to use this skill

- Writing custom tasks → `gradle-tasks`.
- Writing a plugin → `gradle-custom-plugins`.
- Declaring lazy properties → `gradle-providers-properties`.
- Injecting Gradle services → `gradle-dependency-injection`.
- Multi-repo workspaces → `gradle-composite-builds`.

## Mental model

Gradle is **not** a script engine. It's a build tool that:

1. Discovers projects (**initialization**).
2. Builds a task graph from your scripts (**configuration**).
3. Runs the selected tasks in topological order (**execution**).

Each phase produces an object you can hook:

| Phase         | Object     | Script                     |
|---------------|------------|----------------------------|
| Initialization | `Settings` | `settings.gradle.kts`      |
| Configuration  | `Project`  | `build.gradle.kts`         |
| (both)         | `Gradle`   | init scripts, callbacks    |

Rules-of-thumb that follow from the phase split:

- Anything in a `build.gradle.kts` top-level runs **once** at
  configuration, for every invocation. Keep it cheap.
- Anything inside a `@TaskAction` or `doLast { }` runs at execution
  time, only if the task is in the graph and not up-to-date.
- `project.property(...)` at execution time breaks the configuration
  cache; capture at configuration time into a `Provider`.

## The three phases in detail

### 1. Initialization

- Gradle reads `settings.gradle.kts` at the build root.
- `include(":moduleA")` / `include(":sub:moduleB")` defines which
  projects exist.
- `pluginManagement { }` decides where to fetch plugins from.
- `dependencyResolutionManagement { }` decides where to fetch
  dependencies from and whether modules may add their own repos
  (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`).
- `includeBuild("build-logic")` wires composite builds.
- `Project` instances exist but their scripts have **not** run yet.

### 2. Configuration

- For each project, Gradle evaluates `build.gradle.kts`.
- `plugins { }`, `dependencies { }`, `tasks.register(...)`, extension
  configuration, `afterEvaluate { }` all run here.
- This phase **builds the task graph**, it does not run tasks.
- With the configuration cache, Gradle skips this phase on subsequent
  runs when inputs haven't changed — the task graph is replayed from
  a serialized snapshot.

### 3. Execution

- Gradle walks the task graph based on the tasks requested on the
  command line (e.g. `./gradlew build test`).
- For each task:
  - Check up-to-date (compare declared inputs/outputs).
  - If cacheable and cache hit, restore outputs from the build cache.
  - Otherwise run `doFirst { }` → `@TaskAction` → `doLast { }`.
- `finalizedBy` tasks run after, even if the finalized task failed.

## Standard file layout

```
my-project/
├── settings.gradle.kts              # phase 1 — what projects exist
├── build.gradle.kts                 # phase 2 — root project config
├── gradle.properties                # properties read by Gradle itself
├── gradlew / gradlew.bat            # the wrapper scripts
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties  # pin Gradle version here
│   └── libs.versions.toml           # version catalog (optional)
├── build-logic/                     # convention plugins (included build)
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── my.conventions.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/main/kotlin/...
```

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "my-project"

include(":app", ":core", ":feature:auth")
```

### `build.gradle.kts` (root)

```kotlin
plugins {
    id("my.conventions")            // from build-logic
}

allprojects {                       // legacy — prefer convention plugins
    group = "com.example"
    version = providers.gradleProperty("version").getOrElse("0.0.0-SNAPSHOT")
}
```

### `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.daemon=true

# arbitrary build properties — read via providers.gradleProperty("VERSION_NAME")
VERSION_NAME=0.0.0-dev
GROUP=com.example
```

`gradle.properties` is read **before** scripts run. Use it for
build-wide switches (JVM args, parallel, caching) and for project
metadata that scripts fetch lazily.

## The wrapper — always commit it

```
gradle/wrapper/gradle-wrapper.properties
```

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- Run `./gradlew wrapper --gradle-version=8.10.2 --distribution-type=bin`
  to upgrade.
- Never rely on a globally-installed `gradle` command.
- `-bin` downloads the binary distribution (no sources/docs) —
  `-all` includes sources and is IDE-friendly.

## CLI essentials

| Flag                             | Effect                                                  |
|----------------------------------|---------------------------------------------------------|
| `-q`                             | Quiet output (errors only).                             |
| `-i` / `--info`                  | Info logging.                                           |
| `-d` / `--debug`                 | Debug logging (very verbose).                           |
| `-s` / `--stacktrace`            | Full stack trace on failure.                            |
| `-S` / `--full-stacktrace`       | Include framework frames.                               |
| `-P<name>=<value>`               | Set a project property (read via `gradleProperty`).     |
| `-D<name>=<value>`               | Set a JVM system property.                              |
| `--no-daemon` / `--daemon`       | Control daemon.                                         |
| `--configuration-cache`          | Enable configuration cache for this run.                |
| `--no-configuration-cache`       | Disable it.                                             |
| `--configuration-cache-problems=warn` | Downgrade CC violations to warnings.               |
| `--parallel` / `--no-parallel`   | Parallel project execution.                             |
| `--offline`                      | Don't hit the network.                                  |
| `--refresh-dependencies`         | Bypass cached module metadata.                          |
| `--rerun-tasks`                  | Ignore up-to-date checks; rerun every task.             |
| `--dry-run` / `-m`               | Print task graph without executing.                     |
| `--scan`                         | Upload a build scan (requires agreement).               |
| `--write-locks`                  | Regenerate dependency locks.                            |
| `--continue`                     | Keep running after a task failure.                      |
| `-t` / `--continuous`            | Re-run on source changes.                               |

Invocation syntax: `./gradlew [flags] [tasks...] [-Pname=value]...`

Task paths are colon-separated: `:app:test`, `:feature:auth:jar`.
Omit the leading colon for root-relative: `app:test`.

## Lifecycle callbacks

Registered on the `gradle` object (init scripts or `Plugin<Settings>`
/ `Plugin<Project>`):

```kotlin
gradle.beforeSettings { settings -> ... }       // before settings evaluates
gradle.settingsEvaluated { settings -> ... }    // after settings.gradle.kts ran
gradle.beforeProject { project -> ... }         // before each project evaluates
gradle.afterProject { project -> ... }          // after each project evaluates
gradle.projectsEvaluated { ... }                 // all projects done
gradle.buildFinished { result -> ... }           // execution over (success or failure)
gradle.taskGraph.whenReady { graph -> ... }      // task graph finalized
```

Most of these are **incompatible with the configuration cache** — the
cache skips the phase where they'd fire. Keep lifecycle work inside
plugins and lazy `Provider` chains instead.

## Task selection

- `./gradlew test` — run every task named `test` in every project
  reachable from the root (executes `:app:test`, `:core:test`, …).
- `./gradlew :app:test` — run only the one in `:app`.
- `./gradlew check` — the standard aggregating lifecycle task (pulls in
  `test`, `lint`, `detekt`, etc.).
- `./gradlew build` — pulls in `check` plus `assemble`.
- `./gradlew tasks` — list tasks for the current project.
- `./gradlew help --task :app:test` — detailed task metadata.
- Multiple tasks run in dependency order, respecting `dependsOn`,
  `mustRunAfter`, `shouldRunAfter`.
- `--exclude-task :app:test` drops a task from the graph.

## Configuration cache in one paragraph

After a successful build with `--configuration-cache` (or
`org.gradle.configuration-cache=true`), Gradle serializes the task
graph and reuses it on the next run, skipping configuration entirely.
For this to work:

- Tasks may not reference `Project`, `Task` instances (other than
  their own), or `Gradle` at execution time.
- Values crossing the configuration/execution boundary must travel
  through `Provider<T>` / `Property<T>` (see `gradle-providers-properties`).
- Shared mutable state must be expressed as a `BuildService` (see
  `gradle-dependency-injection`).
- Script inputs (env vars, system properties, `gradle.properties`) are
  tracked; changes invalidate the cache entry.

## Daemons, parallel, caching

```properties
org.gradle.daemon=true              # keep a JVM warm between runs
org.gradle.parallel=true            # run tasks in unrelated projects in parallel
org.gradle.caching=true             # local build cache (outputs cached under ~/.gradle/caches/)
org.gradle.configuration-cache=true # task graph cache (per settings input hash)
org.gradle.workers.max=8            # worker API parallelism cap
```

Build cache stores task outputs keyed by input hashes; parallel runs
tasks in unrelated projects simultaneously; daemon avoids JVM
startup on every invocation. None of these change build semantics;
all are safe to turn on.

## Where should logic live?

| Question                                   | Answer                              |
|--------------------------------------------|-------------------------------------|
| Apply to every build on this machine?      | Init script (`~/.gradle/init.d/`).  |
| Apply to every project in this build?      | Convention plugin in `build-logic`. |
| Apply only to this project?                | That project's `build.gradle.kts`.  |
| Cross-module dependency wiring?            | `settings.gradle.kts` + plugin.     |
| Shared setup for plugin consumers?         | Binary plugin published to a repo.  |

See `gradle-init-scripts`, `gradle-build-conventions`, and
`gradle-custom-plugins` for each path.

## Anti-patterns

```kotlin
// WRONG — using allprojects to configure a single concern
allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
}
// RIGHT — each module opts in via plugins { id("my.conventions") }

// WRONG — reading Project at execution
tasks.register("foo") {
    doLast { println(project.name) }       // configuration cache violation
}
// RIGHT — capture at configuration time
tasks.register("foo") {
    val name = project.name
    doLast { println(name) }
}

// WRONG — relying on a globally-installed Gradle
gradle build                               // version drift between developers
// RIGHT
./gradlew build

// WRONG — afterEvaluate for ordering
afterEvaluate { someTask.dependsOn(other) } // fragile; use providers/task refs

// WRONG — mutating state in lifecycle callbacks
gradle.buildFinished { writeReport() }     // not CC-compatible
// RIGHT — use a BuildService + BuildEventsListenerRegistry
```

## Pitfalls

| Symptom                                            | Cause / fix                                                      |
|----------------------------------------------------|------------------------------------------------------------------|
| Build succeeds locally, fails in CI                | Mismatched Gradle version — always pin the wrapper.              |
| `Could not get unknown property 'X'`               | `project.property("X")` with no `-PX=` and no `gradle.properties` entry. Use `providers.gradleProperty("X").orNull`. |
| Massive configuration times                        | Eager `tasks.create` / heavy work at the top of `build.gradle.kts`. Move into `register { }` blocks. |
| `afterEvaluate` block ran twice                    | Multiple plugins hooked it. Use provider chains instead.         |
| Configuration cache complains about `Project`      | A task action captured `project`. Capture only primitives and `Provider`s. |
| `./gradlew clean build` is slower than `build` alone | `clean` defeats every up-to-date check. Only clean when needed. |
| `--offline` fails even though dependency is cached | First resolution of that version hasn't happened yet. Run once online. |
| Settings changes not picked up                     | Gradle daemon holds old settings. Run `./gradlew --stop` or touch `settings.gradle.kts`. |

## Reference points

- User manual — https://docs.gradle.org/current/userguide/userguide.html
- Build lifecycle — https://docs.gradle.org/current/userguide/build_lifecycle.html
- Kotlin DSL primer — https://docs.gradle.org/current/userguide/kotlin_dsl.html
- Command-line reference — https://docs.gradle.org/current/userguide/command_line_interface.html
- Wrapper — https://docs.gradle.org/current/userguide/gradle_wrapper.html
- Configuration cache — https://docs.gradle.org/current/userguide/configuration_cache.html
- Build cache — https://docs.gradle.org/current/userguide/build_cache.html
- Daemon — https://docs.gradle.org/current/userguide/gradle_daemon.html
- Plugin reference — https://docs.gradle.org/current/userguide/plugin_reference.html
- Source — https://github.com/gradle/gradle
