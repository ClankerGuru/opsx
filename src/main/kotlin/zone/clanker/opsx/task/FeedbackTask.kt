package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File
import java.time.LocalDate

/** Provides feedback on a change and asks an agent to incorporate it. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class FeedbackTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @get:Internal
    abstract val changeName: Property<String>

    @get:Internal
    abstract val prompt: Property<String>

    @get:Internal
    abstract val agent: Property<String>

    @get:Internal
    abstract val model: Property<String>

    @TaskAction
    fun run() {
        val name =
            changeName.orNull
                ?: error("Required: -P${zone.clanker.opsx.Opsx.PROP_CHANGE}=\"change-name\"")
        val feedback =
            prompt.orNull
                ?: error("Required: -P${zone.clanker.opsx.Opsx.PROP_PROMPT}=\"your feedback\"")
        val agentVal = agent.get()
        val modelVal = model.getOrElse("")
        val root = rootDir.get()
        val cfg = config.get()

        val reader = ChangeReader(root, cfg)
        val writer = ChangeWriter(root, cfg)
        val promptBuilder = PromptBuilder(root)

        val change =
            reader.readAll().find { it.name == name }
                ?: error("Change not found: $name")

        val formattedFeedback = formatFeedbackEntry(feedback)
        writer.appendFeedback(change.dir, formattedFeedback)

        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val fullPrompt = buildFeedbackPrompt(root, context, changeCtx, feedback)

        logger.quiet("opsx-feedback: asking $agentVal to incorporate feedback on '$name'...")
        val result = AgentDispatcher.dispatch(agentVal, fullPrompt, root, modelVal)
        if (result.exitCode != 0) {
            logger.warn("opsx-feedback: agent exited with code ${result.exitCode}")
        }
    }

    internal fun formatFeedbackEntry(feedback: String): String =
        buildString {
            appendLine("### ${LocalDate.now()}")
            appendLine()
            appendLine(feedback)
        }

    internal fun buildFeedbackPrompt(
        root: File,
        context: String,
        changeCtx: String,
        feedback: String,
    ): String {
        val promptBuilder = PromptBuilder(root)
        return promptBuilder.build(
            "Codebase Context" to context,
            "Change" to changeCtx,
            "Instructions" to
                buildString {
                    appendLine("The user has provided feedback on this change:")
                    appendLine()
                    appendLine(feedback)
                    appendLine()
                    appendLine("Incorporate this feedback into the design and implementation.")
                    appendLine("Update design.md and tasks.md as needed.")
                },
        )
    }
}
