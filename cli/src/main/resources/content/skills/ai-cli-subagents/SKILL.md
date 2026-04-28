---
name: ai-cli-subagents
description: >-
  Use when authoring user-defined subagents/custom agents for an AI coding CLI
  (Claude Code, Copilot CLI, Codex CLI, OpenCode). Covers file locations,
  frontmatter schemas, tool-restriction fields, skill preload/whitelist
  mechanisms, and invocation syntax for all four CLIs side by side.
---

# AI CLI Subagents — authoring reference

All four CLIs support defining named subagents (a.k.a. custom agents,
personas) that the main agent can delegate to. The **file format,
frontmatter fields, and restriction axes differ** — a file that works
for one CLI will not work for another.

## At a glance

| Feature                      | Claude Code                         | Copilot CLI                           | Codex CLI                                  | OpenCode                                    |
| :--------------------------- | :---------------------------------- | :------------------------------------ | :----------------------------------------- | :------------------------------------------ |
| Project dir                  | `.claude/agents/*.md`               | `.github/agents/*.agent.md`           | `.codex/agents/*.toml`                     | `.opencode/agents/*.md`                     |
| User dir                     | `~/.claude/agents/*.md`             | `~/.copilot/agents/*.agent.md`        | `~/.codex/agents/*.toml`                   | `~/.config/opencode/agents/*.md`            |
| Format                       | Markdown + YAML frontmatter         | Markdown + YAML frontmatter           | **TOML** (whole file)                      | Markdown + YAML frontmatter                 |
| Tool allowlist               | `tools:` (CSV string)               | `tools:` (list)                       | *None directly* — via `sandbox_mode` + MCP | `permission:` (allow/ask/deny per tool)     |
| Tool denylist                | `disallowedTools:` (CSV)            | omit tool from allowlist              | omit MCP server                            | `permission: <tool>: deny`                  |
| Skill preload / whitelist    | `skills:` (YAML list — preloads)    | *None* (skills discovered globally)   | `[[skills.config]]` tables                 | `permission.skill: <pattern>: allow\|deny`  |
| Model override               | `model:`                            | `model:`                              | `model:`                                   | `model:`                                    |
| Color                        | `color:`                            | *Not supported*                       | *Not supported*                            | `color:` (hex or theme keyword)             |
| Auto-delegate by description | Yes                                 | Yes (unless `disable-model-invocation`) | **No** — must name explicitly in prompt    | Yes (subagents) / Tab (primary)             |
| Invocation style             | Task tool, @-mention                | `/agent`, @-mention, `--agent <name>` | Name in prompt (`"have X do Y"`)           | `@<name>` mention, Tab cycle                |

## Claude Code (`.claude/agents/<name>.md`)

YAML frontmatter + Markdown body = system prompt.

```yaml
---
name: reviewer                      # unique id (lowercase + hyphens)
description: Reviews diffs for bugs # when to delegate
model: inherit                      # sonnet | opus | haiku | full id | inherit
color: cyan                         # red|blue|green|yellow|purple|orange|pink|cyan
tools: Read, Grep, Glob             # CSV allowlist; omit = inherit all
disallowedTools: Write, Edit        # CSV denylist
permissionMode: default             # default|acceptEdits|auto|dontAsk|bypassPermissions|plan
skills:                             # preloaded into subagent context at startup
  - kotlin-lang
  - kotest
mcpServers:                         # MCP server allowlist
  - github
hooks: { }                          # PreToolUse | PostToolUse | Stop
memory: project                     # user | project | local
background: false                   # run as background task
effort: medium                      # low | medium | high | xhigh | max
isolation: worktree                 # optional isolated git worktree
maxTurns: 30
initialPrompt: "Start with a diff summary."
---
```

Key points:
- Subagents do **not** inherit skills from the parent. List them in `skills:`.
- `skills:` preloads the **full SKILL.md body** into context at startup (not just the description).
- Docs: https://code.claude.com/docs/en/sub-agents

## Copilot CLI (`.github/agents/<name>.agent.md`)

YAML frontmatter + Markdown body = system prompt. Extension is `.agent.md`.

```yaml
---
name: security-expert
description: Reviews code for vulnerabilities and auth issues
model: claude-sonnet-4.5
tools: ["read", "search"]           # allowlist; "*" or [] = all; omit = inherit
mcp-servers:                        # per-agent MCP configs (doubles as tool allowlist)
  semgrep:
    type: local
    command: semgrep-mcp
    args: ["--config", "auto"]
disable-model-invocation: false     # true = main agent won't auto-delegate
user-invocable: true                # false = user can't invoke directly
target: github-copilot              # github-copilot | vscode
metadata: { }                       # free-form annotations
---
```

Missing vs Claude Code: **no `color:`, no `skills:` preload, no instructions include.**
Skills (`.github/skills/*/SKILL.md`) and instructions (`*.instructions.md` with `applyTo:` globs) are **globally discovered** — you cannot scope them per agent. The only restriction axis is `tools:`.

Invocation: `/agent` (interactive), `@name` mention, auto-delegate via description, or `copilot --agent <name> --prompt "..."`.

Docs: https://docs.github.com/en/copilot/reference/custom-agents-configuration

