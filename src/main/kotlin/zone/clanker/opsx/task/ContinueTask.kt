package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.PromptBuilder

/** Continues work on an in-progress change, focusing on unchecked tasks. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ContinueTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val changeName =
            project.findProperty(Opsx.PROP_CHANGE)?.toString()
                ?: error("Required: -P${Opsx.PROP_CHANGE}=\"change-name\"")
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val reader = ChangeReader(project.rootDir, extension)
        val promptBuilder = PromptBuilder(project.rootDir)

        val change =
            reader.readAll().find { it.name == changeName }
                ?: error("Change not found: $changeName")

        val tasksContent = if (change.tasksFile.exists()) change.tasksFile.readText() else ""
        val progress = detectProgress(tasksContent)

        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val fullPrompt = buildContinuePrompt(context, changeCtx, progress)

        logger.quiet("opsx-continue: $progress — asking $agent to continue '$changeName'...")
        val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
        if (result.exitCode != 0) {
            logger.warn("opsx-continue: agent exited with code ${result.exitCode}")
        }
    }

    internal fun detectProgress(tasksContent: String): String {
        val done = Regex("- \\[x]", RegexOption.IGNORE_CASE).findAll(tasksContent).count()
        val pending = Regex("- \\[ ]").findAll(tasksContent).count()
        val total = done + pending
        return if (total == 0) "no tasks found" else "$done/$total tasks complete"
    }

    internal fun buildContinuePrompt(
        context: String,
        changeCtx: String,
        progress: String,
    ): String {
        val promptBuilder = PromptBuilder(project.rootDir)
        return promptBuilder.build(
            "Codebase Context" to context,
            "Change" to changeCtx,
            "Instructions" to
                buildString {
                    appendLine("Continue implementing this change where it was left off.")
                    appendLine("Current progress: $progress")
                    appendLine()
                    appendLine("Focus on the unchecked tasks (- [ ]) in tasks.md.")
                    appendLine("Mark each task as done (- [x]) when you complete it.")
                    appendLine("Follow the design document faithfully.")
                },
        )
    }
}
