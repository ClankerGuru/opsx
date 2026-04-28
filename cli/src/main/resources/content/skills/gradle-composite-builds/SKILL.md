---
name: gradle-composite-builds
description: Provides the wrkx composite build wiring pattern with includeBuild, dependency substitution, project discovery via internal API reflection, and settingsEvaluated lifecycle. Activates when working with composite builds, cross-build dependency substitution, or wrkx workspace configuration.
---

# Gradle Composite Builds (wrkx pattern)

> Project-specific pattern. For generic Gradle mechanisms see:
> - `/gradle` — build lifecycle, phases, Settings/Project/Gradle objects
> - `/gradle-plugins-basics` — `plugins { }`, `pluginManagement`
> - `/gradle-custom-plugins` — writing `Plugin<Settings>`, extensions, `includeBuild`
> - `/gradle-settings-plugin` — this project's settings-plugin conventions
> - `/gradle-dependency-injection` — `@Inject` services, `BuildService`

## Rules

1. **Composite builds are included via `settings.includeBuild()`.** The wrkx plugin reads a `wrkx.json` config file, creates `WorkspaceRepository` entries, and includes enabled repos as composite builds:
   ```kotlin
   settings.includeBuild(cloneDir) { spec ->
       spec.name = repo.sanitizedBuildName
       if (repo.substitute.get() && repo.substitutions.get().isNotEmpty()) {
           spec.dependencySubstitution { sub ->
               repo.substitutions.get().forEach { s ->
                   sub.substitute(sub.module(s.artifact.value))
                       .using(sub.project(s.project.gradlePath))
               }
           }
       }
   }
   ```

2. **Inclusion happens in `settingsEvaluated` callback.** This ensures the DSL block (where users enable/disable repos) has finished before composite builds are wired:
   ```kotlin
   settings.gradle.settingsEvaluated {
       extension.includeEnabled()
   }
   ```

3. **Dependency substitution maps Maven coordinates to local projects.** When `substitute = true` and `substitutions` are defined, Gradle replaces Maven artifacts with local project outputs:
   ```json
   {
     "name": "tokens",
     "path": "git@github.com:example/tokens.git",
     "substitute": true,
     "substitutions": ["com.example:tokens,tokens"]
   }
   ```
   This means `implementation("com.example:tokens:1.0")` resolves to the local `:tokens` project in the included build.

4. **The `IncludedBuild` API is limited.** The public `IncludedBuild` interface only exposes `name`, `projectDir`, and `task(path)`. To discover projects within an included build, you must use Gradle internal APIs via reflection.

5. **Internal API reflection for project discovery.** The `ProjectScanner.discoverIncludedBuildProjects` method uses reflection to access `getTarget() -> getProjects() -> getAllProjects()`:
   ```kotlin
   internal fun discoverIncludedBuildProjects(
       build: org.gradle.api.initialization.IncludedBuild,
   ): List<Pair<String, File>> =
       runCatching {
           val target = build.javaClass.getMethod("getTarget").invoke(build)
           val registry = target!!.javaClass.getMethod("getProjects").let {
               it.isAccessible = true
               it.invoke(target)
           }
           val allProjects = registry!!.javaClass.getMethod("getAllProjects").let {
               it.isAccessible = true
               it.invoke(registry) as Set<*>
           }
           allProjects.mapNotNull { ps ->
               val path = ps!!.javaClass.getMethod("getIdentityPath").let {
                   it.isAccessible = true
                   it.invoke(ps).toString()
               }
               val dir = ps.javaClass.getMethod("getProjectDir").let {
                   it.isAccessible = true
                   it.invoke(ps) as File
               }
               val relativePath = path
                   .removePrefix(":${build.name}")
                   .ifEmpty { ":" }
               relativePath to dir
           }
       }.getOrDefault(listOf(":" to build.projectDir))
   ```
   **Always wrap in `runCatching`** -- internal APIs change between Gradle versions. Fall back to just the root project directory.

6. **Settings plugin lifecycle order:**
   1. `apply(settings)` -- register extension, set conventions
   2. Settings DSL block executes (`wrkx { enableAll() }`)
   3. `settingsEvaluated` callback fires -- include enabled builds
   4. `rootProject` callback fires -- register tasks on the root project

