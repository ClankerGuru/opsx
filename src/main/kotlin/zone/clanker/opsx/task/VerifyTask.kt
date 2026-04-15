package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.model.ChangeConfig
import zone.clanker.opsx.model.ChangeStatus
import zone.clanker.opsx.model.OpsxConfig
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
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @get:Internal
    abstract val changeName: Property<String>

    @get:Internal
    abstract val agent: Property<String>

    @get:Internal
    abstract val model: Property<String>

    @TaskAction
    fun run() {
        val name =
            changeName.orNull
                ?: error("Required: -P${zone.clanker.opsx.Opsx.PROP_CHANGE}=\"change-name\"")

        val root = rootDir.get()
        val cfg = config.get()
        val reader = ChangeReader(root, cfg)
        val writer = ChangeWriter(root, cfg)

        val change =
            reader.readAll().find { it.name == name }
                ?: error("Change not found: $name")

        val changeConfig = ChangeConfig.parse(File(change.dir, ".opsx.yaml"))

        // Ensure the config file exists so status updates succeed
        if (changeConfig == null) {
            writer.writeConfig(change.dir, name, ChangeStatus.from(change.status))
        }

        if (changeConfig != null && changeConfig.verify.isNotBlank()) {
            runVerifyCommand(changeConfig.verify, name, change.dir, writer, root)
        } else {
            runAgentVerify(name, change, writer, root)
        }
    }

    private fun runVerifyCommand(
        verifyCommand: String,
        name: String,
        changeDir: File,
        writer: ChangeWriter,
        root: File,
    ) {
        logger.quiet("opsx-verify: running verify command for '$name'...")
        logger.quiet("opsx-verify: $verifyCommand")

        val result =
            runCatching {
                val process =
                    ProcessBuilder("sh", "-c", verifyCommand)
                        .directory(root)
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
            logger.quiet("opsx-verify: '$name' all green — marked as verified")
        } else {
            throw GradleException("opsx-verify: verify command failed with exit code $result — not marking as verified")
        }
    }

    private fun runAgentVerify(
        name: String,
        change: zone.clanker.opsx.model.Change,
        writer: ChangeWriter,
        root: File,
    ) {
        val agentVal = agent.get()
        val modelVal = model.getOrElse("")

        val promptBuilder = PromptBuilder(root)
        val context = promptBuilder.srcxContext()
        val changeCtx = promptBuilder.changeContext(change)
        val fullPrompt = buildVerifyPrompt(root, context, changeCtx)

        logger.quiet("opsx-verify: asking $agentVal to verify '$name'...")
        val result = AgentDispatcher.dispatch(Agent.fromId(agentVal), fullPrompt, root, modelVal)
        if (result.exitCode == 0) {
            writer.updateStatus(change.dir, ChangeStatus.VERIFIED)
            logger.quiet("opsx-verify: '$name' marked as verified")
        } else {
            throw GradleException("opsx-verify: agent exited with code ${result.exitCode} — not marking as verified")
        }
    }

    internal fun buildVerifyPrompt(
        root: File,
        context: String,
        changeCtx: String,
    ): String {
        val promptBuilder = PromptBuilder(root)
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
