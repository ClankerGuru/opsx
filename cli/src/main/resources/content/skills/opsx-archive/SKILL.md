---
name: opsx-archive
description: Archive a verified or completed opsx change by moving it from opsx/changes/ to opsx/archive/. Activates when the user wants to archive, close, or finalize a change.
---

# /opsx-archive

> Related: `/opsx-verify` (prerequisite) · `/opsx-bulk-archive` · `/opsx-status`

## Purpose

Move a finished change out of the active working set. Archived
changes are preserved verbatim for history; they no longer appear
in `/opsx-status` default output.

## Preconditions

- `.opsx.yaml` status is one of `completed`, `done`, or `verified`.
  Archive refuses any other status.
- The corresponding PR has been merged (enforced socially, not by
  the task).

## Inputs

| Argument | Purpose |
|---|---|
| `<name>` | Change name (required) |

## Activity setup (run first)

Before any real work, export the active change name so the hook and
the `opsx log` writer can find the log:

```bash
export OPSX_ACTIVE_CHANGE=<name>
```

Check progress with a one-shot `opsx status --change <name>`; it
prints once, so rerun it after each operation.

**Every step that does real work must be bracketed with
`opsx log`** — no CLI-specific hook is involved, so
logging is the caller's responsibility. This applies whether the
step delegates to a subagent or runs inline in the main loop.

Pattern for an inline step:

```bash
opsx log <agent> start <task-id|-> <one-line>
# ... do the work ...
opsx log <agent> done  <task-id|-> <one-line>
```

Pattern for a delegated step: call `start` just before invoking the
`Agent` tool, and `done` (or `failed`) immediately after the
subagent returns.

`opsx log` is shared across every AI CLI
(claude / copilot / codex / opencode) — one script, one path, same
behavior everywhere.

## Steps

1. **Log + do**: call
   `opsx log lead start - archive-move`, then (this is the
   `/opsx-archive <name>` agent workflow; there is no
   `opsx archive` shell subcommand):
   - Verify the status is archive-eligible.
   - Move `opsx/changes/<name>/` → `opsx/archive/<name>/`.
   - Preserve every file inside (`.opsx.yaml`, `proposal.md`,
     `design.md`, `tasks.md`, `feedback.md` if present).

   Then call `opsx log lead done - archived=<name>`.

2. **Log + do**: call
   `opsx log lead start - confirm-archive`, confirm the
   archived path in the output, then call
   `opsx log lead done - path=opsx/archive/<name>`.

3. **Delegate** (optional): if the archive move needs to be
   committed to git, use the `Agent` tool with
   `subagent_type=forge` and a prompt to stage the move, commit
   with a conventional message, and push.

## Post-state

```
opsx/archive/<name>/     ← moved from opsx/changes/
    .opsx.yaml
    proposal.md
    design.md
    tasks.md
    feedback.md           (if created by /opsx-feedback)
```

## Asking the user

At any decision gate in this flow, ask the user **one question at a
time**, formatted as a numbered multiple-choice list, with a final
option that lets the user override with their own answer.

Template:

> **Question:** <the single thing you need to know>
>
> 1. <option A>
> 2. <option B>
> 3. <option C>
> 4. other — tell me

Never batch multiple questions in one turn. After the user answers,
ask the next question in the same format. Never include "defer",
"later", or "follow-up change" as an option — see Endurance contract.

Fixes are not questions. If the audit, design, or task list says to
fix a broken thing, just fix it. Only ask the user about genuine
trade-offs, intent, or scope — never about repairs.

## Failure modes

- **Status not archivable** — task prints the current status and
  exits non-zero. Transition with `/opsx-verify` first.
- **Target dir already exists under archive/** — name collision
  from a prior archive. Rename the incoming change first.

## Batch variant

`/opsx-bulk-archive` archives every eligible change in one pass —
useful after a release.

## Related skills

- `/opsx-verify` — transitions to `verified`
- `/opsx-bulk-archive` — archive all eligible in one shot
- `/git-workflow` — commit the archive move alongside the merged PR
