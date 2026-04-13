# Tasks: atomic-task-execution

- [ ] rk4m2nw8p1 | TaskStatus enum
    Create enum with TODO, IN_PROGRESS, DONE, BLOCKED, SKIPPED.
    Each value has a symbol char: space, >, x, !, ~.
    Include from-symbol parser.
  depends: none

- [ ] j7v3qx9t2a | TaskDefinition model
    Data class with id, name, description, status, dependencies.
    ID is 10-char alphanumeric. Dependencies is list of IDs.
  depends:
    - rk4m2nw8p1

- [ ] b5h8fy6c3n | Test TaskParser
    Write Kotest BehaviorSpec for parsing tasks.md format.
    Cover: all status symbols, multi-line descriptions,
    depends none, depends with multiple IDs, malformed input.
  depends: none

- [ ] w2d9gz1e4k | Implement TaskParser
    Parse tasks.md into List<TaskDefinition>.
    Handle checkbox status, ID extraction, pipe-separated name,
    4-space indented description, depends block.
  depends:
    - j7v3qx9t2a
    - b5h8fy6c3n

- [ ] p6t1hm3r5x | Test ChangeLogger
    Write Kotest BehaviorSpec for synchronized log appends.
    Cover: single append, concurrent appends from multiple
    threads, log format matches spec.
  depends: none

- [ ] n8c4ks7v2q | Implement ChangeLogger
    Synchronized writer using ReentrantLock per change directory.
    Append one line per event to log.md in the task format.
  depends:
    - p6t1hm3r5x

- [ ] f3y7dw5j9b | Test TaskExecutor
    Write Kotest BehaviorSpec for hidden task registration.
    Cover: tasks registered without group, dependency wiring,
    terminal task detection, single task targeting.
  depends: none

- [ ] q1x5an8m4g | Implement TaskExecutor
    Register hidden Gradle tasks from TaskDefinitions.
    Wire dependsOn between tasks. Find terminal tasks.
    Dispatch scoped agent per task with context.md + design.md.
  depends:
    - w2d9gz1e4k
    - n8c4ks7v2q
    - f3y7dw5j9b

- [ ] v9b2ek6h1s | Add verify field to ChangeConfig
    Parse verify command from .opsx.yaml.
    Update ChangeConfig model and YAML parser/writer.
  depends:
    - j7v3qx9t2a

- [ ] t4n7rx3c8w | Update ApplyTask
    Use TaskExecutor instead of direct AgentDispatcher.
    Accept task ID or epic name. Register and execute.
  depends:
    - q1x5an8m4g

- [ ] m6g1py9f5d | Update VerifyTask
    Run verify command from .opsx.yaml instead of agent review.
    Exit code 0 → verified. Non-zero → stays active.
  depends:
    - v9b2ek6h1s

- [ ] h2k8sw4b7j | Update StatusTask
    Show per-task status from tasks.md.
    Display task ID, name, status symbol, dependency count.
  depends:
    - w2d9gz1e4k

- [ ] c9f5vn2t6a | Update ArchiveTask
    Check verified status before allowing archive.
    Verify command must have passed.
  depends:
    - m6g1py9f5d

- [ ] y3j6qm1d8k | Integration test
    Full lifecycle: parse tasks, execute with deps,
    log appends, status updates, verify gates archive.
  depends:
    - t4n7rx3c8w
    - h2k8sw4b7j
    - c9f5vn2t6a
