package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import java.io.File

/** Archives a verified change. Requires that the verify command has passed. */
@DisableCachingByDefault(because = "Updates change status on disk")
abstract class ArchiveTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @get:Internal
    abstract val changeName: Property<String>

    @TaskAction
    fun run() {
        val name =
            changeName.orNull
                ?: error("Required: -P${zone.clanker.opsx.Opsx.PROP_CHANGE}=\"change-name\"")

        val cfg = config.get()
        val reader = ChangeReader(rootDir.get(), cfg)
        val writer = ChangeWriter(rootDir.get(), cfg)

        val change =
            reader.readAll().find { it.name == name }
                ?: error("Change not found: $name")

        val status = ChangeStatus.from(change.status)

        val changeConfig = ChangeConfig.parse(File(change.dir, ".opsx.yaml"))
        if (changeConfig != null && changeConfig.verify.isNotBlank()) {
            require(status == ChangeStatus.VERIFIED) {
                "Cannot archive '$name': status is '${status.value}', " +
                    "not 'verified'. Run opsx-verify first."
            }
        } else {
            require(status.canTransitionTo(ChangeStatus.ARCHIVED)) {
                "Cannot archive '$name': status '${change.status}' cannot transition to 'archived'"
            }
        }

        writer.updateStatus(change.dir, ChangeStatus.ARCHIVED)
        logger.quiet("opsx-archive: '$name' archived")
    }
}
