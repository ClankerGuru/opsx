---
name: wrkx-pull
description: Fetch and fast-forward baseBranch for every repo defined in wrkx.json. Activates when the user wants to pull, update, or sync the latest commits across all workspace repos.
---

# /wrkx-pull

> Related: `/wrkx-clone` · `/wrkx-checkout` · `/wrkx-status`

## Purpose

Sync every repo to the tip of its `baseBranch` without merging or
rebasing. Dirty repos are skipped (reported), clean repos are
fast-forwarded.

## Preconditions

- Each repo has been cloned (see `/wrkx-clone`).
- `baseBranch` is declared for the repo (default: `main`).
- The remote is reachable.

## Variants

| Invocation | Scope |
|---|---|
| `/wrkx-pull` | Every repo |
| `/wrkx-pull-<repo>` | Only the named repo |

## Steps

1. Aggregate pull:

   ```bash
   ./gradlew -q wrkx-pull
   ```

2. Per-repo pull:

   ```bash
   ./gradlew -q wrkx-pull-<repo>
   ```

3. For each repo:
   - If the working tree has uncommitted changes → **skip**,
     report as dirty.
   - Otherwise `git fetch` then `git merge --ff-only origin/<baseBranch>`.
   - If the branch is not a fast-forward, the pull aborts for that
     repo with a diverged warning.

4. Summary at the end lists: pulled, skipped (dirty), skipped
   (diverged), failed.

## Post-state

Each clean repo on `baseBranch` at the remote's HEAD. Dirty and
diverged repos unchanged.

## Failure modes

- **Dirty working tree** — pull skipped; commit, stash, or discard
  before retrying.
- **Diverged branch** — manual rebase/merge required. Never run a
  non-ff pull on a shared branch without thinking.
- **Detached HEAD** — pull skipped for that repo; checkout a
  branch first.
- **Remote unreachable** — that repo fails, others continue.

## Related skills

- `/wrkx-status` — pre-flight check (which repos are dirty)
- `/wrkx-checkout` — if you need to switch branches first
- `/git-workflow` — rebase/merge a diverged branch manually
