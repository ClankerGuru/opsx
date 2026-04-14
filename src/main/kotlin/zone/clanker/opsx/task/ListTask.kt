package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.ChangeReader
import java.io.File

/** Lists all changes and their current status. */
@DisableCachingByDefault(because = "Displays change list to console")
abstract class ListTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @TaskAction
    fun run() {
        val cfg = config.get()
        val reader = ChangeReader(rootDir.get(), cfg)
        val changes = reader.readAll()
        if (changes.isEmpty()) {
            logger.quiet("No changes found in ${cfg.outputDir}/${cfg.changesDir}")
            return
        }
        logger.quiet("Changes (${changes.size}):")
        changes.forEach { change ->
            logger.quiet("  ${change.name} [${change.status}]")
        }
    }
}
