---
name: gradle-dependency-injection
description: Gradle service injection — @Inject, ObjectFactory, ExecOperations, WorkerExecutor, BuildService, configuration-cache-safe patterns.
---

# Gradle Service Injection

Gradle has a built-in DI container. Tasks, plugins, extensions, build services, and value sources all receive services via `@Inject`. This replaces the old pattern of pulling things off `Project` at execution time — critical for configuration-cache compatibility.

Source:
- https://docs.gradle.org/current/userguide/service_injection.html
- https://docs.gradle.org/current/userguide/build_services.html
- https://docs.gradle.org/current/userguide/configuration_cache.html

## 1. The `@Inject` rule

On an abstract class (task, extension, service), Gradle constructs an instance and injects:

```kotlin
abstract class MyTask @Inject constructor(
    private val exec: ExecOperations,
    private val fs: FileSystemOperations
) : DefaultTask() { ... }
```

Or via an abstract getter (no constructor needed):

```kotlin
abstract class MyTask : DefaultTask() {
    @get:Inject abstract val exec: ExecOperations
    @get:Inject abstract val fs: FileSystemOperations
}
```

The getter form is nicer for Kotlin — no constructor boilerplate, and the abstract keyword tells Gradle to synthesise the implementation.

## 2. Injectable service catalog

| Service                        | Role                                                      | Inject into                          |
|--------------------------------|-----------------------------------------------------------|---------------------------------------|
| `ObjectFactory`                | Create `Property`, `ListProperty`, `FileCollection` etc.  | Plugin, Task, Extension, Service      |
| `ProjectLayout`                | `projectDirectory`, `buildDirectory`, `settingsDirectory` | Plugin, Task                          |
| `ProviderFactory`              | `gradleProperty`, `environmentVariable`, `fileContents`, `exec` | Plugin, Task, Extension, Service |
| `ExecOperations`               | `exec { ... }`, `javaexec { ... }` (CC-safe)              | Task, Service                         |
| `FileSystemOperations`         | `copy`, `sync`, `delete` (CC-safe)                        | Task, Service                         |
| `ArchiveOperations`            | `zipTree`, `tarTree` readers                              | Task, Service                         |
| `WorkerExecutor`               | Parallel work submission                                  | Task                                  |
| `BuildServiceRegistry`         | Register/access shared services                           | Plugin                                |
| `BuildEventsListenerRegistry`  | Subscribe to build-finished events                        | Plugin                                |
| `ToolingModelBuilderRegistry`  | Register IDE tooling models                               | Plugin                                |
| `ProblemReporter` (incubating) | Report structured problems                                | Plugin, Task                          |
| `FileResolver`                 | Resolve arbitrary `Any` to files (rare)                   | Task, Service                         |

Scope table:

| Inject target    | ObjectFactory | ProjectLayout | ProviderFactory | ExecOperations | FileSystemOperations | WorkerExecutor | BuildServiceRegistry |
|------------------|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| `Plugin<Project>` | ✓ | ✓ | ✓ |   |   |   | ✓ |
| `Plugin<Settings>` | ✓ |   | ✓ |   |   |   | ✓ |
| `Task`            | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |   |
| `Extension`       | ✓ |   | ✓ |   |   |   |   |
| `BuildService`    | ✓ |   | ✓ | ✓ | ✓ |   |   |
| `ValueSource`     |   |   | ✓ | ✓ |   |   |   |

## 3. Why this matters (configuration cache)

The old way:

```kotlin
// BAD — breaks configuration cache
tasks.register("run") {
    doLast {
        project.exec { commandLine("echo", "hi") }      // captures Project
    }
}
```

The new way:

```kotlin
abstract class Run : DefaultTask() {
    @get:Inject abstract val exec: ExecOperations
    @TaskAction fun run() {
        exec.exec { commandLine("echo", "hi") }
    }
}
tasks.register<Run>("run")
```

Same effect, but no `Project` reference survives to execution time. The configuration cache can serialise the task and reload it without re-running configuration.

## 4. `ExecOperations` / `FileSystemOperations`

Replacements for `project.exec`, `project.javaexec`, `project.copy`, `project.sync`, `project.delete`:

```kotlin
abstract class Package : DefaultTask() {
    @get:Inject abstract val fs: FileSystemOperations
    @get:Inject abstract val exec: ExecOperations

    @get:InputDirectory abstract val input: DirectoryProperty
    @get:OutputDirectory abstract val staging: DirectoryProperty

    @TaskAction
    fun run() {
        fs.copy {
            from(input)
            into(staging)
            exclude("**/*.log")
        }
        exec.exec {
            workingDir(staging.get().asFile)
            commandLine("tar", "czf", "out.tgz", ".")
        }
    }
}
```

`ExecOperations.exec` returns an `ExecResult` — capture its `exitValue` to act on failure.

## 5. `ArchiveOperations`

Read-only view of archives — e.g. walk a jar's entries during a task:

```kotlin
abstract class Inspect : DefaultTask() {
    @get:Inject abstract val archives: ArchiveOperations
    @get:InputFile abstract val jar: RegularFileProperty

    @TaskAction
    fun run() {
        archives.zipTree(jar).visit { println(relativePath) }
    }
}
```

## 6. `WorkerExecutor`

Parallelise inside a task — see `/gradle-tasks` §13.

## 7. `BuildService` — shared across tasks

