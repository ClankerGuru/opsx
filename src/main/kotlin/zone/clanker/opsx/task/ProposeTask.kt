package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File

/** Proposes a new change from a spec file or freeform prompt. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ProposeTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val spec = project.findProperty(Opsx.PROP_SPEC)?.toString()
        val prompt = project.findProperty(Opsx.PROP_PROMPT)?.toString()
        val nameOverride = project.findProperty(Opsx.PROP_CHANGE_NAME)?.toString()
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        require(spec != null || prompt != null) {
            "Required: -P${Opsx.PROP_SPEC}=\"spec-name\" or -P${Opsx.PROP_PROMPT}=\"describe the change\""
        }
        require(spec == null || prompt == null) {
            "Use either -P${Opsx.PROP_SPEC} or -P${Opsx.PROP_PROMPT}, not both"
        }

        val changeName = nameOverride ?: resolveChangeName(spec, prompt)
        val promptBuilder = PromptBuilder(project.rootDir)
        val writer = ChangeWriter(project.rootDir, extension)

        val changeDir = writer.createChangeDir(changeName)
        writer.writeConfig(changeDir, changeName, ChangeStatus.DRAFT)

        if (spec != null) {
            val specContent = promptBuilder.specContent(extension, spec)
            require(specContent.isNotBlank()) { "Spec not found: $spec" }
            writer.writeProposal(changeDir, specContent)
            writer.writeDesignSkeleton(changeDir, changeName)
            writer.writeTasksSkeleton(changeDir, changeName)
            logger.quiet("opsx-propose: created change '$changeName' from spec '$spec'")
        } else {
            val context = promptBuilder.srcxContext()
            val projectDesc = promptBuilder.projectDescription(extension)
            val fullPrompt = buildProposalPrompt(context, projectDesc, prompt, changeDir)

            writer.writeProposal(changeDir, "# Proposal: $changeName\n\n$prompt\n")
            writer.writeDesignSkeleton(changeDir, changeName)
            writer.writeTasksSkeleton(changeDir, changeName)

            logger.quiet("opsx-propose: asking $agent to draft proposal for '$changeName'...")
            val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
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
        context: String,
        projectDesc: String,
        prompt: String?,
        changeDir: File,
    ): String {
        val promptBuilder = PromptBuilder(project.rootDir)
        val relPath = changeDir.relativeTo(project.rootDir).path
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
                            appendLine("3. **tasks.md** — Checklist of implementation steps (- [ ] format).")
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
