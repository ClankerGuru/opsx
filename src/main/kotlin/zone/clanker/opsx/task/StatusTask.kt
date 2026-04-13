package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.model.TaskStatus
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.TaskParser

/** Shows status of all changes with their files, dependencies, and progress. */
@DisableCachingByDefault(because = "Displays change status to console")
abstract class StatusTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val reader = ChangeReader(project.rootDir, extension)
        val changes = reader.readAll()
        if (changes.isEmpty()) {
            logger.quiet("opsx: no changes")
            return
        }

        val byStatus = changes.groupBy { it.status }
        val active = countByStatuses(byStatus, "active", "in-progress")
        val done = countByStatuses(byStatus, "done", "completed", "verified")
        logger.quiet("opsx: ${changes.size} changes ($active active, $done done)")
        logger.quiet("")

        // Show in lifecycle order
        STATUS_ORDER.forEach { status ->
            val group = byStatus[status] ?: return@forEach
            logger.quiet("  [$status] (${group.size})")
            group.forEach { change ->
                val deps =
                    if (change.depends.isEmpty()) {
                        ""
                    } else {
                        " <- ${change.depends.joinToString()}"
                    }
                val files = describeFiles(change)
                val taskProgress = describeTaskProgress(change)
                logger.quiet("    ${change.name}$deps  $files$taskProgress")
            }
            logger.quiet("")
        }

        // Show any status not in the known order
        byStatus.keys
            .filter { it !in STATUS_ORDER }
            .forEach { status ->
                val group = byStatus[status] ?: return@forEach
                logger.quiet("  [$status] (${group.size})")
                group.forEach { change ->
                    logger.quiet("    ${change.name}")
                }
                logger.quiet("")
            }
    }

    private fun describeFiles(change: zone.clanker.opsx.model.Change): String {
        val files =
            listOfNotNull(
                "proposal".takeIf { change.proposalFile.exists() },
                "design".takeIf { change.designFile.exists() },
                "tasks".takeIf { change.tasksFile.exists() },
                "feedback".takeIf { change.feedbackFile.exists() },
            )
        return if (files.isEmpty()) "(no artifacts)" else "(${files.joinToString(", ")})"
    }

    private fun describeTaskProgress(change: zone.clanker.opsx.model.Change): String {
        if (!change.tasksFile.exists()) return ""
        val tasks = TaskParser.parse(change.tasksFile)
        if (tasks.isEmpty()) return ""
        val done = tasks.count { it.status == TaskStatus.DONE }
        val inProgress = tasks.count { it.status == TaskStatus.IN_PROGRESS }
        val blocked = tasks.count { it.status == TaskStatus.BLOCKED }
        val parts = mutableListOf("$done/${tasks.size} tasks")
        if (inProgress > 0) parts.add("$inProgress running")
        if (blocked > 0) parts.add("$blocked blocked")
        return " [${parts.joinToString(", ")}]"
    }

    private fun countByStatuses(
        byStatus: Map<String, List<*>>,
        vararg statuses: String,
    ): Int = statuses.sumOf { byStatus[it]?.size ?: 0 }

    companion object {
        private val STATUS_ORDER =
            listOf(
                ChangeStatus.DRAFT.value,
                ChangeStatus.PENDING.value,
                ChangeStatus.ACTIVE.value,
                ChangeStatus.IN_PROGRESS.value,
                ChangeStatus.COMPLETED.value,
                ChangeStatus.DONE.value,
                ChangeStatus.VERIFIED.value,
                ChangeStatus.ARCHIVED.value,
            )
    }
}
