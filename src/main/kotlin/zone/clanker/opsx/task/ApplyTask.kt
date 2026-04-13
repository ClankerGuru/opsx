package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File

/** Applies a change proposal to the codebase via an AI agent. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ApplyTask : DefaultTask() {
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
        val writer = ChangeWriter(project.rootDir, extension)
        val promptBuilder = PromptBuilder(project.rootDir)

        val change =
            reader.readAll().find { it.name == changeName }
                ?: error("Change not found: $changeName")

        val missing = validateForApply(change)
        require(missing.isEmpty()) {
            "Cannot apply '$changeName': missing ${missing.joinToString(", ")}"
        }

        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val fullPrompt = buildApplyPrompt(context, changeCtx, change.dir)

        logger.quiet("opsx-apply: asking $agent to implement '$changeName'...")
        val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
        if (result.exitCode == 0) {
            writer.updateStatus(change.dir, ChangeStatus.IN_PROGRESS)
        } else {
            logger.warn("opsx-apply: agent exited with code ${result.exitCode}")
        }
    }

    internal fun validateForApply(change: Change): List<String> =
        buildList {
            if (!change.proposalFile.exists()) add("proposal.md")
            if (!change.designFile.exists()) add("design.md")
            val status = ChangeStatus.from(change.status)
            if (!status.canTransitionTo(ChangeStatus.IN_PROGRESS)) {
                add("cannot transition from '${status.value}' to 'in-progress'")
            }
        }

    internal fun buildApplyPrompt(
        context: String,
        changeCtx: String,
        changeDir: File,
    ): String {
        val promptBuilder = PromptBuilder(project.rootDir)
        val relPath = changeDir.relativeTo(project.rootDir).path
        return promptBuilder.build(
            "Codebase Context" to context,
            "Change" to changeCtx,
            "Instructions" to
                buildString {
                    appendLine("Implement the change described in the design document above.")
                    appendLine("Change artifacts are at: `$relPath/`")
                    appendLine()
                    appendLine("Follow the design faithfully — do not deviate from the approach.")
                    appendLine("Update `$relPath/tasks.md` checkboxes as you complete each item:")
                    appendLine("- Change `- [ ]` to `- [x]` for completed tasks")
                    appendLine("- Leave `- [ ]` for tasks not yet done")
                    appendLine("Write tests for all new functionality.")
                },
        )
    }
}
