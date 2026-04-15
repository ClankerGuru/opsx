package zone.clanker.opsx.model

@Suppress("LongParameterList")
enum class Agent(
    val cliCommand: String,
    val nonInteractiveArgs: List<String>,
    val modelFlag: String,
    val skillDir: String,
    val skillExtension: String,
    val instructionFile: String?,
    val agentDir: String?,
) : java.io.Serializable {
    CLAUDE(
        "claude",
        listOf("-p", "--dangerously-skip-permissions"),
        "--model",
        ".claude/commands",
        ".md",
        "CLAUDE.md",
        ".claude/agents",
    ),
    COPILOT(
        "copilot",
        listOf("-p"),
        "--model",
        ".github/prompts",
        ".prompt.md",
        ".github/copilot-instructions.md",
        ".github/agents",
    ),
    CODEX(
        "codex",
        listOf("exec"),
        "-m",
        ".codex/prompts",
        ".md",
        null,
        null,
    ),
    OPENCODE(
        "opencode",
        listOf("run"),
        "-m",
        ".opencode/commands",
        ".md",
        null,
        null,
    ),
    ;

    val id: String get() = name.lowercase()

    companion object {
        fun fromId(id: String): Agent =
            entries.find { it.id == id }
                ?: error("Unknown agent '$id'. Valid agents: ${entries.joinToString { it.id }}")

        val allSkillDirs: List<String> get() = entries.map { it.skillDir }

        val allIds: Set<String> get() = entries.map { it.id }.toSet()
    }
}
