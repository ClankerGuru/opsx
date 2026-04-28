---
name: gradle-tasks
description: Gradle Task API — registering, configuring, ordering, custom task classes, inputs/outputs, incremental execution, caching.
---

# Gradle Tasks

Tasks are the unit of work in Gradle. Everything the build does — compiling, testing, zipping, publishing — is a task. A well-written task declares exactly what it reads and what it writes so Gradle can skip it, cache it, or run it in parallel.

Source of truth:
- https://docs.gradle.org/current/userguide/more_about_tasks.html
- https://docs.gradle.org/current/userguide/custom_tasks.html
- https://docs.gradle.org/current/userguide/incremental_build.html
- https://docs.gradle.org/current/userguide/build_cache.html

## 1. Register vs configure

`tasks.register` is lazy — the task is only created if something in the build actually needs it. `tasks.create` is eager and forces creation during configuration.

```kotlin
// Good — lazy
val hello = tasks.register("hello") {
    group = "greeting"
    description = "Say hi"
    doLast { println("hi") }
}

// Bad — eager, configures even if never run
tasks.create("hello") { ... }
```

Registration returns a `TaskProvider<T>`. Use it instead of a concrete task reference — it preserves laziness.

```kotlin
val jar = tasks.named<Jar>("jar")           // TaskProvider<Jar>
val archive = jar.flatMap { it.archiveFile } // Provider<RegularFile>
```

Configuration on a provider runs only when the task is realized:

```kotlin
tasks.named("compileKotlin") { dependsOn(hello) }   // configures lazily
```

Use `tasks.withType<T>().configureEach { ... }` to configure every task of a type without triggering creation:

```kotlin
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxParallelForks = 4
}
```

## 2. Ad-hoc tasks vs typed tasks

Three flavours, listed by increasing reusability:

**Ad-hoc action**
```kotlin
tasks.register("clean-cache") {
    doLast { delete("build/cache") }
}
```

**Typed task from a plugin**
```kotlin
tasks.register<Copy>("copyReports") {
    from(layout.buildDirectory.dir("reports"))
    into(layout.projectDirectory.dir("published"))
}
```

**Custom task class** — see §6.

## 3. Task metadata

```kotlin
tasks.register("release") {
    group = "publishing"                 // shows under this header in `./gradlew tasks`
    description = "Cut a release"
    notCompatibleWithConfigurationCache("uses Git in configure block")
}
```

`group = null` hides the task from the default listing; it still shows under `./gradlew tasks --all`.

## 4. Ordering and dependencies

| Relation          | Meaning                                                                 |
|-------------------|--------------------------------------------------------------------------|
| `dependsOn(x)`    | `x` must run before this task, AND running this task implies running `x`|
| `mustRunAfter(x)` | If both are scheduled, `x` runs first. Does NOT cause `x` to run.        |
| `shouldRunAfter(x)` | Like `mustRunAfter` but can be violated to break cycles.               |
| `finalizedBy(x)`  | `x` runs after this task even if this task fails.                        |

```kotlin
tasks.named("test") {
    finalizedBy("jacocoTestReport")   // always produce coverage
}

tasks.named("integrationTest") {
    mustRunAfter("test")              // but don't imply running test
}
```

`finalizedBy` is the right tool for cleanup tasks (stop docker, release locks) — they fire even when the main task fails.

## 5. Conditional execution

```kotlin
tasks.register("deploy") {
    onlyIf("only on main") { gitBranch() == "main" }
    doLast { /* ... */ }
}
```

`onlyIf` can be stacked; all predicates must be true. The `reason` overload (Gradle 7.6+) is printed on skip.

`enabled = false` disables the task entirely. Prefer `onlyIf` — it runs at execution time and plays well with the configuration cache.

## 6. Custom task classes

A custom task is an `abstract class` extending `DefaultTask` (or a typed parent like `SourceTask`, `Copy`, `Jar`). Abstract properties let Gradle inject a managed `Property`/`ListProperty`/etc — no backing field needed.

```kotlin
abstract class Checksum : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: ConfigurableFileCollection

    @get:Input
    abstract val algorithm: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun hash() {
        val md = MessageDigest.getInstance(algorithm.get())
        source.files.sortedBy { it.name }.forEach { f -> md.update(f.readBytes()) }
        output.get().asFile.writeText(md.digest().joinToString("") { "%02x".format(it) })
    }
}

tasks.register<Checksum>("sha") {
    source.from(fileTree("src"))
    algorithm.set("SHA-256")
    output.set(layout.buildDirectory.file("sha.txt"))
}
```

