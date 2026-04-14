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
import java.io.File

/** Archives all completed or verified changes in bulk. */
@DisableCachingByDefault(because = "Updates change status on disk")
abstract class BulkArchiveTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @TaskAction
    fun run() {
        val cfg = config.get()
        val reader = ChangeReader(rootDir.get(), cfg)
        val writer = ChangeWriter(rootDir.get(), cfg)

        val allChanges = reader.readAll()
        val archivable = findArchivable(allChanges)

        if (archivable.isEmpty()) {
            logger.quiet("opsx-bulk-archive: no changes to archive")
            return
        }

        archivable.forEach { change ->
            writer.updateStatus(change.dir, ChangeStatus.ARCHIVED)
            logger.quiet("opsx-bulk-archive: archived '${change.name}'")
        }
        val label = if (archivable.size == 1) "change" else "changes"
        logger.quiet("opsx-bulk-archive: archived ${archivable.size} $label")
    }

    internal fun findArchivable(changes: List<Change>): List<Change> =
        changes.filter { change ->
            ChangeStatus.from(change.status).canTransitionTo(ChangeStatus.ARCHIVED)
        }
}
