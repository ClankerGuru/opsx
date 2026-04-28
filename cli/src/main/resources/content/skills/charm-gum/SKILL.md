---
name: charm-gum
description: >-
  Use when authoring interactive shell scripts — prompts, menus, filters,
  confirmations, spinners, and styled output — that a human will run in a
  TTY. Covers every gum subcommand, exit-code handling, TTY-detection
  guard, and recipes for commit helpers, release pickers, and wizards.
  Do not use in CI or non-interactive pipelines.
---

## When to use this skill

- Writing a shell script a human will run interactively (opsx wrappers,
  release tooling, setup wizards, onboarding helpers).
- Replacing `read -p` / `select` / ad-hoc `echo -e` color codes with
  something consistent and readable.
- Adding a spinner around a long command.
- Rendering a choice list, file picker, or table for the user to select
  from.

## When NOT to use this skill

- Non-interactive contexts: CI, cron, `ssh host cmd`, pipes with no TTY.
  Always gate with a TTY check (see below) and provide a non-interactive
  fallback.
- Programs that parse output. gum's interactive output is not
  machine-readable.

## Install

```bash
brew install gum              # macOS / Linux
pacman -S gum                 # Arch
dnf install gum               # Fedora
go install github.com/charmbracelet/gum@latest
```

Verify: `gum --version`.

## TTY guard — required at the top of every gum script

```bash
if [ ! -t 0 ] || [ ! -t 1 ]; then
  echo "This script requires an interactive terminal." >&2
  exit 1
fi
```

Or fall back to non-interactive defaults:

```bash
name=${1:-}
if [ -z "$name" ] && [ -t 0 ]; then
  name=$(gum input --placeholder "name")
fi
: "${name:?name is required}"
```

## Subcommands

| Command    | Purpose                                                       |
|------------|---------------------------------------------------------------|
| `input`    | Single-line text prompt.                                      |
| `write`    | Multi-line text (Ctrl+D to finish).                           |
| `choose`   | Pick one or many from a list.                                 |
| `filter`   | Fuzzy-filter over stdin or args.                              |
| `confirm`  | Yes/no. Exit 0 = yes, 1 = no.                                 |
| `file`     | File picker rooted at a folder.                               |
| `spin`     | Spinner wrapping a long command.                              |
| `style`    | Apply color/border/padding to text.                           |
| `format`   | Render Markdown, code, emoji, or template strings.            |
| `join`     | Combine text blocks horizontally or vertically.               |
| `pager`    | Scroll long output with line numbers.                         |
| `table`    | Render or pick from tabular (CSV) input.                      |
| `log`      | Structured log lines with levels and styling.                 |

## Capturing output and exit codes

Every prompt command prints the result to stdout; capture with `$(...)`.
Cancellation (`Esc`, `Ctrl+C`) returns non-zero — always check.

```bash
name=$(gum input --placeholder "your name") || exit 1
gum confirm "Deploy $name to prod?" || { echo "cancelled"; exit 0; }
```

Never pipe gum prompts into other gum prompts on a single line — each
needs its own stdin control. Use separate statements.

## Key flags

| Flag                    | Where it applies    | Effect                                 |
|-------------------------|---------------------|----------------------------------------|
| `--placeholder <text>`  | `input`, `write`    | Shown until the user types.            |
| `--value <text>`        | `input`             | Prefilled value (good for suffixes).   |
| `--password`            | `input`             | Mask characters.                       |
| `--limit <N>`           | `choose`, `filter`  | Max selections. `0` = no limit.        |
| `--no-limit`            | `choose`, `filter`  | Multi-select unlimited.                |
| `--height <N>`          | list-like           | Visible rows.                          |
| `--width <N>`           | most                | Output width cap.                      |
| `--header <text>`       | list-like           | Label above the list.                  |
| `--cursor <char>`       | list-like           | Custom cursor glyph.                   |
| `--spinner <name>`      | `spin`              | `dot`, `line`, `minidot`, `jump`, etc. |
| `--title <text>`        | `spin`              | Message beside spinner.                |
| `--show-output`         | `spin`              | Stream wrapped command's stdout.       |
| `--foreground <hex\|N>` | `style`, prompts    | Text color.                            |
| `--border <name>`       | `style`, prompts    | `none`, `normal`, `rounded`, `double`, `thick`. |
| `--padding "V H"`       | `style`             | Internal spacing.                      |
| `--margin "V H"`        | `style`             | External spacing.                      |

## Recipes

### Conventional-commit helper

```bash
type=$(gum choose --header "Type" fix feat docs style refactor test chore build ci)
scope=$(gum input --placeholder "scope (optional)")
summary=$(gum input --value "$type${scope:+($scope)}: " --placeholder "summary")
[ -z "$summary" ] && exit 1
body=$(gum write --placeholder "details (Ctrl+D to finish)")
gum confirm "Commit?" || exit 0
git commit -m "$summary" -m "$body"
```

### Branch picker

```bash
branch=$(git branch --format='%(refname:short)' | gum filter --header "checkout") \
  && git checkout "$branch"
```

### Release picker

```bash
tag=$(git tag --sort=-creatordate | head -20 | gum choose --header "Release to ship")
gum confirm "Ship $tag?" && gh release edit "$tag" --draft=false
```

### Spinner around a long command

```bash
gum spin --spinner dot --title "Building..." --show-output -- ./gradlew build
```

### Styled banner

```bash
gum style \
  --foreground 212 --border double --border-foreground 63 \
  --padding "1 3" --margin "1" \
  "Deploy Complete" "Service: api-v2"
```

### File picker scoped to a folder

```bash
target=$(gum file --all ./docs)
$EDITOR "$target"
```

### Log block

```bash
gum log --level info --time rfc3339 "build started"
gum log --level error "tests failed: $n"
```

### Multi-select with limit

```bash
flags=$(gum choose --no-limit --header "Toggle flags" \
  '--verbose' '--dry-run' '--force' '--skip-cache')
./run $flags
```

## Styling consistency

Define a single palette at the top of a script and reuse:

```bash
BORDER=rounded
ACCENT=212
gum style --border "$BORDER" --foreground "$ACCENT" --padding "1 2" "Welcome"
```

Don't mix `echo -e "\033[..."` with gum — pick one formatter per script.

## Pitfalls

| Symptom                                  | Cause / fix                                                       |
|------------------------------------------|-------------------------------------------------------------------|
| Script hangs in CI                       | Missing TTY guard. Detect with `[ -t 0 ]`.                        |
| `gum: not found` on a teammate's box     | Not installed. Gate with `command -v gum >/dev/null || { ... }`.  |
| Multi-select returns empty               | User pressed Enter without selecting. Re-prompt or fail loud.     |
| Output has stray ANSI in a file          | Redirecting gum prompts to a file. Capture with `$(...)` instead. |
| Nested `$(...)` scrambles prompts        | Two prompts on one line share a terminal. Split into statements.  |
| Colors look wrong                        | Terminal not true-color. Use 256-color indices (`--foreground 212`) not hex. |

## Reference points

- https://github.com/charmbracelet/gum — full docs and examples
- https://github.com/charmbracelet/lipgloss — styling vocabulary