### 6.1 Input annotations

| Annotation             | Use for                                                              |
|------------------------|----------------------------------------------------------------------|
| `@Input`               | Serializable scalars: `String`, numbers, enums, `Boolean`            |
| `@InputFile`           | Single file whose content matters                                    |
| `@InputFiles`          | Collection of files (no order semantics)                             |
| `@InputDirectory`      | A directory whose contents are inputs                                |
| `@Classpath`           | A classpath — order matters, timestamps ignored                      |
| `@CompileClasspath`    | Compile classpath — only ABI-relevant changes invalidate             |
| `@Nested`              | Composite of more input-annotated properties                         |
| `@Optional`            | Input may be unset; do not fail validation                           |
| `@Internal`            | Property is used at runtime but is NOT an input (no cache effect)    |

### 6.2 Output annotations

| Annotation         | Use for                                        |
|--------------------|------------------------------------------------|
| `@OutputFile`      | Single file the task writes                    |
| `@OutputFiles`     | Map of named output files                      |
| `@OutputDirectory` | Single directory the task writes               |
| `@OutputDirectories` | Map of named output directories              |

### 6.3 Path sensitivity

`@PathSensitive(NONE | NAME_ONLY | RELATIVE | ABSOLUTE)` tells Gradle how to normalize file paths for up-to-date checks.

- `NONE` — only content matters (e.g. a bag of resources)
- `NAME_ONLY` — rename invalidates, move within tree does not
- `RELATIVE` — move within the input root invalidates (sensible default for source trees)
- `ABSOLUTE` — moving the project root invalidates. Rarely correct.

Default is `ABSOLUTE` which breaks relocatability of the build cache — almost always wrong. Declare `RELATIVE` on source inputs.

### 6.4 Empty-input handling

```kotlin
@get:SkipWhenEmpty
@get:InputFiles
abstract val sources: ConfigurableFileCollection
```

Task is skipped (`NO-SOURCE`) if the collection is empty after filtering. Pair with `@IgnoreEmptyDirectories` to ignore empty dirs.

## 7. Incremental tasks (`InputChanges`)

An incremental task receives only the files that changed since the last run. This is how `compileJava` avoids recompiling the world.

```kotlin
abstract class Transform : DefaultTask() {

    @get:Incremental
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    abstract val input: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun run(changes: InputChanges) {
        if (!changes.isIncremental) {
            output.get().asFile.deleteRecursively()
        }
        changes.getFileChanges(input).forEach { c ->
            val target = output.file(c.normalizedPath).get().asFile
            when (c.changeType) {
                ChangeType.ADDED, ChangeType.MODIFIED -> c.file.copyTo(target, overwrite = true)
                ChangeType.REMOVED -> target.delete()
            }
        }
    }
}
```

Rules:
- `InputChanges` is injected as an action parameter, never a field.
- A non-incremental run (first time, changed inputs not marked `@Incremental`, cache miss) sets `isIncremental = false` — rebuild everything.
- Only `@Incremental` properties show up in `getFileChanges`; other inputs trigger a full rerun when they change.

## 8. Caching

```kotlin
@CacheableTask
abstract class Transform : DefaultTask() { ... }
```

To be cacheable a task must:
1. Have `@CacheableTask` (or inherit it).
2. Declare at least one output.
3. Declare path sensitivity on all file inputs (`@PathSensitive` — never `ABSOLUTE` for cacheable tasks).
4. Be deterministic.

Opt out explicitly for tasks that cannot be cached:

```kotlin
@DisableCachingByDefault(because = "not worth storing, runs in milliseconds")
abstract class Touch : DefaultTask()
```

`@UntrackedTask` — the stronger version. Gradle does not track outputs at all, no up-to-date check. Use for tasks whose output is external state (deployments, remote calls).

## 9. Action hooks

```kotlin
tasks.named("jar") {
    doFirst { println("about to jar") }
    doLast  { println("jarred") }
}
```

`doFirst`/`doLast` append to the action list. A `@TaskAction` method on a custom class goes in the middle.

Limits:
- Hooks run at execution time, not configuration time.
- With the configuration cache, closures in `doFirst`/`doLast` must not capture the `Project` — use injected services or providers.

## 10. Task rules

