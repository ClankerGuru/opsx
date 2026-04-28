---
name: gradle-settings-plugin
description: Provides the data-object pattern for Gradle settings plugins, covering SettingsExtension with @Inject, rootProject task registration, convention wiring, configuration cache safety, and plugin registration. Activates when creating or modifying settings plugins.
---

# Gradle Settings Plugin Pattern

> Project-specific `data object` convention. For generic Gradle mechanisms see:
> - `/gradle` — build lifecycle and the `Settings` object
> - `/gradle-custom-plugins` — `Plugin<Settings>` interface, extensions, `gradlePlugin { }` registration
> - `/gradle-providers-properties` — `Property`, `convention`, lazy wiring used below
> - `/gradle-tasks` — task annotations (`@Input`, `@OutputDirectory`, `@DisableCachingByDefault`)
> - `/gradle-dependency-injection` — `@Inject` on the extension
> - `/gradle-composite-builds` — companion pattern for `includeBuild`

## Rules

1. **Use the `data object` pattern.** Every plugin is a single `data object` that contains constants, the `SettingsExtension`, and the `SettingsPlugin` as nested classes:
   ```kotlin
   data object Foo {
       const val GROUP = "foo"
       const val EXTENSION_NAME = "foo"
       const val TASK_DO_THING = "foo-do-thing"

       abstract class SettingsExtension @Inject constructor() { ... }
       class SettingsPlugin : Plugin<Settings> { ... }
   }
   ```

2. **Constants live on the data object.** Task names, group names, extension names, output dirs, and default values are all `const val` on the outer data object. Never scatter them across files.

3. **The extension is `abstract class` with `@Inject constructor()`.** Add `@Suppress("UnnecessaryAbstractClass")` since detekt flags abstract classes without abstract members (the `abstract val` properties satisfy this, but detekt is strict):
   ```kotlin
   @Suppress("UnnecessaryAbstractClass")
   abstract class SettingsExtension
       @Inject
       constructor() {
           abstract val outputDir: Property<String>
       }
   ```

4. **The plugin wires tasks inside `settings.gradle.rootProject(Action { })`.** You cannot register tasks from a settings plugin directly -- you must use the rootProject callback:
   ```kotlin
   class SettingsPlugin : Plugin<Settings> {
       override fun apply(settings: Settings) {
           val extension = settings.extensions.create(EXTENSION_NAME, SettingsExtension::class.java)
           extension.outputDir.convention(OUTPUT_DIR)

           settings.gradle.rootProject(
               Action { rootProject ->
                   registerTasks(rootProject, extension)
               },
           )
       }
   }
   ```

5. **Register tasks with `tasks.register()`, wire extension properties via `.convention()`.** Inside the `registerTasks` method:
   ```kotlin
   internal fun registerTasks(rootProject: Project, extension: SettingsExtension) {
       rootProject.tasks.register(TASK_CONTEXT, ContextTask::class.java).apply {
           configure { task ->
               task.outputDir.convention(extension.outputDir)
               task.sourceFiles.from(
                   rootProject.provider { collectSourceTrees(rootProject) },
               )
           }
       }
   }
   ```

6. **Wire `LifecycleBasePlugin` for clean tasks.** If your plugin has a clean task, hook it into the standard `clean` lifecycle:
   ```kotlin
   rootProject.plugins.withType(
       org.gradle.language.base.plugins.LifecycleBasePlugin::class.java,
   ) {
       rootProject.tasks.named("clean").configure { it.dependsOn(cleanTask) }
   }
   ```

7. **Tasks extend `DefaultTask()` and use `@DisableCachingByDefault`.** Settings plugin tasks typically depend on local file layout and should not be cached:
   ```kotlin
   @org.gradle.work.DisableCachingByDefault(because = "Output depends on local file layout")
   abstract class ContextTask : DefaultTask() {
   ```

8. **Task properties use `@get:Input`, `@get:InputFiles`, `@get:OutputDirectory`, `@get:Internal`.** Choose the right annotation:
   - `@get:Input` for scalar values (strings, booleans, sets)
   - `@get:InputFiles` with `@get:PathSensitive(PathSensitivity.RELATIVE)` for source files
   - `@get:OutputDirectory` for the output directory
   - `@get:Internal` for data that should not trigger up-to-date checks (File objects, Maps of complex types)

