#!/bin/sh
# install.sh — curl-pipe installer for opsx
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/ClankerGuru/opsx/main/install.sh | sh
#
# Environment:
#   OPSX_HOME    install directory (default: ~/.opsx)
#   OPSX_REPO    GitHub repo       (default: ClankerGuru/opsx)
#   OPSX_VERSION specific version  (default: latest)

set -eu

OPSX_HOME="${OPSX_HOME:-$HOME/.opsx}"
OPSX_REPO="${OPSX_REPO:-ClankerGuru/opsx}"

# ── Detect platform ────────────────────────────────────────────

detect_platform() {
    os="$(uname -s)"
    arch="$(uname -m)"

    case "$os" in
        Darwin) os="macos" ;;
        Linux)  os="linux" ;;
        *)      echo "error: unsupported OS: $os" >&2; exit 1 ;;
    esac

    case "$arch" in
        arm64|aarch64) arch="arm64" ;;
        x86_64|amd64)  arch="x64" ;;
        *)             echo "error: unsupported architecture: $arch" >&2; exit 1 ;;
    esac

    echo "${os}-${arch}"
}

# ── Resolve version ────────────────────────────────────────────

resolve_version() {
    if [ -n "${OPSX_VERSION:-}" ]; then
        echo "$OPSX_VERSION"
        return
    fi

    url="https://api.github.com/repos/${OPSX_REPO}/releases/latest"
    tag=$(curl -sSL -H "Accept: application/vnd.github+json" "$url" | grep '"tag_name"' | head -1 | sed 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\(.*\)".*/\1/')

    if [ -z "$tag" ]; then
        echo "error: could not resolve latest release from $url" >&2
        exit 1
    fi

    echo "$tag"
}

# ── Download + extract ────────────────────��────────────────────

install() {
    platform="$(detect_platform)"
    version="$(resolve_version)"

    asset="opsx-${platform}.tar.gz"
    url="https://github.com/${OPSX_REPO}/releases/download/${version}/${asset}"

    echo "opsx: installing ${version} for ${platform}"
    echo "opsx: downloading ${url}"

    mkdir -p "$OPSX_HOME"

    curl -sSL "$url" | tar xz -C "$OPSX_HOME"

    if [ ! -x "$OPSX_HOME/bin/opsx" ]; then
        echo "error: $OPSX_HOME/bin/opsx not found after extraction" >&2
        exit 1
    fi

    echo "opsx: installed to $OPSX_HOME/bin/opsx"
}

# ── PATH setup ─────────────────────────────────────────────────

ensure_path() {
    bin_dir="$OPSX_HOME/bin"

    # Already on PATH?
    case ":${PATH}:" in
        *":${bin_dir}:"*) return ;;
    esac

    # Detect shell rc
    rc=""
    if [ -n "${ZSH_VERSION:-}" ] || [ "$(basename "${SHELL:-}")" = "zsh" ]; then
        rc="$HOME/.zshrc"
    elif [ -f "$HOME/.bashrc" ]; then
        rc="$HOME/.bashrc"
    elif [ -f "$HOME/.profile" ]; then
        rc="$HOME/.profile"
    fi

    if [ -z "$rc" ]; then
        echo "opsx: add $bin_dir to your PATH manually"
        return
    fi

    # Already in rc?
    if grep -q '# >>> opsx >>>' "$rc" 2>/dev/null; then
        return
    fi

    cat >> "$rc" << EOF

# >>> opsx >>>
export OPSX_HOME="$OPSX_HOME"
export PATH="\$OPSX_HOME/bin:\$PATH"
# <<< opsx <<<
EOF

    echo "opsx: added PATH to $rc (restart your shell or: source $rc)"
}

# ── Main ───────────────────────────────────────────────────────

install
ensure_path

echo "opsx: done. Run 'opsx --help' to get started."
