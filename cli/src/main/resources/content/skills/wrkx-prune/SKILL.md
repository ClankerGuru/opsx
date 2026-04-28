---
name: wrkx-prune
description: Remove repo directories on disk that are not defined in wrkx.json. Activates when the user wants to prune, clean up, or remove stale repo clones that were deleted from wrkx.json.
---

# /wrkx-prune

> Related: `/wrkx-status` · `/wrkx-clone`

## Purpose

Delete directories in the workspace that look like repos but are
no longer listed in `wrkx.json`. Refuses to delete anything with
uncommitted changes — destructive operations on dirty work is a
bug, not a feature.

## Preconditions

- `wrkx.json` is the authoritative list; anything removed from it
  is considered stale.
- You've committed or pushed any work you want to keep.

## Steps

1. Run:

   ```bash
   ./gradlew -q wrkx-prune
   ```

2. For each directory at the configured repo roots that is **not**
   in `wrkx.json`:
   - If the directory has uncommitted changes → **skip**, report
     as dirty.
   - Otherwise delete the directory recursively.

3. Summary at the end lists: pruned, skipped (dirty), skipped
   (not a git repo).

## Post-state

Workspace contains only directories listed in `wrkx.json`, plus
any stale dirty repos the task refused to touch.

## Failure modes

- **Dirty repo present but intended to be pruned** — task refuses.
  Commit/stash/discard first, then rerun. This is a feature, not a
  bug.
- **Dir removed from `wrkx.json` but still needed** — re-add the
  entry before pruning.

## Related skills

- `/wrkx-status` — preview which dirs are not in `wrkx.json`
  before pruning
- `/wrkx-clone` — re-clone if you pruned by accident
- `/git-workflow` — stashing vs committing before destructive
  operations
