---
name: gradle-custom-plugins
description: Writing Gradle plugins — Plugin<Project>/Settings/Gradle, extensions, precompiled script plugins, build-logic, TestKit.
---

# Writing Custom Gradle Plugins

Three flavours, in increasing power:

1. **Script plugin** — a `.gradle.kts` file applied via `apply(from = ...)`. Legacy.
2. **Precompiled script plugin** — a `.gradle.kts` in `buildSrc/src/main/kotlin` or `build-logic/<module>/src/main/kotlin`. Compiled once, reused, type-safe accessors.
3. **Binary plugin** — a Kotlin/Java class implementing `Plugin<T>`, built as a jar, published or shared via composite. The full feature set.

Source:
- https://docs.gradle.org/current/userguide/custom_plugins.html
- https://docs.gradle.org/current/userguide/implementing_gradle_plugins.html
- https://docs.gradle.org/current/userguide/testing_gradle_plugins.html

## 1. Where plugin code lives

| Location                              | When to use                                  |
|---------------------------------------|-----------------------------------------------|
| `buildSrc/`                           | Simple, single project, no reuse across repos |
| `build-logic/` as an included build   | Multi-module or shared conventions            |
| Standalone repo published as jar      | Reuse across multiple projects                |

`buildSrc` implicitly applies to the build — no `includeBuild` needed — but its classpath is shared with everything. A composite `build-logic` is cleaner: lazier, parallelisable, and swappable.

```kotlin
// settings.gradle.kts (root)
pluginManagement {
    includeBuild("build-logic")
}
```

Inside `build-logic/settings.gradle.kts`:

```kotlin
rootProject.name = "build-logic"
include("kotlin-jvm", "publishing", "testing")
```

Each module is a Gradle plugin project — see §3.

## 2. `Plugin<T>` interface

```kotlin
class MyPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply("java-library")
        target.extensions.create<MyExtension>("my")
        target.tasks.register<MyTask>("myTask") {
            config.set(target.extensions.getByType<MyExtension>().value)
        }
    }
}
```

Three target types:

| Interface           | `apply(T)` param | Applied from                          | Purpose                                |
|---------------------|------------------|----------------------------------------|-----------------------------------------|
| `Plugin<Project>`   | `Project`        | `build.gradle.kts` `plugins { }`      | 99% of plugins                         |
| `Plugin<Settings>`  | `Settings`       | `settings.gradle.kts` `plugins { }`   | Toolchains, dep management, repos      |
| `Plugin<Gradle>`    | `Gradle`         | Init scripts                          | Global hooks; see `/gradle-init-scripts` |

## 3. Binary plugin structure

```
build-logic/kotlin-jvm/
├── build.gradle.kts
└── src/main/kotlin/
    └── example/KotlinJvmConventions.kt
```

```kotlin
// build-logic/kotlin-jvm/build.gradle.kts
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "example.kotlin-jvm"
            implementationClass = "example.KotlinJvmConventions"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.0.21")
}
```

`java-gradle-plugin` auto-registers the plugin marker, wires a TestKit dependency, and validates annotations. `kotlin-dsl` makes Kotlin-DSL idioms work inside the plugin.

Notes:
- Plugin markers: `java-gradle-plugin` generates a no-op pom named `<id>.gradle.plugin` so downstream builds can resolve by ID.
- `implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:<v>")` is the plugin marker coordinate; depending on it pulls the real plugin jar.

## 4. Precompiled script plugins

A `.gradle.kts` under `src/main/kotlin` becomes a plugin whose ID matches the file name (with optional package).

```kotlin
// build-logic/kotlin-jvm/src/main/kotlin/example.kotlin-jvm.gradle.kts
plugins {
    kotlin("jvm")
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

Apply as `id("example.kotlin-jvm")`. The file name `example.kotlin-jvm.gradle.kts` compiles to a plugin whose ID is the file name minus `.gradle.kts`.

Package directories count: `src/main/kotlin/example/foo.gradle.kts` → ID `example.foo`.

Pros: type-safe accessors, concise. Cons: no direct access to `Plugin<T>` lifecycle — for that, use a binary plugin.

## 5. Extensions

Extensions are the plugin's public API. `extensions.create` registers a typed extension bean.

```kotlin
abstract class MyExtension {
    abstract val value: Property<String>
    abstract val sources: ConfigurableFileCollection
    abstract val nested: NamedDomainObjectContainer<Flavour>
}

abstract class Flavour(val name: String) {
    abstract val enabled: Property<Boolean>
}

class MyPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val ext = target.extensions.create<MyExtension>("my").apply {
            value.convention("default")
        }
        val flavours = target.objects.domainObjectContainer(Flavour::class.java)
        target.extensions.add("flavours", flavours)
    }
}
```

Rules:
- Declare the extension as an `abstract class` — Gradle synthesises a subclass with injected `Property`/`ListProperty`/`ConfigurableFileCollection` backing.
- Prefer `Property<T>` over plain fields: supports lazy wiring, configuration cache, input declarations.
- Set defaults with `convention(...)`, not assignment — a user's `set(...)` overrides a convention, so the plugin never clobbers user intent.

Using it:

```kotlin
my {
    value.set("prod")
    sources.from("src/special")
}
```

### Nested DSL blocks

```kotlin
abstract class ReportsExtension @Inject constructor(objects: ObjectFactory) {
    val html: ReportConfig = objects.newInstance(ReportConfig::class.java, "html")
    fun html(action: Action<ReportConfig>) = action.execute(html)
}
```

Or a container for flavour-style configs:

```kotlin
flavours {
    create("debug") { enabled.set(true) }
    create("release") { enabled.set(false) }
}
```

## 6. Registering tasks from a plugin

```kotlin
class MyPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val ext = target.extensions.create<MyExtension>("my")
        target.tasks.register<MyTask>("check") {
            group = "verification"
            value.set(ext.value)                          // lazy wiring
            output.set(target.layout.buildDirectory.file("my/check.txt"))
        }
    }
}
```

Hooks onto other plugins:

```kotlin
target.plugins.withType<JavaPlugin> {
    target.tasks.named("check") { dependsOn("myTask") }
}

