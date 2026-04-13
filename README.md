# opsx

[![Maven Central](https://img.shields.io/maven-central/v/zone.clanker/opsx)](https://central.sonatype.com/artifact/zone.clanker/opsx)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Spec-driven development workflows, skill generation, and AI agent orchestration for Gradle workspaces.

## Usage

**settings.gradle.kts**

```kotlin
plugins {
    id("zone.clanker.opsx") version "<version>"
}

opsx {
    outputDir = "opsx"           // default
    defaultAgent = "claude"      // claude | copilot | codex | opencode
    changesDir = "changes"       // default
    specsDir = "specs"           // default
    projectFile = "project.md"   // default
}
```

## Tasks

Six tasks are visible in `./gradlew tasks`:

| Task | Description |
|---|---|
| `opsx-propose` | Propose a new change (`-Pzone.clanker.opsx.prompt="..."`) |
| `opsx-apply` | Apply a change to the codebase (`-Pzone.clanker.opsx.change="..."`) |
| `opsx-verify` | Verify a change was applied correctly (`-Pzone.clanker.opsx.change="..."`) |
| `opsx-archive` | Archive a completed change (`-Pzone.clanker.opsx.change="..."`) |
| `opsx-status` | Show all changes grouped by status |
| `opsx-sync` | Generate agent skills for Claude, Copilot, Codex, OpenCode |

Additional tasks are available but hidden from `./gradlew tasks`:

| Task | Description |
|---|---|
| `opsx-continue` | Continue work on an in-progress change |
| `opsx-explore` | Explore the codebase with an AI agent |
| `opsx-feedback` | Provide feedback on a change |
| `opsx-ff` | Fast-forward a change to the latest state |
| `opsx-onboard` | Onboard a new contributor |
| `opsx-bulk-archive` | Archive all completed changes |
| `opsx-list` | List all changes |
| `opsx-clean` | Remove generated skill files |

## Common Flags

All agent tasks support:

- `-Pzone.clanker.opsx.agent=copilot` — Override the default agent
- `-Pzone.clanker.opsx.model=opus` — Override the model

## Change Lifecycle

```
draft → active → in-progress → completed/done → verified → archived
```

Changes live in `opsx/changes/<name>/` with:

- `.opsx.yaml` — Change metadata (name, status, depends)
- `proposal.md` — The change proposal
- `design.md` — Technical design
- `tasks.md` — Implementation checklist

## Agent Skills

`opsx-sync` generates slash commands for each agent:

- **Claude**: `.claude/commands/` + `CLAUDE.md`
- **Copilot**: `.github/prompts/` + `AGENTS.md` + `.github/copilot-instructions.md`
- **Codex**: `~/.codex/prompts/`
- **OpenCode**: `.opencode/commands/`

## License

MIT
