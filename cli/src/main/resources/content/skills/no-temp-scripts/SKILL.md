---
name: no-temp-scripts
description: Absolute prohibition on writing scripts, helpers, or intermediate files outside the workspace. Everything lives in-repo where the user can read, diff, and review it.
---

# /no-temp-scripts

> Related: `/git-workflow` · `/no-python`

## Rule

**Every script, helper, one-off, or intermediate file lives inside the
workspace.** No `/tmp`, no `/var/tmp`, no `$TMPDIR`, no `~/.cache`, no
agent-only hidden dirs. If you create it, the user can see it.

## Why

Hidden scratch files:
- cannot be code-reviewed,
- silently disappear across sessions (lost work),
- let agents take actions the user never sees,
- break reproducibility for the next contributor.

The user has explicitly forbidden this pattern.

## Where things go instead

| Kind of file | Workspace location |
|---|---|
| Throwaway shell helper | `scripts/<name>.sh` (or delete it when done) |
| CLI wrapper / hook body | `.claude/hooks/<name>.sh`, `.opsx/bin/<name>`, etc. |
| Build/test intermediates | `build/` (already git-ignored by Gradle) |
| Gradle scratch output | `build/tmp/` — stays in-repo, inspectable |
| Test fixtures | `src/test/resources/` |
| Diff/patch staging | commit it, or keep it under `scratch/` in the repo root |

## When you *think* you need temp

- "I just need to pipe this somewhere" → pipe to a file in
  `build/scratch/` or inline with `tee`.
- "The tool insists on a temp path" → let it write there, then
  immediately move the output into the workspace and tell the user.
- "It's just for my own thinking" → use a comment, or a file under
  `notes/` in the repo.

## Unit-test scaffolding is the one exception

`File.createTempFile(...).apply { deleteOnExit() }` inside a JVM test
is acceptable — it is a testing idiom, scoped to a single test run,
cleaned up automatically. This exception does NOT extend to shell
scripts, hook bodies, or interactive tool plumbing.

## Enforcement

When reviewing an agent's work, reject any diff that creates files
outside the workspace. When an agent proposes a solution that uses
temp dirs, redirect them to a workspace path before implementation
begins.

## Scope

Applies to every agent, every CLI, every project under the clanker
workspace. No exceptions without explicit user approval.
