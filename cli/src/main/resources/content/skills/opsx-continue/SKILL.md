---
name: opsx-continue
description: Resume work on an in-progress opsx change from the first unchecked task. Activates when the user wants to continue, resume, or pick up a change that was already started.
---

# /opsx-continue

> Related: `/opsx-apply` (initial start) · `/opsx-feedback` · `/opsx-verify` (next step) · `/opsx-ff`

## Purpose

Pick up an in-progress change where it was left off. Identical in
spirit to `/opsx-apply` but safe to run when status is already
`in-progress` or when the last session was interrupted.

## Preconditions

- `opsx/changes/<name>/` exists with the three artifacts.
- `.opsx.yaml` status is `draft`, `active`, `in-progress`, or
  `completed` (completed means tasks left, manual audit needed).
- Current working tree is clean or the in-flight changes relate to
  this change (not another change's work).

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
   `opsx log lead start - show-progress`, make sure `.opsx.yaml`
   status is `in-progress` and print the checked/unchecked counts
   from `tasks.md`, then call
   `opsx log lead done - progress-shown`. This is the
   `/opsx-continue <name>` agent workflow; there is no
   `opsx continue` shell subcommand.

2. **Log + do**: call
   `opsx log lead start - read-artifacts`, read
   `proposal.md`, `design.md`, `tasks.md` inline, then call
   `opsx log lead done - artifacts-loaded`.

3. **Log + do**: call
   `opsx log lead start - pick-next-task`, find the
   first unchecked `[ ]` task whose `depends:` are all `[x]`,
   then call `opsx log lead done - task=<id>` (or
   `opsx log lead done - all-blocked` and stop with a
   report if none is available).

4. **Delegate**: use the `Agent` tool with
   `subagent_type=developer` for the picked task, then
   `subagent_type=qa` to run the relevant tests. Mark `[x]` only
   after qa's return confirms verification (same rule as
   `/opsx-apply`).

5. Repeat step 3–4 until no unchecked tasks remain, then
   **log + do**: call
   `opsx log lead start - finalize-status`, transition
   `.opsx.yaml` to `completed`, then call
   `opsx log lead done - status=completed`.

## Post-state

Same as `/opsx-apply` when done. If stopped mid-way, status stays
`in-progress` and the next invocation resumes cleanly.

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

- **All remaining tasks blocked by a failing earlier task** — the
  earlier task should not have been marked `[x]`. Revert it, fix,
  mark again.
- **Design has drifted under you** — run `/opsx-ff`.
- **You've realised design is wrong** — run `/opsx-feedback` with
  the correction, then resume.

## Next step

`/opsx-verify <name>` once all tasks are checked.

## Related skills

- `/opsx-apply` — same machinery, different entry point
- `/opsx-status` — pre-flight check: which changes are in-progress
- `/testing-patterns` — test coverage before marking done