7. **Build names must be unique and sanitized.** The `sanitizedBuildName` property strips non-alphanumeric characters, replaces with hyphens, and lowercases:
   ```kotlin
   val sanitizedBuildName: String
       get() = directoryName
           .replace(Regex("[^a-zA-Z0-9-]"), "-")
           .replace(Regex("-+"), "-")
           .trim('-')
           .lowercase()
   ```

8. **Validate no duplicate build names before including.** Two repos could sanitize to the same name. Check before calling `includeBuild`:
   ```kotlin
   internal fun checkForDuplicateBuildNames(repos: List<WorkspaceRepository>) {
       val byName = repos.groupBy { it.sanitizedBuildName }
       val dupes = byName.filter { it.value.size > 1 }
       check(dupes.isEmpty()) { ... }
   }
   ```

9. **Guard against missing clone directories.** If a repo is enabled but not cloned, warn and skip instead of failing:
   ```kotlin
   if (!cloneDir.exists()) {
       logger.warn("wrkx: Repository '${repo.repoName}' not cloned at ${cloneDir.absolutePath}.")
       return
   }
   ```

10. **Prevent double-apply.** Check if the extension already exists before applying:
    ```kotlin
    internal fun isAlreadyApplied(settings: Settings): Boolean =
        settings.extensions.findByType(SettingsExtension::class.java) != null
    ```

## Examples

### wrkx.json configuration
```json
[
  {
    "name": "tokens",
    "path": "git@github.com:example/tokens.git",
    "baseBranch": "main",
    "category": "ui",
    "substitute": true,
    "substitutions": ["com.example:tokens,tokens"]
  },
  {
    "name": "tooling",
    "path": "git@github.com:example/tooling.git",
    "category": "tooling",
    "substitute": false
  }
]
```

### Using the workspace DSL in settings.gradle.kts
```kotlin
plugins {
    id("com.example.gradle.wrkx") version "1.0.0"
}

wrkx {
    workingBranch = "feature/new-catalog"
    disableAll()
    enable(repos["tokens"], repos["tooling"])
}
```

### Accessing included builds from a task
```kotlin
task.includedBuildInfos.set(
    rootProject.provider {
        rootProject.gradle.includedBuilds.map { build ->
            val relPath = build.projectDir.relativeToOrNull(rootProject.projectDir)?.path
                ?: build.projectDir.absolutePath
            IncludedBuildInfo(
                name = build.name,
                dir = build.projectDir,
                relPath = relPath,
                projects = ProjectScanner.discoverIncludedBuildProjects(build),
            )
        }
    },
)
```

### Cross-build dependency edges
```kotlin
internal fun computeBuildEdges(
    builds: List<Pair<String, File>>,
    buildSummaries: Map<String, List<ProjectSummary>>,
): List<DashboardRenderer.BuildEdge> {
    val buildNames = builds.map { it.first }.toSet()
    return buildSummaries
        .flatMap { (name, projects) ->
            projects.flatMap { summary ->
                summary.dependencies
                    .map { it.artifact.value }
                    .filter { it in buildNames && it != name }
                    .map { DashboardRenderer.BuildEdge(name, it) }
            }
        }.distinct()
}
```

## Anti-patterns

```kotlin
// WRONG: including builds eagerly in apply()
override fun apply(settings: Settings) {
    settings.includeBuild("../gort")  // DSL hasn't run yet, user can't control this
}
// Correct: defer to settingsEvaluated
settings.gradle.settingsEvaluated { extension.includeEnabled() }

// WRONG: assuming internal API always works
val target = build.javaClass.getMethod("getTarget").invoke(build)
// This will throw on Gradle version changes
// Correct: always wrap in runCatching
runCatching { ... }.getOrDefault(listOf(":" to build.projectDir))

// WRONG: hardcoding repo paths
settings.includeBuild("/Users/me/repos/gort")
// Correct: use config-driven discovery from wrkx.json

// WRONG: failing the build when a repo isn't cloned
check(cloneDir.exists()) { "Repo not found" }
// Correct: warn and skip
if (!cloneDir.exists()) { logger.warn(...); return }

// WRONG: not validating duplicate build names
// Gradle will fail with a confusing error if two builds share a name
```
