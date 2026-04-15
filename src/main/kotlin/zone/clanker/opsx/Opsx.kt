package zone.clanker.opsx

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.OpsxConfig
import zone.clanker.opsx.skill.SkillGenerator
import zone.clanker.opsx.skill.TaskInfo
import zone.clanker.opsx.task.ApplyTask
import zone.clanker.opsx.task.ArchiveTask
import zone.clanker.opsx.task.BulkArchiveTask
import zone.clanker.opsx.task.ContinueTask
import zone.clanker.opsx.task.ExploreTask
import zone.clanker.opsx.task.FeedbackTask
import zone.clanker.opsx.task.FfTask
import zone.clanker.opsx.task.OnboardTask
import zone.clanker.opsx.task.ProposeTask
import zone.clanker.opsx.task.VerifyTask

data object Opsx {
    const val GROUP = "opsx"
    const val EXTENSION_NAME = "opsx"
    const val OUTPUT_DIR = "opsx"

    // Workflow tasks
    const val TASK_PROPOSE = "opsx-propose"
    const val TASK_APPLY = "opsx-apply"
    const val TASK_VERIFY = "opsx-verify"
    const val TASK_ARCHIVE = "opsx-archive"
    const val TASK_CONTINUE = "opsx-continue"
    const val TASK_EXPLORE = "opsx-explore"
    const val TASK_FEEDBACK = "opsx-feedback"
    const val TASK_ONBOARD = "opsx-onboard"
    const val TASK_FF = "opsx-ff"
    const val TASK_BULK_ARCHIVE = "opsx-bulk-archive"

    // Infrastructure tasks
    const val TASK_SYNC = "opsx-sync"
    const val TASK_CLEAN = "opsx-clean"
    const val TASK_STATUS = "opsx-status"
    const val TASK_LIST = "opsx-list"

    // Namespaced Gradle properties (-P flags)
    private const val PREFIX = "zone.clanker.opsx"
    const val PROP_PROMPT = "$PREFIX.prompt"
    const val PROP_SPEC = "$PREFIX.spec"
    const val PROP_CHANGE = "$PREFIX.change"
    const val PROP_CHANGE_NAME = "$PREFIX.changeName"
    const val PROP_AGENT = "$PREFIX.agent"
    const val PROP_MODEL = "$PREFIX.model"

    open class SettingsExtension {
        var outputDir: String = OUTPUT_DIR
        var agents: MutableList<String> = mutableListOf()
        var skillDirectories: MutableList<String> = mutableListOf()
        var agentDirectories: MutableList<String> = mutableListOf()
        var specsDir: String = "specs"
        var changesDir: String = "changes"
        var projectFile: String = "project.md"

        internal fun toOpsxConfig(): OpsxConfig =
            OpsxConfig(
                outputDir = outputDir,
                specsDir = specsDir,
                changesDir = changesDir,
                projectFile = projectFile,
            )
    }

    class SettingsPlugin : Plugin<Settings> {
        override fun apply(settings: Settings) {
            val extension = settings.extensions.create(EXTENSION_NAME, SettingsExtension::class.java)
            settings.gradle.rootProject(
                Action { rootProject ->
                    registerTasks(rootProject, extension)
                },
            )
        }

        internal fun registerTasks(
            rootProject: Project,
            extension: SettingsExtension,
        ) {
            registerWorkflowTasks(rootProject, extension)
            registerInfrastructureTasks(rootProject, extension)
        }

        private fun configProvider(
            rootProject: Project,
            extension: SettingsExtension,
        ) = rootProject.provider { extension.toOpsxConfig() }

        private fun registerWorkflowTasks(
            rootProject: Project,
            extension: SettingsExtension,
        ) {
            val config = configProvider(rootProject, extension)
            val agentProp =
                rootProject.providers
                    .gradleProperty(PROP_AGENT)
                    .orElse(
                        rootProject.provider {
                            extension.agents.firstOrNull() ?: ""
                        },
                    )
            val modelProp = rootProject.providers.gradleProperty(PROP_MODEL).orElse("")

            registerPrimaryWorkflowTasks(rootProject, config, agentProp, modelProp)
            registerSecondaryWorkflowTasks(rootProject, config, agentProp, modelProp)
        }

