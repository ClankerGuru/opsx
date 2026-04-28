---
name: wrkx-checkout
description: Checkout workingBranch (or baseBranch as fallback) for every repo defined in wrkx.json. Activates when the user wants to switch branches or align the workspace on a consistent branch across all repos.
---

# /wrkx-checkout

> Related: `/wrkx-clone` · `/wrkx-pull` · `/wrkx-status`

## Purpose

Align every repo on a consistent branch — its configured
`workingBranch` if declared, otherwise `baseBranch`. Dirty repos
are skipped.

## Preconditions

- Each repo has been cloned (`/wrkx-clone`).
- The target branch exists locally or on the remote. Branches only
  on the remote are tracked automatically.

## Variants

| Invocation | Scope |
|---|---|
| `/wrkx-checkout` | Every repo |
| `/wrkx-checkout-<repo>` | Only the named repo |

## Steps

1. Aggregate:

   ```bash
   ./gradlew -q wrkx-checkout
   ```

2. Per-repo:

   ```bash
   ./gradlew -q wrkx-checkout-<repo>
   ```

3. For each repo:
   - If dirty → **skip**, report as dirty.
   - Determine target: `workingBranch` if set, else `baseBranch`.
   - If the branch exists locally, `git checkout <branch>`.
   - If only on the remote, `git checkout -b <branch>
     origin/<branch>` (sets up tracking).
   - If the branch doesn't exist anywhere, that repo fails with a
     clear message.

4. Summary lists: checked out, skipped (dirty), failed (branch
   missing).

## Post-state

Every clean repo sits on the intended branch. `/wrkx-status`
reflects the alignment.

## Failure modes

- **Dirty working tree** — checkout skipped; commit/stash first.
- **Branch missing locally and remotely** — task reports the repo;
  create the branch first (`git switch -c <name>`) if you
  intended a new branch.
- **Uncommitted changes would be overwritten** — Git refuses;
  same as dirty skip.

## Related skills

- `/wrkx-status` — confirm branch alignment after checkout
- `/wrkx-pull` — pull after checkout to get latest
- `/git-workflow` — branch naming conventions
