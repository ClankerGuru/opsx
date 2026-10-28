---
name: opsx-feedback
description: Record user feedback on an in-progress opsx change and update design.md and tasks.md to reflect it. Activates when the user wants to refine, correct, or amend a change mid-flight.
---

# /opsx-feedback

> Related: `/opsx-apply` · `/opsx-continue` · `/opsx-ff` · `/opsx-propose`

## Purpose

Capture mid-flight feedback from the user and fold it into the
change's design and task list without losing the original intent.

## Preconditions

- `opsx/changes/<name>/` exists.
- `.opsx.yaml` status is `draft`, `active`, or `in-progress`
  (feedback on an archived change doesn't apply — open a new
  change instead).

## Inputs

| Argument | Purpose |
|---|---|
| `<name>` | Target change (required) |
| Feedback text | User-supplied feedback (required) |

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
   `opsx log lead start - record-feedback`, append a dated entry
   with the user-supplied feedback text to
   `opsx/changes/<name>/feedback.md`, then call
   `opsx log lead done - feedback-recorded`.

2. **Log + do**: call
   `opsx log lead start - read-artifacts`, read the
   existing `proposal.md`, `design.md`, `tasks.md`, then call
   `opsx log lead done - artifacts-loaded`.

3. **Delegate**: use the `Agent` tool with
   `subagent_type=architect` and a prompt to update `design.md` —
   explicit about what changes, what stays, and why. Keep the
   original intent intact; describe the delta.

4. **Delegate**: use the `Agent` tool with
   `subagent_type=developer` and a prompt to update `tasks.md`:
   - Add new `[ ]` tasks for work the feedback introduces.
   - Mark superseded tasks with a strike note (`~~`) in the body
     rather than deleting; downstream dependencies may still
     reference their IDs.
   - Adjust `depends:` edges.
   - Keep existing `[x]` tasks untouched unless they need redoing
     — in which case flip to `[ ]` with a note.

5. **Log + do**: call
   `opsx log lead start - handoff`, do **not** implement
   the feedback here — return control to the user (they invoke
   `/opsx-continue` when ready), then call
   `opsx log lead done - awaiting=/opsx-continue`.

## Post-state

```
opsx/changes/<name>/
    feedback.md       appended with new dated entry
    design.md         updated to reflect delta
    tasks.md          new/adjusted tasks
    .opsx.yaml        unchanged status
```

## Endurance contract

Every proposal is **one continuous effort**. When writing
`proposal.md`, `design.md`, and `tasks.md`:

- No `## Phase A / Phase B`, `## Group 0 / Group 1`, or any
  phase/group sub-headers.
- Tasks live under a single `# Tasks` heading as a flat ordered list
  with `depends:` edges between them.
- Never propose "defer to a follow-up change", "split into phases",
  or "do X now, Y later". Deferral = giving up. The contract is to
  complete the job end-to-end.
- Smaller atomic tasks are encouraged — grouping is not.

The only legitimate split is when the user **explicitly** asks to
split the work. Do not volunteer it.

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

- **Change doesn't exist** — stop and report it. Use
  `/opsx-propose` first.
- **Feedback contradicts already-merged work** — treat it as a new
  proposal via `/opsx-propose`; don't retcon.

## Next step

`/opsx-continue <name>` — pick up with the new task list.

## Related skills

- `/opsx-ff` — fast-forward against current codebase after feedback
- `/opsx-apply` `/opsx-continue` — execute the updated tasks
