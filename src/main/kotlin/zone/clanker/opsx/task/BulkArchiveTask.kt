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

/** Archives all completed or verified changes in bulk. */
@DisableCachingByDefault(because = "Updates change status on disk")
abstract class BulkArchiveTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val reader = ChangeReader(project.rootDir, extension)
        val writer = ChangeWriter(project.rootDir, extension)

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
            val status = ChangeStatus.from(change.status)
            status in setOf(ChangeStatus.COMPLETED, ChangeStatus.DONE, ChangeStatus.VERIFIED)
        }
}
