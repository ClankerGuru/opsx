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
    defaultAgent = "claude"      // default
    changesDir = "changes"       // default
    specsDir = "specs"           // default
    projectFile = "project.md"   // default
}
```

## Tasks

| Task | Description |
|---|---|
| `opsx-sync` | Generate slash commands for all agents (Claude, Copilot, Codex, OpenCode) |
| `opsx-list` | List all changes and their status |
| `opsx-status` | Show detailed status of all changes |
| `opsx-propose` | Propose a new change from a spec |
| `opsx-apply` | Apply a change proposal to the codebase |
| `opsx-verify` | Verify a change was applied correctly |
| `opsx-archive` | Archive a completed change |
| `opsx-continue` | Continue work on an in-progress change |
| `opsx-explore` | Explore the codebase for a change |
| `opsx-feedback` | Provide feedback on a change |
| `opsx-onboard` | Onboard a new contributor to the project |
| `opsx-ff` | Fast-forward a change to the latest state |
| `opsx-bulk-archive` | Archive all completed changes in bulk |

## Change Structure

Changes live in `opsx/changes/<name>/` with:

- `.opsx.yaml` - Change metadata (name, status, depends)
- `proposal.md` - The change proposal
- `design.md` - Technical design
- `tasks.md` - Implementation tasks

## License

MIT
