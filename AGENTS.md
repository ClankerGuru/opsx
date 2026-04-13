<!-- OPSX:AUTO -->
# OPSX Workspace

This is a Gradle workspace managed by OPSX.
Use the available slash commands or Gradle tasks.

## opsx

| Skill | Gradle Task | Description |
|-------|------------|-------------|
| `/opsx-apply` | `./gradlew -q opsx-apply -Pzone.clanker.opsx.change="add-retry-logic"` | Apply a change to the codebase |
| `/opsx-archive` | `./gradlew -q opsx-archive -Pzone.clanker.opsx.change="add-retry-logic"` | Archive a completed change |
| `/opsx-bulk-archive` | `./gradlew -q opsx-bulk-archive` | Archive all completed changes in bulk |
| `/opsx-clean` | `./gradlew -q opsx-clean` | Remove all generated skill files and symlinks |
| `/opsx-continue` | `./gradlew -q opsx-continue -Pzone.clanker.opsx.change="add-retry-logic"` | Continue work on an in-progress change |
| `/opsx-explore` | `./gradlew -q opsx-explore -Pzone.clanker.opsx.prompt="how does auth work?"` | Explore the codebase with an AI agent |
| `/opsx-feedback` | `./gradlew -q opsx-feedback -Pzone.clanker.opsx.change="add-retry-logic" -Pzone.clanker.opsx.prompt="use exponential backoff"` | Provide feedback on a change |
| `/opsx-ff` | `./gradlew -q opsx-ff -Pzone.clanker.opsx.change="add-retry-logic"` | Fast-forward a change to the latest state |
| `/opsx-list` | `./gradlew -q opsx-list` | List all changes |
| `/opsx-onboard` | `./gradlew -q opsx-onboard` | Onboard a new contributor to the project |
| `/opsx-propose` | `./gradlew -q opsx-propose -Pzone.clanker.opsx.prompt="add retry logic to HTTP client"` | Propose a new change |
| `/opsx-status` | `./gradlew -q opsx-status` | Show all changes and their status |
| `/opsx-sync` | `./gradlew -q opsx-sync` | Generate agent skills and instruction files |
| `/opsx-verify` | `./gradlew -q opsx-verify -Pzone.clanker.opsx.change="add-retry-logic"` | Verify a change was applied correctly |

## Included Builds

- opsx-build-logic

## Rules

- Do NOT use grep/sed/awk for refactoring. Use srcx tasks.
- Do NOT manually edit files across included builds. Use the Gradle tasks.
- Always check `.srcx/context.md` for codebase context.
- Changes are proposed via `/opsx-propose` or `./gradlew -q opsx-propose`.

<!-- /OPSX:AUTO -->
