package zone.clanker.opsx.workflow

import zone.clanker.opsx.model.TaskDefinition
import zone.clanker.opsx.model.TaskStatus
import java.io.File
import java.security.SecureRandom

/**
 * Parses tasks.md into a list of [TaskDefinition]s.
 *
 * Format:
 * ```
 * - [ ] a1b2c3d4e5 | Task name
 *     Description line 1.
 *     Description line 2.
 *   depends: none
 *
 *   OR
 *
 *   depends:
 *     - othertaskid01
 *     - othertaskid02
 * ```
 */
object TaskParser {
    private val TASK_LINE = Regex("""^- \[(.)] ([a-zA-Z0-9]{10}) \| (.+)$""")
    private val DEPENDS_ID = Regex("""^\s{4}- ([a-zA-Z0-9]{10})$""")
    private const val ID_LENGTH = 10
    private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
    private const val DESCRIPTION_INDENT = 4

    fun parse(file: File): List<TaskDefinition> {
        if (!file.exists()) return emptyList()
        return parse(file.readLines())
    }

    fun parse(lines: List<String>): List<TaskDefinition> {
        val chunks = splitIntoTaskChunks(lines)
        return chunks.mapNotNull { parseChunk(it) }
    }

    private fun splitIntoTaskChunks(lines: List<String>): List<List<String>> {
        val chunks = mutableListOf<MutableList<String>>()
        for (line in lines) {
            if (TASK_LINE.matchEntire(line) != null) {
                chunks.add(mutableListOf(line))
            } else if (chunks.isNotEmpty()) {
                chunks.last().add(line)
            }
        }
        return chunks
    }

    private fun parseChunk(chunk: List<String>): TaskDefinition? {
        val header = TASK_LINE.matchEntire(chunk.first()) ?: return null
        val statusChar = header.groupValues[1].firstOrNull() ?: ' '
        val id = header.groupValues[2]
        val name = header.groupValues[3].trim()

        val body = chunk.drop(1).filter { it.isNotBlank() }
        val description = extractDescription(body)
        val dependencies = extractDependencies(body)

        return TaskDefinition(
            id = id,
            name = name,
            description = description,
            status = TaskStatus.fromSymbol(statusChar),
            dependencies = dependencies,
        )
    }

    private fun extractDescription(body: List<String>): String =
        body
            .filter { it.startsWith("    ") && !it.trimStart().startsWith("depends:") }
            .map { it.substring(DESCRIPTION_INDENT) }
            .joinToString("\n")
            .trim()

    private fun extractDependencies(body: List<String>): List<String> {
        val dependsLine =
            body.firstOrNull { it.trimStart().startsWith("depends:") }
                ?: return emptyList()
        val inlineValue = dependsLine.substringAfter("depends:").trim()
        if (inlineValue == "none" || inlineValue.isNotEmpty()) return emptyList()
        return body
            .dropWhile { !it.trimStart().startsWith("depends:") }
            .drop(1)
            .mapNotNull { DEPENDS_ID.matchEntire(it)?.groupValues?.get(1) }
    }

    fun generateId(): String {
        val random = SecureRandom()
        return (1..ID_LENGTH)
            .map { ID_CHARS[random.nextInt(ID_CHARS.length)] }
            .joinToString("")
    }

    fun updateStatus(
        file: File,
        taskId: String,
        newStatus: TaskStatus,
    ) {
        if (!file.exists()) return
        val lines = file.readLines()
        val updated =
            lines.map { line ->
                val match = TASK_LINE.matchEntire(line)
                if (match != null && match.groupValues[2] == taskId) {
                    line.replaceRange(3, DESCRIPTION_INDENT, newStatus.symbol.toString())
                } else {
                    line
                }
            }
        file.writeText(updated.joinToString("\n") + "\n")
    }

    fun findTerminalTasks(tasks: List<TaskDefinition>): List<TaskDefinition> {
        val allDependedOn = tasks.flatMap { it.dependencies }.toSet()
        return tasks.filter { it.id !in allDependedOn }
    }

    fun topologicalOrder(tasks: List<TaskDefinition>): List<TaskDefinition> {
        val byId = tasks.associateBy { it.id }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<TaskDefinition>()

        fun visit(task: TaskDefinition) {
            if (task.id in visited) return
            visited.add(task.id)
            task.dependencies.forEach { depId ->
                byId[depId]?.let { visit(it) }
            }
            result.add(task)
        }

        tasks.forEach { visit(it) }
        return result
    }
}
