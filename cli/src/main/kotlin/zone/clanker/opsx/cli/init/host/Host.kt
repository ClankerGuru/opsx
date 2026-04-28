/**
 * Enumeration of AI coding hosts supported by opsx, together with each host's
 * directory layout, instruction file path, and filename conventions.
 */
package zone.clanker.opsx.cli.init.host

/**
 * How agent markdown files are named on disk for a given host.
 */
enum class FilenameConvention {
    /** Agent files use plain `.md` extension (e.g. `dev.md`). */
    PLAIN_MD,

    /** Agent files use `.agent.md` suffix (e.g. `dev.agent.md`), required by Copilot CLI. */
    AGENT_MD,
}

/**
 * Supported AI coding CLI hosts that opsx can emit skills and agents for.
 *
 * Each entry defines the on-disk directory layout and the CLI binary name (if any)
 * needed to complete installation. The [HostEmitter] implementations use these
 * properties to decide where to write files.
 *
 * @property id short lowercase identifier used in CLI flags and config (e.g. `"claude"`)
 * @property skillsDir path relative to workspace root where skill markdown files are written
 * @property agentDir path relative to workspace root where agent definitions are written, or null
 * @property instructionFile path to the host's root instruction markdown, or null
 * @property filenameConvention how agent files should be named on disk
 * @property externalCli name of the host's CLI binary to probe via `which`, or null if none needed
 */
enum class Host(
    val id: String,
    val skillsDir: String,
    val agentDir: String?,
    val instructionFile: String?,
    val filenameConvention: FilenameConvention,
    val externalCli: String?,
) {
    /** Anthropic Claude Code -- writes to `.claude/skills/` and `.claude/agents/`. */
    CLAUDE(
        id = "claude",
        skillsDir = ".claude/skills",
        agentDir = ".claude/agents",
        instructionFile = "CLAUDE.md",
        filenameConvention = FilenameConvention.PLAIN_MD,
        externalCli = null,
    ),

    /** GitHub Copilot CLI -- writes skills and agents directly to `.github/`. */
    COPILOT(
        id = "copilot",
        skillsDir = ".github/skills",
        agentDir = ".github/agents",
        instructionFile = ".github/copilot-instructions.md",
        filenameConvention = FilenameConvention.AGENT_MD,
        externalCli = "copilot",
    ),

    /** OpenAI Codex CLI -- writes a `.codex-plugin/` directory and registers in marketplace.json. */
    CODEX(
        id = "codex",
        skillsDir = ".codex-plugin/skills",
        agentDir = ".codex-plugin/agents",
        instructionFile = "AGENTS.md",
        filenameConvention = FilenameConvention.PLAIN_MD,
        externalCli = "codex",
    ),

    /** OpenCode CLI -- writes commands to `.opencode/command/` and agents to `.opencode/agent/`. */
    OPENCODE(
        id = "opencode",
        skillsDir = ".opencode/command",
        agentDir = ".opencode/agent",
        instructionFile = "AGENTS.md",
        filenameConvention = FilenameConvention.PLAIN_MD,
        externalCli = null,
    ),
    ;

    companion object {
        /** Look up a host by its lowercase [id], returning null if unknown. */
        fun fromId(id: String): Host? = entries.firstOrNull { it.id == id }
    }
}
