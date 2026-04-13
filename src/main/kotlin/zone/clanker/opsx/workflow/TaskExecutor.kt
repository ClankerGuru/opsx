package zone.clanker.opsx.workflow

import org.gradle.api.logging.Logging
import zone.clanker.opsx.model.TaskDefinition
import zone.clanker.opsx.model.TaskStatus
import java.io.File

/**
 * Executes atomic tasks from tasks.md respecting dependency ordering.
 *
 * Each task dispatches an agent with a scoped prompt containing only:
 * - The task description
 * - The change's context.md
 * - The change's design.md
 *
 * Tasks execute in topological order. Tasks with satisfied dependencies
 * run sequentially within each dependency level. Failed tasks block
 * their dependents.
 */
class TaskExecutor(
    private val changeDir: File,
    private val agent: String,
    private val model: String,
    private val workDir: File,
    private val dispatcher: (String, String, File, String, Long) -> AgentDispatcher.Result = { a, p, w, m, t ->
        AgentDispatcher.dispatch(a, p, w, m, t)
    },
) {
    private val logger = Logging.getLogger(TaskExecutor::class.java)
    private val tasksFile = File(changeDir, "tasks.md")

    fun execute(targetTaskId: String?) {
        val allTasks = TaskParser.parse(tasksFile)
        if (allTasks.isEmpty()) {
            logger.quiet("opsx: no tasks found in ${tasksFile.path}")
            return
        }

        val target = resolveExecutionTarget(allTasks, targetTaskId)
        if (target.isEmpty()) {
            logger.quiet("opsx: no pending tasks to execute")
            return
        }

        val ordered = TaskParser.topologicalOrder(target)
        val completed = allTasks.filter { it.status == TaskStatus.DONE }.map { it.id }.toMutableSet()
        val failed = mutableSetOf<String>()

        for (task in ordered) {
            if (task.status == TaskStatus.DONE || task.id in completed) {
                // already done
            } else if (task.dependencies.any { it in failed }) {
                logger.quiet("opsx: skipping ${task.id} | ${task.name} — blocked by failed dependency")
                TaskParser.updateStatus(tasksFile, task.id, TaskStatus.BLOCKED)
                ChangeLogger.append(changeDir, task.id, task.name, TaskStatus.BLOCKED, "blocked by failed dependency")
                failed.add(task.id)
            } else {
                executeTask(task, completed, failed)
            }
        }

        val totalTarget = target.size
        val doneCount = completed.size
        val failedCount = failed.size
        logger.quiet("opsx: execution complete — $doneCount/$totalTarget done, $failedCount failed")
    }

    private fun executeTask(
        task: TaskDefinition,
        completed: MutableSet<String>,
        failed: MutableSet<String>,
    ) {
        logger.quiet("opsx: executing ${task.id} | ${task.name}")
        TaskParser.updateStatus(tasksFile, task.id, TaskStatus.IN_PROGRESS)
        ChangeLogger.append(changeDir, task.id, task.name, TaskStatus.IN_PROGRESS, "started")

        val startTime = System.currentTimeMillis()
        val prompt = buildTaskPrompt(task, changeDir)
        val result = dispatcher(agent, prompt, workDir, model, AgentDispatcher.DEFAULT_TIMEOUT_SECONDS)
        val elapsed = (System.currentTimeMillis() - startTime) / MILLIS_PER_SECOND

        if (result.exitCode == 0) {
            TaskParser.updateStatus(tasksFile, task.id, TaskStatus.DONE)
            ChangeLogger.append(changeDir, task.id, task.name, TaskStatus.DONE, "done in ${elapsed}s")
            completed.add(task.id)
            logger.quiet("opsx: ${task.id} | ${task.name} — done in ${elapsed}s")
        } else {
            TaskParser.updateStatus(tasksFile, task.id, TaskStatus.BLOCKED)
            ChangeLogger.append(
                changeDir,
                task.id,
                task.name,
                TaskStatus.BLOCKED,
                "failed with exit code ${result.exitCode}",
            )
            failed.add(task.id)
            logger.warn("opsx: ${task.id} | ${task.name} — failed with exit code ${result.exitCode}")
        }
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000

        fun resolveExecutionTarget(
            allTasks: List<TaskDefinition>,
            targetTaskId: String?,
        ): List<TaskDefinition> {
            if (targetTaskId == null) {
                return allTasks.filter { it.status != TaskStatus.DONE && it.status != TaskStatus.SKIPPED }
            }

            val byId = allTasks.associateBy { it.id }
            val target = byId[targetTaskId] ?: error("Task not found: $targetTaskId")
            val result = mutableListOf<TaskDefinition>()
            val visited = mutableSetOf<String>()

            fun collect(task: TaskDefinition) {
                if (task.id in visited) return
                visited.add(task.id)
                task.dependencies.forEach { depId ->
                    val dep = byId[depId]
                    if (dep != null && dep.status != TaskStatus.DONE && dep.status != TaskStatus.SKIPPED) {
                        collect(dep)
                    }
                }
                if (task.status != TaskStatus.DONE && task.status != TaskStatus.SKIPPED) {
                    result.add(task)
                }
            }

            collect(target)
            return result
        }

        fun buildTaskPrompt(
            task: TaskDefinition,
            changeDir: File,
        ): String {
            val contextFile = File(changeDir, "context.md")
            val designFile = File(changeDir, "design.md")

            return buildString {
                appendLine("# Task")
                appendLine()
                appendLine("Task ID: ${task.id}")
                appendLine("Task: ${task.name}")
                appendLine()
                appendLine(task.description)
                appendLine()
                appendLine("---")
                appendLine()
                if (designFile.exists()) {
                    appendLine("# Design")
                    appendLine()
                    appendLine(designFile.readText().trim())
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                if (contextFile.exists()) {
                    appendLine("# Context")
                    appendLine()
                    appendLine(contextFile.readText().trim())
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                appendLine("# Instructions")
                appendLine()
                appendLine("Implement ONLY the task described above.")
                appendLine("Do not implement other tasks. Focus on this single task.")
                appendLine("Follow the design document faithfully.")
            }.trimEnd()
        }
    }
}