A `BuildService` is a singleton kept alive for the build. Use cases:
- Connection pool to a test database.
- An external tool process (browser, emulator).
- Throttling / semaphore shared across parallel tasks.

```kotlin
abstract class WebDriverService :
    BuildService<WebDriverService.Params>,
    AutoCloseable {

    interface Params : BuildServiceParameters {
        val version: Property<String>
    }

    private val driver: WebDriver = startDriver(parameters.version.get())

    fun navigate(url: String) { driver.get(url) }

    override fun close() = driver.quit()
}
```

Register once (usually in a plugin):

```kotlin
val driver = project.gradle.sharedServices.registerIfAbsent(
    "webDriver", WebDriverService::class.java
) {
    parameters.version.set("121.0")
    maxParallelUsages.set(1)              // single-threaded access
}
```

Use from a task:

```kotlin
abstract class UiTest : DefaultTask() {
    @get:Internal abstract val driver: Property<WebDriverService>

    @TaskAction fun run() { driver.get().navigate("https://example.com") }
}

tasks.register<UiTest>("uiTest") {
    driver.set(driverProvider)
    usesService(driverProvider)            // declares dependency — CC + parallelism safety
}
```

Rules:
- `registerIfAbsent` returns a `Provider<T>` — never the service directly.
- `maxParallelUsages` caps concurrency; default is unbounded.
- `usesService(...)` on the task is required for CC; without it Gradle can't tell which tasks depend on which services.
- `AutoCloseable.close()` is called when the build finishes.
- Parameters are CC-serialised — use lazy `Property`/`ListProperty`.

## 8. `BuildEventsListenerRegistry`

Subscribe to task completion events — the CC-safe replacement for `gradle.buildFinished { }`:

```kotlin
abstract class TelemetryService :
    BuildService<BuildServiceParameters.None>,
    OperationCompletionListener,
    AutoCloseable {

    override fun onFinish(event: FinishEvent) {
        if (event is TaskFinishEvent) { /* record */ }
    }

    override fun close() { /* flush */ }
}

class TelemetryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val listeners = target.gradle.sharedServices
        val events = target.serviceOf<BuildEventsListenerRegistry>()
        val svc = listeners.registerIfAbsent("telemetry", TelemetryService::class.java) {}
        events.onTaskCompletion(svc)
    }
}
```

`project.serviceOf<T>()` is the Kotlin-DSL accessor for pulling a Gradle-internal service — here we need the listener registry.

## 9. `ValueSource` — external values with tracked sources

A `ValueSource<T, P>` computes a value outside the build and declares it as a tracked source for CC.

```kotlin
abstract class GitShaSource : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject abstract val exec: ExecOperations

    override fun obtain(): String {
        val out = java.io.ByteArrayOutputStream()
        exec.exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = out
        }
        return out.toString().trim()
    }
}

val sha: Provider<String> = providers.of(GitShaSource::class) {}
```

Why this instead of `Runtime.exec`? Gradle tracks the value across builds; on CC hit, it re-executes `obtain()` and compares — if the value is the same, the cache is still valid.

## 10. Plugin-side: access services from `Plugin<Project>`

```kotlin
class MyPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val objects = target.objects                   // ObjectFactory
        val providers = target.providers               // ProviderFactory
        val layout = target.layout                     // ProjectLayout
        val registry = target.gradle.sharedServices    // BuildServiceRegistry
        // ...
    }
}
```

Or explicit injection (useful inside a nested class):

```kotlin
abstract class MyPlugin @Inject constructor(
    private val objects: ObjectFactory
) : Plugin<Project> { ... }
```

## 11. Anti-patterns

- Accessing `project.exec`, `project.copy`, `project.file` from inside `@TaskAction` — breaks CC. Inject the operations service.
- `@Inject` on a field (`@field:Inject`) — wrong target. Use getter (`@get:Inject`) or constructor.
- Storing a `Project` reference in a task field — breaks CC.
- Using `System.getenv` / `System.getProperty` in task code — invisible to CC. Use `providers.environmentVariable` / `systemProperty`.
- Calling `gradle.buildFinished { }` — legacy and CC-incompatible. Use `BuildEventsListenerRegistry` + a `BuildService`.
- Forgetting `usesService(...)` on tasks that consume a build service — works until CC is enabled, then fails.
- Long-lived mutable state on a `BuildService` without `maxParallelUsages` — race conditions under parallel execution.
- `BuildService` parameters with non-serialisable types — CC fails with a clear error at write time.

## 12. Quick reference

```kotlin
// Inject into a task
abstract class T : DefaultTask() {
    @get:Inject abstract val exec: ExecOperations
    @get:Inject abstract val fs: FileSystemOperations
    @get:Inject abstract val workers: WorkerExecutor
    @get:Inject abstract val objects: ObjectFactory
    @get:Inject abstract val providers: ProviderFactory
    @get:Inject abstract val layout: ProjectLayout
}

// Register a build service
val svc = project.gradle.sharedServices.registerIfAbsent(
    "name", MyService::class.java
) {
    parameters.x.set(...)
    maxParallelUsages.set(1)
}

// Use in a task
tasks.register<T>("t") {
    service.set(svc)
    usesService(svc)
}
```

## 13. Sub-skill cross-reference

- Lazy values (`Property`, `ProviderFactory`) → `/gradle-providers-properties`
- Task API, `@TaskAction`, `WorkerExecutor` usage → `/gradle-tasks`
- Writing a plugin that registers a `BuildService` → `/gradle-custom-plugins`
