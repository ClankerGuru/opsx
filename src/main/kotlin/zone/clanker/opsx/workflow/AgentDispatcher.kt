package zone.clanker.opsx.workflow

import org.gradle.api.logging.Logging
import zone.clanker.opsx.model.Agent
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Dispatches prompts to AI agent CLIs via temp file piped to stdin.
 *
 * Prompt is written to a temp file and redirected as stdin. stdout/stderr are inherited
 * so output streams live to the calling terminal — no daemon threads or shell wrappers.
 *
 * Each agent's non-interactive flag:
 * - claude: `-p` (print mode, reads prompt from stdin when no arg given)
 * - copilot: (prompt piped to stdin in non-interactive mode)
 * - codex: `exec` (reads from stdin)
 * - opencode: `run` (reads message from stdin)
 */
object AgentDispatcher {
    private val logger = Logging.getLogger(AgentDispatcher::class.java)
    internal const val DEFAULT_TIMEOUT_SECONDS = 600L
    private const val READER_JOIN_TIMEOUT_MS = 5000L

    enum class FailureReason { TIMEOUT, DISPATCH_EXCEPTION }

    data class Result(
        val exitCode: Int,
        val logFile: File?,
        val reason: FailureReason? = null,
    )

    fun dispatch(
        agent: Agent,
        prompt: String,
        workDir: File,
        model: String = "",
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    ): Result {
        val promptFile = createPromptFile(prompt)
        val logFile = createLogFile(agent.id)

        logger.quiet("opsx: dispatching to ${agent.id} (timeout ${timeoutSeconds}s)")
        logger.quiet("opsx: prompt size ${prompt.length} chars")

        val command = buildCommand(agent, promptFile, model)
        val result =
            runCatching {
                val process =
                    ProcessBuilder(command)
                        .directory(workDir)
                        .redirectErrorStream(true)
                        .start()
                // Stream output to log file and logger in a reader thread
                val reader =
                    Thread {
                        process.inputStream.bufferedReader().useLines { lines ->
                            lines.forEach { line ->
                                logFile.appendText(line + "\n")
                                logger.quiet(line)
                            }
                        }
                    }
                reader.isDaemon = true
                reader.start()
                val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    joinReader(reader, agent)
                    logger.warn("opsx: ${agent.id} timed out after ${timeoutSeconds}s")
                    return@runCatching Result(-1, logFile, FailureReason.TIMEOUT)
                }
                joinReader(reader, agent)
                Result(process.exitValue(), logFile)
            }.onFailure { e ->
                logger.warn("opsx: failed to run ${agent.id}: ${e.message}")
            }.getOrDefault(Result(-1, logFile, FailureReason.DISPATCH_EXCEPTION))

        promptFile.delete()

        if (result.exitCode == 0) {
            logger.quiet("opsx: ${agent.id} completed successfully")
        } else {
            logger.warn("opsx: ${agent.id} exited with code ${result.exitCode} — log: ${logFile.absolutePath}")
        }
        return result
    }

    internal fun buildCommand(
        agent: Agent,
        promptFile: File,
        model: String = "",
    ): List<String> =
        buildList {
            add(agent.cliCommand)
            addAll(agent.nonInteractiveArgs)
            if (model.isNotEmpty()) {
                add(agent.modelFlag)
                add(model)
            }
            add(promptFile.readText())
        }

    private fun joinReader(
        reader: Thread,
        agent: Agent,
    ) {
        reader.join(READER_JOIN_TIMEOUT_MS)
        if (reader.isAlive) {
            logger.warn("opsx: output reader still running for ${agent.id}, interrupting")
            reader.interrupt()
        }
    }

    private fun createPromptFile(prompt: String): File {
        val file = File.createTempFile("opsx-prompt-", ".md")
        file.writeText(prompt)
        return file
    }

    private fun createLogFile(agent: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "opsx-logs")
        dir.mkdirs()
        return File(dir, "$agent-${System.currentTimeMillis()}.log")
    }
}
