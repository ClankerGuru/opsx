---
name: charm-vhs
description: >-
  Use when producing GIF, MP4, or WebM recordings of a terminal session
  from a reproducible `.tape` script — README demos, docs screencasts,
  PR visuals, or regression snapshots. Covers every subcommand, the tape
  DSL (Output, Require, Set, Type, Keys, Sleep, Hide/Show), theming,
  validation, and common pitfalls.
---

## When to use this skill

- Demoing a CLI in a README: a reproducible GIF that rebuilds on every
  release.
- Capturing a bug reproduction with a scripted timeline instead of a
  fragile screen recording.
- Generating docs visuals in CI so they never drift from actual
  command output.
- Standardizing look-and-feel (font, theme, dimensions) across every
  project demo.

## When NOT to use this skill

- Live screencasts with a human driving. Use OBS / `asciinema` instead.
- Apps that need a real GUI (X11, browser). VHS renders a terminal.
- One-off captures where the investment in a `.tape` isn't justified.

## Install

```bash
brew install vhs                              # macOS / Linux
go install github.com/charmbracelet/vhs@latest
```

Runtime deps: `ffmpeg` and `ttyd` must be on PATH. Homebrew pulls them
automatically; on other systems install explicitly.

Verify: `vhs --version`.

## Subcommands

| Command     | Purpose                                                 |
|-------------|---------------------------------------------------------|
| `vhs FILE`  | Run `FILE.tape` and emit its `Output` files.            |
| `new NAME`  | Scaffold a fully-commented starter tape.                |
| `record`    | Record your live shell into a tape (`-s zsh` default).  |
| `validate`  | Parse tapes without running them. Takes a glob.         |
| `themes`    | List available color themes, one per line.              |
| `publish`   | Upload a rendered GIF to vhs.charm.sh for a share URL.  |
| `serve`     | Start the VHS SSH server (hosted tape runner).          |

## Global flags

| Flag                  | Effect                                                |
|-----------------------|-------------------------------------------------------|
| `-o, --output <path>` | Override `Output` in the tape. Repeatable.            |
| `-p, --publish`       | After render, upload and print a share URL.           |
| `-q, --quiet`         | Suppress progress logs.                               |

## Tape DSL

A tape is a sequence of directives. Capture by convention: settings and
`Output` up top, then a silent `Hide` block to set up state, then the
visible script.

### Output (required — one or more)

```tape
Output demo.gif
Output demo.mp4
Output demo.webm
```

### Require — fail fast if a binary is missing

```tape
Require git
Require gum
```

Runs before anything else; aborts with a clear error if absent.

### Settings — render + terminal tuning

| Directive                          | Value                                         |
|------------------------------------|-----------------------------------------------|
| `Set FontSize <n>`                 | Terminal font size.                           |
| `Set FontFamily <string>`          | Font family name.                             |
| `Set Width <n>` / `Set Height <n>` | Terminal pixel dimensions.                    |
| `Set LetterSpacing <f>`            | Character tracking.                           |
| `Set LineHeight <f>`               | Line height multiplier.                       |
| `Set Padding <n>`                  | Inner terminal padding.                       |
| `Set Framerate <n>`                | Frames per second.                            |
| `Set PlaybackSpeed <f>`            | Speed up / slow down playback.                |
| `Set TypingSpeed <time>`           | Per-character delay for `Type`. Default 50ms. |
| `Set LoopOffset <f>%`              | GIF loop start frame, as % of total.          |
| `Set Theme <json\|string>`         | Named theme or inline JSON.                   |
| `Set Margin <n>`                   | Outer margin (no effect without MarginFill).  |
| `Set MarginFill <file\|#hex>`      | Background image or color for the margin.     |
| `Set BorderRadius <n>`             | Rounded corners in pixels.                    |
| `Set WindowBar <Rings\|RingsRight\|Colorful\|ColorfulRight>` | macOS-style title bar. |
| `Set WindowBarSize <n>`            | Window bar height in pixels. Default 40.      |
| `Set Shell <string>`               | Shell to run. Default `bash`.                 |

