---
name: charm-glow
description: >-
  Use when rendering Markdown in the terminal for a human reader — local
  files, piped output, or remote READMEs from GitHub/GitLab/HTTP. Covers
  install, the TUI browser, CLI flags (style, width, pager), stdin piping,
  remote sources, and when NOT to use glow (machine-parsed pipelines).
---

## When to use this skill

- Reading a local `README.md`, `CHANGELOG.md`, or spec note in the terminal.
- Piping Markdown from another tool (`gh pr view`, `curl`, `kubectl`) into
  a pretty renderer.
- Browsing Markdown files in a project interactively (TUI mode).
- Fetching a remote README from GitHub/GitLab without cloning.

## When NOT to use this skill

- Output will be parsed by another program. Glow emits ANSI; downstream
  greps and JSON parsers break. Pipe raw Markdown instead.
- You need editing, linking, or search across many notes — use Obsidian,
  an IDE, or `grep`/`rg`.
- You are not in a TTY (CI logs, non-interactive shells). Glow auto-plain
  in some cases but behavior is not guaranteed.

## Install

```bash
# macOS / Linux
brew install glow

# Arch
pacman -S glow

# Windows
winget install charmbracelet.glow

# Anywhere with Go
go install github.com/charmbracelet/glow/v2@latest
```

Verify: `glow --version`.

## Core usage

```bash
glow FILE.md                       # render a file
glow                               # TUI browser: current dir + subdirs
glow -                             # read from stdin
glow github.com/charmbracelet/glow # fetch remote README
glow https://example.com/page.md   # fetch HTTP(S) Markdown
```

## Main flags

| Flag                | Effect                                                          |
|---------------------|-----------------------------------------------------------------|
| `-p`                | Page output through `$PAGER` (or `less -r`).                    |
| `-w <N>`            | Hard-wrap at N columns. Default: terminal width.                |
| `-s <name\|path>`   | Style: `dark`, `light`, `auto`, `notty`, or path to JSON style. |
| `-a`                | All files (include hidden) in TUI.                              |
| `--config <file>`   | Load a non-default config file.                                 |
| `--local`           | TUI: don't scan parent Git repo, just current dir.              |

Default style is `auto` — picks dark or light from terminal background.
Pass `-s notty` when rendering to a log file to strip ANSI.

## Practical recipes

### View a PR description without leaving the shell

```bash
gh pr view 123 --json body -q .body | glow -
```

### Read a remote README

```bash
glow github.com/charmbracelet/gum
glow github.com/kubernetes/kubernetes   # works for any public repo
```

### Render a long doc with paging + wrap

```bash
glow -p -w 100 docs/architecture.md
```

### Diff-friendly: strip ANSI for a log

```bash
glow -s notty CHANGELOG.md > changelog.txt
```

### Browse project docs interactively

```bash
cd ~/repo && glow
# j/k to move, enter to open, q to quit
```

### Inline docs block from a script

```bash
cat <<'EOF' | glow -
# Deploy complete

- **Service:** api-v2
- **Commit:** `abc123`
EOF
```

## Style notes

- Styles ship with glow; pick with `-s dark` or `-s light` to override
  auto-detect.
- Custom styles: point `-s` at a JSON file. Format documented at
  [Glamour styles](https://github.com/charmbracelet/glamour/tree/master/styles).
- Config file lives at `~/.config/glow/glow.yml`. Edit with `glow config`.

## Pitfalls

| Symptom                                  | Cause / fix                                                  |
|------------------------------------------|--------------------------------------------------------------|
| Garbled characters downstream in a pipe  | Don't pipe `glow` into parsers; it emits ANSI.               |
| Colors too dim or invisible              | Terminal background mis-detected. Force `-s dark` / `-s light`. |
| No paging with long output               | Pass `-p` explicitly; `$PAGER` may be unset.                 |
| Fenced code blocks look plain            | Language tag missing or unknown. Glow highlights only tagged fences. |
| Remote fetch returns 404                 | Path syntax wrong — use `owner/repo`, not `github.com/owner/repo/blob/...`. |

## Reference points

- https://github.com/charmbracelet/glow — repo + flags
- https://github.com/charmbracelet/glamour — rendering engine + styles