        private fun registerPrimaryWorkflowTasks(
            rootProject: Project,
            config: org.gradle.api.provider.Provider<OpsxConfig>,
            agentProp: org.gradle.api.provider.Provider<String>,
            modelProp: org.gradle.api.provider.Provider<String>,
        ) {
            rootProject.tasks.register(TASK_PROPOSE, ProposeTask::class.java) {
                it.group = GROUP
                it.description = "Propose a new change"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.spec.set(rootProject.providers.gradleProperty(PROP_SPEC))
                it.prompt.set(rootProject.providers.gradleProperty(PROP_PROMPT))
                it.changeNameOverride.set(rootProject.providers.gradleProperty(PROP_CHANGE_NAME))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_APPLY, ApplyTask::class.java) {
                it.group = GROUP
                it.description = "Apply a change to the codebase"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.changeName.set(rootProject.providers.gradleProperty(PROP_CHANGE))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_VERIFY, VerifyTask::class.java) {
                it.group = GROUP
                it.description = "Verify a change was applied correctly"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.changeName.set(rootProject.providers.gradleProperty(PROP_CHANGE))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_ARCHIVE, ArchiveTask::class.java) {
                it.group = GROUP
                it.description = "Archive a completed change"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.changeName.set(rootProject.providers.gradleProperty(PROP_CHANGE))
            }
        }

        private fun registerSecondaryWorkflowTasks(
            rootProject: Project,
            config: org.gradle.api.provider.Provider<OpsxConfig>,
            agentProp: org.gradle.api.provider.Provider<String>,
            modelProp: org.gradle.api.provider.Provider<String>,
        ) {
            rootProject.tasks.register(TASK_CONTINUE, ContinueTask::class.java) {
                it.group = GROUP
                it.description = "Continue work on an in-progress change"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.changeName.set(rootProject.providers.gradleProperty(PROP_CHANGE))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_EXPLORE, ExploreTask::class.java) {
                it.group = GROUP
                it.description = "Explore the codebase for a change"
                it.rootDir.set(rootProject.projectDir)
                it.prompt.set(rootProject.providers.gradleProperty(PROP_PROMPT))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_FEEDBACK, FeedbackTask::class.java) {
                it.group = GROUP
                it.description = "Provide feedback on a change"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.changeName.set(rootProject.providers.gradleProperty(PROP_CHANGE))
                it.prompt.set(rootProject.providers.gradleProperty(PROP_PROMPT))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_ONBOARD, OnboardTask::class.java) {
                it.group = GROUP
                it.description = "Onboard a new contributor to the project"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.prompt.set(rootProject.providers.gradleProperty(PROP_PROMPT).orElse(""))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_FF, FfTask::class.java) {
                it.group = GROUP
                it.description = "Fast-forward a change to the latest state"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
                it.changeName.set(rootProject.providers.gradleProperty(PROP_CHANGE))
                it.agent.set(agentProp)
                it.model.set(modelProp)
            }
            rootProject.tasks.register(TASK_BULK_ARCHIVE, BulkArchiveTask::class.java) {
                it.group = GROUP
                it.description = "Archive all completed changes in bulk"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
            }
        }

        private fun registerInfrastructureTasks(
            rootProject: Project,
            extension: SettingsExtension,
        ) {
            val config = configProvider(rootProject, extension)

            rootProject.tasks.register(TASK_STATUS, zone.clanker.opsx.task.StatusTask::class.java) {
                it.group = GROUP
                it.description = "Show all changes and their status"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
            }
            val snapshotTaskInfos =
                rootProject.tasks
                    .filter { task -> task.group in SkillGenerator.TRACKED_GROUPS }
                    .map { task -> TaskInfo(task.name, task.group ?: "", task.description ?: "") }
            val snapshotBuildNames =
                rootProject.gradle.includedBuilds.map { build -> build.name }
            val snapshotBuildDirs =
                rootProject.gradle.includedBuilds.map { build -> build.projectDir }

            rootProject.tasks.register(TASK_SYNC, zone.clanker.opsx.task.SyncTask::class.java) {
                it.group = GROUP
                it.description = "Generate agent skills and instruction files"
                it.rootDir.set(rootProject.projectDir)
                it.taskInfos.set(snapshotTaskInfos)
                it.includedBuildNames.set(snapshotBuildNames)
                it.includedBuildDirs.set(snapshotBuildDirs)
                it.agents.set(extension.agents)
                it.skillDirectories.set(extension.skillDirectories)
                it.agentDirectories.set(extension.agentDirectories)
            }

            rootProject.tasks.register(TASK_CLEAN, zone.clanker.opsx.task.CleanTask::class.java) {
                it.group = GROUP
                it.description = "Remove all generated skill files and symlinks"
                it.rootDir.set(rootProject.projectDir)
                it.includedBuildDirs.set(snapshotBuildDirs)
            }
            rootProject.tasks.register(TASK_LIST, zone.clanker.opsx.task.ListTask::class.java) {
                it.group = GROUP
                it.description = "List all changes"
                it.rootDir.set(rootProject.projectDir)
                it.config.set(config)
            }
        }
    }

    @DisableCachingByDefault(because = "Stub tasks produce no cacheable output")
    abstract class StubTask : DefaultTask() {
        @get:Internal
        var taskMessage: String = "not yet implemented"

        @TaskAction
        fun run() {
            logger.lifecycle(taskMessage)
        }
    }
}
