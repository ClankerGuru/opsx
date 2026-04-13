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

/** Archives a completed change. */
@DisableCachingByDefault(because = "Updates change status on disk")
abstract class ArchiveTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val changeName =
            project.findProperty(Opsx.PROP_CHANGE)?.toString()
                ?: error("Required: -P${Opsx.PROP_CHANGE}=\"change-name\"")

        val reader = ChangeReader(project.rootDir, extension)
        val writer = ChangeWriter(project.rootDir, extension)

        val change =
            reader.readAll().find { it.name == changeName }
                ?: error("Change not found: $changeName")

        require(validateForArchive(change)) {
            "Cannot archive '$changeName': status is '${change.status}' " +
                "(must be completed, verified, or archived)"
        }

        writer.updateStatus(change.dir, ChangeStatus.ARCHIVED)
        logger.quiet("opsx-archive: '$changeName' archived")
    }

    internal fun validateForArchive(change: Change): Boolean {
        val status = ChangeStatus.from(change.status)
        return status in setOf(ChangeStatus.COMPLETED, ChangeStatus.DONE, ChangeStatus.VERIFIED, ChangeStatus.ARCHIVED)
    }
}
