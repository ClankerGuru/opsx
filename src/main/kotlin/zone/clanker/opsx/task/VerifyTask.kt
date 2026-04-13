package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.ChangeReader
import zone.clanker.opsx.workflow.ChangeWriter
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File
import java.util.concurrent.TimeUnit

/** Verifies a change by running the verify command from .opsx.yaml, or agent review as fallback. */
@DisableCachingByDefault(because = "Runs an external process")
abstract class VerifyTask : DefaultTask() {
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

        val config = ChangeConfig.parse(File(change.dir, ".opsx.yaml"))

        if (config != null && config.verify.isNotBlank()) {
            runVerifyCommand(config.verify, changeName, change.dir, writer)
        } else {
            runAgentVerify(changeName, change, writer)
        }
    }

    private fun runVerifyCommand(
        verifyCommand: String,
        changeName: String,
        changeDir: File,
        writer: ChangeWriter,
    ) {
        logger.quiet("opsx-verify: running verify command for '$changeName'...")
        logger.quiet("opsx-verify: $verifyCommand")

        val result =
            runCatching {
                val process =
                    ProcessBuilder("sh", "-c", verifyCommand)
                        .directory(project.rootDir)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start()
                val completed = process.waitFor(VERIFY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    return@runCatching -1
                }
                process.exitValue()
            }.getOrDefault(-1)

        if (result == 0) {
            writer.updateStatus(changeDir, ChangeStatus.VERIFIED)
            logger.quiet("opsx-verify: '$changeName' all green — marked as verified")
        } else {
            logger.warn("opsx-verify: verify command failed with exit code $result — not marking as verified")
        }
    }

    private fun runAgentVerify(
        changeName: String,
        change: zone.clanker.opsx.model.Change,
        writer: ChangeWriter,
    ) {
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val promptBuilder = PromptBuilder(project.rootDir)
        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val fullPrompt = buildVerifyPrompt(context, changeCtx)

        logger.quiet("opsx-verify: asking $agent to verify '$changeName'...")
        val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
        if (result.exitCode == 0) {
            writer.updateStatus(change.dir, ChangeStatus.VERIFIED)
            logger.quiet("opsx-verify: '$changeName' marked as verified")
        } else {
            logger.warn("opsx-verify: agent exited with code ${result.exitCode} — not marking as verified")
        }
    }

    internal fun buildVerifyPrompt(
        context: String,
        changeCtx: String,
    ): String {
        val promptBuilder = PromptBuilder(project.rootDir)
        return promptBuilder.build(
            "Codebase Context" to context,
            "Change" to changeCtx,
            "Instructions" to
                buildString {
                    appendLine("Verify that this change was implemented correctly:")
                    appendLine("1. All tests pass")
                    appendLine("2. The design was followed faithfully")
                    appendLine("3. No regressions were introduced")
                    appendLine("4. Each task item in tasks.md is checked off")
                    appendLine()
                    appendLine("Report any issues found. Exit with code 0 if verification passes.")
                },
        )
    }

    companion object {
        private const val VERIFY_TIMEOUT_SECONDS = 600L
    }
}
