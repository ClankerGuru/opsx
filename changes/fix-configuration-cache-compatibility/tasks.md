# Tasks: Fix Configuration Cache Compatibility

- [ ] **Refactor SkillGenerator to accept plain data instead of Project** — Change constructor from `SkillGenerator(project)` to `SkillGenerator(rootDir, tasks, includedBuilds)`. Add `IncludedBuildEntry` data class. Remove `discoverTasks()`. Update `generate()`, `generateSkillFiles()`, `generateInstructionFiles()` to use constructor params. File: `opsx/src/main/kotlin/zone/clanker/opsx/skill/SkillGenerator.kt`

- [ ] **Add @Internal Property fields to all 10 workflow tasks** — Add `abstract val rootDir: Property<File>` plus applicable `-P` property fields (`changeProp`, `promptProp`, `specProp`, `changeNameProp`, `agentProp`, `modelProp`) to each task. Replace all `project.findProperty()` calls with `.orNull` and all `project.rootDir` with `rootDir.get()`. Fix internal helper methods that also reference `project.rootDir`. Files:
  - `task/ProposeTask.kt` — rootDir, specProp, promptProp, changeNameProp, agentProp, modelProp
  - `task/ApplyTask.kt` — rootDir, changeProp, agentProp, modelProp
  - `task/VerifyTask.kt` — rootDir, changeProp, agentProp, modelProp
  - `task/ArchiveTask.kt` — rootDir, changeProp
  - `task/ContinueTask.kt` — rootDir, changeProp, agentProp, modelProp
  - `task/ExploreTask.kt` — rootDir, promptProp, agentProp, modelProp
  - `task/FeedbackTask.kt` — rootDir, changeProp, promptProp, agentProp, modelProp
  - `task/OnboardTask.kt` — rootDir, promptProp, agentProp, modelProp
  - `task/FfTask.kt` — rootDir, changeProp, agentProp, modelProp
  - `task/BulkArchiveTask.kt` — rootDir

- [ ] **Add @Internal Property fields to all 4 infrastructure tasks** — Add `rootDir` to StatusTask, ListTask, CleanTask, SyncTask. Add `includedBuildDirs: ListProperty<File>` to CleanTask. Add `taskInfos: ListProperty<SkillGenerator.TaskInfo>` and `includedBuildInfos: ListProperty<SkillGenerator.IncludedBuildEntry>` to SyncTask. Replace all `project.*` calls. Files:
  - `task/StatusTask.kt` — rootDir
  - `task/ListTask.kt` — rootDir
  - `task/CleanTask.kt` — rootDir, includedBuildDirs
  - `task/SyncTask.kt` — rootDir, taskInfos, includedBuildInfos

- [ ] **Update task registration in Opsx.kt** — In `registerWorkflowTasks()` and `registerInfrastructureTasks()`, add `.set()` calls for every new property on each task. Capture `rootProject.rootDir`, `rootProject.findProperty()`, task list, and included builds at configuration time. File: `opsx/src/main/kotlin/zone/clanker/opsx/Opsx.kt`

- [ ] **Remove dead inner classes from Opsx.kt** — Delete `Opsx.StubTask`, `Opsx.SyncTask`, `Opsx.CleanTask`, `Opsx.StatusTask`, `Opsx.ListTask` (lines 168-325). These are superseded by the `zone.clanker.opsx.task` package versions.

- [ ] **Run tests** — `./gradlew :opsx:test` to verify no regressions.

- [ ] **Smoke test with --configuration-cache** — Run `./gradlew --configuration-cache opsx-status` twice to verify cache hit on second run.
