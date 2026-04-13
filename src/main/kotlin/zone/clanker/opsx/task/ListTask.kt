package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.workflow.ChangeReader

/** Lists all changes and their current status. */
@DisableCachingByDefault(because = "Displays change list to console")
abstract class ListTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val reader = ChangeReader(project.rootDir, extension)
        val changes = reader.readAll()
        if (changes.isEmpty()) {
            logger.quiet("No changes found in ${extension.outputDir}/${extension.changesDir}")
            return
        }
        logger.quiet("Changes (${changes.size}):")
        changes.forEach { change ->
            logger.quiet("  ${change.name} [${change.status}]")
        }
    }
}
