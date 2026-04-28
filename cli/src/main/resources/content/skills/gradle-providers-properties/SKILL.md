---
name: gradle-providers-properties
description: Gradle lazy API — Provider, Property, ListProperty, file properties, conventions, finalization, providers from external sources.
---

# Gradle Providers & Properties — Lazy Configuration

Gradle's lazy API is how modern plugins wire values. `Provider<T>` is the read-only view; `Property<T>` is the writable cell. Everything (task inputs, extension fields, plugin outputs) flows through these so Gradle can delay evaluation, enforce input declarations, and serialise the configuration cache.

Source:
- https://docs.gradle.org/current/userguide/lazy_configuration.html
- https://docs.gradle.org/current/userguide/properties_providers.html

## 1. Mental model

Eager assignment:
```kotlin
val x = tasks.named("jar").get().archiveFile.get().asFile    // forces both realisations NOW
```

Lazy wiring:
```kotlin
val x: Provider<RegularFile> = tasks.named<Jar>("jar").flatMap { it.archiveFile }
```

The second form never realises `jar` at configuration time. It resolves on demand, during execution, and plays well with the configuration cache.

Rule of thumb: if you're writing `.get()` during configuration, you're doing it wrong unless you're writing a test assertion.

## 2. Type hierarchy

| Type                         | Read? | Write? | Backing           |
|------------------------------|-------|--------|-------------------|
| `Provider<T>`                | yes   | no     | any computation   |
| `Property<T>`                | yes   | yes    | single value      |
| `ListProperty<T>`            | yes   | yes    | ordered list      |
| `SetProperty<T>`             | yes   | yes    | unordered set     |
| `MapProperty<K,V>`           | yes   | yes    | keyed map         |
| `RegularFileProperty`        | yes   | yes    | `RegularFile`     |
| `DirectoryProperty`          | yes   | yes    | `Directory`       |
| `ConfigurableFileCollection` | yes   | yes    | file collection   |

All extend `Provider<T>` (or `Provider<Collection<T>>`) — a `Property<String>` IS a `Provider<String>`.

## 3. `Provider<T>` methods

```kotlin
val p: Provider<String> = someProperty
p.get()                             // evaluate now; throws if not present
p.getOrNull()                       // null if absent
p.getOrElse("fallback")             // concrete fallback
p.isPresent                         // has a value?
p.orElse("fallback")                // Provider<String> with fallback
p.orElse(otherProvider)             // chain providers
p.map { it.uppercase() }            // Provider<String>
p.flatMap { loadProvider(it) }      // Provider<U>, flatten nested provider
p.zip(otherProvider) { a, b -> "$a/$b" }   // combine two
```

`map`/`flatMap`/`zip` return new providers — nothing is computed until something calls `get()`.

## 4. `Property<T>` additions

```kotlin
val value: Property<String> = objects.property(String::class.java)

value.set("hello")                  // set a T
value.set(otherProvider)            // wire from another provider
value.convention("default")         // default if no explicit set
value.convention(otherProvider)     // lazy default
value.unset()                       // drop current value (rarely useful)
value.finalizeValue()               // freeze now; later set() → error
value.finalizeValueOnRead()         // freeze on first get()
value.disallowChanges()             // forbid further set(); value stays mutable via wiring
```

Ordering matters:
- `set(...)` overrides `convention(...)`.
- `convention(...)` after `set(...)` is ignored — the explicit set wins.
- This asymmetry is what lets a plugin supply a default that a user can override without the plugin clobbering back.

## 5. Collection properties

```kotlin
abstract val flags: ListProperty<String>

flags.set(listOf("-x"))
flags.add("-y")                     // literal
flags.addAll(someProvider)          // wire from provider
flags.empty()                       // clear
flags.convention(listOf())          // default empty

flags.get()                         // List<String>
```

`MapProperty<K,V>`:

```kotlin
abstract val env: MapProperty<String, String>
env.put("FOO", "bar")
env.put("VER", versionProvider)     // key literal, value from provider
env.putAll(mapOf("A" to "1"))
env.putAll(providerOfMap)
```

## 6. File properties

```kotlin
abstract val input: RegularFileProperty
abstract val outDir: DirectoryProperty
abstract val sources: ConfigurableFileCollection
```

Setting:
```kotlin
input.set(file("src/foo.txt"))                      // File
input.set(layout.projectDirectory.file("src/foo.txt"))
outDir.set(layout.buildDirectory.dir("generated"))
sources.from(fileTree("src"), anotherTask.map { it.outputs.files })
```

Deriving:
```kotlin
val outputFile = outDir.file("report.html")         // Provider<RegularFile>
val outputDir = outDir.dir("html")                  // Provider<Directory>
val outputAsFile: Provider<File> = outputFile.map { it.asFile }
```

`ConfigurableFileCollection.from(...)` accepts: `File`, `Path`, `String`, `FileCollection`, `Task` / `TaskProvider` (uses its outputs), or a `Provider<...>` of any of those. Order matters for `@Classpath`.

## 7. `ObjectFactory`

Creates managed instances. Inject via `@Inject`:

```kotlin
abstract class MyExtension @Inject constructor(objects: ObjectFactory) {
    val tag: Property<String> = objects.property(String::class.java)
    val files: ConfigurableFileCollection = objects.fileCollection()
    val items: ListProperty<String> = objects.listProperty(String::class.java)
    val env: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)
    val dir: DirectoryProperty = objects.directoryProperty()
    val file: RegularFileProperty = objects.fileProperty()
}
```

