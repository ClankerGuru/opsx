---
name: go
description: >-
  Use when you need to install the Go toolchain or install a Go-distributed
  CLI (skate, gum, glow, vhs, yq, and similar) via `go install`. Covers
  toolchain install on macOS/Linux, PATH setup so `go install` binaries are
  on PATH, versioned `go install pkg@version` usage, and common proxy or
  checksum pitfalls. Not a Go language tutorial.
---

## When to use this skill

- Installing a Go-distributed CLI because `brew`/`apt` lacks it or ships an
  outdated version (common for `charmbracelet/*` tools, newer `yq`, etc.).
- Getting Go onto a fresh macOS or Linux box just to `go install` binaries.
- Debugging "command not found" after a `go install` — almost always PATH.
- Pinning a tool to a specific commit or tag in CI via `go install`.

## When NOT to use this skill

- You are writing, testing, or structuring Go source code. This skill
  covers toolchain install and `go install`, nothing else.
- A first-class package exists (`brew install gum`). Prefer the package
  manager; fall back to `go install` only if the version is stale or the
  host lacks that manager.

## Install the toolchain

### macOS

```bash
# Preferred: Homebrew tracks recent stable Go
brew install go

# Alternative: official .pkg (installs to /usr/local/go)
#   https://go.dev/dl/  → download go1.XX.darwin-arm64.pkg → run
```

### Linux

```bash
# Tarball install (official, always current)
GO_VERSION=1.23.4
curl -LO https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz
sudo rm -rf /usr/local/go
sudo tar -C /usr/local -xzf go${GO_VERSION}.linux-amd64.tar.gz
```

Distro packages (`apt install golang`, `dnf install golang`) are often
one or two minor versions behind. Use the tarball when the tool you're
installing requires a recent Go.

### Verify

```bash
go version              # → go version go1.23.4 darwin/arm64
go env GOROOT GOPATH    # toolchain root + workspace
```

## Set PATH so installed binaries resolve

`go install` writes binaries to `$GOBIN` if set, otherwise
`$(go env GOPATH)/bin` — defaults to `$HOME/go/bin`. That directory is
**not** on PATH by default.

Add to your shell rc (`~/.zshrc`, `~/.bashrc`, `~/.config/fish/config.fish`):

```bash
# bash / zsh
export PATH="$PATH:/usr/local/go/bin:$(go env GOPATH)/bin"

# fish
set -gx PATH $PATH /usr/local/go/bin (go env GOPATH)/bin
```

Reload: `source ~/.zshrc` (or open a new shell). Confirm:

```bash
echo $PATH | tr ':' '\n' | grep -E 'go(/|bin)'
```

If Homebrew installed Go, `/usr/local/go/bin` is not needed — Brew
symlinks `go` into `/opt/homebrew/bin` (Apple Silicon) or
`/usr/local/bin` (Intel). Still add `$(go env GOPATH)/bin`.

## Install a Go CLI

Modern syntax (Go 1.17+):

```bash
go install <module-path>@<version>
```

`<version>` is required. Use `@latest` for the newest released tag, a
specific tag (`@v0.15.0`), or a commit SHA.

```bash
# Charm tools
go install github.com/charmbracelet/gum@latest
go install github.com/charmbracelet/glow/v2@latest   # v2 module path
go install github.com/charmbracelet/skate@latest
go install github.com/charmbracelet/vhs@latest

# Pinned for CI reproducibility
go install github.com/charmbracelet/gum@v0.14.5
```

Then confirm:

```bash
which gum && gum --version
```

### Multi-binary modules

Some modules publish several commands. Install each explicitly:

```bash
go install github.com/org/tool/cmd/thing@latest
go install github.com/org/tool/cmd/other@latest
```

### Uninstall

`go install` has no `uninstall`. Delete the binary:

```bash
rm "$(go env GOPATH)/bin/gum"
```

## `go install` vs `go get`

- `go install pkg@version` — install or upgrade a binary. This is the
  only correct form for installing CLIs on Go 1.17+.
- `go get pkg` — now scoped to managing module dependencies inside a
  `go.mod`. **Do not use it to install binaries.** Using `go get` to
  install a CLI is a deprecated pattern from Go ≤ 1.16.

If a tool's README still says `go get github.com/foo/bar`, translate it
mentally to `go install github.com/foo/bar@latest`.

## CI: GitHub Actions

```yaml
- uses: actions/setup-go@v5
  with:
    go-version: '1.23'
    cache: true
- run: go install github.com/charmbracelet/vhs@v0.8.0
- run: echo "$(go env GOPATH)/bin" >> "$GITHUB_PATH"
- run: vhs demo.tape
```

Pin the Go version and the tool version; `@latest` in CI means
non-reproducible builds.

## Common pitfalls

| Symptom                                     | Cause / fix                                                                 |
|---------------------------------------------|-----------------------------------------------------------------------------|
| `command not found` after `go install`      | `$(go env GOPATH)/bin` not on PATH. Add it to your shell rc.                |
| `missing go.sum entry` during `go install`  | Stale checksum db. Run `go clean -modcache` and retry.                      |
| Corporate proxy blocks `proxy.golang.org`   | `GOPROXY=direct go install ...` or set `GOPROXY` to an internal mirror.     |
| `unrecognized import path` / `dial tcp`     | Network / DNS. Retry; set `GOSUMDB=off` only if using an internal proxy.    |
| Old Go version: `go install` fails on new tool | Toolchain too old. Upgrade before installing.                            |
| `go get: installing executables... not supported` | You're on Go ≥ 1.17 using the old `go get` form. Use `go install`.    |
| Binary installs but shell calls old version | Stale PATH / another binary earlier on PATH. Run `which -a <tool>`.         |

## Reference points

- https://go.dev/doc/install — official install
- https://go.dev/ref/mod#go-install — `go install` semantics
- https://go.dev/doc/go1.17 — `go get` deprecation for CLI install
- https://go.dev/ref/mod#goproxy-protocol — proxy/checksum config
- https://github.com/actions/setup-go — CI action
