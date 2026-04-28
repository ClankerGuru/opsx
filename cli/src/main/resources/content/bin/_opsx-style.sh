# shellcheck shell=bash
#
# _opsx-style.sh — shared palette + primitives for opsx terminal UI.
#
# Sourced by `.opsx/bin/opsx-status` and `.opsx/bin/opsx-log`. The
# sole definition of the color palette, the glyph vocabulary, the
# NO_COLOR gate, and the formatting helpers.
#
# Design: opsx/changes/status-display-design-system/design.md §8.
#
# Safe to source under `set -u`. Defines variables with defaults and
# functions that take explicit arguments — no global state mutation
# beyond the documented names.

# ── Style catalog ───────────────────────────────────────────────────

color_enabled() {
    # Respect NO_COLOR per no-color.org (user opts out = highest priority).
    [ -z "${NO_COLOR:-}" ] || return 1
    # Otherwise default ON. We intentionally do NOT gate on `[ -t 1 ]`
    # because opsx-log writes the rendered row to stderr AND tees it
    # to .opsx/last-log.txt. Under an AI CLI host (Claude Code,
    # Copilot, Codex, OpenCode) fd 1 is rarely a TTY — gating on it
    # would strip every color and defeat the whole "pretty side-pane
    # tree" design. Hosts and `tail -f` both honor ANSI; users who
    # prefer plain text can export NO_COLOR=1.
    return 0
}

if color_enabled; then
    OPSX_STYLE_RESET=$'\033[0m'
    OPSX_STYLE_BOLD=$'\033[1m'
    OPSX_STYLE_DIM=$'\033[2m'
    OPSX_STYLE_STRIKE=$'\033[9m'
    OPSX_STYLE_STRIKE_OFF=$'\033[29m'

    OPSX_STYLE_FG_OK=$'\033[32m'
    OPSX_STYLE_FG_FAIL=$'\033[31m'
    OPSX_STYLE_FG_WARN=$'\033[33m'
    OPSX_STYLE_FG_INFO=$'\033[36m'
    OPSX_STYLE_FG_MUTED=$'\033[90m'
    OPSX_STYLE_FG_ACCENT=$'\033[94m'
    OPSX_STYLE_FG_GREY=$'\033[90m'
    OPSX_STYLE_FG_LEAD=$'\033[1;96m'
    OPSX_STYLE_FG_SCOUT=$'\033[32m'
    OPSX_STYLE_FG_FORGE=$'\033[34m'
    OPSX_STYLE_FG_DEVELOPER=$'\033[33m'
    OPSX_STYLE_FG_QA=$'\033[31m'
    OPSX_STYLE_FG_ARCHITECT=$'\033[38;5;208m'
    OPSX_STYLE_FG_DEVOPS=$'\033[94m'
    OPSX_STYLE_FG_OPSX=$'\033[90m'
    OPSX_STYLE_FG_UNKNOWN=$'\033[39m'
    OPSX_STYLE_FG_DRAFT=$'\033[38;5;67m'
    OPSX_STYLE_FG_FADED=$'\033[38;5;240m'
    OPSX_STYLE_FG_RED=$'\033[31m'
else
    OPSX_STYLE_RESET=''
    OPSX_STYLE_BOLD=''
    OPSX_STYLE_DIM=''
    OPSX_STYLE_STRIKE=''
    OPSX_STYLE_STRIKE_OFF=''

    OPSX_STYLE_FG_OK=''
    OPSX_STYLE_FG_FAIL=''
    OPSX_STYLE_FG_WARN=''
    OPSX_STYLE_FG_INFO=''
    OPSX_STYLE_FG_MUTED=''
    OPSX_STYLE_FG_ACCENT=''
    OPSX_STYLE_FG_GREY=''
    OPSX_STYLE_FG_LEAD=''
    OPSX_STYLE_FG_SCOUT=''
    OPSX_STYLE_FG_FORGE=''
    OPSX_STYLE_FG_DEVELOPER=''
    OPSX_STYLE_FG_QA=''
    OPSX_STYLE_FG_ARCHITECT=''
    OPSX_STYLE_FG_DEVOPS=''
    OPSX_STYLE_FG_OPSX=''
    OPSX_STYLE_FG_UNKNOWN=''
    OPSX_STYLE_FG_DRAFT=''
    OPSX_STYLE_FG_FADED=''
    OPSX_STYLE_FG_RED=''
