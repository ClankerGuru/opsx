package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File

/** Fast-forwards a change to match the current codebase state. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class FfTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @get:Internal
    abstract val changeName: Property<String>

    @get:Internal
    abstract val agent: Property<String>

    @get:Internal
    abstract val model: Property<String>

    @TaskAction
    fun run() {
        val name =
            changeName.orNull
                ?: error("Required: -P${zone.clanker.opsx.Opsx.PROP_CHANGE}=\"change-name\"")
        val agentVal = agent.get()
        val modelVal = model.getOrElse("")
        val root = rootDir.get()

        val reader = ChangeReader(root, config.get())
        val promptBuilder = PromptBuilder(root)

        val change =
            reader.readAll().find { it.name == name }
                ?: error("Change not found: $name")

        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val relPath = change.dir.relativeTo(root).path
        val fullPrompt = buildFfPrompt(root, context, changeCtx, relPath)

        logger.quiet("opsx-ff: asking $agentVal to fast-forward '$name'...")
        val result = AgentDispatcher.dispatch(agentVal, fullPrompt, root, modelVal)
        if (result.exitCode != 0) {
            logger.warn("opsx-ff: agent exited with code ${result.exitCode}")
        }
    }

    internal fun buildFfPrompt(
        root: File,
        context: String,
        changeCtx: String,
        changePath: String,
    ): String {
        val promptBuilder = PromptBuilder(root)
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