### Input

```tape
Type "echo hello"                    # type at default speed
Type@50ms "slower, please"           # override per-call
Sleep 500ms                          # pause
Sleep 2s
Enter                                # press Enter once
Enter 3                              # press Enter 3 times
Backspace 5                          # 5 backspaces
Ctrl+C                               # modifier combos
```

Every key directive accepts `[@<time>]` (delay between presses) and
`[number]` (repeat count): `Down@100ms 5`, `Tab 2`, `Escape`.

Supported keys: `Escape`, `Backspace`, `Delete`, `Insert`, `Down`,
`Enter`, `Space`, `Tab`, `Left`, `Right`, `Up`, `PageUp`, `PageDown`.

### Hide / Show — suppress setup from the output

```tape
Hide
Type "source ./fixtures/setup.sh" Enter
Sleep 1s
Show
Type "./my-cli" Enter
```

Anything between `Hide` and `Show` runs but isn't captured. Use it for
`cd`, sourcing env, clearing screen, or priming caches.

## Recipes

### Minimal demo tape

```tape
Output demo.gif

Set FontSize 20
Set Width 1000
Set Height 500

Type "ls -la" Enter
Sleep 1s
Type "echo 'done'" Enter
Sleep 500ms
```

Render: `vhs demo.tape`.

### README GIF with setup hidden

```tape
Output readme.gif
Require git

Set WindowBar Colorful
Set Theme "Dracula"
Set Width 1200
Set Height 700

Hide
Type "cd /tmp && rm -rf demo && mkdir demo && cd demo && git init -q" Enter
Sleep 500ms
Ctrl+L
Show

Type "git status" Enter
Sleep 2s
```

### Multiple outputs in one run

```bash
vhs demo.tape -o demo.gif -o demo.mp4
```

`-o` overrides the tape's `Output` directive; use for CI matrix builds.

### Validate tapes in CI

```bash
vhs validate 'docs/demos/*.tape'
```

Fails fast on syntax errors without spending ffmpeg cycles.

### Record an interactive session, then edit

```bash
vhs record -s zsh > session.tape
# Then trim/extend/replace Type calls by hand.
vhs session.tape
```

### Pick a theme

```bash
vhs themes | grep -i dracula
# Then in the tape:
# Set Theme "Dracula"
```

Or pass inline JSON for a custom palette.

### Share a one-off

```bash
vhs demo.tape -p
# → prints an https://vhs.charm.sh/... URL
```

## Project layout

Keep tapes checked in next to the thing they document:

```
docs/
  demos/
    install.tape
    quickstart.tape
  media/            # render output (git-ignore or commit)
    install.gif
    quickstart.gif
```

A Makefile / Gradle task that runs `vhs docs/demos/*.tape` keeps media
current. Run `vhs validate` in pre-commit to catch syntax errors early.

## Pitfalls

| Symptom                                   | Cause / fix                                                    |
|-------------------------------------------|----------------------------------------------------------------|
| `ttyd: command not found`                 | Missing runtime dep. `brew install ttyd` (or equivalent).      |
| `ffmpeg` errors during encoding           | Missing or old ffmpeg. Upgrade.                                |
| GIF is enormous                           | Reduce `Width`/`Height`, drop `Framerate`, prefer MP4/WebM.    |
| Typing looks robotic                      | Vary `Type@<time>` per line, add `Sleep` between commands.     |
| Output file ignored                       | CLI `-o` overrides tape `Output`. Remove `-o` or update tape.  |
| Setup commands visible in GIF             | Wrap them in `Hide` / `Show`.                                  |
| Tape runs, no file appears                | No `Output` directive. At least one is required.               |
| Non-deterministic output (timestamps, PIDs) | Pipe through `sed` to stub volatile bits before rendering.   |

## Reference points

- https://github.com/charmbracelet/vhs — repo, full directive list
- https://vhs.charm.sh — hosted runner and share links
