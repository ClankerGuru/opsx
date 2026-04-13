package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.TaskExecutor

/** Continues work on an in-progress change, resuming from the first unfinished task. */
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
            project.findProperty(Opsx.PROP_AGENT)?.toString()?.takeUnless { it.isBlank() }
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val reader = ChangeReader(project.rootDir, extension)

        val change =
            reader.readAll().find { it.name == changeName }
                ?: error("Change not found: $changeName")

        val tasksContent = if (change.tasksFile.exists()) change.tasksFile.readText() else ""
        val progress = detectProgress(tasksContent)

        logger.quiet("opsx-continue: $progress — resuming '$changeName'...")

        val executor =
            TaskExecutor(
                changeDir = change.dir,
                agent = agent,
                model = model,
                workDir = project.rootDir,
            )

        executor.execute(null)
    }

    internal fun detectProgress(tasksContent: String): String {
        val done = Regex("""- \[[xX]]""").findAll(tasksContent).count()
        val total = Regex("""- \[[ xX>!~]]""").findAll(tasksContent).count()
        return if (total == 0) "no tasks found" else "$done/$total tasks complete"
    }
}
