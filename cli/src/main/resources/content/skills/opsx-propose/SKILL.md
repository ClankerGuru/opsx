---
name: opsx-propose
description: Scaffold a new opsx change (proposal.md, design.md, tasks.md) from a prompt or spec. Activates when the user wants to propose, start, or plan a new code change following the opsx workflow.
---

# /opsx-propose

> Related: `/opsx-apply` (next step) · `/opsx-continue` · `/opsx-feedback` · `/opsx-ff` · `/srcx-context` (before structural changes)

## Purpose

Create `opsx/changes/<name>/` with the three workflow artifacts —
`proposal.md` (what + why), `design.md` (how), `tasks.md` (atomic
checklist) — plus a `.opsx.yaml` status file.

## Preconditions

- Running from the project root (the dir containing `settings.gradle.kts`).
- The `zone.clanker.opsx` settings plugin is applied.
- No existing `opsx/changes/<name>/` directory with the chosen name
  (the task refuses to overwrite).

## Inputs

| Argument | Purpose |
|---|---|
| Request | Free-text description from the user; name is derived from the first 4 words |
| Spec name | Explicit slug — skips name derivation |
| Change name | Override derived name |

At least one of the request or the spec name should be present;
otherwise the change is named `untitled-change`.

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

**Before delegating, classify the change.** If the change touches
no production code — e.g. documentation rewrites, skill edits,
config tweaks, CI-only changes — fill `proposal.md`, `design.md`,
and `tasks.md` inline and SKIP the `@scout` call in step 2 and the
`@architect` call in step 3. The `@lead` call inside step 2 may
also be inlined when there is no non-obvious framing to delegate.
Do not invoke a sub-agent that has no architectural decision to
make. A blank `design.md` section is better than a sub-agent
padding space to justify the call.

1. **Log + do**: call
   `opsx log lead start - scaffold-change`, create the change
   skeleton below, then call
   `opsx log lead done - skeleton-written`. This is the
   `/opsx-propose <request>` agent workflow; there is no
   `opsx propose` shell subcommand.

   Create `opsx/changes/<name>/` with:
   - `.opsx.yaml` — `status: draft`, `name: <name>`
   - `proposal.md` — contains the raw prompt
   - `design.md` — skeleton
   - `tasks.md` — skeleton

2. **Delegate scout + lead in parallel — one turn, two `Agent`
   calls.** Scout's context map and lead's proposal skeleton
   touch disjoint artifacts (scout: `.srcx/context.md`-driven
   scoping; lead: `proposal.md` prose), so they never race. Issue
   both in the **same message**:

   - `Agent` with `subagent_type=scout` — prompt asks for the
     relevant hub classes, interfaces, and blast radius for the
     proposed work. Scout reads `.srcx/context.md` (or recommends
     `/srcx-context` if stale) and returns scoped paths + test
     coverage.
   - `Agent` with `subagent_type=lead` — prompt asks to fill in
     `opsx/changes/<name>/proposal.md` with problem, scope,
     constraints, affected systems, non-goals.

   Skip BOTH calls if the change touches no production code
   (docs / skills / config / CI only) — `@lead` fills
   `proposal.md` inline and you drop straight to step 4.

3. **Delegate (skip if no production code changes)**: use the
   `Agent` tool with `subagent_type=architect` and a prompt to
   fill in `opsx/changes/<name>/design.md` — approach, package
   layout, files to change, dependency direction, test strategy,
   acceptance criteria, and any Konsist rules that should enforce
   structural decisions. Pass along scout's context from step 2.

4. **Delegate**: use the `Agent` tool with
   `subagent_type=developer` and a prompt to fill in
   `opsx/changes/<name>/tasks.md` with atomic tasks derived from
   the design. Required format:

   ```
   - [ ] a1b2c3d4e5 | Task name here
       Description of what to do.
       Can span multiple lines with 4-space indent.
     depends: none

   - [ ] f6g7h8i9j0 | Another task
       Description of this task.
     depends:
       - a1b2c3d4e5
   ```

   Each task MUST have a unique 10-character alphanumeric ID after
   the checkbox. Use `depends: none` or a dependency list. Pair
   tests with implementations. Be specific about file paths and
   function names. Prefer the simplest approach.

5. **Audit inline**: before finalizing `status: draft`, re-read
   `proposal.md`, `design.md`, and `tasks.md` inline. Ask three
   questions and delete anything the answers don't defend:

   - **Can any task be deleted without the goal slipping?** If
     yes, delete it.
   - **Do any two tasks assert the same invariant?** If yes, fold
     into one.
   - **Does any new file/abstraction pay its keep against just
     editing an existing file?** If no, drop it.

   The audit is by the same agent that wrote the artifacts — no
   sub-agent is invoked. Run it once. Commit the trimmed result
   to disk before step 6.

6. **Log + do**: call
   `opsx log lead start - leave-as-draft`, confirm
   `.opsx.yaml` status is still `draft` (the next command,
   `/opsx-apply`, transitions it to `in-progress`), then call
   `opsx log lead done - status=draft`.

## Post-state

```
opsx/changes/<name>/
    .opsx.yaml      status: draft
    proposal.md     filled in
    design.md       filled in
    tasks.md        filled in with atomic [ ] tasks
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

## Scope minimization

Endurance means finishing what the user asked for, not maximizing
what's possible. Before writing the design and task list, state
the **minimum change set** that satisfies the prompt. Every
addition beyond that minimum must name what it buys that editing
existing files cannot.

- Prefer editing existing files over creating new ones.
- Prefer expanding existing docs pages over adding new ones.
- Prefer one diagram that covers the topic over three that carve
  it into slices.
- New abstractions, helpers, or subclasses only when the task
  genuinely cannot be done without them.

This rule ranks **above** "complete the job end-to-end" when they
conflict. Endurance forbids deferral; scope minimization forbids
inflation. They are not in tension when read correctly: finish
the *needed* work; do not invent extra.

## Test-cost rule

Tests earn their keep by guarding an invariant that would
otherwise be hard to notice when it breaks. Apply this filter
when proposing tests in `tasks.md`:

- **One test per invariant**, not one test per file. Two tests
  asserting the same thing is one test too many — fold them.
- Prefer **one targeted assertion** close to the invariant over a
  broad scaffolding test that re-validates the framework.
- A test that would pass even if the feature were deleted is not
  a test — delete it.
- Drift tests (asserting docs match code, filenames match
  manifest entries, etc.) are high-value when they catch real
  drift and low-value when they duplicate a Konsist rule already
  enforcing the same thing. Check before adding.

This rule applies at propose-time, not apply-time. `@qa` may still
add tests during verification if a real bug surfaces.

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

- **Directory already exists** — do not overwrite it. Pick a
  different change name or delete the stale dir.
- **No request and no spec name** — scaffold `untitled-change`;
  rename before committing.
- **`.srcx/` missing and structural change planned** — pause, run
  `/srcx-context`, resume.

## Next step

`/opsx-apply <name>` — execute the tasks. If the user wants
to refine first, iterate on the files directly or use
`/opsx-feedback`.

## Related skills

- `/srcx-context` — regenerate codebase map before design
- `/naming-conventions` — how to name the change (kebab-case,
  verb-object)
- `/package-structure` — informs the design section
- `/testing-patterns` — informs the test strategy in design.md