// or by ID
target.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    // Kotlin plugin is applied — safe to configure its extension
}
```

`withType`/`withPlugin` are reactive: if the JavaPlugin is applied later, the block still runs.

## 7. Settings plugins

```kotlin
class DevelocitySettings : Plugin<Settings> {
    override fun apply(target: Settings) {
        target.pluginManager.apply("com.gradle.develocity")
        target.extensions.configure<DevelocityConfiguration> {
            server.set("https://ge.example.com")
            buildScan {
                publishing.onlyIf { false }     // internal only
            }
        }
    }
}
```

Registration in `gradlePlugin { plugins { register("settings") { id = "..."; implementationClass = "..." } } }` works the same as project plugins; it's the `Plugin<Settings>` interface that routes it.

## 8. Testing plugins

### Unit test with `ProjectBuilder`

Fastest, no daemon, but no real execution.

```kotlin
class MyPluginTest {
    @Test
    fun `registers myTask`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("example.my")
        assertNotNull(project.tasks.findByName("myTask"))
    }
}
```

Use for: task registration, extension wiring, exception on misuse.

### Functional test with TestKit

Runs a real Gradle build in an isolated test directory.

```kotlin
class MyPluginFunctionalTest {
    @TempDir lateinit var dir: File

    @Test
    fun `runs myTask`() {
        dir.resolve("settings.gradle.kts").writeText("""rootProject.name = "t"""")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("example.my") }
            my { value.set("prod") }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments("myTask", "--stacktrace")
            .build()

        assertTrue(result.output.contains("prod"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":myTask")?.outcome)
    }
}
```

`withPluginClasspath()` requires the `java-gradle-plugin` plugin in the plugin project — it injects the plugin under test.

Matrix-test against supported Gradle versions by parameterising `.withGradleVersion(...)`.

## 9. Validation

```bash
./gradlew :build-logic:<module>:validatePlugins
```

Fails on:
- Missing `@Input`/`@Output` on task properties
- `@Input` on a `File` type
- Missing `@PathSensitive` on a cacheable input
- Setter on a `Property<T>` (should be abstract val)

Add `tasks.validatePlugins { enableStricterValidation = true }` to promote warnings to errors.

## 10. Publishing a plugin

```kotlin
plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.3.0"       // for Plugin Portal
}

gradlePlugin {
    website.set("https://github.com/example/my-plugin")
    vcsUrl.set("https://github.com/example/my-plugin.git")
    plugins {
        register("my") {
            id = "example.my"
            displayName = "My Plugin"
            description = "Does things"
            tags.set(listOf("kotlin", "convention"))
            implementationClass = "example.MyPlugin"
        }
    }
}
```

`./gradlew publishPlugins` uploads to the Gradle Plugin Portal (needs credentials in `~/.gradle/gradle.properties`).

## 11. Anti-patterns

- `Plugin<Project>` with business logic directly in `apply` — split into task classes + extension. `apply` should wire, not do.
- Mutable `var` fields in extensions — prevents lazy wiring. Use abstract `Property<T>`.
- `project.afterEvaluate { ... }` — a smell. Usually `pluginManager.withPlugin(...)` or `tasks.withType(...).configureEach { }` is cleaner and lazy.
- Accessing another plugin's extension eagerly — `extensions.getByType(...)` forces creation. Use `extensions.configure<FooExt> { }` or the reactive API.
- A plugin that applies other plugins by string in an unguarded way — `plugins.apply("java-library")` is fine, but if the caller already applied `java`, know that plugins are idempotent, and that ordering matters only via `withPlugin`.
- Publishing a plugin without `gradlePlugin { website/vcsUrl/tags }` — rejected by the portal.
- Using `Project` inside a `doLast` of a task your plugin registers — breaks configuration cache. Pass providers in.

## 12. Quick checklist for a new plugin

- [ ] Decide precompiled-script vs binary plugin.
- [ ] Extension with abstract `Property` fields, `convention` defaults.
- [ ] Tasks registered with `tasks.register<T>(...)` (lazy), wired from extension via providers.
- [ ] `pluginManager.withPlugin(...)` for cross-plugin integration.
- [ ] `@PathSensitive(RELATIVE)` on file inputs; `@CacheableTask` where deterministic.
- [ ] Plugin marker via `java-gradle-plugin` + `gradlePlugin { plugins { register(...) } }`.
- [ ] Tests: `ProjectBuilder` for wiring, `GradleRunner` for behaviour.
- [ ] `./gradlew validatePlugins` clean.

## 13. Sub-skill cross-reference

- Applying plugins → `/gradle-plugins-basics`
- Writing tasks inside a plugin → `/gradle-tasks`
- Lazy properties used in extensions → `/gradle-providers-properties`
- Services a plugin may inject → `/gradle-dependency-injection`
- Project-specific convention plugin layout → `/gradle-build-conventions`
- Settings plugin pattern used in this project → `/gradle-settings-plugin`
- Composite includes for `build-logic` → `/gradle-composite-builds`