## Codex CLI (`.codex/agents/<name>.toml`)

Whole file is TOML — **not** Markdown with frontmatter.

```toml
name = "pr_explorer"
description = "Read-only explorer for gathering evidence before proposing changes."
developer_instructions = """
Stay in exploration mode. Cite files and symbols; do not propose fixes.
"""
nickname_candidates = ["Atlas", "Delta", "Echo"]
model = "gpt-5.3-codex-spark"
model_reasoning_effort = "medium"                 # low | medium | high
sandbox_mode = "read-only"                        # read-only | workspace-write | danger-full-access

[mcp_servers.docs]                                # allowlist: only listed servers exposed
url = "https://developers.openai.com/mcp"

[[skills.config]]                                 # per-agent skill enable/disable
path = "/abs/path/to/SKILL.md"
enabled = true
```

Key points:
- **No `tools:` allowlist field.** Tool surface is restricted indirectly via
  `sandbox_mode` + `[mcp_servers.*]` tables.
- **No auto-delegation.** The main agent does **not** pick a subagent from its
  description. You must name the subagent explicitly in the prompt:
  `"have pr_explorer reproduce it"`.
- `[[skills.config]]` overrides are known-buggy as of Codex issue #14161 —
  verify against your version.
- Top-level `[agents]` in `~/.codex/config.toml`: `max_threads` (6),
  `max_depth` (1), `job_max_runtime_seconds`.
- Built-in defaults: `explorer`, `worker`, `default`.
- Distinct from **profiles** (`[profiles.<name>]` in `~/.codex/config.toml`) —
  profiles are CLI-switchable config bundles (`codex --profile <name>`), not
  delegated roles.
- Distinct from **AGENTS.md** — project instruction memory, not a subagent.

Docs: https://developers.openai.com/codex/subagents

## OpenCode (`.opencode/agents/<name>.md`)

YAML frontmatter + Markdown body.

```yaml
---
description: Reviews code for quality and best practices
mode: subagent                      # primary | subagent | all
model: anthropic/claude-sonnet-4-20250514
temperature: 0.1
top_p: 0.9
color: "#FF5733"                    # hex or theme keyword (primary|accent|success|...)
steps: 30                           # max agentic iterations
disable: false
hidden: false                       # hide from @ autocomplete
tools:                              # DEPRECATED — prefer `permission`
  write: false
  edit: false
permission:                         # allow | ask | deny
  edit: deny
  bash: ask
  webfetch: deny
  skill:                            # skills are gated by pattern, not listed
    "documents-*": allow
    "internal-*": deny
    "*": ask
---
```

Key points:
- `mode: subagent` → invoked via `@<name>` or auto-dispatched by a primary.
- `mode: primary` → cycled by `Tab` in the TUI; set default via
  `"default_agent": "plan"` in `opencode.json`.
- No dedicated `skills:` preload — skills are gated through `permission.skill`
  pattern maps.
- Plugins (`opencode.json`'s `plugin` array) are process-wide; cannot be
  scoped per agent.
- Same schema is also accepted inline under `opencode.json`'s `agent` key.

Docs: https://opencode.ai/docs/agents/ · https://opencode.ai/docs/permissions/

## Picking the right restriction axis

| Goal                                  | Claude Code       | Copilot           | Codex                         | OpenCode                             |
| :------------------------------------ | :---------------- | :---------------- | :---------------------------- | :----------------------------------- |
| Read-only agent (no writes)           | `disallowedTools` | omit from `tools` | `sandbox_mode = "read-only"`  | `permission: { edit: deny, write: deny }` |
| Limit to specific skills              | `skills: [...]`   | *Not possible*    | `[[skills.config]]` per agent | `permission.skill` patterns          |
| Limit to specific MCP servers         | `mcpServers: [...]` | `mcp-servers:`  | `[mcp_servers.*]`             | *Process-wide via `opencode.json`*   |
| Block auto-delegation                 | `description` tuning | `disable-model-invocation: true` | Native default      | `hidden: true` hides from `@`        |

## Gotchas

- **Markdown vs TOML**: Codex is the outlier — do not port a `.md` agent to
  Codex without rewriting the whole file as TOML.
- **Filename extensions matter**: Copilot uses `.agent.md`, the others use
  plain `.md` (or `.toml` for Codex).
- **Skill preloading is only a Claude Code primitive.** The other three do
  not inject skill bodies into the agent prompt at startup — skills still
  activate via their own `description` field, if the CLI supports skills
  at all in that context.
- **Color is only Claude Code + OpenCode.** Don't add `color:` to Copilot
  or Codex agent files — it's silently ignored.
- **Auto-delegation behavior differs.** Codex will not pick a subagent from
  its `description` — you must name it in the prompt.

## References

- Claude Code — https://code.claude.com/docs/en/sub-agents
- Copilot — https://docs.github.com/en/copilot/reference/custom-agents-configuration
- Copilot how-to — https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/create-custom-agents-for-cli
- Codex — https://developers.openai.com/codex/subagents
- Codex config — https://developers.openai.com/codex/config-reference
- OpenCode — https://opencode.ai/docs/agents/
- OpenCode permissions — https://opencode.ai/docs/permissions/
