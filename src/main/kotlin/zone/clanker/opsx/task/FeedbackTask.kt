package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.PromptBuilder
import java.time.LocalDate

/** Provides feedback on a change and asks an agent to incorporate it. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class FeedbackTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val changeName =
            project.findProperty(Opsx.PROP_CHANGE)?.toString()
                ?: error("Required: -P${Opsx.PROP_CHANGE}=\"change-name\"")
        val feedback =
            project.findProperty(Opsx.PROP_PROMPT)?.toString()
                ?: error("Required: -P${Opsx.PROP_PROMPT}=\"your feedback\"")
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val reader = ChangeReader(project.rootDir, extension)
        val writer = ChangeWriter(project.rootDir, extension)
        val promptBuilder = PromptBuilder(project.rootDir)

        val change =
            reader.readAll().find { it.name == changeName }
                ?: error("Change not found: $changeName")

        val formattedFeedback = formatFeedbackEntry(feedback)
        writer.appendFeedback(change.dir, formattedFeedback)

        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val fullPrompt = buildFeedbackPrompt(context, changeCtx, feedback)

        logger.quiet("opsx-feedback: asking $agent to incorporate feedback on '$changeName'...")
        val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
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
        context: String,
        changeCtx: String,
        feedback: String,
    ): String {
        val promptBuilder = PromptBuilder(project.rootDir)
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
