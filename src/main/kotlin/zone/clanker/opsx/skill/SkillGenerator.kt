package zone.clanker.opsx.skill

import zone.clanker.opsx.Opsx
import java.io.File
import java.nio.file.Files

class SkillGenerator(
    private val rootDir: File,
    private val tasks: List<TaskInfo>,
    private val buildNames: List<String>,
    private val defaultAgent: String,
) {
    private fun activeTarget(): AgentTarget =
        AGENT_CONFIG[defaultAgent]
            ?: error("Unknown agent '$defaultAgent'. Valid agents: ${AGENT_CONFIG.keys.joinToString()}")

    fun generate(): List<File> {
        val sourceDir = generateSkillFiles(tasks, buildNames)
        generateInstructionFiles(tasks, buildNames)
        generateAgentDefinitions()

        return listOf(sourceDir)
    }

    internal fun generateSkillFiles(
        tasks: List<TaskInfo>,
        builds: List<String>,
    ): File {
        val sourceDir = File(homeDir(), SKILLS_DIR)
        sourceDir.mkdirs()

        // Write all skills to the single source directory
        tasks.forEach { task ->
            File(sourceDir, "${task.name}.md").writeText(buildCommandFile(task, builds))
        }

        // Symlink from the active agent's expected location (home + project)
        val skillDir = activeTarget().skillDir
        val allTargetDirs =
            listOf(
                File(homeDir(), skillDir),
                File(rootDir, skillDir),
            )
        allTargetDirs.forEach { targetDir ->
            targetDir.mkdirs()
            tasks.forEach { task ->
                val source = File(sourceDir, "${task.name}.md").toPath()
                val link = File(targetDir, "${task.name}.md").toPath()
                runCatching {
                    // Only remove the file if it is a symlink pointing into our source dir.
                    // This avoids clobbering user-created files that happen to share the same name.
                    if (Files.exists(link) || Files.isSymbolicLink(link)) {
                        if (Files.isSymbolicLink(link) &&
                            Files.readSymbolicLink(link).startsWith(sourceDir.toPath())
                        ) {
                            Files.delete(link)
                        } else if (!Files.isSymbolicLink(link)) {
                            // Not a symlink — leave user-owned file untouched
                            return@runCatching
                        } else {
                            // Symlink pointing elsewhere — leave it
                            return@runCatching
                        }
                    }
                    Files.createSymbolicLink(link, source)
                }
            }
        }

        return sourceDir
    }

    private fun homeDir(): String = System.getProperty("user.home")

    internal fun buildCommandFile(
        task: TaskInfo,
        builds: List<String>,
    ): String =
        buildString {
            appendLine("# ${task.name}")
            appendLine()
            appendLine(task.description.ifEmpty { "No description available." })

            val usage = TASK_USAGE[task.name]
            if (usage != null) {
                appendLine()
                appendLine("## Usage")
                appendLine()
                appendLine("```bash")
                appendLine(usage.example)
                appendLine("```")
                if (usage.flags.isNotEmpty()) {
                    appendLine()
                    appendLine("## Flags")
                    appendLine()
                    usage.flags.forEach { (flag, desc) ->
                        appendLine("- `-P$flag` — $desc")
                    }
                }
                if (usage.notes.isNotBlank()) {
                    appendLine()
                    appendLine("## Notes")
                    appendLine()
                    appendLine(usage.notes)
                }
            } else {
                appendLine()
                appendLine("## Usage")
                appendLine()
                appendLine("```bash")
                appendLine("./gradlew -q ${task.name}")
                appendLine("```")
            }

            if (task.name in AGENT_TASKS) {
                appendLine()
                appendLine("## Execution")
                appendLine()
                appendLine("This task dispatches to an external AI agent and may take several minutes.")
                appendLine("Run the Gradle command in the background so it does not block the session.")
                appendLine("When it completes, display the output to the user.")
            }

            if (builds.isNotEmpty()) {
                appendLine()
                appendLine("## Included Builds")
                appendLine()
                builds.forEach { name -> appendLine("- $name") }
            }
        }

    internal fun generateInstructionFiles(
        tasks: List<TaskInfo>,
        builds: List<String>,
    ) {
        val instructions = buildInstructions(tasks, builds)

        // Always write AGENTS.md (universal fallback)
        val agentsMd = File(rootDir, "AGENTS.md")
        writeWithMarkers(agentsMd, instructions)

        // Write agent-specific instruction file only if configured
        val instructionFile = activeTarget().instructionFile
        if (instructionFile != null) {
            val agentFile = File(rootDir, instructionFile)
            agentFile.parentFile?.mkdirs()
            writeWithMarkers(agentFile, instructions)
        }
    }

    internal fun generateAgentDefinitions() {
        val agentDir = activeTarget().agentDir ?: return
        val dir = File(rootDir, agentDir)
        dir.mkdirs()
        val file = File(dir, "opsx.md")
        file.writeText(buildAgentDefinition())
    }

    @Suppress("LongMethod")
    private fun buildAgentDefinition(): String =
        buildString {
            when (defaultAgent) {
                "claude" -> {
                    appendLine("---")
                    appendLine("name: opsx")
                    appendLine("description: |")
                    appendLine("  Use this agent when the user wants to propose, apply, verify, or manage")
                    appendLine("  changes using the opsx spec-driven development workflow.")
                    appendLine("model: inherit")
                    appendLine("color: green")
                    appendLine("---")
                    appendLine()
                }
                "copilot" -> {
                    appendLine("# opsx Agent")
                    appendLine()
                    appendLine("Use this agent when the user wants to propose, apply, verify, or manage")
                    appendLine("changes using the opsx spec-driven development workflow.")
                    appendLine()
                }
            }
            appendLine("You are the opsx workflow agent for this workspace.")
            appendLine()
            appendLine("## Change Lifecycle")
            appendLine()
            appendLine("All changes follow a strict lifecycle:")
            append("1. **Propose** — ")
            append("`./gradlew -q opsx-propose -P${Opsx.PROP_PROMPT}=\"description\"`")
            appendLine(" creates a change with proposal.md, design.md, and tasks.md.")
            append("2. **Apply** — ")
            append("`./gradlew -q opsx-apply -P${Opsx.PROP_CHANGE}=\"change-name\"`")
            appendLine(" sends the proposal + design to the agent for implementation.")
            append("3. **Verify** — ")
            append("`./gradlew -q opsx-verify -P${Opsx.PROP_CHANGE}=\"change-name\"`")
            appendLine(" reviews the implementation against the design.")
            append("4. **Archive** — ")
            append("`./gradlew -q opsx-archive -P${Opsx.PROP_CHANGE}=\"change-name\"`")
            appendLine(" archives a completed change.")
            appendLine()
            appendLine("## Available Tasks")
            appendLine()
            appendLine("| Task | Description |")
            appendLine("|------|-------------|")
            appendLine("| `opsx-propose` | Propose a new change |")
            appendLine("| `opsx-apply` | Apply a change to the codebase |")
            appendLine("| `opsx-verify` | Verify a change was applied correctly |")
            appendLine("| `opsx-archive` | Archive a completed change |")
            appendLine("| `opsx-continue` | Continue work on an in-progress change |")
            appendLine("| `opsx-explore` | Explore the codebase (read-only) |")
            appendLine("| `opsx-feedback` | Provide feedback on a change |")
            appendLine("| `opsx-onboard` | Onboard a new contributor |")
            appendLine("| `opsx-ff` | Fast-forward a change to the latest state |")
            appendLine("| `opsx-bulk-archive` | Archive all completed changes |")
            appendLine("| `opsx-status` | Show all changes and their status |")
            appendLine("| `opsx-list` | List all changes |")
            appendLine("| `opsx-sync` | Generate agent skills and instruction files |")
            appendLine("| `opsx-clean` | Remove all generated skill files and symlinks |")
            appendLine()
            appendLine("## Rules")
            appendLine()
            appendLine("- **All workflow operations MUST use opsx Gradle tasks.** Never modify change files manually.")
            appendLine("- Do NOT manually create or edit files in the `opsx/changes/` directory.")
            appendLine("- Do NOT manually edit `tasks.md`, `proposal.md`, or `design.md` inside change directories")
            appendLine("  unless the user explicitly asks you to.")
            appendLine("- Use `opsx-status` to check current change state before starting work.")
            appendLine("- Always check `.srcx/context.md` for codebase context before making changes.")
            appendLine("- Do NOT use grep/sed/awk for refactoring. Use srcx tasks.")
        }

    private fun writeWithMarkers(
        file: File,
        content: String,
    ) {
        val marker = "<!-- OPSX:AUTO -->"
        val endMarker = "<!-- /OPSX:AUTO -->"

        if (file.exists()) {
            val existing = file.readText()
            if (existing.contains(marker)) {
                val before = existing.substringBefore(marker)
                val after = existing.substringAfter(endMarker, "")
                file.writeText("$before$marker\n$content\n$endMarker$after")
                return
            }
            file.appendText("\n$marker\n$content\n$endMarker\n")
        } else {
            file.writeText("$marker\n$content\n$endMarker\n")
        }
    }

    @Suppress("LongMethod")
    private fun buildInstructions(
        tasks: List<TaskInfo>,
        builds: List<String>,
    ): String =
        buildString {
            appendLine("# OPSX Workspace")
            appendLine()
            appendLine("This is a Gradle workspace managed by OPSX.")
            appendLine("Use the available slash commands or Gradle tasks.")
            appendLine()
            tasks.groupBy { it.group }.forEach { (group, groupTasks) ->
                appendLine("## $group")
                appendLine()
                appendLine("| Skill | Gradle Task | Description |")
                appendLine("|-------|------------|-------------|")
                groupTasks.forEach { task ->
                    val usage = TASK_USAGE[task.name]
                    val example = if (usage != null) "`${usage.example}`" else "`./gradlew -q ${task.name}`"
                    appendLine("| `/${task.name}` | $example | ${task.description} |")
                }
                appendLine()
            }
            if (builds.isNotEmpty()) {
                appendLine("## Included Builds")
                appendLine()
                builds.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("## Rules")
            appendLine()
            appendLine(
                "- **All workflow operations MUST use opsx Gradle tasks.** Use `opsx-propose` to propose changes,",
            )
            appendLine("  `opsx-apply` to implement, `opsx-verify` to validate, `opsx-archive` to complete.")
            appendLine("- Do NOT use grep/sed/awk for refactoring. Use srcx tasks.")
            append("- Do NOT manually create or edit files in the `opsx/changes/` directory.")
            appendLine(" Use the opsx Gradle tasks.")
            appendLine("- Do NOT manually edit `tasks.md`, `proposal.md`, or `design.md` inside change directories")
            appendLine("  unless the user explicitly asks you to.")
            appendLine("- Do NOT manually edit files across included builds. Use the Gradle tasks.")
            appendLine("- Always check `.srcx/context.md` for codebase context before making changes.")
            appendLine("- Use `opsx-status` to check current change state before starting work.")
        }

    data class TaskUsage(
        val example: String,
        val flags: Map<String, String>,
        val notes: String = "",
    )

    companion object {
        val TRACKED_GROUPS =
            setOf("opsx", "srcx", "claude", "copilot", "codex", "opencode", "wrkx")

        const val SKILLS_DIR = ".clkx/skills"

        data class AgentTarget(
            val skillDir: String,
            val instructionFile: String?,
            val agentDir: String?,
        )

        val AGENT_CONFIG =
            mapOf(
                "claude" to AgentTarget(".claude/commands", "CLAUDE.md", ".claude/agents"),
                "copilot" to AgentTarget(".github/prompts", ".github/copilot-instructions.md", ".github/agents"),
                "codex" to AgentTarget(".codex/prompts", null, null),
                "opencode" to AgentTarget(".opencode/commands", null, null),
            )

        val AGENT_TARGETS = AGENT_CONFIG.values.map { it.skillDir }

        val AGENT_TASKS =
            setOf(
                "opsx-propose",
                "opsx-apply",
                "opsx-verify",
                "opsx-continue",
                "opsx-explore",
                "opsx-feedback",
                "opsx-onboard",
                "opsx-ff",
            )

        fun generatedDirs(agent: String? = null): List<File> {
            val home = File(System.getProperty("user.home"))
            val targets =
                if (agent != null) {
                    val config = AGENT_CONFIG[agent] ?: error("Unknown agent: $agent")
                    listOf(config.skillDir)
                } else {
                    AGENT_TARGETS
                }
            return listOf(File(home, SKILLS_DIR)) +
                targets.map { File(home, it) }
        }

        fun instructionFiles(
            rootDir: File,
            agent: String? = null,
        ): List<File> {
            if (agent != null) {
                val config = AGENT_CONFIG[agent] ?: error("Unknown agent: $agent")
                val files = mutableListOf(File(rootDir, "AGENTS.md"))
                config.instructionFile?.let { files.add(File(rootDir, it)) }
                return files
            }
            return listOf(
                File(rootDir, "CLAUDE.md"),
                File(rootDir, "AGENTS.md"),
                File(rootDir, ".github/copilot-instructions.md"),
            )
        }

        @Suppress("MaxLineLength")
        internal val TASK_USAGE =
            mapOf(
                "opsx-propose" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-propose -P${Opsx.PROP_PROMPT}=\"add retry logic to HTTP client\"",
                        flags =
                            mapOf(
                                Opsx.PROP_PROMPT to "Freeform description of the change (required unless spec is given)",
                                Opsx.PROP_SPEC to "Name of a spec file in opsx/specs/ (alternative to prompt)",
                                Opsx.PROP_CHANGE_NAME to "Override the auto-generated change name",
                                Opsx.PROP_AGENT to "Agent to use: claude, copilot, codex, opencode (default: claude)",
                                Opsx.PROP_MODEL to "Model override for the agent",
                            ),
                        notes = "Creates a new change directory with proposal.md, design.md, and tasks.md.",
                    ),
                "opsx-apply" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-apply -P${Opsx.PROP_CHANGE}=\"add-retry-logic\"",
                        flags =
                            mapOf(
                                Opsx.PROP_CHANGE to "Name of the change to apply (required)",
                                Opsx.PROP_AGENT to "Agent to use (default: claude)",
                                Opsx.PROP_MODEL to "Model override",
                            ),
                        notes = "Sends the proposal + design to the agent for implementation. Marks change as in-progress.",
                    ),
                "opsx-verify" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-verify -P${Opsx.PROP_CHANGE}=\"add-retry-logic\"",
                        flags =
                            mapOf(
                                Opsx.PROP_CHANGE to "Name of the change to verify (required)",
                                Opsx.PROP_AGENT to "Agent to use (default: claude)",
                                Opsx.PROP_MODEL to "Model override",
                            ),
                        notes = "Agent reviews the implementation against the design. Marks as verified on success.",
                    ),
                "opsx-archive" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-archive -P${Opsx.PROP_CHANGE}=\"add-retry-logic\"",
                        flags =
                            mapOf(
                                Opsx.PROP_CHANGE to "Name of the change to archive (required)",
                            ),
                        notes = "Archives a completed/verified change. Use opsx-bulk-archive to archive all done changes.",
                    ),
                "opsx-status" to
                    TaskUsage(
                        example = "./gradlew -q opsx-status",
                        flags = emptyMap(),
                        notes = "Shows all changes grouped by status. Use -q for clean output without Gradle noise.",
                    ),
                "opsx-sync" to
                    TaskUsage(
                        example = "./gradlew -q opsx-sync",
                        flags = emptyMap(),
                        notes = "Generates slash commands for Claude, Copilot, Codex, and OpenCode. Also updates CLAUDE.md and AGENTS.md.",
                    ),
                "opsx-continue" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-continue -P${Opsx.PROP_CHANGE}=\"add-retry-logic\"",
                        flags =
                            mapOf(
                                Opsx.PROP_CHANGE to "Name of the change to continue (required)",
                                Opsx.PROP_AGENT to "Agent to use (default: claude)",
                                Opsx.PROP_MODEL to "Model override",
                            ),
                        notes = "Picks up where the last apply left off. Focuses on unchecked tasks in tasks.md.",
                    ),
                "opsx-explore" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-explore -P${Opsx.PROP_PROMPT}=\"how does auth work?\"",
                        flags =
                            mapOf(
                                Opsx.PROP_PROMPT to "Question about the codebase (required)",
                                Opsx.PROP_AGENT to "Agent to use (default: claude)",
                                Opsx.PROP_MODEL to "Model override",
                            ),
                        notes = "Sends the question with srcx context to an agent. Read-only — no files are modified.",
                    ),
                "opsx-feedback" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-feedback -P${Opsx.PROP_CHANGE}=\"add-retry-logic\" -P${Opsx.PROP_PROMPT}=\"use exponential backoff\"",
                        flags =
                            mapOf(
                                Opsx.PROP_CHANGE to "Name of the change (required)",
                                Opsx.PROP_PROMPT to "Feedback to incorporate (required)",
                                Opsx.PROP_AGENT to "Agent to use (default: claude)",
                                Opsx.PROP_MODEL to "Model override",
                            ),
                        notes = "Records feedback in feedback.md and asks the agent to incorporate it.",
                    ),
                "opsx-onboard" to
                    TaskUsage(
                        example = "./gradlew -q opsx-onboard",
                        flags =
                            mapOf(
                                Opsx.PROP_PROMPT to "Background of the new contributor (optional)",
                                Opsx.PROP_AGENT to "Agent to use (default: claude)",
                                Opsx.PROP_MODEL to "Model override",
                            ),
                        notes = "Gives a guided tour of the project: architecture, key files, how to contribute.",
                    ),
                "opsx-ff" to
                    TaskUsage(
                        example =
                            "./gradlew -q opsx-ff -P${Opsx.PROP_CHANGE}=\"add-retry-logic\"",
                        flags =
                            mapOf(
                                Opsx.PROP_CHANGE to "Name of the change to fast-forward (required)",
                                Opsx.PROP_AGENT to "Agent to use (default: claude)",
                                Opsx.PROP_MODEL to "Model override",
                            ),
                        notes = "Updates proposal and design to match current codebase state after other changes landed.",
                    ),
                "opsx-bulk-archive" to
                    TaskUsage(
                        example = "./gradlew -q opsx-bulk-archive",
                        flags = emptyMap(),
                        notes = "Archives all changes with status completed, done, or verified.",
                    ),
            )
    }
}
