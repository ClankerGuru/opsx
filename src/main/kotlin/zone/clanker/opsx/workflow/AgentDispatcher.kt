package zone.clanker.opsx.workflow

import org.gradle.api.logging.Logging
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

    data class Result(
        val exitCode: Int,
        val logFile: File?,
    )

    fun dispatch(
        agent: String,
        prompt: String,
        workDir: File,
        model: String = "",
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    ): Result {
        val promptFile = createPromptFile(prompt)
        val logFile = createLogFile(agent)

        logger.quiet("opsx: dispatching to $agent (timeout ${timeoutSeconds}s)")
        logger.quiet("opsx: prompt size ${prompt.length} chars")
        logger.quiet("opsx: log → ${logFile.absolutePath}")
        logger.quiet("opsx: run `tail -f ${logFile.absolutePath}` to watch progress")

        val command = buildCommand(agent, promptFile, model)
        val result =
            runCatching {
                val process =
                    ProcessBuilder(command)
                        .directory(workDir)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                        .redirectErrorStream(true)
                        .start()
                val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    logger.warn("opsx: $agent timed out after ${timeoutSeconds}s")
                    return@runCatching Result(-1, logFile)
                }
                Result(process.exitValue(), logFile)
            }.onFailure { e ->
                logger.warn("opsx: failed to run $agent: ${e.message}")
            }.getOrDefault(Result(-1, logFile))

        promptFile.delete()

        if (result.exitCode == 0) {
            logger.quiet("opsx: $agent completed successfully")
        } else {
            logger.warn("opsx: $agent exited with code ${result.exitCode} — log: ${logFile.absolutePath}")
        }
        return result
    }

    internal fun buildCommand(
        agent: String,
        promptFile: File,
        model: String = "",
    ): List<String> =
        when (agent) {
            "claude" ->
                buildList {
                    add("claude")
                    add("-p")
                    add("--dangerously-skip-permissions")
                    if (model.isNotEmpty()) {
                        add("--model")
                        add(model)
                    }
                    add(promptFile.readText())
                }
            "copilot" ->
                buildList {
                    add("copilot")
                    add("-p")
                    if (model.isNotEmpty()) {
                        add("--model")
                        add(model)
                    }
                    add(promptFile.readText())
                }
            "codex" ->
                buildList {
                    add("codex")
                    add("exec")
                    if (model.isNotEmpty()) {
                        add("-m")
                        add(model)
                    }
                    add(promptFile.readText())
                }
            "opencode" ->
                buildList {
                    add("opencode")
                    add("run")
                    if (model.isNotEmpty()) {
                        add("-m")
                        add(model)
                    }
                    add(promptFile.readText())
                }
            else -> error("Unknown agent: $agent. Use: claude, copilot, codex, opencode")
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
