---
name: opsx-apply
description: Implement a proposed opsx change by working through tasks.md in dependency order, transitioning status to in-progress then completed. Activates when the user wants to execute, implement, or apply a proposed change.
---

# /opsx-apply

> Related: `/opsx-propose` (prerequisite) · `/opsx-continue` (resume) · `/opsx-verify` (next step) · `/opsx-feedback` · `/opsx-ff`

## Purpose

Execute the atomic task list in `tasks.md` for a given change. Mark
each task `[x]` after implementation **and** verification. Transition
`.opsx.yaml` status from `draft` → `in-progress` → `completed`.

## Preconditions

- `opsx/changes/<name>/` exists with `proposal.md`, `design.md`,
  `tasks.md`, `.opsx.yaml`.
- `.opsx.yaml` status is `draft`, `active`, or `in-progress` (not
  `completed`, `verified`, or `archived`).
- Build is green on main (run `./gradlew check` to confirm).

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

Regardless of host, print the side-pane renderer banner so the user
can watch the pretty tree even when a host UI truncates or reformats
the live stderr stream:

```
>>> Check status: opsx status --change <name>
>>>          or:    tail -f .opsx/last-log.txt
```

This is redundant on Claude (which passes stderr through inline) but
uniform across hosts simplifies the user mental model.

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
   `opsx log lead start - transition-status`, set `status: in-progress`
   in `.opsx.yaml` and print the plan, then call
   `opsx log lead done - status=in-progress`. This is the
   `/opsx-apply <name>` agent workflow; there is no `opsx apply`
   shell subcommand.

2. **Log + do**: call
   `opsx log lead start - read-artifacts`, read the three
   source-of-truth files inline, then call
   `opsx log lead done - artifacts-loaded`:

   - `opsx/changes/<name>/proposal.md` — intent
   - `opsx/changes/<name>/design.md` — contract
   - `opsx/changes/<name>/tasks.md` — checklist

3. **Delegate via the ready frontier, in parallel.** Loop until
   every task is `[x]`:

   1. **Compute the frontier.** Parse `tasks.md` with
      `TaskParser.readyFrontier(tasks)` — the pure helper returns
      every TODO task whose `depends:` list is fully satisfied by
      already-`[x]` tasks. Do NOT iterate the file linearly; the
      design decided on dependency-ordered parallelism, not
      textual order.
   2. **Dispatch the whole frontier in one turn.** For every task
      on the frontier, issue one `Agent` call with
      `subagent_type=developer` in the **same turn** — a single
      message with N `Agent` tool uses, one per task. Each prompt
      names the task ID, the exact files to touch, and the
      `design.md` acceptance criteria it fulfils. The host's
      parallel cap (see Host-specific rules below) may force
      serialization; otherwise every ready task runs in parallel.
   3. **Collect every result before moving on.** Wait for all
      `Agent` calls from the turn to return. For each success, log
      `opsx log developer done <id> <one-line>`; for
      each failure, log `developer failed <id> <reason>` and do
      NOT mark `[x]`.
   4. **Re-read `tasks.md`** after every turn. The developer agent
      flips `[ ]` → `[x]` for tasks it completes, which unlocks
      new frontier edges. Recompute the frontier from the fresh
      file contents — do not reuse the previous frontier snapshot.
   5. **Repeat** from step 1 until the frontier is empty. If the
      frontier is empty *and* any `[ ]` tasks remain, fail with
      `lead failed - frontier-stall` — the graph is cyclic or a
      dependency was mistyped.

   **Hardened `[x]` gate.** Before marking a task `[x]`, run
   `./gradlew detekt ktlintCheck --console=plain`. If either is
   red — **including "pre-existing" issues in files the task did
   not touch** — do NOT mark `[x]`. Log
   `lead failed <id> lint-gate-red` and stop the task. The red
   lint may be owned by another task; apply stops until the tree
   is green again. No lawyering.

   **No scout during apply.** Apply MUST NOT invoke
   `Agent subagent_type=scout`. If a task description is
   insufficient, fail with `lead failed <id> task-underspecified`
   and ask the user to re-run propose or edit `tasks.md`.
   Re-scouting belongs in propose or `/opsx-feedback`, not apply.

4. **Delegate**: once implementation tasks for a slice are done,
   use the `Agent` tool with `subagent_type=qa` and a prompt naming
   the tests to run (`./gradlew test`, `./gradlew slopTest`, or
   `./gradlew check`). Revert any premature `[x]` if qa reports a
   failure.

5. **Log + do**: when every task is `[x]`, call
   `opsx log lead start - finalize-status`, edit `.opsx.yaml` to
   set status to `completed`, then call
   `opsx log lead done - status=completed`.

6. **Delegate**: use the `Agent` tool with `subagent_type=forge` to
   commit the change and prepare a PR after `/opsx-verify` passes.

## Host-specific rules

The shared rules above apply to every host. The block below only
applies when `$OPSX_HOST=copilot` (set automatically by
`.opsx/bin/_opsx-host.sh`, or exportable by the user).

### Copilot (`$OPSX_HOST=copilot`)

Copilot's provider rate-limit kicks in well below the documented
subagent cap and manifests as `agent limit reached` — a hard reject
with no queue. Running many parallel delegations also contaminates
lint state across the shared working tree. To avoid silent drops
and cross-task contamination:

- **Serial by default.** `OPSX_MAX_PARALLEL_AGENTS=1` unless the
  user exports a higher value. Iterate ready tasks one at a time;
  only when `$OPSX_MAX_PARALLEL_AGENTS > 1` may you issue multiple
  `Agent` calls per turn.
- **Retry on `agent limit reached`.** If a delegation returns
  `agent limit reached`, do NOT record `lead failed`. Instead:
  sleep 30s and retry; on second failure sleep 60s and retry; on
  third failure sleep 120s and retry; on fourth failure log
  `lead failed <id> agent-limit-exhausted` and move on.
  **Never** allow an un-retried `agent limit reached` to drop a
  task silently.
- **Extra banner line.** Prepend
  `>>> Copilot apply: serial by default (OPSX_MAX_PARALLEL_AGENTS=1)`
  to the side-pane renderer banner emitted in Activity setup.

## Post-state

```
opsx/changes/<name>/
    .opsx.yaml      status: completed
    tasks.md        every task [x]
```

Source code updated per `design.md`. All tests green.

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

- **Status already completed/verified** — skill refuses. Use
  `/opsx-continue` only if tasks are still unchecked.
- **Task marked [x] but tests fail** — revert the `[x]`, fix the
  failure, mark again.
- **Design drift discovered mid-apply** — pause, run
  `/opsx-feedback` to update design, then resume with
  `/opsx-continue`.
- **Upstream code moved since propose** — run `/opsx-ff` to
  fast-forward proposal + design, then continue.

## Next step

`/opsx-verify <name>` — runs tests, confirms design
compliance, transitions to `verified`.

## Related skills

- `/opsx-propose` — created the change
- `/opsx-continue` — resume if interrupted
- `/opsx-verify` — validate after apply
- `/testing-patterns` — where tests go (test / slopTest / functionalTest)
- `/ktlint` `/detekt` — run before marking tasks done
- `/git-workflow` — commit after each task (or logical group)
