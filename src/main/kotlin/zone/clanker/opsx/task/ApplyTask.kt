package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.Change
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.TaskExecutor
import zone.clanker.opsx.workflow.TaskParser

/** Applies a change by executing atomic tasks via TaskExecutor. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ApplyTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val changeInput =
            project.findProperty(Opsx.PROP_CHANGE)?.toString()
                ?: error("Required: -P${Opsx.PROP_CHANGE}=\"change-name-or-task-id\"")
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val reader = ChangeReader(project.rootDir, extension)
        val writer = ChangeWriter(project.rootDir, extension)

        val resolved = resolveTarget(reader, changeInput)
        val change = resolved.first
        val taskId = resolved.second

        val missing = validateForApply(change)
        require(missing.isEmpty()) {
            "Cannot apply '${change.name}': missing ${missing.joinToString(", ")}"
        }

        writer.updateStatus(change.dir, ChangeStatus.IN_PROGRESS)

        val executor =
            TaskExecutor(
                changeDir = change.dir,
                agent = agent,
                model = model,
                workDir = project.rootDir,
            )

        val suffix = if (taskId != null) " (task $taskId)" else ""
        logger.quiet("opsx-apply: executing tasks for '${change.name}'$suffix...")
        executor.execute(taskId)
    }

    internal fun resolveTarget(
        reader: ChangeReader,
        input: String,
    ): Pair<Change, String?> {
        val byName = reader.readAll().find { it.name == input }
        if (byName != null) return byName to null

        val isTaskId = input.matches(Regex("[a-zA-Z0-9]{10}"))
        if (isTaskId) {
            val match = findChangeByTaskId(reader, input)
            if (match != null) return match to input
        }

        error("Change or task not found: $input")
    }

    private fun findChangeByTaskId(
        reader: ChangeReader,
        taskId: String,
    ): Change? =
        reader
            .readAll()
            .filter { it.tasksFile.exists() }
            .find { TaskParser.parse(it.tasksFile).any { t -> t.id == taskId } }

    internal fun validateForApply(change: Change): List<String> =
        buildList {
            if (!change.proposalFile.exists()) add("proposal.md")
            if (!change.designFile.exists()) add("design.md")
            val status = ChangeStatus.from(change.status)
            if (!status.canTransitionTo(ChangeStatus.IN_PROGRESS)) {
                add("cannot transition from '${status.value}' to 'in-progress'")
            }
        }
}
