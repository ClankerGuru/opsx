package zone.clanker.opsx.model

enum class Agent(
    val cliCommand: String,
    val nonInteractiveArgs: List<String>,
    val modelFlag: String,
    val skillsDir: String,
    val instructionFile: String?,
    val agentDir: String?,
) : java.io.Serializable {
    CLAUDE(
        "claude",
        listOf("-p", "--dangerously-skip-permissions"),
        "--model",
        ".claude/skills",
        "CLAUDE.md",
        ".claude/agents",
    ),
    COPILOT(
        "copilot",
        listOf("-p"),
        "--model",
        ".github/skills",
        ".github/copilot-instructions.md",
        ".github/agents",
    ),
    CODEX(
        "codex",
        listOf("exec"),
        "-m",
        ".codex/skills",
        "AGENTS.md",
        ".agents",
    ),
    OPENCODE(
        "opencode",
        listOf("run"),
        "-m",
        ".opencode/skills",
        "AGENTS.md",
        ".opencode/agents",
    ),
    ;

    val id: String get() = name.lowercase()
    val usesCopy: Boolean get() = this == COPILOT

    companion object {
        fun fromId(id: String): Agent =
            entries.find { it.id == id }
                ?: error("Unknown agent '$id'. Valid agents: ${entries.joinToString { it.id }}")

        val allSkillsDirs: List<String> get() = entries.map { it.skillsDir }

        val allIds: Set<String> get() = entries.map { it.id }.toSet()
    }
}
