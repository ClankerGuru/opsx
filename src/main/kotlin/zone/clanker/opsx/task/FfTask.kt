package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.PromptBuilder

/** Fast-forwards a change to match the current codebase state. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class FfTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val changeName =
            project.findProperty(Opsx.PROP_CHANGE)?.toString()
                ?: error("Required: -P${Opsx.PROP_CHANGE}=\"change-name\"")
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()?.takeUnless { it.isBlank() }
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val reader = ChangeReader(project.rootDir, extension)
        val promptBuilder = PromptBuilder(project.rootDir)

        val change =
            reader.readAll().find { it.name == changeName }
                ?: error("Change not found: $changeName")

        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val relPath = change.dir.relativeTo(project.rootDir).path
        val fullPrompt = buildFfPrompt(context, changeCtx, relPath)

        logger.quiet("opsx-ff: asking $agent to fast-forward '$changeName'...")
        val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
        if (result.exitCode != 0) {
            logger.warn("opsx-ff: agent exited with code ${result.exitCode}")
        }
    }

    internal fun buildFfPrompt(
        context: String,
        changeCtx: String,
        changePath: String,
    ): String {
        val promptBuilder = PromptBuilder(project.rootDir)
        return promptBuilder.build(
            "Codebase Context" to context,
            "Change" to changeCtx,
            "Instructions" to
                buildString {
                    appendLine("Fast-forward this change to match the current codebase state.")
                    appendLine("Change artifacts are at: `$changePath/`")
                    appendLine("The codebase may have changed since this proposal was written.")
                    appendLine()
                    appendLine("Update `$changePath/proposal.md` and `$changePath/design.md` to reflect:")
                    appendLine("- Any files that have moved, been renamed, or deleted")
                    appendLine("- New APIs or patterns that should be used instead")
                    appendLine("- Dependencies that have changed")
                    appendLine()
                    appendLine("Note what changed and why in each updated file.")
                },
        )
    }
}
