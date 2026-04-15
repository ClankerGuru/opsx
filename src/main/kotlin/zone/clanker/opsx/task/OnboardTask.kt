package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.PromptBuilder
import java.io.File

/** Onboards a new contributor by giving a guided tour of the project. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class OnboardTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val config: Property<OpsxConfig>

    @get:Internal
    abstract val prompt: Property<String>

    @get:Internal
    abstract val agent: Property<String>

    @get:Internal
    abstract val model: Property<String>

    @TaskAction
    fun run() {
        val userPrompt = prompt.getOrElse("")
        val agentVal = agent.get()
        val modelVal = model.getOrElse("")
        val root = rootDir.get()
        val cfg = config.get()

        val promptBuilder = PromptBuilder(root)
        val context = promptBuilder.srcxContext()
        val projectDesc = promptBuilder.projectDescription(cfg)
        val fullPrompt = buildOnboardPrompt(root, context, projectDesc, userPrompt)

        logger.quiet("opsx-onboard: asking $agentVal for a guided tour...")
        val result = AgentDispatcher.dispatch(Agent.fromId(agentVal), fullPrompt, root, modelVal)
        if (result.exitCode != 0) {
            logger.warn("opsx-onboard: agent exited with code ${result.exitCode}")
        }
    }

    internal fun buildOnboardPrompt(
        root: File,
        context: String,
        projectDesc: String,
        userPrompt: String,
    ): String {
        val promptBuilder = PromptBuilder(root)
        return promptBuilder.build(
            "Codebase Context" to context,
            "Project Description" to projectDesc,
            "Instructions" to
                buildString {
                    appendLine("Give a guided tour of this project for a new contributor.")
                    appendLine("Cover the following:")
                    appendLine("- Architecture overview and key modules")
                    appendLine("- Key files and their purposes")
                    appendLine("- How to build, test, and run the project")
                    appendLine("- How to contribute changes using the opsx workflow")
                    if (userPrompt.isNotBlank()) {
                        appendLine()
                        appendLine("The new contributor has this background/context:")
                        appendLine(userPrompt)
                    }
                },
        )
    }
}
