# TUI Screen Mocks

Every screen uses the shared AppShell layout:
```
[blank line]
[logo with gradient]
[blank line]
[content area — varies per screen]
[padding to fill terminal height]
[status bar: left hints | right info]
```

---

## 1. Main Menu

```
 ██████╗ ██████╗ ███████╗██╗  ██╗
██╔═══██╗██╔══██╗██╔════╝╚██╗██╔╝
██║   ██║██████╔╝███████╗ ╚███╔╝
██║   ██║██╔═══╝ ╚════██║ ██╔██╗
╚██████╔╝██║     ███████║██╔╝ ██╗
 ╚═════╝ ╚═╝     ╚══════╝╚═╝  ╚═╝

❯  status    View changes and progress
   init      Configure workspace hosts
   install   Install opsx globally
   update    Check for updates
   nuke      Uninstall opsx

  ↵ select  ↑/k up  ↓/j down  q quit                              opsx v0.0.5
```

---

## 2. Status — Change Ledger

Shows all opsx changes in the current project with lifecycle badge + progress.

```
 ██████╗ ...

      opsx  status                                        3 changes

      [  active  ]  ████░░░░░░  40%  (4/10)  tui-overhaul
      [ in-prog  ]  ██████████ 100%  (8/8)   fix-emitters
   ❯  [ verified ]  ██████████ 100%  (5/5)   cli-refactor

  ↵ open  ↑/k up  ↓/j down  r refresh  ← back                        2/3
```

**Empty state** (no opsx/changes/ directory):
```
 ██████╗ ...

      opsx  status

      No changes found.
      Run /opsx-propose to create your first change.

  ← back
```

---

## 3. Status — Change Detail (tree view)

Drill into one change, showing activity events grouped by agent.

```
 ██████╗ ...

      opsx  status  →  tui-overhaul

      [  active  ]  ████░░░░░░  40%  (4/10)

      @lead
        → scaffold-change
        ✓ proposal.md written
      @developer
        → implement Dashboard
        ✓ Dashboard.kt done
        → implement StatusView
      @qa
        ✓ DashboardTest.kt

  ← h back                                                    4/10 tasks
```

---

## 4. Init — Host Selection

Select which AI coding host(s) to configure. All skills + agents install by default.

```
 ██████╗ ...

      opsx  init

      Select hosts to configure:

      ● claude       Claude Code CLI
      ● copilot      GitHub Copilot CLI
      ○ codex        OpenAI Codex CLI (not found on PATH)
      ○ opencode     OpenCode CLI (not found on PATH)

      All skills and agents will be installed for selected hosts.

  space toggle  ↵ apply  ← back                          2/4 selected
```

**After applying:**
```
 ██████╗ ...

      opsx  init  →  complete

      ✓ .opsx/config.json

      claude
        ✓ 28 skills  → .claude/skills/
        ✓ 7 agents   → .claude/agents/
        ✓ marker     → CLAUDE.md

      copilot
        ✓ 28 skills  → .opsx/cache/opsx-plugin/skills/
        ✓ 7 agents   → .opsx/cache/opsx-plugin/agents/
        → copilot plugin install .opsx/cache/opsx-plugin

      Cleaned up 0 legacy paths.
      ✓ done

  ↵ done                                                     2 hosts
```

---

## 5. Install

Copies the opsx binary to ~/.opsx/ for global use.

```
 ██████╗ ...

      opsx  install

      Install opsx globally to ~/.opsx/bin/opsx

      current:  not installed
      source:   ./app/build/install/app-shadow/

      This will:
        • Copy bin/opsx to ~/.opsx/bin/
        • Copy lib/ to ~/.opsx/lib/
        • Add ~/.opsx/bin to PATH in ~/.zshrc
        • Install zsh completions

  ↵ install  ← back
```

**After installing:**
```
 ██████╗ ...

      opsx  install  →  complete

      ✓ ~/.opsx/bin/opsx
      ✓ ~/.opsx/lib/opsx.jar
      ✓ PATH added to ~/.zshrc
      ✓ completions → ~/.opsx/_opsx

      Restart your shell or run:
        source ~/.zshrc

  ↵ done
```

---

## 6. Update

Self-update from GitHub releases.

```
 ██████╗ ...

      opsx  update

      current   0.0.5-SNAPSHOT
      latest    0.1.0

      ███████████████████████████████████  downloading opsx-macos-arm64.tar.gz
      ✓ checksum verified

  ↵ update  ← back
```

**Already up to date:**
```
 ██████╗ ...

      opsx  update

      current   0.1.0
      latest    0.1.0

      ✓ up to date

  ← back
```

---

## 7. Nuke

Remove opsx from the system.

```
 ██████╗ ...

      opsx  nuke

      This will remove:
        • ~/.opsx/bin/opsx
        • ~/.opsx/lib/opsx.jar
        • PATH block from ~/.zshrc
        • zsh completions ~/.opsx/_opsx

      Per-project files (.claude/skills/, .opencode/command/, etc.)
      will NOT be removed.

  y confirm  n cancel
```

**After nuking:**
```
 ██████╗ ...

      opsx  nuke  →  complete

      ✓ removed ~/.opsx/bin/opsx
      ✓ removed ~/.opsx/lib/
      ✓ removed PATH block from ~/.zshrc
      ✓ removed ~/.opsx/_opsx

      Restart your shell to pick up the PATH change.

  ↵ done
```

---

## 8. Guide (future)

Paged onboarding tutorial. 4 pages navigable with ←/→.

```
 ██████╗ ...

      opsx  guide                                          1 › 2 › 3 › 4

      What is opsx?

      opsx is a workspace lifecycle tool for AI coding agents.
      It manages the propose → apply → verify → archive workflow
      for code changes driven by AI agents like Claude, Copilot,
      Codex, and OpenCode.

      Each change lives in opsx/changes/<name>/ with:
        • proposal.md — what and why
        • design.md   — how
        • tasks.md    — implementation checklist
        • .opsx.yaml  — lifecycle status

  ← prev  → next  q back                                     page 1/4
```

---

## Open Questions

<!-- Add notes here about what to change, add, or reconsider -->
