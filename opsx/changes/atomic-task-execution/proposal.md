# Proposal: atomic-task-execution

## Problem

opsx currently dispatches one large prompt to one agent per change. The agent runs for minutes, has no structure, can't be parallelized, and burns tokens on irrelevant context. There's no visibility into what's happening until it finishes. Changes can't be partially applied or resumed at a granular level.

## Objective

Replace the single-agent dispatch model with structured, atomic task execution. A change (epic) decomposes into many small tasks that execute in 30-60 seconds each. Tasks have dependencies, run in parallel where possible, and follow a strict test-first pattern. Verification happens at the epic level, not per task.

## Epic Sizing

An epic is roughly a 13-point Jira ticket. ~3 days of work. 20-40 atomic tasks inside. Small enough to avoid massive merges, big enough to be meaningful. Only one epic executes at a time unless using a git worktree.

## Task Format

Tasks live in `tasks.md` as structured markdown:

```
- [ ] a1b2c3d4e5 | Test ChangeConfig.parse
    Write Kotest BehaviorSpec for parse method.
    Valid YAML, missing fields, malformed input.
  depends: none

- [ ] e5f6g7h8i9 | Implement ChangeConfig.parse
    Add parse method to ChangeConfig.kt.
    Read YAML file, return ChangeConfig instance.
  depends:
    - a1b2c3d4e5
```

Structure per task:
- `- [{status}] {10-char-alphanumeric-id} | {name}` — first line
- `    {description}` — 4-space indent, human readable, multi-line
- `  depends: none` or `  depends:` followed by `    - {id}` per line

Status symbols: `[ ]` to do, `[>]` in progress, `[x]` done, `[!]` blocked, `[~]` skipped.

The ID is unique, generated, alphanumeric, 10 characters. Copy-pasteable:
```
/opsx-apply e5f6g7h8i9
```

## Task Pairing

Tasks always come in test/implementation pairs:
1. Write the test (no deps or depends on prior impl)
2. Write the implementation (depends on its test)
3. Repeat

No task ever runs verification. Tasks only generate code. Verification is an epic-level gate.

## Execution Model

Tasks register as hidden Gradle tasks (no group, invisible in `./gradlew tasks`). Only top-level opsx commands are visible.

Tasks with no unfinished dependencies execute in parallel via `--parallel`. Gradle is the scheduler — no custom orchestration. When a test task finishes, its implementation task fires immediately.

Targeting:
- `/opsx-apply e5f6g7h8i9` — run one task (and its unfinished deps)
- `/opsx-apply refactor-config-parser` — run all tasks for the epic

## Verification

The epic's `.opsx.yaml` declares the verify command:

```yaml
name: refactor-config-parser
status: active
verify: ./gradlew test --tests "zone.clanker.opsx.model.*"
```

`/opsx-verify refactor-config-parser` runs it. Green → verified. Red → stays active, fix and retry. You can only archive when verified. All tests must be green.

## Log

`log.md` is append-only, written by a synchronized writer (tasks share the Gradle daemon JVM):

```
- [>] a1b2c3d4e5 | Test ChangeConfig.parse — started just now
- [x] a1b2c3d4e5 | Test ChangeConfig.parse — done in 22s
- [>] e5f6g7h8i9 | Implement ChangeConfig.parse — started just now
- [x] e5f6g7h8i9 | Implement ChangeConfig.parse — done in 18s
```

Same format as tasks: status symbol, ID, pipe, name, dash, what happened. Human readable. Agent parseable.

## Change Directory

```
opsx/changes/refactor-config-parser/
├── .opsx.yaml      # Epic: name, status, verify command, depends on other epics
├── proposal.md     # WHY — the problem and objective
├── design.md       # HOW — approach, acceptance criteria
├── tasks.md        # WHAT — atomic units with deps
├── context.md      # AUTO — srcx-generated, scoped to this change
└── log.md          # HISTORY — append-only execution journal
```

## Lifecycle

```
propose      → proposal.md, design.md created
               srcx generates context.md scoped to the change
decompose    → tasks.md with test/impl pairs, IDs generated
               hidden Gradle tasks registered
execute      → /opsx-apply {id} or /opsx-apply {epic}
               parallel where deps allow
               log.md appends, tasks.md checkboxes update
checkpoint   → optional sanity check every ~10 tasks
verify       → /opsx-verify {epic}
               runs verify command from .opsx.yaml
               green → verified, red → fix and retry
archive      → /opsx-archive {epic}
               only if verified, all green
```