9. **Set `group` and `description` in the task's `init` block:**
   ```kotlin
   init {
       group = Srcx.GROUP
       description = "Generate comprehensive context report"
   }
   ```

10. **No `Project` access at execution time.** All Gradle model data (project dirs, paths, dependencies) must be captured at configuration time via task properties. The `@TaskAction` method operates only on files and pre-computed data.

11. **Serializable data for configuration cache.** Data classes passed through task properties must implement `java.io.Serializable`:
    ```kotlin
    data class IncludedBuildInfo(
        val name: String,
        val dir: File,
        val relPath: String,
        val projects: List<Pair<String, File>>,
    ) : java.io.Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }
    ```

12. **Register the plugin in `build.gradle.kts`** with the `$` escape for nested classes:
    ```kotlin
    gradlePlugin {
        plugins {
            register("myplugin") {
                id = "com.example.gradle.myplugin"
                implementationClass = "com.example.gradle.myplugin.MyPlugin\$SettingsPlugin"
                displayName = "My Plugin"
                description = "A description of my plugin."
            }
        }
    }
    ```

## Examples

### Minimal complete settings plugin
```kotlin
package com.example.gradle.foo

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import javax.inject.Inject

data object Foo {
    const val GROUP = "foo"
    const val EXTENSION_NAME = "foo"
    const val OUTPUT_DIR = ".foo"
    const val TASK_GENERATE = "foo-generate"
    const val TASK_CLEAN = "foo-clean"

    @Suppress("UnnecessaryAbstractClass")
    abstract class SettingsExtension
        @Inject
        constructor() {
            abstract val outputDir: Property<String>
        }

    class SettingsPlugin : Plugin<Settings> {
        override fun apply(settings: Settings) {
            val extension = settings.extensions.create(EXTENSION_NAME, SettingsExtension::class.java)
            extension.outputDir.convention(OUTPUT_DIR)

            settings.gradle.rootProject(
                Action { rootProject ->
                    rootProject.tasks.register(TASK_GENERATE, GenerateTask::class.java).apply {
                        configure { task ->
                            task.outputDir.convention(extension.outputDir)
                        }
                    }
                },
            )
        }
    }
}
```

### Task with proper annotations
```kotlin
@org.gradle.work.DisableCachingByDefault(because = "Output depends on local file layout")
abstract class GenerateTask : DefaultTask() {
    @get:Input
    abstract val outputDir: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val projectDirs: MapProperty<String, File>

    init {
        group = Foo.GROUP
        description = "Generate foo report"
    }

    @TaskAction
    fun generate() {
        val outDir = outputDir.get()
        val projects = projectDirs.get()
        // operate only on captured data -- no Project access
        logger.lifecycle("foo: generated report at $outDir")
    }
}
```

### DSL accessor
```kotlin
// FooDsl.kt
public fun Settings.foo(action: Foo.SettingsExtension.() -> Unit) {
    extensions.getByType(Foo.SettingsExtension::class.java).action()
}
```

## Anti-patterns

```kotlin
// WRONG: plugin class at top level instead of inside data object
class FooPlugin : Plugin<Settings> { ... }
object FooConstants { const val GROUP = "foo" }

// WRONG: registering tasks directly in apply() without rootProject callback
override fun apply(settings: Settings) {
    // ERROR: settings has no tasks
    settings.tasks.register(...)
}

// WRONG: accessing Project in @TaskAction
@TaskAction
fun generate() {
    val root = project.rootProject  // configuration-cache violation
    root.subprojects.forEach { ... }
}

// WRONG: non-serializable data in task properties
data class BuildInfo(val name: String, val dir: File)  // missing Serializable

// WRONG: creating tasks eagerly
rootProject.tasks.create("foo", FooTask::class.java)  // not configuration-avoidant

// WRONG: using tasks.create in settings.gradle.rootProject
// This works but violates configuration avoidance
```
