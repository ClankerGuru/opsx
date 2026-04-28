---
name: wrkx-clone
description: Clone every repo defined in wrkx.json from its remote into the workspace. Activates when the user wants to clone, bootstrap, or set up a fresh workspace from wrkx.json.
---

# /wrkx-clone

> Related: `/wrkx-pull` · `/wrkx-checkout` · `/wrkx-status`

## Purpose

Bootstrap a workspace by cloning every repo listed in `wrkx.json`
into its configured `path`. Idempotent — repos already present on
disk are skipped.

## Preconditions

- Running from the workspace root.
- `wrkx.json` declares each repo as `{ name, path, remote,
  category, baseBranch?, workingBranch? }`.
- Git is available on PATH.
- Network access to each `remote` URL (SSH key or HTTPS creds as
  the URL requires).

## Variants

| Invocation | Scope |
|---|---|
| `/wrkx-clone` | Every repo in `wrkx.json` |
| `/wrkx-clone-<repo>` | Only the named repo (e.g. `/wrkx-clone-opsx`) |

## Steps

1. Aggregate clone:

   ```bash
   ./gradlew -q wrkx-clone
   ```

2. Per-repo clone:

   ```bash
   ./gradlew -q wrkx-clone-<repo>
   ```

3. For each entry whose `path` does not already exist, `git clone
   <remote> <path>` is run. Existing paths are left untouched.

4. After clone, `baseBranch` (if declared) is checked out. If
   `workingBranch` is declared and exists on the remote, it is
   also checked out.

## Post-state

```
<workspace>/
    <path>/         one dir per entry in wrkx.json
        .git/
        ... cloned content
```

## Failure modes

- **Remote unreachable / auth fails** — clone errors, task
  continues with the rest, reports failures in summary.
- **`path` collides with an existing dir that isn't a git repo** —
  clone refuses; resolve manually.
- **`remote` field missing** — entry is skipped with a warning.

## Next step

`/wrkx-status` — verify branch state, then `/wrkx-checkout` or
`/wrkx-pull` as needed.

## Related skills

- `/wrkx-status` — see which repos are on which branch post-clone
- `/wrkx-pull` — pull after clone if a repo moved while you
  bootstrapped
- `/gradle-composite-builds` — how the cloned repos wire into the
  composite build