fi

OPSX_AGENT_LEAD="$OPSX_STYLE_FG_LEAD"
OPSX_AGENT_SCOUT="$OPSX_STYLE_FG_SCOUT"
OPSX_AGENT_FORGE="$OPSX_STYLE_FG_FORGE"
OPSX_AGENT_DEVELOPER="$OPSX_STYLE_FG_DEVELOPER"
OPSX_AGENT_QA="$OPSX_STYLE_FG_QA"
OPSX_AGENT_ARCHITECT="$OPSX_STYLE_FG_ARCHITECT"
OPSX_AGENT_DEVOPS="$OPSX_STYLE_FG_DEVOPS"
OPSX_AGENT_OPSX="$OPSX_STYLE_FG_OPSX"
OPSX_AGENT_UNKNOWN="$OPSX_STYLE_FG_UNKNOWN"

OPSX_GLYPH_START='→'
OPSX_GLYPH_DONE='✓'
OPSX_GLYPH_FAIL='✗'
OPSX_GLYPH_WAIT='·'
OPSX_GLYPH_RUN='●'
OPSX_GLYPH_IDLE='○'
OPSX_GLYPH_SPINNER='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'

OPSX_BADGE_PLAN='[ plan       ]'
OPSX_BADGE_DECIDE='[ decide     ]'
OPSX_BADGE_IMPLEMENT='[ implement  ]'
OPSX_BADGE_VERIFY='[ verify     ]'
OPSX_BADGE_ARCHIVE='[ archive    ]'

OPSX_BADGE_ACTIVE='[   active   ]'
OPSX_BADGE_IN_PROGRESS='[in-progress ]'
OPSX_BADGE_DRAFT='[   draft    ]'
OPSX_BADGE_COMPLETED='[ completed  ]'
OPSX_BADGE_VERIFIED='[  verified  ]'
OPSX_BADGE_ARCHIVED='[  archived  ]'
OPSX_BADGE_DELETED='[  deleted   ]'

OPSX_BAR_CELLS='█ ░'

export \
    OPSX_STYLE_RESET OPSX_STYLE_BOLD OPSX_STYLE_DIM OPSX_STYLE_STRIKE OPSX_STYLE_STRIKE_OFF \
    OPSX_STYLE_FG_OK OPSX_STYLE_FG_FAIL OPSX_STYLE_FG_WARN OPSX_STYLE_FG_INFO \
    OPSX_STYLE_FG_MUTED OPSX_STYLE_FG_ACCENT OPSX_STYLE_FG_GREY \
    OPSX_STYLE_FG_LEAD OPSX_STYLE_FG_SCOUT OPSX_STYLE_FG_FORGE OPSX_STYLE_FG_DEVELOPER \
    OPSX_STYLE_FG_QA OPSX_STYLE_FG_ARCHITECT OPSX_STYLE_FG_DEVOPS OPSX_STYLE_FG_OPSX \
    OPSX_STYLE_FG_UNKNOWN \
    OPSX_STYLE_FG_DRAFT OPSX_STYLE_FG_FADED OPSX_STYLE_FG_RED \
    OPSX_AGENT_LEAD OPSX_AGENT_SCOUT OPSX_AGENT_FORGE OPSX_AGENT_DEVELOPER OPSX_AGENT_QA \
    OPSX_AGENT_ARCHITECT OPSX_AGENT_DEVOPS OPSX_AGENT_OPSX OPSX_AGENT_UNKNOWN \
    OPSX_GLYPH_START OPSX_GLYPH_DONE OPSX_GLYPH_FAIL OPSX_GLYPH_WAIT OPSX_GLYPH_RUN \
    OPSX_GLYPH_IDLE OPSX_GLYPH_SPINNER \
    OPSX_BADGE_PLAN OPSX_BADGE_DECIDE OPSX_BADGE_IMPLEMENT OPSX_BADGE_VERIFY OPSX_BADGE_ARCHIVE \
    OPSX_BADGE_ACTIVE OPSX_BADGE_IN_PROGRESS OPSX_BADGE_DRAFT OPSX_BADGE_COMPLETED \
    OPSX_BADGE_VERIFIED OPSX_BADGE_ARCHIVED OPSX_BADGE_DELETED \
    OPSX_BAR_CELLS

