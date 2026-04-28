# opsx

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-purple)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow)](https://opensource.org/licenses/MIT)
[![Coverage](https://img.shields.io/badge/Coverage-%E2%89%A595%25-brightgreen)](https://github.com/ClankerGuru/opsx)

> **⚠️ Experimental — pre-1.0**
>
> opsx is under active development. The API and CLI surface may change between releases.
> Production use is not recommended until 1.0. This tool is AI-driven — agents write the
> proposals, designs, and implementations. Humans approve and verify.

**Workspace lifecycle CLI for AI coding agents.**

opsx manages structured change workflows across Claude, Copilot, Codex, and OpenCode. It installs skills and agents into each host's native format, tracks changes through propose → apply → verify → archive, and provides a full-screen TUI dashboard.

## Install

```bash
# From a built distribution
./run.sh            # build + launch TUI
opsx install        # install globally to ~/.opsx/bin

# Or from GitHub Releases (once published)
curl -sSL https://raw.githubusercontent.com/ClankerGuru/opsx/main/install.sh | sh
```

After installing, restart your shell or `source ~/.zshrc`.

## Quick Start

```bash
opsx                # launch TUI dashboard
opsx init           # install skills/agents for detected hosts
opsx status         # show change ledger
opsx nuke           # remove opsx files from project
opsx update         # self-update from GitHub releases
opsx --help         # all commands
```

## TUI

Running `opsx` with no arguments launches a full-screen dashboard:

- **Status** — change ledger with badges, progress bars, drill-down into task trees
- **Init** — interactive host selector (Claude, Copilot, Codex, OpenCode)
- **Install** — copy binary to `~/.opsx/bin` with PATH wiring
- **Update** — check GitHub for new releases, download + verify
- **Nuke** — selectively remove only opsx-owned files, preserve user content
- **Guide** — 5-page onboarding walkthrough

Navigation: arrow keys, j/k, h/l, Enter, q. Gum-style confirm dialogs on destructive actions.

## Hosts

opsx writes skills and agents to each host's native directory:

| Host | Skills | Agents | Instruction File |
|------|--------|--------|-----------------|
| Claude | `.claude/skills/` | `.claude/agents/` | `CLAUDE.md` |
| Copilot | `.github/skills/` | `.github/agents/` | `.github/copilot-instructions.md` |
| Codex | `.codex-plugin/skills/` | `.codex-plugin/agents/` | `AGENTS.md` |
| OpenCode | `.opencode/command/` | `.opencode/agent/` | `AGENTS.md` |

`opsx init` detects which hosts are on PATH and lets you select which to configure.
`opsx nuke` removes only opsx-owned entries (reads the manifest), leaving user-created files untouched.

## Architecture

Three modules:

```
app/  — entry point (Main.kt)
cli/  — headless domain logic, organized by feature
tui/  — full-screen interactive UI with Mordant
```

### CLI packages

```
cli/
├── init/           host selector + emitters (Claude, Copilot, Codex, OpenCode)
│   ├── host/       Host enum + PATH detection
│   ├── emitter/    per-host file writers
│   └── config/     manifest, markers, config.json, resource loading
├── install/        global install to ~/.opsx
├── update/         self-update from GitHub releases
├── nuke/           selective project + global cleanup
├── status/         change ledger, activity log, progress bars
├── log/            append activity events
├── list/           one-liner change listing
└── completion/     shell completion scripts
```

### Convention plugins (build-logic)

| Plugin | Provides |
|--------|----------|
| `clkx-kotlin` | Kotlin JVM + serialization + toolchain 17 |
| `clkx-lib` | `java-library` |
| `clkx-cli` | kotlin + lib + kover + detekt + ktlint + konsist |
| `clkx-app` | cli + application + shadow + runtime |
| `clkx-detekt` | detekt with `config/detekt.yml` |
| `clkx-ktlint` | ktlint 1.5.0 |
| `clkx-kover` | kover with 70% minimum threshold |
| `clkx-konsist` | konsist + slopTest source set |

## Development

```bash
./gradlew clean check    # compile + detekt + ktlint + tests + coverage
./run.sh                 # build + launch TUI (requires real terminal)
./gradlew :app:installShadowDist   # build binary
./app/build/install/app-shadow/bin/opsx --version
```

### Requirements

- JDK 17+
- Gradle 9.4.1 (wrapper included)

### Test coverage

95%+ on both `cli` and `tui` modules. No coverage exclusions.

```bash
./gradlew :cli:koverLog -q    # print cli coverage
./gradlew :tui:koverLog -q    # print tui coverage
```

## License

[MIT](LICENSE)
