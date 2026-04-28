---
name: no-python
description: Absolute prohibition on suggesting, using, or adding Python as a dependency, scripting language, tool, or implementation option in this workspace.
---

# /no-python

> Related: `/kotlin-lang` · `/kotlin-functional-first` · `/clikt`

## Rule

**Python is not an acceptable solution in this workspace.** Not for
scripts, not for hooks, not for one-offs, not for "zero-install"
defaults, not for tooling glue, not for tests.

Omit Python from every menu of alternatives. Do not list-and-reject it.
Do not offer it as a fallback. Do not add `.py` files. Do not add
`requirements.txt`, `pyproject.toml`, `uv.lock`, or any Python
toolchain artefact.

## What to reach for instead

When a task looks "Pythonic" (glue script, data wrangling, CLI helper,
log parser, hook body), pick in this order:

1. **Kotlin/JVM** inside the existing Gradle build.
2. **Kotlin/Native** when a small standalone binary is needed and JVM
   cold-start is a problem. Use `kotlinx.cli` or `clikt` for arg parsing.
3. **Bash + POSIX utilities** (`sed`, `awk`, `grep`, `sort`) for
   throw-away shell plumbing. Use `jq` when JSON is involved — it is
   an acceptable install.
4. **Node.js** only when the surrounding project already depends on it.
5. **Go** when an existing Go binary is already in the workspace and
   extending it is the lowest-friction option.

## Hook scripts and small CLIs

When an agent needs a fast append-only logger, status reporter, or
JSON emitter:

- Kotlin/Native `opsxctl`-style binary → ~5ms startup, shared types
  with the rest of the codebase.
- Or `bash` + `jq` → ~3ms, depends on one well-known tool.

Never `python3 -c '...'`. Never `python3 script.py`.

## Assistant conduct (no semantic loopholes)

The rule binds the assistant's own shell use, not just its
recommendations to the user.

- Bash is fine. Inside bash, **do not invoke the Python
  interpreter** — no `python`, `python3`, `pip`, `pipx`, `uv`,
  `poetry`, `venv`, `.py` files, no `python -c "…"`, no
  `… | python3 …`, not transiently, not "just for this one
  replacement", not "the quickest way to do X".
- When generating a proposal, a `tasks.md`, a `design.md`, or any
  skill update: omit Python from every candidate tool list.
- When performing a bulk text transform across files: use per-file
  `Edit` calls, or `sed`/`awk`. Never Python.
- If a task appears to require Python, stop and tell the user it
  conflicts with this rule, then propose the non-Python path.

There is no "it was only a one-liner" exception. The point of this
rule being in a skill (not just in private memory) is that every
agent under every CLI inherits it — consistently, with no
reinterpretation.

## When asked "what about Python?"

Answer: "Python is excluded from this workspace. Here is the
non-Python path." Then propose the real answer.

## Scope

Applies to:
- Every project under the clanker workspace.
- Every agent (@lead, @scout, @developer, @qa, @forge, @architect, @devOps).
- Every CLI (Claude Code, Copilot, Codex, opencode).
- Production code, test code, tooling, CI, hooks, one-off scripts.

No exceptions. If a user's environment genuinely cannot host anything
else, escalate to the user rather than quietly adding Python.

## Rationale

The user has explicitly, repeatedly rejected Python for this workspace.
This skill exists so future conversations do not re-litigate the
decision. Raising Python wastes everyone's time.