# Legacy aliases retained for current callers and pre-polish status rendering.
OPSX_GLYPH_FAILED="$OPSX_GLYPH_FAIL"
OPSX_GLYPH_OTHER="$OPSX_GLYPH_WAIT"
OPSX_GLYPH_RUNNING="$OPSX_GLYPH_RUN"
OPSX_GLYPH_WAITING="$OPSX_GLYPH_IDLE"

# Legacy aliases retained for current callers.
RST="$OPSX_STYLE_RESET"
BOLD="$OPSX_STYLE_BOLD"
DIM="$OPSX_STYLE_DIM"
GREY="$OPSX_STYLE_FG_GREY"
C_LEAD="$OPSX_STYLE_FG_LEAD"
C_SCOUT="$OPSX_STYLE_FG_SCOUT"
C_FORGE="$OPSX_STYLE_FG_FORGE"
C_DEV="$OPSX_STYLE_FG_DEVELOPER"
C_QA="$OPSX_STYLE_FG_QA"
C_ARCH="$OPSX_STYLE_FG_ARCHITECT"
C_OPS="$OPSX_STYLE_FG_DEVOPS"
C_UNKNOWN="$OPSX_STYLE_FG_UNKNOWN"
C_OK="$OPSX_STYLE_FG_OK"
C_FAIL="$OPSX_STYLE_FG_FAIL"
C_WARN="$OPSX_STYLE_FG_WARN"
C_INFO="$OPSX_STYLE_FG_INFO"
C_DRAFT="$OPSX_STYLE_FG_DRAFT"
C_FADED="$OPSX_STYLE_FG_FADED"
C_RED="$OPSX_STYLE_FG_RED"
STRIKE="$OPSX_STYLE_STRIKE"
STRIKE_OFF="$OPSX_STYLE_STRIKE_OFF"

# ── Agent color ─────────────────────────────────────────────────────

color_for_agent() {
    case "$1" in
        lead)      printf '%s' "$OPSX_AGENT_LEAD" ;;
        scout)     printf '%s' "$OPSX_AGENT_SCOUT" ;;
        forge)     printf '%s' "$OPSX_AGENT_FORGE" ;;
        developer) printf '%s' "$OPSX_AGENT_DEVELOPER" ;;
        qa)        printf '%s' "$OPSX_AGENT_QA" ;;
        architect) printf '%s' "$OPSX_AGENT_ARCHITECT" ;;
        devOps)    printf '%s' "$OPSX_AGENT_DEVOPS" ;;
        *)         printf '%s' "$OPSX_AGENT_UNKNOWN" ;;
    esac
}

# ── Lifecycle badge (12-col padded, bracketed) ──────────────────────

badge_for_status() {
    local s="$1" badge="" col="" bold=""
    case "$s" in
        active)       badge="$OPSX_BADGE_ACTIVE"; col="$C_INFO" ;;
        in-progress)  badge="$OPSX_BADGE_IN_PROGRESS"; col="$C_WARN" ;;
        draft)        badge="$OPSX_BADGE_DRAFT"; col="$C_DRAFT" ;;
        completed)    badge="$OPSX_BADGE_COMPLETED"; col="$C_OK" ;;
        verified)     badge="$OPSX_BADGE_VERIFIED"; col="$C_OK"; bold="$BOLD" ;;
        archived)     badge="$OPSX_BADGE_ARCHIVED"; col="$C_FADED" ;;
        deleted)      badge="$OPSX_BADGE_DELETED"; col="$C_RED" ;;
        *)            badge="$OPSX_BADGE_DRAFT"; col="$C_DRAFT" ;;
    esac
    printf '%s%s%s%s' "$col" "$bold" "$badge" "$RST"
}

# ── Progress bar ────────────────────────────────────────────────────
# bar <filled> <total> → "████░░░░░░  40%  (4/10)"

