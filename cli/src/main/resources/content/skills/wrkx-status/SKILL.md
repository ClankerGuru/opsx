---
name: wrkx-status
description: Generate a workspace status report at .wrkx/repos.md showing branch, dirty state, ahead/behind counts, and divergence for each repo. Activates when the user wants a health check, status report, or overview of all workspace repos.
---

# /wrkx-status

> Related: `/wrkx-pull` · `/wrkx-checkout` · `/wrkx-prune`

## Purpose

Produce a per-repo snapshot of workspace health: current branch,
dirty files, ahead/behind counts vs the remote, and divergence
classification.

## Preconditions

- Repos are cloned (`/wrkx-clone`).
- `wrkx.json` is present and valid.

## Steps

1. Run:

   ```bash
   ./gradlew -q wrkx-status
   ```

2. For each repo the task computes:
   - Current branch (or detached SHA).
   - Count of uncommitted changes (untracked + modified).
   - Ahead / behind counts vs `origin/<currentBranch>`.
   - State classification: `clean`, `dirty`, `ahead`, `behind`,
     `diverged`.

3. Output is written to `.wrkx/repos.md` in the workspace root —
   one Markdown section per repo. The file is overwritten on each
   run.

4. Stdout shows a short summary table.

## Post-state

```
.wrkx/
    repos.md      one section per repo, overwritten per run
```

## Failure modes

- **Repo on disk not in `wrkx.json`** — reported under an "untracked
  dirs" section. See `/wrkx-prune` to clean them.
- **Repo in `wrkx.json` not on disk** — reported as missing. Run
  `/wrkx-clone-<name>` to fix.
- **Remote unreachable** — ahead/behind show as `?`; other fields
  still computed.

## Related skills

- `/wrkx-pull` — after reviewing status, pull clean repos
- `/wrkx-checkout` — after status, align branches
- `/wrkx-prune` — remove dirs not in wrkx.json
- `/git-workflow` — interpret divergence
