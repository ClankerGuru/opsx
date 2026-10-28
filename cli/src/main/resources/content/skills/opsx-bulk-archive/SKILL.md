---
name: opsx-bulk-archive
description: Archive every opsx change whose status is completed, done, or verified in a single pass. Activates when the user wants to clean up, bulk-archive, or batch-close finished changes after a release.
---

# /opsx-bulk-archive

> Related: `/opsx-archive` (single) · `/opsx-status`

## Purpose

Clear the active working set of all archive-eligible changes.
Typically run after a release or sprint boundary.

## Preconditions

- `opsx/changes/` contains one or more changes with status
  `completed`, `done`, or `verified`.

## Steps

1. For each change under `opsx/changes/*/.opsx.yaml` whose status
   is archive-eligible, move the directory to `opsx/archive/`. This
   is the `/opsx-bulk-archive` agent workflow; there is no
   `opsx bulk-archive` shell subcommand.

2. Leave changes with status `draft`, `active`, or `in-progress` in
   place.

3. Report a summary: `archived: N`, `skipped: M` with the reason
   per skipped change.

## Post-state

```
opsx/changes/             only draft / active / in-progress remain
opsx/archive/<name>/      N archived changes added
```

## Failure modes

- **Name collision** — an archived change with the same name
  already exists. Resolve manually (rename or delete the stale
  archive) and rerun.
- **Nothing to archive** — task prints `archived: 0` and exits
  successfully.

## Related skills

- `/opsx-archive` — archive one by one when you want to be
  selective
- `/opsx-status` — see the ledger before and after
- `/git-workflow` — commit the archive sweep as one logical change
