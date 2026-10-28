---
name: opsx-verify
description: Verify a completed opsx change against its design, run build + tests, and transition status to verified. Activates when the user wants to verify, QA, or sign off a change.
---

# /opsx-verify

> Related: `/opsx-apply` (prerequisite) · `/opsx-archive` (next step) · `/opsx-feedback`

## Purpose

Confirm the implementation matches `design.md` and `tasks.md`, that
all tests pass, and that the repo is PR-ready. Transition
`.opsx.yaml` status to `verified`.

## Preconditions

- `.opsx.yaml` status is `completed` (every task `[x]`).
- Clean working tree or uncommitted state that belongs to this
  change only.

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
   `opsx log lead start - read-artifacts`, read
   `proposal.md`, `design.md`, `tasks.md` inline and mentally diff
   the repo against the design, then call
   `opsx log lead done - artifacts-loaded`.

2. **Log + do**: call
   `opsx log lead start - check-task-completion`,
   confirm every task in `tasks.md` is `[x]`, no acceptance
   criterion in `design.md` is skipped, no scope creep — then
   call `opsx log lead done - tasks=all-checked` (or
   fail loudly and stop).

3. **Delegate**: use the `Agent` tool with `subagent_type=qa` and
   a prompt asking for full verification. The qa agent runs a
   **single Gradle invocation** that collapses verify,
   `check` (unit + arch + detekt + ktlint), `koverVerify`
   (coverage threshold), and `functionalTest` (end-to-end,
   opt-in) into one task graph. Gradle parallelizes across
   projects and skips up-to-date work automatically, so running
   four separate invocations — as earlier versions of this skill
   did — burns time for no gain.

   ```bash
   ./gradlew check --parallel
   ```

   Add `koverVerify` and `functionalTest` to that invocation only
   when the affected repository defines them. Step 4 transitions
   `.opsx.yaml` status to `verified` explicitly; no task does it
   automatically.

   qa may patch production code if a bug is found during
   verification; hand back to @developer (via another `Agent`
   call with `subagent_type=developer`) if a whole task is wrong.

4. **Log + do**: on pass, call
   `opsx log lead start - transition-verified`, set
   `.opsx.yaml` status to `verified`, then call
   `opsx log lead done - status=verified`. On fail,
   **do not** touch status — call
   `opsx log lead done - verify=failed` with the exact
   failure summary (which test, which acceptance criterion).

## Post-state

```
opsx/changes/<name>/
    .opsx.yaml      status: verified
```

All checks green. Ready for `/opsx-archive` and PR creation.

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

- **A test fails** — fix it (if shallow) via @qa, or report to
  @developer; status stays `completed`.
- **Design acceptance criterion missed** — open `/opsx-feedback`
  with the gap; move back to `in-progress`.
- **Coverage below threshold** — see `/kover`; add unit tests for
  the uncovered `internal` methods.
- **Architecture rule violated** — see `/konsist`; fix the
  package/import that broke the rule.

## Next step

`/opsx-archive <name>` — move the change to `opsx/archive/`.
Usually alongside `@forge` creating the PR via `/git-workflow` +
`/gh-cli`.

## Related skills

- `/testing-patterns` — what `check` covers
- `/kover` — coverage rules and exclusions
- `/konsist` — architecture rule enforcement
- `/ktlint` `/detekt` — static analysis gates
