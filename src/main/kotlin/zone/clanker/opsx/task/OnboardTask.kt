package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.workflow.AgentDispatcher
import zone.clanker.opsx.workflow.PromptBuilder

/** Onboards a new contributor by giving a guided tour of the project. */
@DisableCachingByDefault(because = "Runs an external agent process")
abstract class OnboardTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val userPrompt = project.findProperty(Opsx.PROP_PROMPT)?.toString() ?: ""
        val agent =
            project.findProperty(Opsx.PROP_AGENT)?.toString()?.takeUnless { it.isBlank() }
                ?: extension.defaultAgent
        val model = project.findProperty(Opsx.PROP_MODEL)?.toString() ?: ""

        val promptBuilder = PromptBuilder(project.rootDir)
        val context = promptBuilder.srcxContext()
        val projectDesc = promptBuilder.projectDescription(extension)
        val fullPrompt = buildOnboardPrompt(context, projectDesc, userPrompt)

        logger.quiet("opsx-onboard: asking $agent for a guided tour...")
        val result = AgentDispatcher.dispatch(agent, fullPrompt, project.rootDir, model)
        if (result.exitCode != 0) {
            logger.warn("opsx-onboard: agent exited with code ${result.exitCode}")
        }
    }

    internal fun buildOnboardPrompt(
        context: String,
        projectDesc: String,
        userPrompt: String,
    ): String {
        val promptBuilder = PromptBuilder(project.rootDir)
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