For an `abstract class` with `abstract val`, Gradle injects these automatically — `ObjectFactory` is only needed when you want to call `newInstance(...)` to compose nested types.

## 8. `ProviderFactory` — providers from external sources

Inject `@Inject ProviderFactory` or access via `project.providers`.

```kotlin
val prop: Provider<String> = providers.gradleProperty("release")      // -Prelease=...
val env: Provider<String> = providers.environmentVariable("CI")
val sys: Provider<String> = providers.systemProperty("user.home")

val version = providers.fileContents(
    layout.projectDirectory.file("VERSION")
).asText.map { it.trim() }

val gitSha = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }
```

These are configuration-cache-safe: Gradle serialises the source (the env var name, the file path) and re-reads on cache hit.

Do NOT do this:
```kotlin
val bad = System.getenv("CI")      // evaluated once at configuration time; invisible to CC
```

## 9. `layout` — project/build paths

```kotlin
layout.projectDirectory                              // DirectoryProperty-like
layout.buildDirectory                                // DirectoryProperty (usually $project/build)

layout.buildDirectory.file("reports/out.xml")        // Provider<RegularFile>
layout.buildDirectory.dir("generated/src")           // Provider<Directory>
layout.settingsDirectory                             // Settings-level (new)
```

Always prefer `layout.buildDirectory.dir(...)` over `file("$buildDir/...")` — the latter uses the deprecated `buildDir` getter and breaks custom build directories.

## 10. Wiring across tasks

```kotlin
val generate = tasks.register<GenerateSources>("gen") {
    output.set(layout.buildDirectory.dir("gen"))
}

val compile = tasks.named<JavaCompile>("compileJava") {
    source(generate.flatMap { it.output })           // lazy, also implies dependsOn(gen)
}
```

When a task's input is a provider derived from another task's output, Gradle infers `dependsOn` automatically — no need to declare it.

## 11. `zip` and `map` vs `flatMap`

- `map { T -> U }` — pure transform.
- `flatMap { T -> Provider<U> }` — flatten; use when the transform itself produces a provider.
- `zip(Provider<U>) { t, u -> ... }` — combine two providers.

```kotlin
val pair: Provider<String> = version.zip(gitSha) { v, sha -> "$v+$sha" }
```

Chains compose — you can build complex pipelines without triggering configuration.

## 12. Finalisation and safety

```kotlin
abstract val value: Property<String>

init {
    // plugin sets a convention
    value.convention(providers.gradleProperty("my.value").orElse("default"))
    value.finalizeValueOnRead()     // user can set in build script; frozen when first read
}
```

Finalisation is how a plugin author says "after I read this, you can't change it." This is what Gradle's own task properties use internally — once a task starts executing, its inputs are frozen.

| Method                     | Effect                                                    |
|----------------------------|-----------------------------------------------------------|
| `finalizeValue()`          | Freezes now.                                              |
| `finalizeValueOnRead()`    | Freezes on next `get()`.                                  |
| `disallowChanges()`        | `set(...)` fails from now on; provider wiring preserved.  |

## 13. Configuration-cache implications

The CC serialises task state between builds. All `Property`/`Provider` values must be:
- Serialisable (primitives, strings, `File`, other providers).
- Computed from tracked sources (use `providers.gradleProperty`, not `System.getenv`).
- Free of `Project` references at execution time.

Things that break CC:
- `task.project.property("x")` in `doLast` → use `@Input` + a providers chain.
- Capturing `project` in a lambda stored on a task.
- `providers.exec` without declaring it as an input if its output changes — Gradle tracks the provider source, not the command result, unless you write the result to an input.

## 14. Kotlin DSL sugar

```kotlin
extension.value = "x"           // setter mapped to value.set("x")
extension.list = listOf("a")    // list property setter
extension.outDir = layout.buildDirectory.dir("x").get()

// equivalent explicit
extension.value.set("x")
extension.list.set(listOf("a"))
extension.outDir.set(layout.buildDirectory.dir("x"))
```

The assignment form works when the Kotlin DSL accessor exposes `value` as `var` — which abstract `Property<T>` does when the plugin defines it that way. For plain extension fields it's just a var.

## 15. Anti-patterns

- `.get()` at configuration time — defeats laziness; use `.map`/`.flatMap` and wire to a task input.
- `Property<File>` — use `RegularFileProperty`.
- Setting a property from `System.getenv` or `System.getProperty` — invisible to CC; use `providers.environmentVariable` / `systemProperty`.
- Storing values in a `val` field and using them in a task action — breaks CC; wrap in a `Provider` and declare as `@Input`.
- Passing a `ListProperty<File>` — use `ConfigurableFileCollection` for files.
- `convention(...)` then `set(...)` in the same plugin code — you're just overwriting; drop the convention.
- Reading `buildDir` / `project.buildDir` — deprecated; use `layout.buildDirectory`.

## 16. Cheat sheet

```kotlin
// Most common provider chain
val v: Provider<String> = providers.gradleProperty("release")
    .orElse(providers.environmentVariable("RELEASE"))
    .orElse("snapshot")

// Wire to a task input
tasks.register<MyTask>("t") {
    version.set(v)
    output.set(layout.buildDirectory.file("t.txt"))
}

// Combine two providers
val tag = version.zip(gitSha) { ver, sha -> "$ver-$sha" }
```

## 17. Sub-skill cross-reference

- Task input/output annotations → `/gradle-tasks`
- Plugin extensions using Property → `/gradle-custom-plugins`
- Services injected via `@Inject` → `/gradle-dependency-injection`
