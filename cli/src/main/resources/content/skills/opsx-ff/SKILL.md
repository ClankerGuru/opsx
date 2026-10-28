---
name: opsx-ff
description: Fast-forward a stale opsx change so its proposal and design match the current codebase after other changes landed. Activates when the user wants to rebase, refresh, or reconcile a change against the latest code. No Gradle backing task — this is an agent-driven refactor of the change files.
---

# /opsx-ff

> Related: `/opsx-feedback` · `/opsx-propose` · `/srcx-context`

## Purpose

Reconcile a change's `proposal.md` and `design.md` with the current
state of the codebase. Useful when another change landed that moved
files, renamed APIs, or replaced patterns referenced in this
change.

This is a **manifest-only** skill — no Gradle task. The agent
edits the change files directly.

## Preconditions

- `opsx/changes/<name>/` exists with `proposal.md`, `design.md`,
  `tasks.md`.
- `.opsx.yaml` status is `draft`, `active`, or `in-progress`.
- `.srcx/context.md` is fresh (run `/srcx-context` first if not).

## Steps

1. Read the change files: `proposal.md`, `design.md`, `tasks.md`.

2. Re-read the current codebase context — especially:
   - `.srcx/context.md` (top-level map)
   - `.srcx/hub-classes.md` (things with high blast radius)
   - `.srcx/interfaces.md` (contracts)
   - Each included build's `.srcx/context.md` if the change crosses
     builds

3. Identify drift:
   - Files mentioned in design that have moved, been renamed, or
     deleted.
   - APIs referenced that have changed signatures.
   - Patterns recommended that a newer pattern has superseded.
   - New dependencies or removed dependencies.

4. Update `proposal.md` — keep the original intent intact. Note
   what changed in the landscape ("as of <date>, X now lives in
   Y").

5. Update `design.md` — point at the current file paths and APIs.
   If a task is now a no-op (work already done by another change),
   record that.

6. Update `tasks.md`:
   - Add new `[ ]` tasks for work the drift introduces.
   - Mark obsolete tasks strike-through in the body; don't delete
     IDs.
   - Adjust `depends:` edges.

7. Leave `.opsx.yaml` status unchanged.

## Post-state

`proposal.md`, `design.md`, `tasks.md` aligned with current
codebase. Change is ready to resume via `/opsx-continue`.

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

- **`.srcx/` missing or stale** — run `/srcx-context`, then retry.
- **Drift is too large** — fast-forward isn't reconciling; the
  change has effectively been subsumed. Archive it or rewrite as a
  new proposal.

## Related skills

- `/srcx-context` — regenerate the codebase map before ff
- `/opsx-feedback` — for user-initiated changes (vs codebase drift)
- `/opsx-continue` — resume work after ff
