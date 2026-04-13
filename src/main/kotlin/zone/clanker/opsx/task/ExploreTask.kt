package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.PromptBuilder

/**
 * Explore the codebase by asking an agent a question with full srcx context.
 *
 * Usage: `./gradlew opsx-explore -Pprompt="How does auth work?"`
 */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ExploreTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val prompt =
            project.findProperty(Opsx.PROP_PROMPT)?.toString()
                ?: error("Required: -P${Opsx.PROP_PROMPT}=\"your question about the codebase\"")
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()?.takeUnless { it.isBlank() }
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val promptBuilder = PromptBuilder(project.rootDir)
        val context = promptBuilder.srcxContext()
        val fullPrompt = buildPrompt(context, prompt)

        logger.quiet("opsx-explore: asking $agent...")
        val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
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
