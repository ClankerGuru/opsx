package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import java.io.File

/** Archives a verified change. Requires that the verify command has passed. */
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

        val status = ChangeStatus.from(change.status)

        val config = ChangeConfig.parse(File(change.dir, ".opsx.yaml"))
        if (config != null && config.verify.isNotBlank()) {
            require(status == ChangeStatus.VERIFIED) {
                "Cannot archive '$changeName': status is '${status.value}', " +
                    "not 'verified'. Run opsx-verify first."
            }
        } else {
            require(status.canTransitionTo(ChangeStatus.ARCHIVED)) {
                "Cannot archive '$changeName': status '${change.status}' cannot transition to 'archived'"
            }
        }

        writer.updateStatus(change.dir, ChangeStatus.ARCHIVED)
        logger.quiet("opsx-archive: '$changeName' archived")
    }
}
