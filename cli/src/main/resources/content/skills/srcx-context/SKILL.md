---
name: srcx-context
description: Regenerate the .srcx/ codebase context report (hub classes, interfaces, entry points, anti-patterns, cross-build links) from current Kotlin source. Activates when the user wants to refresh, regenerate, or rebuild the srcx map after code changes.
---

# /srcx-context

> Related: `/srcx-clean` (paired removal) · `/opsx-explore` `/opsx-onboard` `/opsx-propose` (consumers)

## Purpose

Scan Kotlin source via PSI and produce a set of Markdown reports
under `.srcx/` that summarize the codebase's structure, hub
classes, public interfaces, entry points, anti-patterns, and
cross-build dependency flow. Agents (especially @scout) read these
to answer context questions without grepping the whole tree.

## Preconditions

- Running from the project root (root or an included build).
- The `zone.clanker.srcx` settings plugin is applied.
- Source sets are compilable (PSI parsing does not require
  successful compile, but malformed Kotlin will produce partial
  maps).

## Steps

1. Regenerate:

   ```bash
   ./gradlew -q srcx-context
   ```

2. The task writes under `.srcx/` in the project root and inside
   each included build:

   ```
   .srcx/
       context.md           top-level map linking the below
       hub-classes.md       most-depended-on classes (high blast radius)
       interfaces.md        public API contracts
       entry-points.md      app / test / mock entry points
       anti-patterns.md     detected violations
       cross-build.md       how included builds depend on each other
   ```

3. Each included build gets its own `.srcx/` tree — the root-level
   `cross-build.md` links them.

4. Run this whenever code has moved materially since the last
   generation. Agents rely on `.srcx/` being fresh.

## Post-state

```
.srcx/context.md             root map
.srcx/{hub-classes,interfaces,entry-points,anti-patterns,cross-build}.md
<each included build>/.srcx/context.md + section files
```

## Failure modes

- **Kotlin source fails to parse** — task reports the file, emits
  a partial map covering the rest. Fix the parse error and rerun.
- **No Kotlin sources at all** — task produces an empty
  `context.md` with a note.
- **Included build not yet cloned** — its section in
  `cross-build.md` is empty; run `/wrkx-clone-<name>`, then
  re-run `/srcx-context`.

## Cadence

- After merging a change that moved files or added new public
  APIs.
- Before running `/opsx-propose` for a structural change.
- Before `/opsx-ff` to fast-forward a stale change.

## Related skills

- `/srcx-clean` — delete `.srcx/` before a fresh rebuild
- `/opsx-explore` — consumes the generated reports
- `/opsx-onboard` — uses the reports to tour new contributors
- `/package-structure` — interpret the layout reported
- `/konsist` — static rules that complement anti-patterns