Rules create tasks on demand matching a pattern. Used by plugins for task families (`publish<Repo>`, `link<Flavor>`).

```kotlin
tasks.addRule("Pattern: ping<ID>") {
    val name = this
    if (name.startsWith("ping")) {
        tasks.register(name) {
            doLast { println("Pinging ${name.removePrefix("ping")}") }
        }
    }
}
```

Rules are heavyweight — prefer an extension + explicit registration for user configuration.

## 11. Task graph access

```kotlin
gradle.taskGraph.whenReady {
    if (hasTask(":release")) {
        project.version = "${project.version}"             // final version
    }
}
```

`whenReady` fires after the graph is resolved, before execution. Safe for configuration-cache only if registered via a build service — closures that capture `Project` will not re-execute from the cache.

## 12. Finalizers for cleanup

Finalizers fire even on failure — correct for cleanup:

```kotlin
val startDocker = tasks.register<Exec>("startDocker") { ... }
val stopDocker  = tasks.register<Exec>("stopDocker")  { ... }

tasks.register<Test>("integrationTest") {
    dependsOn(startDocker)
    finalizedBy(stopDocker)
}
```

Without `finalizedBy`, a test failure would leak the container.

## 13. Worker API

Parallelise work inside a single task. Three isolation levels:

- `noIsolation()` — same JVM, shared classpath, no reloading. Fastest.
- `classLoaderIsolation()` — new classloader per worker. Use to isolate plugin dependencies.
- `processIsolation()` — forked JVM. Use for tools needing a specific classpath (compiler, annotation processor) or JVM args.

```kotlin
abstract class Thumbnailer : DefaultTask() {
    @get:Inject abstract val workers: WorkerExecutor
    @get:InputFiles abstract val sources: ConfigurableFileCollection
    @get:OutputDirectory abstract val out: DirectoryProperty

    @TaskAction
    fun run() {
        val q = workers.noIsolation()
        sources.forEach { f ->
            q.submit(ThumbAction::class.java) {
                input.set(f)
                target.set(out.file(f.nameWithoutExtension + ".png"))
            }
        }
    }
}

abstract class ThumbAction : WorkAction<ThumbAction.Params> {
    interface Params : WorkParameters {
        val input: RegularFileProperty
        val target: RegularFileProperty
    }
    override fun execute() { /* resize */ }
}
```

Gradle waits for the queue to drain before the task finishes. No explicit `await`.

## 14. Task validation

Gradle validates task classes at configuration time and reports missing annotations. Run:

```bash
./gradlew :build-logic:validatePlugins
```

Treat validation warnings as errors in CI — missing `@PathSensitive` or an output without `@OutputFile` will silently break caching.

## 15. Anti-patterns

- `tasks.create(...)` — eager; use `register`.
- `tasks["foo"]` / `tasks.getByName("foo")` at configuration time — eager resolution, breaks laziness.
- `@Input` on a `File` — use `@InputFile` or `@InputFiles`.
- `@InputFiles` without `@PathSensitive` — defaults to `ABSOLUTE`, breaks cache relocation.
- Mutable `val` fields on a task class — prevents configuration cache. Use abstract `Property<T>`.
- `project.exec`/`project.copy`/`project.file` inside `@TaskAction` — not CC-compatible; use injected `ExecOperations`, `FileSystemOperations`, `ProjectLayout`.
- `doLast { project.something }` — captures `Project`; store what you need in a local val or a build service.
- Configuring a task that you may not need in `afterEvaluate` — usually a `configureEach` on the task type is cleaner and lazy.

## 16. Quick reference

```kotlin
// Register
val t = tasks.register<MyTask>("myTask") { input.set(...) }

// Configure existing (lazy)
tasks.named<Test>("test") { useJUnitPlatform() }

// Configure all of a type (lazy)
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

// Chain outputs as inputs
val zip = tasks.register<Zip>("zip") {
    from(t.flatMap { it.output })    // lazy wiring
}

// Ordering
tasks.named("publish") { dependsOn("build"); mustRunAfter("check") }
```

## 17. Sub-skill cross-reference

- Task inputs/outputs in depth (lazy API) → `/gradle-providers-properties`
- Where to put reusable task classes → `/gradle-custom-plugins`
- Applying & configuring tasks from plugins → `/gradle-plugins-basics`
- Services injected into tasks (`ExecOperations`, `WorkerExecutor`, `BuildService`) → `/gradle-dependency-injection`
