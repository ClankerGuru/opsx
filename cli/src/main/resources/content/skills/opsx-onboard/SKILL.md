---
name: opsx-onboard
description: Guided tour of the project for a new contributor — architecture, build, agents, and opsx workflow. Activates when the user wants to onboard, tour, or introduce themselves to the project. No Gradle backing task — this is an agent-driven walkthrough.
---

# /opsx-onboard

> Related: `/opsx-explore` · `/opsx-status` · `/srcx-context`

## Purpose

Give a new contributor a scoped, depth-tailored tour of the project
— what it is, how to build it, how changes flow, and which agent
owns what.

## Preconditions

- Nothing required, but the tour is deeper if `.srcx/context.md`
  is fresh (run `/srcx-context` first).

## Inputs

Optional: a user-supplied prompt about their background
(experience level, familiar stacks). Tailor depth accordingly.

## Steps

1. **Architecture overview** — from `.srcx/context.md` if present,
   otherwise summarize from `settings.gradle.kts`, root
   `build.gradle.kts`, and `wrkx.json`. Cover:
   - Which included builds compose the workspace
   - What each plugin does (opsx, srcx, wrkx)
   - Top-level package layout per `/package-structure`

2. **Hub classes and entry points** — pull from
   `.srcx/hub-classes.md` and `.srcx/entry-points.md`. Five-class
   tour, no more: which classes most other code depends on, which
   classes are tasks / CLIs / tests.

3. **Build and test** — the commands that matter:
   - `./gradlew build` — compile everything
   - `./gradlew check` — unit + architecture + detekt + ktlint
   - `./gradlew koverVerify` — coverage gate
   - `./gradlew <plugin>-test` etc. for scoped runs

4. **Change workflow** — the opsx lifecycle. Point at
   `/opsx-propose` → `/opsx-apply` → `/opsx-verify` →
   `/opsx-archive`. Explain the three artifacts
   (`proposal.md`, `design.md`, `tasks.md`) and the status
   progression.

5. **Agents** — who handles what. Reference `AGENTS.md` or
   `CLAUDE.md` for the canonical list:
   - @lead — orchestrates
   - @scout — context
   - @architect — design
   - @developer — production code
   - @qa — tests
   - @forge — build/repos/PRs
   - @devOps — CI/CD

6. **Conventions** — point at the reference skills they'll reach
   for most: `/naming-conventions`, `/package-structure`,
   `/kotlin-conventions`, `/testing-patterns`.

## Post-state

None — purely informational. No files modified.

## Failure modes

- **No `.srcx/` and no understanding** — suggest running
  `/srcx-context` for a deeper tour.
- **User's background unknown** — ask one clarifying question,
  then proceed with a medium-depth default.

## Related skills

- `/opsx-explore` — answer specific Q&A after the tour
- `/srcx-context` — generate the map the tour draws from
- `/opsx-status` — show current work in flight
