package zone.clanker.opsx.skill

import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Agent
import java.io.File
import java.nio.file.Files

class SkillGenerator(
    private val rootDir: File,
    private val tasks: List<TaskInfo>,
    private val buildNames: List<String>,
    private val agents: List<Agent>,
    private val additionalSourceDirs: List<File> = emptyList(),
    private val additionalAgentDirs: List<File> = emptyList(),
) {
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

        // Clean stale skill subdirs
        val activeNames = tasks.map { it.name }.toSet()
        sourceDir
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name !in activeNames }
            .forEach { it.deleteRecursively() }

        // Write all skills as {name}/SKILL.md
        tasks.forEach { task ->
            val skillDir = File(sourceDir, task.name)
            skillDir.mkdirs()
            File(skillDir, SKILL_FILE).writeText(buildCommandFile(task, builds))
        }

        // Copy entire skill directories from additional source dirs — never override ours
        additionalSourceDirs.forEach { extraDir ->
            if (extraDir.exists()) {
                extraDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.isDirectory && it.name !in activeNames }
                    .forEach { dir ->
                        dir.copyRecursively(File(sourceDir, dir.name), overwrite = true)
                    }
            }
        }

        // Symlink from shared path .agents/skills/{name}/ to source
        val sharedDir = File(rootDir, SHARED_SKILLS_DIR)
        sharedDir.mkdirs()
        sourceDir.listFiles().orEmpty().filter { it.isDirectory }.forEach { skillDir ->
            createSymlink(File(sharedDir, skillDir.name), skillDir)
        }

        // Distribute to each agent (project-level only)
        agents.forEach { agent ->
            distributeSkills(File(rootDir, agent.skillsDir), sourceDir, agent)
        }

        return sourceDir
    }

    private fun homeDir(): String = System.getProperty("user.home")

    private fun distributeSkills(
        targetDir: File,
        sourceDir: File,
        agent: Agent,
    ) {
        targetDir.mkdirs()
        sourceDir.listFiles().orEmpty().filter { it.isDirectory }.forEach { skillDir ->
            val targetSkillDir = File(targetDir, skillDir.name)
            if (agent.usesCopy) {
                skillDir.copyRecursively(targetSkillDir, overwrite = true)
            } else {
                createSymlink(targetSkillDir, skillDir)
            }
        }
    }

    private fun createSymlink(
        target: File,
        source: File,
    ) {
        val link = target.toPath()
        val sourcePath = source.toPath()
        runCatching {
            if (Files.isSymbolicLink(link)) {
                Files.delete(link)
            } else if (Files.exists(link)) {
                return@runCatching
            }
            Files.createSymbolicLink(link, sourcePath)
        }
    }

    @Suppress("LongMethod")
    internal fun buildCommandFile(
        task: TaskInfo,
        builds: List<String>,
    ): String =
        buildString {
            val desc = task.description.ifEmpty { "No description available." }
            val usage = TASK_USAGE[task.name]
            val whenToUse = SKILL_TRIGGERS[task.name]

            appendLine("---")
            appendLine("name: ${task.name}")
            append("description: ")
            if (whenToUse != null) {
                appendLine(">")
                appendLine("  $desc $whenToUse")
            } else {
                appendLine(desc)
            }
            appendLine("---")
            appendLine()
            appendLine("# ${task.name}")
            appendLine()
            appendLine(desc)

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

        // Clean inactive agent instruction files first
        Agent.entries
            .filter { it !in agents }
            .mapNotNull { it.instructionFile }
            .forEach { path -> removeMarkersIfExists(File(rootDir, path)) }

        // Always write AGENTS.md (universal fallback)
        val agentsMd = File(rootDir, "AGENTS.md")
        writeWithMarkers(agentsMd, instructions)

        // Write agent-specific instruction file for each agent
        agents
            .mapNotNull { it.instructionFile }
            .forEach { instructionFile ->
                val agentFile = File(rootDir, instructionFile)
                agentFile.parentFile?.mkdirs()
                writeWithMarkers(agentFile, instructions)
            }
    }

    internal fun generateAgentDefinitions() {
        // Clean inactive agent symlinks from project-level
        Agent.entries
            .filter { it !in agents }
            .mapNotNull { it.agentDir }
            .forEach { path ->
                val file = File(rootDir, "$path/$GENERATED_AGENT_FILE")
                val filePath = file.toPath()
                if (Files.isSymbolicLink(filePath) || Files.exists(filePath)) {
                    Files.deleteIfExists(filePath)
                }
            }

        // Write source of truth to ~/.clkx/agents/opsx.md
        // Prefer CLAUDE for frontmatter, then COPILOT, then any with agentDir
        val primaryAgent =
            PREFERRED_PRIMARY_ORDER
                .firstOrNull { it in agents && it.agentDir != null }
                ?: agents.firstOrNull { it.agentDir != null }
                ?: return
        val homeAgentsDir = File(homeDir(), AGENTS_DIR)
        homeAgentsDir.mkdirs()
        writeAgentDefinition(primaryAgent, homeAgentsDir)

        // Distribute from each active agent's project dir (symlink or copy)
        val sourceFile = File(homeAgentsDir, GENERATED_AGENT_FILE)
        agents.forEach { agent ->
            val agentDir = agent.agentDir ?: return@forEach
            val projectDir = File(rootDir, agentDir)
            projectDir.mkdirs()
            distributeAgentFile(File(projectDir, GENERATED_AGENT_FILE), sourceFile, agent)

            // Distribute additional .md files from ~/.clkx/agents/
            homeAgentsDir
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".md") && it.name != GENERATED_AGENT_FILE }
                .forEach { src -> distributeAgentFile(File(projectDir, src.name), src, agent) }
        }
    }

    private fun distributeAgentFile(
        target: File,
        source: File,
        agent: Agent,
    ) {
        if (agent.usesCopy) {
            source.copyTo(target, overwrite = true)
        } else {
            createSymlink(target, source)
        }
    }

    private fun writeAgentDefinition(
        agent: Agent,
        targetDir: File,
    ) {
        File(targetDir, GENERATED_AGENT_FILE).writeText(buildAgentDefinition(agent))

        // Copy .md files from additional agent directories to ~/.clkx/agents/
        additionalAgentDirs.forEach { extraDir ->
            if (extraDir.exists()) {
                extraDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.name.endsWith(".md") && it.name != GENERATED_AGENT_FILE }
                    .forEach { src -> src.copyTo(File(targetDir, src.name), overwrite = true) }
            }
        }
    }

    @Suppress("LongMethod")
    private fun buildAgentDefinition(agent: Agent): String =
        buildString {
            when (agent) {
                Agent.CLAUDE -> {
                    appendLine("---")
                    appendLine("name: opsx")
                    appendLine("description: |")
                    appendLine("  Manages spec-driven development workflows. Use for proposing,")
                    appendLine("  applying, verifying, and archiving changes via opsx.")
                    appendLine("model: inherit")
                    appendLine("color: green")
                    appendLine("---")
                    appendLine()
                }
                Agent.COPILOT -> {
                    appendLine("---")
                    appendLine("name: opsx")
                    appendLine("description: |")
                    appendLine("  Manages spec-driven development workflows. Use for proposing,")
                    appendLine("  applying, verifying, and archiving changes via opsx.")
                    appendLine("---")
                    appendLine()
                }
                else -> { /* no header for codex/opencode */ }
            }
            appendLine("You are the opsx workflow agent. You manage structured changes")
            appendLine("in this workspace using the opsx, srcx, and wrkx Gradle plugins.")
            appendLine()
            appendLine("## How This Workspace Works")
            appendLine()
            appendLine("This workspace has three integrated systems:")
            appendLine()
            appendLine("1. **opsx** orchestrates changes through a strict lifecycle.")
            appendLine("   Every code change is a tracked proposal with design, tasks, and verification.")
            appendLine("2. **srcx** extracts codebase context into `.srcx/context.md`.")
            appendLine("   This is your primary source of truth about the code structure.")
            appendLine("3. **wrkx** manages multiple repos as included Gradle builds.")
            appendLine("   Each build has its own source code and `.srcx/context.md`.")
            appendLine()
            appendLine("## Before You Do Anything")
            appendLine()
            appendLine("1. Run `/opsx-status` to see active changes and their state")
            appendLine("2. Read `.srcx/context.md` for the codebase overview")
            appendLine("3. Read the split context files for details:")
            appendLine("   - `.srcx/hub-classes.md` — Most-depended-on classes (high blast radius)")
            appendLine("   - `.srcx/entry-points.md` — App, test, and mock entry points")
            appendLine("   - `.srcx/interfaces.md` — API boundaries and contracts")
            appendLine("   - `.srcx/anti-patterns.md` — Detected code issues")
            appendLine("   - `.srcx/cross-build.md` — How included builds depend on each other")
            appendLine()
            appendLine("## Change Lifecycle")
            appendLine()
            appendLine("```")
            appendLine("draft -> active -> in-progress -> completed -> verified -> archived")
            appendLine("```")
            appendLine()
            append("- **Propose**: ")
            appendLine("`./gradlew -q opsx-propose -P${Opsx.PROP_PROMPT}=\"...\"`")
            appendLine("  Creates proposal.md, design.md, tasks.md")
            append("- **Apply**: ")
            appendLine("`./gradlew -q opsx-apply -P${Opsx.PROP_CHANGE}=\"name\"`")
            appendLine("  Executes tasks or dispatches agent")
            append("- **Verify**: ")
            appendLine("`./gradlew -q opsx-verify -P${Opsx.PROP_CHANGE}=\"name\"`")
            appendLine("  Runs verification command or agent review")
            append("- **Archive**: ")
            appendLine("`./gradlew -q opsx-archive -P${Opsx.PROP_CHANGE}=\"name\"`")
            appendLine("  Closes the change (must be verified)")
            appendLine()
            appendLine("Additional: `/opsx-continue` (resume), `/opsx-explore` (Q&A),")
            appendLine("`/opsx-feedback` (refine), `/opsx-ff` (fast-forward),")
            appendLine("`/opsx-onboard` (onboard), `/opsx-status` (show changes).")
            appendLine()
            appendLine("## Change Directory Structure")
            appendLine()
            appendLine("Each change lives at `opsx/changes/<name>/`:")
            appendLine()
            appendLine("- `.opsx.yaml` — Status and metadata")
            appendLine("- `proposal.md` — Problem statement, scope, constraints")
            appendLine("- `design.md` — Technical approach, files to change, acceptance criteria")
            appendLine("- `tasks.md` — Structured tasks: `- [ ] {10-char-id} | Task name`")
            appendLine()
            appendLine("## Strict Rules")
            appendLine()
            appendLine("- MUST use opsx Gradle tasks for all workflow operations")
            appendLine("- MUST read `.srcx/context.md` before making changes")
            appendLine("- MUST run `/opsx-status` before starting any work")
            appendLine("- MUST NOT use grep/sed/awk for refactoring — use srcx tasks")
            appendLine("- MUST NOT manually create or edit files in `opsx/changes/`")
            appendLine("- MUST NOT manually edit files across included builds")
            appendLine("- MUST NOT bypass the lifecycle — propose first, then apply")
        }

    private fun removeMarkersIfExists(file: File) {
        if (!file.exists()) return
        val marker = "<!-- OPSX:AUTO -->"
        val endMarker = "<!-- /OPSX:AUTO -->"
        val content = file.readText()
        if (!content.contains(marker)) return
        val before = content.substringBefore(marker).trimEnd()
        val after = content.substringAfter(endMarker, "").trimStart()
        val result = "$before\n$after".trim()
        if (result.isEmpty()) file.delete() else file.writeText("$result\n")
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
            appendLine("This workspace uses three integrated Gradle plugins:")
            appendLine()
            appendLine("- **opsx** — Spec-driven development workflow.")
            appendLine("  Changes go through a lifecycle: propose -> apply -> verify -> archive.")
            appendLine("  Each change has a proposal, design, and structured task list.")
            appendLine("  AI agents execute tasks. You MUST use opsx commands for all workflow operations.")
            appendLine()
            appendLine("- **srcx** — Source symbol extraction for LLM context.")
            appendLine("  Generates `.srcx/context.md` with the full codebase map.")
            appendLine("  Run `/srcx-context` to regenerate it after code changes.")
            appendLine()
            appendLine("- **wrkx** — Multi-repo workspace management.")
            appendLine("  Manages cloning, syncing, and branching across included builds.")
            appendLine("  Run `/wrkx` to see all available workspace tasks.")
            appendLine()
            appendLine("## Understanding the Codebase")
            appendLine()
            appendLine("Before making any changes, READ `.srcx/context.md`. It contains:")
            appendLine()
            appendLine("- **Hub Classes** (`.srcx/hub-classes.md`) — The most-depended-on")
            appendLine("  classes in the codebase. Changes here have the highest blast radius.")
            appendLine("- **Entry Points** (`.srcx/entry-points.md`) — App entry points,")
            appendLine("  test classes, and mock/fake implementations.")
            appendLine("- **Interfaces** (`.srcx/interfaces.md`) — API boundaries between")
            appendLine("  packages. These define the contracts other code depends on.")
            appendLine("- **Anti-Patterns** (`.srcx/anti-patterns.md`) — Code issues detected")
            appendLine("  by static analysis: forbidden packages, naming violations, etc.")
            appendLine("- **Cross-Build** (`.srcx/cross-build.md`) — Dependencies between")
            appendLine("  included builds. Shows how wrkx, srcx, opsx, etc. relate.")
            appendLine()
            appendLine("Each included build also has its own `.srcx/context.md` with the")
            appendLine("same structure for that build's source code.")
            appendLine()
            appendLine("## Change Lifecycle")
            appendLine()
            appendLine("Every change goes through these states:")
            appendLine()
            appendLine("```")
            appendLine("draft -> active -> in-progress -> completed -> verified -> archived")
            appendLine("```")
            appendLine()
            appendLine("- **draft** — Proposal created, not yet ready for work")
            appendLine("- **active** — Ready to be applied")
            appendLine("- **in-progress** — Agent is implementing the change")
            appendLine("- **completed** — Implementation done, needs verification")
            appendLine("- **verified** — Passed verification, ready to archive")
            appendLine("- **archived** — Done and closed")
            appendLine()
            appendLine("Changes live in `opsx/changes/<name>/` with:")
            appendLine()
            appendLine("- `.opsx.yaml` — Status, name, dependencies on other changes")
            appendLine("- `proposal.md` — What and why: problem, scope, constraints")
            appendLine("- `design.md` — How: approach, files to change, acceptance criteria")
            appendLine("- `tasks.md` — Atomic task checklist with IDs and dependencies")
            appendLine()
            // Command tables
            tasks.groupBy { it.group }.forEach { (group, groupTasks) ->
                appendLine("## $group")
                appendLine()
                appendLine("| Skill | Gradle Task | Description |")
                appendLine("|-------|------------|-------------|")
                groupTasks.forEach { task ->
                    val usage = TASK_USAGE[task.name]
                    val example =
                        if (usage != null) "`${usage.example}`" else "`./gradlew -q ${task.name}`"
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
        val TRACKED_GROUPS: Set<String> =
            setOf("opsx", "srcx", "wrkx") + Agent.allIds

        const val SKILLS_DIR = ".clkx/skills"
        const val AGENTS_DIR = ".clkx/agents"
        const val SHARED_SKILLS_DIR = ".agents/skills"
        const val SKILL_FILE = "SKILL.md"
        private const val GENERATED_AGENT_FILE = "opsx.md"
        private val PREFERRED_PRIMARY_ORDER = listOf(Agent.CLAUDE, Agent.COPILOT)

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

        @Suppress("UnusedParameter")
        fun generatedDirs(agent: Agent? = null): List<File> {
            val home = File(System.getProperty("user.home"))
            return listOf(File(home, SKILLS_DIR), File(home, AGENTS_DIR))
        }

        fun instructionFiles(
            rootDir: File,
            agent: Agent? = null,
        ): List<File> {
            if (agent != null) {
                val files = mutableListOf(File(rootDir, "AGENTS.md"))
                agent.instructionFile?.let { files.add(File(rootDir, it)) }
                return files.distinct()
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

        internal val SKILL_TRIGGERS =
            mapOf(
                "opsx-propose" to
                    "Use when the user wants to propose, start, or plan a new code change.",
                "opsx-apply" to
                    "Use when the user wants to implement or apply a proposed change.",
                "opsx-verify" to
                    "Use when the user wants to verify or review a completed change.",
                "opsx-archive" to
                    "Use when the user wants to archive or close a finished change.",
                "opsx-status" to
                    "Use when the user wants to see the status of all changes.",
                "opsx-continue" to
                    "Use when the user wants to resume or continue work on an in-progress change.",
                "opsx-explore" to
                    "Use when the user wants to ask questions about the codebase.",
                "opsx-feedback" to
                    "Use when the user wants to provide feedback or refinements on a change.",
                "opsx-onboard" to
                    "Use when a new contributor needs a guided tour of the project.",
                "opsx-ff" to
                    "Use when a change needs to be updated after other changes landed.",
                "opsx-bulk-archive" to
                    "Use when the user wants to archive all completed changes at once.",
                "srcx-context" to
                    "Use when the user needs to regenerate or refresh codebase context.",
                "srcx-clean" to
                    "Use when the user wants to delete generated srcx output files.",
                "wrkx" to
                    "Use when the user wants to see available workspace tasks.",
                "wrkx-clone" to
                    "Use when the user wants to clone all repos in the workspace.",
                "wrkx-pull" to
                    "Use when the user wants to pull latest changes for all repos.",
                "wrkx-checkout" to
                    "Use when the user wants to checkout branches across all repos.",
                "wrkx-status" to
                    "Use when the user wants a status report of all workspace repos.",
                "wrkx-prune" to
                    "Use when the user wants to remove repo directories not in wrkx.json.",
            )
    }
}
