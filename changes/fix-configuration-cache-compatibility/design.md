# Design: Fix Configuration Cache Compatibility

## Approach

Replace every `project.*` access in `@TaskAction` methods with pre-captured `Property<T>` fields annotated `@Internal`. Populate these properties during task registration in `Opsx.kt`.

## Pattern

Before (broken):
```kotlin
abstract class FooTask : DefaultTask() {
    @get:Internal lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val change = project.findProperty(Opsx.PROP_CHANGE)?.toString()
        val reader = ChangeReader(project.rootDir, extension)
    }
}
```

After (configuration-cache safe):
```kotlin
abstract class FooTask : DefaultTask() {
    @get:Internal lateinit var extension: Opsx.SettingsExtension
    @get:Internal abstract val rootDir: Property<File>
    @get:Internal abstract val changeProp: Property<String>

    @TaskAction
    fun run() {
        val change = changeProp.orNull
        val reader = ChangeReader(rootDir.get(), extension)
    }
}
```

Registration:
```kotlin
rootProject.tasks.register(TASK_FOO, FooTask::class.java) {
    it.group = GROUP
    it.description = "..."
    it.extension = extension
    it.rootDir.set(rootProject.rootDir)
    it.changeProp.set(rootProject.findProperty(Opsx.PROP_CHANGE)?.toString())
}
```

## Files to Change

### 1. `Opsx.kt` — Task registration + remove dead inner classes

**`opsx/src/main/kotlin/zone/clanker/opsx/Opsx.kt`**

- In `registerWorkflowTasks()` and `registerInfrastructureTasks()`: for each task registration, add `.set()` calls for `rootDir` and all applicable `-P` property fields.
- Remove dead inner classes: `Opsx.SyncTask`, `Opsx.CleanTask`, `Opsx.StatusTask`, `Opsx.ListTask`, `Opsx.StubTask` (lines 168-325). These are superseded by the package versions and are never registered.

Helper to reduce duplication in registration:
```kotlin
private fun <T : DefaultTask> T.configureCommon(
    rootProject: Project,
    extension: SettingsExtension,
) {
    // Only set if the task has these properties (checked via interface or cast)
}
```

Actually, since each task needs different properties, just set them inline — no helper needed.

### 2. Workflow task classes (10 files)

Each file in `opsx/src/main/kotlin/zone/clanker/opsx/task/`:

| File | Properties to add |
|------|-------------------|
| `ProposeTask.kt` | `rootDir`, `specProp`, `promptProp`, `changeNameProp`, `agentProp`, `modelProp` |
| `ApplyTask.kt` | `rootDir`, `changeProp`, `agentProp`, `modelProp` |
| `VerifyTask.kt` | `rootDir`, `changeProp`, `agentProp`, `modelProp` |
| `ArchiveTask.kt` | `rootDir`, `changeProp` |
| `ContinueTask.kt` | `rootDir`, `changeProp`, `agentProp`, `modelProp` |
| `ExploreTask.kt` | `rootDir`, `promptProp`, `agentProp`, `modelProp` |
| `FeedbackTask.kt` | `rootDir`, `changeProp`, `promptProp`, `agentProp`, `modelProp` |
| `OnboardTask.kt` | `rootDir`, `promptProp`, `agentProp`, `modelProp` |
| `FfTask.kt` | `rootDir`, `changeProp`, `agentProp`, `modelProp` |
| `BulkArchiveTask.kt` | `rootDir` |

In each `@TaskAction`, replace:
- `project.findProperty(Opsx.PROP_X)?.toString()` -> `xProp.orNull`
- `project.rootDir` -> `rootDir.get()`

Also replace `project.rootDir` in internal helper methods like `buildProposalPrompt()` and `buildVerifyPrompt()` — these create `PromptBuilder(project.rootDir)` inside them. Pass `rootDir.get()` as a parameter or capture it in a local val at the top of `@TaskAction`.

### 3. Infrastructure task classes (4 files)

| File | Properties to add |
|------|-------------------|
| `StatusTask.kt` | `rootDir` |
| `ListTask.kt` | `rootDir` |
| `SyncTask.kt` | `rootDir`, `taskInfos` (ListProperty), `includedBuildInfos` (ListProperty) |
| `CleanTask.kt` | `rootDir`, `includedBuildDirs` (ListProperty) |

### 4. `SkillGenerator.kt`

**`opsx/src/main/kotlin/zone/clanker/opsx/skill/SkillGenerator.kt`**

Refactor constructor from `SkillGenerator(project: Project)` to accept plain data:

```kotlin
class SkillGenerator(
    private val rootDir: File,
    private val tasks: List<TaskInfo>,
    private val includedBuilds: List<IncludedBuildEntry>,
)
```

Where `IncludedBuildEntry` is a simple data class:
```kotlin
data class IncludedBuildEntry(val name: String, val projectDir: File)
```

- Remove `discoverTasks()` — task discovery moves to registration time in `Opsx.kt`.
- `generate()`, `generateSkillFiles()`, `generateInstructionFiles()` use the constructor params instead of `rootProject.*`.
- `homeDir()` uses `System.getProperty("user.home")` — this is fine (not a project reference).

### 5. Update SyncTask registration in Opsx.kt

Capture task list and included builds at configuration time:
```kotlin
rootProject.tasks.register(TASK_SYNC, SyncTask::class.java) {
    it.group = GROUP
    it.description = "..."
    it.extension = extension
    it.rootDir.set(rootProject.rootDir)
    // Capture task infos as serializable data
    it.taskInfos.set(
        rootProject.provider {
            rootProject.tasks
                .filter { t -> t.group in SkillGenerator.TRACKED_GROUPS }
                .map { t -> SkillGenerator.TaskInfo(t.name, t.group ?: "", t.description ?: "") }
        }
    )
    it.includedBuildInfos.set(
        rootProject.provider {
            rootProject.gradle.includedBuilds.map { b ->
                SkillGenerator.IncludedBuildEntry(b.name, b.projectDir)
            }
        }
    )
}
```

## Testing Strategy

1. **Existing tests** — run `./gradlew :opsx:test` to verify no regressions.
2. **Configuration cache validation** — run a representative task with `--configuration-cache`:
   ```bash
   ./gradlew --configuration-cache opsx-status
   ./gradlew --configuration-cache opsx-list
   ```
   These should succeed on the second run (cache hit).
3. **Manual smoke test** — `opsx-propose` with a `-P` flag to verify properties flow through.
