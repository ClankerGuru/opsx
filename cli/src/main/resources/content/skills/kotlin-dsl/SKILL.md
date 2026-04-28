---
name: kotlin-dsl
description: Provides Gradle Kotlin DSL patterns for settings and project plugins, covering extension creation, convention wiring, Provider/Property lazy evaluation, Action callbacks, and configuration avoidance. Activates when writing Gradle plugins or build scripts using Kotlin DSL.
---

# Gradle Kotlin DSL Patterns

> Project-specific Gradle-Kotlin-DSL patterns (`data object` + `SettingsExtension`). For generic Gradle mechanisms see:
> - `/gradle` — lifecycle, Settings/Project model
> - `/gradle-plugins-basics` — `plugins { }`, `pluginManagement`
> - `/gradle-custom-plugins` — `Plugin<Project>`/`Plugin<Settings>`, precompiled script plugins
> - `/gradle-providers-properties` — `Property`, `Provider`, `convention`, `.map`/`.flatMap`
> - `/gradle-tasks` — task registration, ordering, input/output annotations
> - `/gradle-dependency-injection` — `@Inject` on extensions, `ObjectFactory`, `NamedDomainObjectContainer`
> - `/gradle-settings-plugin` — companion pattern for settings-targeted plugins
> - `/kotlin-dsl-builders` — generic lambdas-with-receiver, `@DslMarker`

## Rules

1. **Settings plugins use `Plugin<Settings>`, project plugins use `Plugin<Project>`.** Settings plugins run during settings evaluation and can register extensions on `Settings`, wire `rootProject` callbacks, and include builds. Project plugins run per-project.

2. **Register extensions with `settings.extensions.create()`.** Pass the extension name constant and the class:
   ```kotlin
   val extension = settings.extensions.create(EXTENSION_NAME, SettingsExtension::class.java)
   ```

3. **Wire conventions on extension properties immediately after creation.** Conventions set the default values that the DSL block can override:
   ```kotlin
   extension.outputDir.convention(OUTPUT_DIR)
   extension.autoGenerate.convention(false)
   extension.excludeDepScopes.convention(DEFAULT_EXCLUDED_DEP_SCOPES)
   ```

4. **Use `settings.gradle.rootProject(Action { })` to register tasks.** This callback fires after settings evaluation but gives you access to the root project for task registration:
   ```kotlin
   settings.gradle.rootProject(
       Action { rootProject ->
           registerTasks(rootProject, extension)
       },
   )
   ```

5. **Use `settings.gradle.settingsEvaluated { }` for post-DSL work.** When you need the DSL block to have finished before acting (e.g., including enabled composite builds):
   ```kotlin
   settings.gradle.settingsEvaluated {
       extension.includeEnabled()
   }
   ```

6. **Register tasks with `tasks.register()`, never `tasks.create()`.** Configuration avoidance prevents creating task objects until they are needed:
   ```kotlin
   val contextTask = rootProject.tasks.register(TASK_CONTEXT, ContextTask::class.java)
   ```

7. **Wire extension properties to task properties with `.convention()`.** Inside `configure { }`, connect the extension values to the task's `@Input` properties:
   ```kotlin
   contextTask.configure { task ->
       task.outputDir.convention(extension.outputDir)
       task.excludeDepScopes.convention(extension.excludeDepScopes)
   }
   ```

8. **Use `Provider`/`Property` API for lazy evaluation.** Wrap computed values in `provider { }` so they resolve at execution time:
   ```kotlin
   task.subprojectPaths.set(
       rootProject.provider { rootProject.subprojects.map { it.path } },
   )
   ```

9. **Use `.map { }` on properties for transformations.** Chain providers without eagerly evaluating:
   ```kotlin
   task.projectDeps.set(
       task.excludeDepScopes.map { excludes ->
           rootProject.allprojects.associate { proj ->
               proj.path to SymbolExtractor.extractDependenciesFromProject(proj, excludes)
           }
       },
   )
   ```

10. **Abstract properties are Gradle-managed.** Declare properties as `abstract val` on abstract classes annotated with `@Inject`. Gradle instantiates and wires them:
    ```kotlin
    abstract class SettingsExtension @Inject constructor() {
        abstract val outputDir: Property<String>
        abstract val autoGenerate: Property<Boolean>
        abstract val excludeDepScopes: SetProperty<String>
    }
    ```

11. **Use `Action<T>` for configuration lambdas in plugin APIs.** Gradle's `Action` interface is configuration-cache compatible:
    ```kotlin
    fun repos(action: Action<NamedDomainObjectContainer<WorkspaceRepository>>) {
        action.execute(repos)
    }
    ```

12. **String-based configuration names in convention plugins.** In precompiled script plugins, use string-based dependency declarations:
    ```kotlin
    dependencies {
        "implementation"(gradleApi())
        "testImplementation"("io.kotest:kotest-runner-junit5:5.9.1")
    }
    ```

## Examples

### Complete settings plugin structure
```kotlin
data object MyPlugin {
    const val GROUP = "myplugin"
    const val EXTENSION_NAME = "myplugin"

    abstract class SettingsExtension @Inject constructor() {
        abstract val outputDir: Property<String>
    }

    class SettingsPlugin : Plugin<Settings> {
        override fun apply(settings: Settings) {
            val ext = settings.extensions.create(EXTENSION_NAME, SettingsExtension::class.java)
            ext.outputDir.convention("default")

            settings.gradle.rootProject(
                Action { rootProject ->
                    rootProject.tasks.register("myplugin-generate", MyTask::class.java).configure {
                        it.outputDir.convention(ext.outputDir)
                    }
                },
            )
        }
    }
}
```

### DSL accessor file
```kotlin
// MyPluginDsl.kt
public fun Settings.myplugin(action: MyPlugin.SettingsExtension.() -> Unit) {
    extensions.getByType(MyPlugin.SettingsExtension::class.java).action()
}
```

### Convention plugin (precompiled script plugin)
```kotlin
// clkx-module.gradle.kts
plugins {
    `java-library`
    kotlin("jvm")
}

group = "com.example"

dependencies {
    "implementation"(gradleApi())
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### NamedDomainObjectContainer usage
```kotlin
abstract class SettingsExtension @Inject constructor(
    private val settings: Settings,
    private val objects: ObjectFactory,
) {
    val repos: NamedDomainObjectContainer<WorkspaceRepository> =
        objects.domainObjectContainer(WorkspaceRepository::class.java) { name ->
            objects.newInstance(WorkspaceRepository::class.java, name).apply {
                substitute.convention(false)
                baseBranch.convention(GitReference("main"))
            }
        }
}
```

## Anti-patterns

```kotlin
// WRONG: tasks.create() -- eagerly creates the task
rootProject.tasks.create("my-task", MyTask::class.java)
// Correct: tasks.register()
rootProject.tasks.register("my-task", MyTask::class.java)

// WRONG: accessing .get() eagerly in plugin apply()
val value = extension.outputDir.get()  // fails if not set yet
// Correct: wire with convention or provider
task.outputDir.convention(extension.outputDir)

// WRONG: using raw lambda instead of Action
settings.gradle.rootProject { root -> ... }
// Correct: explicit Action for configuration-cache safety
settings.gradle.rootProject(Action { root -> ... })

// WRONG: registering tasks outside rootProject callback
class SettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        // Can't access tasks here -- no project yet
    }
}

// WRONG: eager task configuration with configureEach on all tasks
rootProject.tasks.configureEach { ... }  // runs for every task
// Correct: configure only the tasks you register
rootProject.tasks.register("mine", MyTask::class.java).configure { ... }
```
