---
name: opsx-status
description: Show opsx change status — either the ledger of all changes grouped by lifecycle state with progress bars, or the per-change task tree grouped by agent. Activates when the user wants to see progress on opsx work.
---

# /opsx-status

> Related: `/opsx-propose` · `/opsx-continue` · `/opsx-bulk-archive`

## Purpose

Render opsx status with the `opsx status` command — no Gradle.

Two modes:

1. **Ledger** (no args) — every change in `opsx/changes/` grouped
   by status with a lifecycle badge, a progress bar, percent
   complete, task count, and elapsed time.
2. **Tree** (one change name) — the change's tasks grouped by the
   agent that ran them, with a `(parallel)` tag when two tasks of
   the same agent overlap.

## Steps

Execute `opsx status` via the Bash tool, forwarding any
arguments the user passed. Do not summarize, interpret, or repeat
the output — the ledger/tree is self-explanatory. Only speak if
the user asks a follow-up.

Invocation map:

| User's args             | Command to run                                  |
|-------------------------|-------------------------------------------------|
| (none)                  | `opsx status`                         |
| `--archive`             | `opsx status --archive`               |
| `--only in-progress`    | `opsx status --only in-progress`      |
| `<name>`                | `opsx status --change <name>`         |

Lifecycle order (top → bottom in ledger):
`active` → `in-progress` → `draft` → `completed` → `verified` →
`archived` → `deleted` (last two only with `--archive`).

## Preconditions

- `opsx/changes/` exists (may be empty).
- `opsx status` is present and executable.

## Failure modes

- **No changes** — script prints `no changes found` and exits 0.
- **`.opsx.yaml` malformed** — entry falls back to `draft`; the
  rest of the report still prints.
- **Change not found** (tree mode) — script errors with the name
  it tried; check `opsx/changes/` and `opsx/archive/`.
- **Change is `deleted`** (tree mode) — prints
  `<name> is deleted — no tree to show` and exits 0.

## Related skills

- `/opsx-bulk-archive` — after reading status, archive the eligible
- `/opsx-continue` — resume one of the `in-progress` entries
