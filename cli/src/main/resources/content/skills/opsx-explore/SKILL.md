---
name: opsx-explore
description: Answer read-only questions about the codebase using srcx context reports and source files. Activates when the user wants to ask, explore, or understand how something works without modifying anything. No Gradle backing task.
---

# /opsx-explore

> Related: `/opsx-onboard` · `/srcx-context`

## Purpose

Answer a user's question about the codebase. Read-only — no edits,
no writes, no side effects.

## Preconditions

- A specific question or topic from the user.
- Preferably a fresh `.srcx/context.md` (run `/srcx-context` if
  stale).

## Activity setup (run first)

Before any real work, export the active change name so the hook and
the `opsx log` writer can find the log:

```bash
export OPSX_ACTIVE_CHANGE=<name>
```

Check progress with a one-shot `opsx status --change <name>`; it
prints once, so rerun it after each operation.

`/opsx-explore` is not tied to a change — export
`OPSX_ACTIVE_CHANGE=explore` (or any stable slug) so the log entry
lands in a predictable location.

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

1. **Delegate**: use the `Agent` tool with `subagent_type=scout`
   and a prompt that restates the user's question verbatim. Ask
   scout to start from `.srcx/context.md` (hub classes,
   interfaces, entry points, anti-patterns, cross-build links),
   follow references into source via Glob / Grep / Read (never
   execute code), and return:
   - The answer to the question.
   - File path + line number citations (`src/main/.../Foo.kt:42`).
   - Class and method names.
   - Cross-build links if the answer spans plugins.
   - Short load-bearing code snippets (under ~10 lines) when a
     quote is necessary.

2. **Log + do**: call
   `opsx log lead start - relay-answer`, relay scout's
   answer to the user unchanged (with citations intact), then call
   `opsx log lead done - answered`.

3. Do **not** modify any file. If the answer reveals work to do,
   suggest `/opsx-propose` as the next step.

## Post-state

None — strictly read-only.

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

- **`.srcx/` missing** — suggest `/srcx-context` to bootstrap, or
  proceed with direct grep but warn accuracy is lower.
- **Question too broad** — ask one clarifying question to scope
  down before searching.
- **Question requires running code** — explain and ask the user to
  run it (`! ./gradlew <task>`).

## Related skills

- `/srcx-context` — refresh the map before exploring
- `/opsx-onboard` — for a structured tour, not a single question
- `/opsx-propose` — if the exploration reveals work to do
