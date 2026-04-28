---
name: kotlin-conventions
description: Provides Kotlin coding conventions enforced via detekt, ktlint, and konsist, including runCatching error handling, Gradle logging, value classes, and forbidden patterns. Activates when writing or modifying Kotlin code in a Gradle plugin project.
---

# Kotlin Conventions

> Project-specific conventions for the clanker Gradle-plugin codebase. For generic Kotlin topics see:
> - `/kotlin-lang` — idiomatic Kotlin language features
> - `/kotlin-functional-first` — functional style guidance
> - `/kotlin-dsl-builders` — lambdas-with-receiver, `@DslMarker`
> - `/kotlin-dsl` — Gradle-specific Kotlin DSL usage
> - `/detekt` — rules referenced below (MaxLineLength, LongMethod, etc.)
> - `/ktlint` — formatting rules
> - `/konsist` — architecture-test rules referenced below
> - `/gradle-providers-properties` — `Property<T>`, `SetProperty<T>` usage

## Rules

1. **Use `runCatching` instead of `try-catch`.** All error handling must use `runCatching { }.getOrElse { }`, `runCatching { }.getOrDefault()`, `.onFailure { }`, or `.fold(onSuccess, onFailure)`. Never write `try {` or `catch (`. This is enforced by konsist `ForbiddenPatternTest`.

2. **No `println` or `System.err`.** Use Gradle's logging API exclusively:
   - `logger.lifecycle(...)` for user-visible output
   - `logger.warn(...)` for warnings
   - `logger.info(...)` for debug-level detail
   - Obtain loggers via `Logging.getLogger(ClassName::class.java)` in objects, or use the inherited `logger` in `DefaultTask` subclasses.

3. **No wildcard imports.** Every import must be explicit. `import foo.bar.*` will fail detekt (`WildcardImport`) and konsist checks.

4. **No standalone constant files.** Files named `Constants.kt` or `Consts.kt` are banned. Constants belong in the `data object` or class that owns them (e.g., `Srcx.GROUP`, `Wrkx.TASK_CLONE`).

5. **Use `Property<T>` with `@Inject` for extensions.** Extension classes are `abstract class` with an `@Inject constructor()`. Properties are declared `abstract val`:
   ```kotlin
   @Suppress("UnnecessaryAbstractClass")
   abstract class SettingsExtension
       @Inject
       constructor() {
           abstract val outputDir: Property<String>
           abstract val autoGenerate: Property<Boolean>
       }
   ```

6. **Use `@JvmInline value class` for domain primitives.** Wrap strings that have semantic meaning (paths, names, IDs) in inline value classes with `init { require(...) }` validation:
   ```kotlin
   @JvmInline
   value class SourceSetName(val value: String) {
       init {
           require(value.isNotBlank()) { "SourceSetName must not be blank" }
       }
       override fun toString(): String = value
   }
   ```

7. **Data class conventions.** Use data classes for value types. Include `init { require(...) }` blocks for invariant validation. Keep data classes in the `model` package:
   ```kotlin
   data class DependencyEntry(
       val group: ArtifactGroup,
       val artifact: ArtifactName,
       val version: ArtifactVersion,
       val scope: String,
   ) {
       init {
           require(scope.isNotBlank()) { "scope must not be blank" }
       }
   }
   ```

8. **Enum pattern.** Enums carry a `label` or `icon` property for display. The constructor parameter is the display value:
   ```kotlin
   enum class ComponentRole(val label: String) {
       CONTROLLER("Controller"),
       SERVICE("Service"),
       OTHER(""),
   }

   enum class SymbolDetailKind(val label: String) {
       CLASS("class"),
       INTERFACE("interface"),
       ENUM("enum"),
       DATA_CLASS("data class"),
       OBJECT("object"),
       FUNCTION("fun"),
       PROPERTY("val/var"),
   }
   ```

9. **Extension function pattern.** DSL accessors are top-level extension functions in a separate `*Dsl.kt` file:
   ```kotlin
   // SrcxDsl.kt
   public fun Settings.srcx(action: Srcx.SettingsExtension.() -> Unit) {
       extensions.getByType(Srcx.SettingsExtension::class.java).action()
   }
   ```

10. **No forbidden class name suffixes.** Classes must not end with `Helper`, `Manager`, `Util`, or `Utils`. This is enforced by konsist `NamingConventionTest`.

11. **Max line length is 120 characters.** Enforced by detekt `MaxLineLength`.

12. **Max function length is 60 lines.** Enforced by detekt `LongMethod`.

13. **Max 6 function parameters, 7 constructor parameters.** Enforced by detekt `LongParameterList`.

14. **Max nesting depth is 4.** Enforced by detekt `NestedBlockDepth`.

15. **Max 3 return statements per function.** Enforced by detekt `ReturnCount`. Suppress with `@Suppress("ReturnCount")` only when unavoidable.

16. **Trailing commas on multi-line parameter lists and collection literals.** Enforced by ktlint.

17. **`TODO:` comments are forbidden unless they have an owner.** Use `TODO(owner):` format. Bare `TODO:`, `FIXME:`, and `HACK:` fail detekt `ForbiddenComment`.

## Examples

### Error handling with runCatching
```kotlin
// Correct
return runCatching {
    val sources = scanSources(allDirs)
    val components = classifyAll(sources)
    generateDependencyDiagram(components, depEdges)
}.onFailure { e ->
    logger.warn("srcx: class diagram generation failed: ${e.message}")
}.getOrDefault("")

// Correct
runCatching { settings.pluginManager.apply(pluginId) }

// Correct
runCatching { work(repo) }
    .getOrElse { e -> "FAIL ${repo.repoName}: ${e.message}" }
```

### Gradle logging
```kotlin
private val logger = Logging.getLogger(Srcx::class.java)

logger.lifecycle("srcx: deleted ${dir.name}/ ($count files)")
logger.lifecycle("srcx: context written to $outDir/context.md")
logger.warn("wrkx: Repository '${repo.repoName}' not cloned")
```

### Package structure
```
com.example.gradle.myplugin/
    MyPlugin.kt           -- data object with constants, extension, plugin
    MyPluginDsl.kt        -- Settings.myplugin {} extension function
    model/                -- data classes, value classes, enums (leaf nodes)
    parse/                -- parsers producing model types
    analysis/             -- analyzers consuming model types
    scan/                 -- scanners using parse + model
    report/               -- renderers consuming model + scan
    task/                 -- Gradle tasks orchestrating everything
```

## Anti-patterns

```kotlin
// WRONG: try-catch
try {
    parseFile(f)
} catch (e: Exception) {
    logger.warn("Failed: ${e.message}")
}

// WRONG: println
println("Processing ${file.name}")
System.err.println("Error: $msg")

// WRONG: wildcard import
import java.io.*
import org.gradle.api.*

// WRONG: standalone constants file
// Constants.kt
object Constants {
    const val GROUP = "srcx"
}

// WRONG: generic suffix
class ReportHelper { ... }
class FileUtils { ... }

// WRONG: raw string where value class should be used
fun process(projectPath: String, sourceSet: String) { ... }
// Correct:
fun process(projectPath: ProjectPath, sourceSet: SourceSetName) { ... }
```
