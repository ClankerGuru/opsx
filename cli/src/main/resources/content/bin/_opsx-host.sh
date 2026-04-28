# shellcheck shell=bash
#
# _opsx-host.sh — detect which AI CLI is driving this opsx invocation
# and export `OPSX_HOST` so opsx-log / opsx-render / opsx-status can
# tag and filter rows by host.
#
# Precedence:
#   1. User-exported `OPSX_HOST` wins (highest priority).
#   2. Heuristics on CLI-specific env vars.
#   3. Fallback `unknown`.
#
# Sourced by opsx-log. Idempotent: re-sourcing leaves OPSX_HOST
# untouched when already set. Safe under `set -u`.

if [ -z "${OPSX_HOST:-}" ]; then
    if [ -n "${COPILOT_AGENT_ID:-}" ] \
        || [ -n "${GITHUB_COPILOT_CLI:-}" ] \
        || [ -n "${COPILOT_SUBAGENT_MAX_CONCURRENT:-}" ]; then
        OPSX_HOST="copilot"
    elif [ -n "${CLAUDE_CODE_SESSION_ID:-}" ] \
        || [ -n "${CLAUDE_CODE_ENTRYPOINT:-}" ] \
        || [ -n "${CLAUDECODE:-}" ]; then
        OPSX_HOST="claude"
    elif [ -n "${CODEX_SESSION_ID:-}" ] \
        || [ -n "${CODEX_HOME:-}" ]; then
        OPSX_HOST="codex"
    elif [ -n "${OPENCODE_SESSION_ID:-}" ] \
        || [ -n "${OPENCODE_HOME:-}" ]; then
        OPSX_HOST="opencode"
    else
        OPSX_HOST="unknown"
    fi
fi

export OPSX_HOST
