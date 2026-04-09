package zone.clanker.opsx.skill

import org.gradle.api.Project
import org.gradle.api.initialization.IncludedBuild
import java.io.File

class SkillGenerator(
    private val rootProject: Project,
) {
    /** Generate skill files for all agents. Returns the directories written to. */
    fun generate(): List<File> {
        val tasks = discoverTasks()
        val includedBuilds = rootProject.gradle.includedBuilds
        val dirs = mutableListOf<File>()

        dirs.add(generateForClaude(tasks, includedBuilds))
        dirs.add(generateForCopilot(tasks, includedBuilds))
        dirs.add(generateForOpenCode(tasks, includedBuilds))
        generateForCodex(tasks, includedBuilds)
        generateInstructionFiles(tasks, includedBuilds)

        return dirs
    }

    internal fun discoverTasks(): List<TaskInfo> =
        rootProject.tasks
            .filter { it.group in TRACKED_GROUPS }
            .map { TaskInfo(it.name, it.group ?: "", it.description ?: "") }

    internal fun generateForClaude(
        tasks: List<TaskInfo>,
        builds: Collection<IncludedBuild>,
    ): File {
        val dir = File(rootProject.rootDir, ".claude/commands")
        dir.mkdirs()
        tasks.forEach { task ->
            File(dir, "${task.name}.md").writeText(buildCommandFile(task, builds))
        }
        return dir
    }

    internal fun generateForCopilot(
        tasks: List<TaskInfo>,
        builds: Collection<IncludedBuild>,
    ): File {
        val dir = File(rootProject.rootDir, ".github/prompts")
        dir.mkdirs()
        tasks.forEach { task ->
            File(dir, "${task.name}.md").writeText(buildCommandFile(task, builds))
        }
        return dir
    }

    internal fun generateForCodex(
        tasks: List<TaskInfo>,
        builds: Collection<IncludedBuild>,
    ) {
        val homeDir = System.getProperty("user.home")
        val dir = File(homeDir, ".codex/prompts")
        dir.mkdirs()
        tasks.forEach { task ->
            File(dir, "${task.name}.md").writeText(buildCommandFile(task, builds))
        }
    }

    internal fun generateForOpenCode(
        tasks: List<TaskInfo>,
        builds: Collection<IncludedBuild>,
    ): File {
        val dir = File(rootProject.rootDir, ".opencode/commands")
        dir.mkdirs()
        tasks.forEach { task ->
            File(dir, "${task.name}.md").writeText(buildCommandFile(task, builds))
        }
        return dir
    }

    internal fun buildCommandFile(
        task: TaskInfo,
        builds: Collection<IncludedBuild>,
    ): String =
        buildString {
            appendLine("# ${task.name}")
            appendLine()
            appendLine(task.description.ifEmpty { "No description available." })
            appendLine()
            appendLine("## Usage")
            appendLine()
            appendLine("```bash")
            appendLine("./gradlew ${task.name}")
            appendLine("```")
            appendLine()
            appendLine("## Group")
            appendLine()
            appendLine(task.group)

            if (builds.isNotEmpty()) {
                appendLine()
                appendLine("## Included Builds")
                appendLine()
                builds.forEach { build -> appendLine("- ${build.name}") }
            }

            appendLine()
            appendLine("## Context")
            appendLine()
            appendLine("See `.srcx/context.md` for project context if available.")
        }

    internal fun generateInstructionFiles(
        tasks: List<TaskInfo>,
        builds: Collection<IncludedBuild>,
    ) {
        val rootDir = rootProject.rootDir
        val instructions = buildInstructions(tasks, builds)

        // Claude instructions — append to CLAUDE.md
        val claudeMd = File(rootDir, "CLAUDE.md")
        writeWithMarkers(claudeMd, instructions)

        // Copilot instructions — AGENTS.md + .github/copilot-instructions.md
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
            appendLine("Use the available Gradle tasks instead of manual commands.")
            appendLine()
            appendLine("## Available Tasks")
            appendLine()
            tasks.groupBy { it.group }.forEach { (group, groupTasks) ->
                appendLine("### $group")
                groupTasks.forEach { task ->
                    appendLine("- `./gradlew ${task.name}` — ${task.description}")
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
            appendLine("- Changes are proposed via `./gradlew opsx-propose`.")
        }

    companion object {
        val TRACKED_GROUPS =
            setOf("opsx", "srcx", "claude", "copilot", "codex", "opencode", "wrkx")

        /** Files and directories that opsx-sync generates (for opsx-clean). */
        fun generatedDirs(rootDir: File): List<File> =
            listOf(
                File(rootDir, ".claude/commands"),
                File(rootDir, ".github/prompts"),
                File(rootDir, ".opencode/commands"),
            )

        /** Instruction files with OPSX markers (for opsx-clean). */
        fun instructionFiles(rootDir: File): List<File> =
            listOf(
                File(rootDir, "CLAUDE.md"),
                File(rootDir, "AGENTS.md"),
                File(rootDir, ".github/copilot-instructions.md"),
            )
    }
}
