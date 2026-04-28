---
name: wrkx
description: List every registered wrkx workspace task (clone, pull, checkout, status, prune) plus the per-repo variants. Activates when the user wants to see, list, or discover what workspace commands are available.
---

# /wrkx

> Related: `/wrkx-clone` · `/wrkx-pull` · `/wrkx-checkout` · `/wrkx-status` · `/wrkx-prune`
> Manages: `wrkx.json`

## Purpose

Print the catalog of every `wrkx-*` task registered by the wrkx
Gradle plugin, including per-repo variants (`wrkx-clone-<repo>`,
`wrkx-pull-<repo>`, `wrkx-checkout-<repo>`).

## Preconditions

- Running from the workspace root (the repo containing
  `settings.gradle.kts` and `wrkx.json`).
- The `zone.clanker.wrkx` settings plugin is applied.
- `wrkx.json` is valid (an array of `{ name, path, category,
  remote?, baseBranch?, workingBranch? }` entries).

## Steps

1. Run:

   ```bash
   ./gradlew -q wrkx
   ```

2. Output lists:
   - Aggregate tasks: `wrkx-clone`, `wrkx-pull`,
     `wrkx-checkout`, `wrkx-status`, `wrkx-prune`.
   - Per-repo tasks: `wrkx-clone-<repo>`, `wrkx-pull-<repo>`,
     `wrkx-checkout-<repo>` for each entry in `wrkx.json`.
   - A short description per task.

## Post-state

None — read-only listing.

## Failure modes

- **`wrkx.json` missing or malformed** — task errors with a parse
  hint. Fix the file and retry.
- **Plugin disabled** (via `-Pzone.clanker.wrkx.enabled=false`) —
  task registers nothing; `/wrkx` shows an empty list.

## Related skills

- `/wrkx-clone` — bootstrap a fresh workspace
- `/wrkx-status` — health snapshot across repos
- `/gradle-composite-builds` — understand why wrkx matters
