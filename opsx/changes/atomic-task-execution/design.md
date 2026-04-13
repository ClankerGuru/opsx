# Design: atomic-task-execution

## Overview

Three new subsystems in opsx: task parser, task executor, and synchronized logger.

## 1. Task Parser

Reads `tasks.md` markdown and produces a list of `TaskDefinition` objects.

### Model

```
TaskDefinition:
  id: String (10-char alphanumeric)
  name: String
  description: String (multi-line)
  status: TaskStatus (TODO, IN_PROGRESS, DONE, BLOCKED, SKIPPED)
  dependencies: List<String> (task IDs)
```

### Parsing rules

- Line starts with `- [` → new task
- Character inside brackets → status: space=TODO, >=IN_PROGRESS, x=DONE, !=BLOCKED, ~=SKIPPED
- After `] ` → ID (until ` | `), then name (rest of line)
- Following lines with 4-space indent → description
- Line with `  depends: none` → empty deps
- Line with `  depends:` followed by `    - {id}` lines → deps list

### ID generation

10-char alphanumeric, generated from a secure random source. Checked for uniqueness within the tasks.md file.

## 2. Task Executor

Registers hidden Gradle tasks and executes them.

### Registration

When `opsx-apply` is invoked with a change name or task ID:

1. Parse `tasks.md` for the change
2. For each TaskDefinition, register a Gradle task:
   - Name: `opsx-{id}` (hidden, no group)
   - DependsOn: `opsx-{depId}` for each dependency
   - Action: dispatch agent with scoped prompt (task description + context.md)
3. Find the target:
   - If task ID provided → that task
   - If epic name provided → terminal tasks (those no other task depends on)
4. Execute via Gradle with `--parallel`

### Scoped prompts

Each agent receives only:
- The task description from tasks.md
- The change's `context.md` (srcx-generated, scoped to the change)
- The change's `design.md` for architectural guidance

No global 50K context dump. The context.md for the change is already targeted.

### Status updates

After each task completes:
- Update the checkbox in tasks.md (atomic file write with lock)
- Append to log.md (synchronized writer)

## 3. Synchronized Logger

A shared object in the Gradle daemon JVM that serializes writes to `log.md`.

```kotlin
object ChangeLogger {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun append(changeDir: File, taskId: String, name: String, status: TaskStatus, message: String) {
        val lock = locks.computeIfAbsent(changeDir.absolutePath) { ReentrantLock() }
        lock.lock()
        try {
            val logFile = File(changeDir, "log.md")
            val symbol = status.symbol
            val line = "- [$symbol] $taskId | $name — $message\n"
            logFile.appendText(line)
        } finally {
            lock.unlock()
        }
    }
}
```

## 4. Epic verification

`.opsx.yaml` adds a `verify` field:

```yaml
name: refactor-config-parser
status: active
verify: ./gradlew test --tests "zone.clanker.opsx.model.*"
depends:
  - searchable-context-index
```

`opsx-verify` reads the verify command and executes it. Exit code 0 → status moves to `verified`. Non-zero → stays active.

`opsx-archive` checks status is `verified` before archiving.

## Files to Change

### New files
- `model/TaskDefinition.kt` — data class with id, name, description, status, dependencies
- `model/TaskStatus.kt` — enum: TODO, IN_PROGRESS, DONE, BLOCKED, SKIPPED with symbol chars
- `workflow/TaskParser.kt` — parses tasks.md → List<TaskDefinition>
- `workflow/TaskExecutor.kt` — registers hidden Gradle tasks, wires deps, dispatches agents
- `workflow/ChangeLogger.kt` — synchronized append-only log writer

### Modified files
- `model/ChangeConfig.kt` — add verify field to .opsx.yaml parsing
- `task/ApplyTask.kt` — use TaskExecutor instead of direct AgentDispatcher
- `task/VerifyTask.kt` — run verify command from .opsx.yaml
- `task/ArchiveTask.kt` — check verified status
- `task/StatusTask.kt` — show per-task status from tasks.md
- `task/ContinueTask.kt` — resume from first unfinished task

### Test files
- `workflow/TaskParserTest.kt`
- `workflow/TaskExecutorTest.kt`
- `workflow/ChangeLoggerTest.kt`
- `model/TaskDefinitionTest.kt`
- `model/TaskStatusTest.kt`

## Acceptance Criteria

- Task IDs are unique, 10-char alphanumeric
- tasks.md parses correctly with all status symbols
- Hidden tasks don't appear in `./gradlew tasks`
- Parallel execution respects dependency ordering
- Log file handles concurrent appends without corruption
- Verify command gates archiving
- Existing opsx workflow (propose/apply/verify/archive) still works