bar() {
    local filled="$1" total="$2"
    awk -v f="$filled" -v t="$total" -v cells="$OPSX_BAR_CELLS" '
        BEGIN {
            w = 10
            split(cells, bar_cells, / /)
            filled_cell = bar_cells[1]
            empty_cell = bar_cells[2]
            if (t <= 0) { pct = 0; fw = 0 }
            else        { pct = int((f * 100) / t); fw = int((f * w) / t) }
            if (fw > w) fw = w
            if (fw < 0) fw = 0
            out = ""
            for (i = 0; i < fw; i++)   out = out filled_cell
            for (i = fw; i < w; i++)   out = out empty_cell
            printf "%s %3d%%  (%d/%d)", out, pct, f, t
        }
    '
}

# ── Row renderer (shared by opsx-log and opsx-render) ───────────────
# opsx_render_row <state> <agent> <description> [elapsed] → stylized row.
#
# Single source of truth for the row format emitted to stderr by
# opsx-log (live) and to stdout by opsx-render (replay). Both
# invocations produce byte-identical output for the same inputs,
# enforced by the Konsist parity test.
#
# State vocabulary matches opsx-log: start / done / failed / other.

opsx_render_row() {
    local state="${1:-}" agent="${2:-}" desc="${3:-}" elapsed="${4:-}"
    local glyph col_agent agent_field agent_raw agent_pad_len agent_pad desc_pad

    case "$state" in
        start)  glyph="${DIM}→${RST}" ;;
        done)   glyph="${C_OK}✓${RST}" ;;
        failed) glyph="${C_RED}✗${RST}" ;;
        *)      glyph="${DIM}·${RST}" ;;
    esac

    col_agent="$(color_for_agent "$agent")"
    agent_field="$(printf '%s@%s%s' "$col_agent" "$agent" "$RST")"
    agent_raw="@${agent}"
    agent_pad_len=$(( 12 - ${#agent_raw} ))
    if [ "$agent_pad_len" -lt 1 ]; then agent_pad_len=1; fi
    agent_pad="$(printf "%${agent_pad_len}s" "")"
    desc_pad=$(printf '%-48s' "$desc")

    if [ -n "$elapsed" ]; then
        printf '  %s  %s%s%s  %s%s%s\n' \
            "$glyph" "$agent_field" "$agent_pad" "$desc_pad" "$DIM" "$elapsed" "$RST"
    else
        printf '  %s  %s%s%s\n' \
            "$glyph" "$agent_field" "$agent_pad" "$desc_pad"
    fi
}

# ── log_row (canonical stderr emitter used by opsx-log) ─────────────
#
# Thin stable-API wrapper around opsx_render_row that writes the one
# canonical line directly to stderr so callers do not have to remember
# the `printf ... >&2` dance. Respects NO_COLOR (handled by the
# palette) and the `OPSX_STYLE_WIDTH` environment hint — currently
# advisory since opsx_render_row pads descriptions to column 48
# regardless.
#
# Usage: log_row <state> <agent> <description> [<elapsed>]

log_row() {
    local state="${1:-}" agent="${2:-}" desc="${3:-}" elapsed="${4:-}"
    local row
    # opsx_render_row's trailing newline is swallowed by $(...); re-add
    # it so one log_row call == one stderr line, matching the golden
    # contract enforced by OpsxLogRowGoldenTest.
    row="$(opsx_render_row "$state" "$agent" "$desc" "$elapsed")"
    printf '%s\n' "$row" >&2 || true
}

# ── Elapsed humaniser ───────────────────────────────────────────────
# elapsed <seconds> → "12s" / "1m 02s" / "2h 14m" / "1d 3h" / "3d"

elapsed() {
    local s="${1:-0}"
    awk -v s="$s" '
        BEGIN {
            if (s < 0)    s = 0
            if (s < 60)   { printf "%ds", s; exit }
            if (s < 3600) { printf "%dm %02ds", int(s/60), s%60; exit }
            if (s < 86400){ printf "%dh %02dm", int(s/3600), int((s%3600)/60); exit }
            d = int(s/86400); h = int((s%86400)/3600)
            if (h == 0) { printf "%dd", d; exit }
            printf "%dd %dh", d, h
        }
    '
}
