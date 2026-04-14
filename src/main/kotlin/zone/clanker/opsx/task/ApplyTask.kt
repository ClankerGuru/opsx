package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.Change
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.TaskExecutor
import zone.clanker.opsx.workflow.TaskParser
import java.io.File

/** Applies a change by executing atomic tasks via TaskExecutor. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ApplyTask : DefaultTask() {
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
        val changeInput =
            changeName.orNull
                ?: error("Required: -P${zone.clanker.opsx.Opsx.PROP_CHANGE}=\"change-name-or-task-id\"")
        val agentVal = agent.get()
        val modelVal = model.getOrElse("")
        val root = rootDir.get()
        val cfg = config.get()

        val reader = ChangeReader(root, cfg)
        val writer = ChangeWriter(root, cfg)

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
                agent = agentVal,
                model = modelVal,
                workDir = root,
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
            if (!change.tasksFile.exists()) add("tasks.md")
            val status = ChangeStatus.from(change.status)
            if (!status.canTransitionTo(ChangeStatus.IN_PROGRESS)) {
                add("cannot transition from '${status.value}' to 'in-progress'")
            }
        }
}
