package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File

/**
 * Explore the codebase by asking an agent a question with full srcx context.
 *
 * Usage: `./gradlew opsx-explore -Pprompt="How does auth work?"`
 */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ExploreTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val prompt: Property<String>

    @get:Internal
    abstract val agent: Property<String>

    @get:Internal
    abstract val model: Property<String>

    @TaskAction
    fun run() {
        val promptVal =
            prompt.orNull
                ?: error("Required: -P${zone.clanker.opsx.Opsx.PROP_PROMPT}=\"your question about the codebase\"")
        val agentVal = agent.get()
        val modelVal = model.getOrElse("")
        val root = rootDir.get()

        val promptBuilder = PromptBuilder(root)
        val context = promptBuilder.srcxContext()
        val fullPrompt = buildPrompt(context, promptVal)

        logger.quiet("opsx-explore: asking $agentVal...")
        val result = AgentDispatcher.dispatch(agentVal, fullPrompt, root, modelVal)
        if (result.exitCode != 0) {
            logger.warn("opsx-explore: agent exited with code ${result.exitCode}")
        }
    }

    internal fun buildPrompt(
        context: String,
        question: String,
    ): String =
        buildString {
            if (context.isNotEmpty()) {
                appendLine("# Codebase Context")
                appendLine()
                appendLine(context)
                appendLine()
                appendLine("---")
                appendLine()
            }
            appendLine("# Question")
            appendLine()
            appendLine(question)
            appendLine()
            appendLine("Answer based on the codebase context above.")
            appendLine("Reference specific files, classes, and line numbers when relevant.")
        }
}
