package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File

/** Proposes a new change from a spec file or freeform prompt. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ProposeTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @get:Internal
    abstract val spec: Property<String>

    @get:Internal
    abstract val prompt: Property<String>

    @get:Internal
    abstract val changeNameOverride: Property<String>

    @get:Internal
    abstract val agent: Property<String>

    @get:Internal
    abstract val model: Property<String>

    @TaskAction
    fun run() {
        val specVal = spec.orNull
        val promptVal = prompt.orNull
        val nameOverride = changeNameOverride.orNull
        val agentVal = agent.get()
        val modelVal = model.getOrElse("")

        require(specVal != null || promptVal != null) {
            "Required: -P${zone.clanker.opsx.Opsx.PROP_SPEC}=\"spec-name\" or " +
                "-P${zone.clanker.opsx.Opsx.PROP_PROMPT}=\"describe the change\""
        }
        require(specVal == null || promptVal == null) {
            "Use either -P${zone.clanker.opsx.Opsx.PROP_SPEC} or -P${zone.clanker.opsx.Opsx.PROP_PROMPT}, not both"
        }

        val root = rootDir.get()
        val cfg = config.get()
        val changeName = nameOverride ?: resolveChangeName(specVal, promptVal)
        val promptBuilder = PromptBuilder(root)
        val writer = ChangeWriter(root, cfg)

        if (specVal != null) {
            require(promptBuilder.specContent(cfg, specVal).isNotBlank()) {
                "Spec not found: $specVal"
            }
        }

        val changeDir = writer.createChangeDir(changeName)
        writer.writeConfig(changeDir, changeName, ChangeStatus.DRAFT)

        if (specVal != null) {
            val specContent = promptBuilder.specContent(cfg, specVal)
            writer.writeProposal(changeDir, specContent)
            writer.writeDesignSkeleton(changeDir, changeName)
            writer.writeTasksSkeleton(changeDir, changeName)
            logger.quiet("opsx-propose: created change '$changeName' from spec '$specVal'")
        } else {
            val context = promptBuilder.srcxContext()
            val projectDesc = promptBuilder.projectDescription(cfg)
            val fullPrompt = buildProposalPrompt(root, context, projectDesc, promptVal, changeDir)

            writer.writeProposal(changeDir, "# Proposal: $changeName\n\n$promptVal\n")
            writer.writeDesignSkeleton(changeDir, changeName)
            writer.writeTasksSkeleton(changeDir, changeName)

            logger.quiet("opsx-propose: asking $agentVal to draft proposal for '$changeName'...")
            val result = AgentDispatcher.dispatch(Agent.fromId(agentVal), fullPrompt, root, modelVal)
            if (result.exitCode != 0) {
                logger.warn("opsx-propose: agent exited with code ${result.exitCode}")
            }
        }
    }

    internal fun resolveChangeName(
        spec: String?,
        prompt: String?,
    ): String {
        if (spec != null) return spec
        val raw = (prompt ?: "").trim()
        val words =
            raw
                .lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .take(SLUG_WORD_COUNT)
        return if (words.isEmpty()) "untitled-change" else words.joinToString("-")
    }

    internal fun buildProposalPrompt(
        root: File,
        context: String,
        projectDesc: String,
        prompt: String?,
        changeDir: File,
    ): String {
        val promptBuilder = PromptBuilder(root)
        val relPath = changeDir.relativeTo(root).path
        val sections =
            mutableListOf<Pair<String, String>>().apply {
                add("Codebase Context" to context)
                add("Project" to projectDesc)
                if (!prompt.isNullOrBlank()) add("User Request" to prompt)
                add(
                    "Instructions" to
                        buildString {
                            appendLine("You are proposing a change to this codebase.")
                            appendLine("Write artifacts to: `$relPath/`")
                            appendLine()
                            appendLine("1. **proposal.md** — What and why. Scope, constraints, affected systems.")
                            appendLine("2. **design.md** — How. Approach, files to change, testing strategy.")
                            appendLine("3. **tasks.md** — Atomic task checklist in this exact format:")
                            appendLine()
                            appendLine("```")
                            appendLine("- [ ] a1b2c3d4e5 | Task name here")
                            appendLine("    Description of what to do.")
                            appendLine("    Can be multiple lines with 4-space indent.")
                            appendLine("  depends: none")
                            appendLine()
                            appendLine("- [ ] f6g7h8i9j0 | Another task")
                            appendLine("    Description of this task.")
                            appendLine("  depends:")
                            appendLine("    - a1b2c3d4e5")
                            appendLine("```")
                            appendLine()
                            appendLine("Each task MUST have a unique 10-character alphanumeric ID after the checkbox.")
                            appendLine("Use `depends: none` or list dependency IDs. Pair tests with implementations.")
                            appendLine()
                            appendLine("Be specific about file paths and function names.")
                            appendLine("Keep the design minimal — prefer the simplest approach.")
                        },
                )
            }
        return promptBuilder.build(*sections.toTypedArray())
    }

    companion object {
        internal const val SLUG_WORD_COUNT = 4
    }
}
