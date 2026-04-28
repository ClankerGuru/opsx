---
name: srcx-clean
description: Delete the .srcx/ directory in the root and in every included build to reset srcx context reports. Activates when the user wants to clean, reset, or wipe the srcx output before regeneration.
---

# /srcx-clean

> Related: `/srcx-context` (regenerate after clean)

## Purpose

Remove every generated `.srcx/` directory in the workspace — root
project plus each included build. Use before a full regeneration
when you suspect stale fragments.

## Preconditions

- The `zone.clanker.srcx` plugin is applied.
- No tooling currently relies on the context being present (other
  agents will report missing context until `/srcx-context` runs).

## Steps

1. Run:

   ```bash
   ./gradlew -q srcx-clean
   ```

2. The task deletes:
   - `.srcx/` in the project root
   - `.srcx/` in each included build's root

3. Follow up with `/srcx-context` to regenerate. Agents that
   depend on context will otherwise report it as missing.

## Post-state

No `.srcx/` directories anywhere. Ready for a fresh `/srcx-context`.

## Failure modes

- **`.srcx/` not present** — task is a no-op; exits successfully.
- **Permission denied** — check ownership of `.srcx/`; run from
  the right user.

## Related skills

- `/srcx-context` — regenerate after cleaning
- `/opsx-explore` `/opsx-onboard` — will report missing context
  until the regeneration completes
