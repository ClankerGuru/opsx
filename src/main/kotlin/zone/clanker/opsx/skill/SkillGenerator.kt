package zone.clanker.opsx.skill

import org.gradle.api.Project
import org.gradle.api.initialization.IncludedBuild
import zone.clanker.opsx.Opsx
import java.io.File
import java.nio.file.Files

class SkillGenerator(
    private val rootProject: Project,
) {
    fun generate(): List<File> {
        val tasks = discoverTasks()
        val includedBuilds = rootProject.gradle.includedBuilds

        val sourceDir = generateSkillFiles(tasks, includedBuilds)
        generateInstructionFiles(tasks, includedBuilds)

        return listOf(sourceDir)
    }

    internal fun discoverTasks(): List<TaskInfo> =
        rootProject.tasks
            .filter { it.group in TRACKED_GROUPS }
            .map { TaskInfo(it.name, it.group ?: "", it.description ?: "") }

    internal fun generateSkillFiles(
        tasks: List<TaskInfo>,
        builds: Collection<IncludedBuild>,
    ): File {
        val sourceDir = File(homeDir(), SKILLS_DIR)
        sourceDir.mkdirs()

        // Write all skills to the single source directory
        tasks.forEach { task ->
            File(sourceDir, "${task.name}.md").writeText(buildCommandFile(task, builds))
        }

        // Symlink from each agent's expected location
        AGENT_TARGETS.forEach { target ->
            val targetDir = File(homeDir(), target)
            targetDir.mkdirs()
            tasks.forEach { task ->
                val source = File(sourceDir, "${task.name}.md").toPath()
                val link = File(targetDir, "${task.name}.md").toPath()
                runCatching {
                    Files.deleteIfExists(link)
                    Files.createSymbolicLink(link, source)
                }
            }
        }

        return sourceDir
    }

    private fun homeDir(): String = System.getProperty("user.home")

    internal fun buildCommandFile(
        task: TaskInfo,
        builds: Collection<IncludedBuild>,
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
                builds.forEach { build -> appendLine("- ${build.name}") }
            }
        }

    internal fun generateInstructionFiles(
        tasks: List<TaskInfo>,
        builds: Collection<IncludedBuild>,
    ) {
        val rootDir = rootProject.rootDir
        val instructions = buildInstructions(tasks, builds)

        val claudeMd = File(rootDir, "CLAUDE.md")
        writeWithMarkers(claudeMd, instructions)

        val agentsMd = File(rootDir, "AGENTS.md")
        writeWithMarkers(agentsMd, instructions)

        val copilotInstructions = File(rootDir, ".github/copilot-instructions.md")
        copilotInstructions.parentFile.mkdirs()
        writeWithMarkers(copilotInstructions, instructions)
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
        builds: Collection<IncludedBuild>,
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
                builds.forEach { appendLine("- ${it.name}") }
                appendLine()
            }
            appendLine("## Rules")
            appendLine()
            appendLine("- Do NOT use grep/sed/awk for refactoring. Use srcx tasks.")
            appendLine("- Do NOT manually edit files across included builds. Use the Gradle tasks.")
            appendLine("- Always check `.srcx/context.md` for codebase context.")
            appendLine("- Changes are proposed via `/opsx-propose` or `./gradlew -q opsx-propose`.")
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

        val AGENT_TARGETS =
            listOf(
                ".claude/commands",
                ".github/prompts",
                ".codex/prompts",
                ".opencode/commands",
            )

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

        fun generatedDirs(): List<File> {
            val home = File(System.getProperty("user.home"))
            return listOf(File(home, SKILLS_DIR)) +
                AGENT_TARGETS.map { File(home, it) }
        }

        fun instructionFiles(rootDir: File): List<File> =
            listOf(
                File(rootDir, "CLAUDE.md"),
                File(rootDir, "AGENTS.md"),
                File(rootDir, ".github/copilot-instructions.md"),
            )

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
