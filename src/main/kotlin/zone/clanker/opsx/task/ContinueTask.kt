package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.TaskExecutor
import java.io.File

/** Continues work on an in-progress change, resuming from the first unfinished task. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class ContinueTask : DefaultTask() {
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

        val change =
            reader.readAll().find { it.name == name }
                ?: error("Change not found: $name")

        val tasksContent = if (change.tasksFile.exists()) change.tasksFile.readText() else ""
        val progress = detectProgress(tasksContent)

        logger.quiet("opsx-continue: $progress — resuming '$name'...")

        val executor =
            TaskExecutor(
                changeDir = change.dir,
                agent = Agent.fromId(agentVal),
                model = modelVal,
                workDir = root,
            )

        executor.execute(null)
    }

    internal fun detectProgress(tasksContent: String): String {
        val done = Regex("""- \[[xX]]""").findAll(tasksContent).count()
        val total = Regex("""- \[[ xX>!~]]""").findAll(tasksContent).count()
        return if (total == 0) "no tasks found" else "$done/$total tasks complete"
    }
}
