package zone.clanker.opsx

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.skill.SkillGenerator
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
import zone.clanker.opsx.workflow.ChangeReader

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
        var defaultAgent: String = "claude"
        var specsDir: String = "specs"
        var changesDir: String = "changes"
        var projectFile: String = "project.md"
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

        private fun registerWorkflowTasks(
            rootProject: Project,
            extension: SettingsExtension,
        ) {
            // Primary workflow — visible in `./gradlew tasks`
            rootProject.tasks.register(TASK_PROPOSE, ProposeTask::class.java) {
                it.group = GROUP
                it.description = "Propose a new change"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_APPLY, ApplyTask::class.java) {
                it.group = GROUP
                it.description = "Apply a change to the codebase"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_VERIFY, VerifyTask::class.java) {
                it.group = GROUP
                it.description = "Verify a change was applied correctly"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_ARCHIVE, ArchiveTask::class.java) {
                it.group = GROUP
                it.description = "Archive a completed change"
                it.extension = extension
            }

            // Secondary workflow
            rootProject.tasks.register(TASK_CONTINUE, ContinueTask::class.java) {
                it.group = GROUP
                it.description = "Continue work on an in-progress change"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_EXPLORE, ExploreTask::class.java) {
                it.group = GROUP
                it.description = "Explore the codebase for a change"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_FEEDBACK, FeedbackTask::class.java) {
                it.group = GROUP
                it.description = "Provide feedback on a change"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_ONBOARD, OnboardTask::class.java) {
                it.group = GROUP
                it.description = "Onboard a new contributor to the project"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_FF, FfTask::class.java) {
                it.group = GROUP
                it.description = "Fast-forward a change to the latest state"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_BULK_ARCHIVE, BulkArchiveTask::class.java) {
                it.group = GROUP
                it.description = "Archive all completed changes in bulk"
                it.extension = extension
            }
        }

        private fun registerInfrastructureTasks(
            rootProject: Project,
            extension: SettingsExtension,
        ) {
            rootProject.tasks.register(TASK_STATUS, zone.clanker.opsx.task.StatusTask::class.java) {
                it.group = GROUP
                it.description = "Show all changes and their status"
                it.extension = extension
            }
            rootProject.tasks.register(TASK_SYNC, zone.clanker.opsx.task.SyncTask::class.java) {
                it.group = GROUP
                it.description = "Generate agent skills and instruction files"
                it.extension = extension
            }

            rootProject.tasks.register(TASK_CLEAN, zone.clanker.opsx.task.CleanTask::class.java) {
                it.group = GROUP
                it.description = "Remove all generated skill files and symlinks"
            }
            rootProject.tasks.register(TASK_LIST, zone.clanker.opsx.task.ListTask::class.java) {
                it.group = GROUP
                it.description = "List all changes"
                it.extension = extension
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

    @DisableCachingByDefault(because = "Generates files in multiple agent-specific directories")
    abstract class SyncTask : DefaultTask() {
        @get:Internal
        lateinit var extension: SettingsExtension

        @TaskAction
        fun run() {
            val generator = SkillGenerator(project)
            val dirs = generator.generate()
            dirs.forEach { dir ->
                java.io.File(dir, ".gitignore").writeText("# Generated by opsx-sync\n*\n!.gitignore\n")
            }
            logger.lifecycle("opsx-sync: ${dirs.size} skill directories generated")
        }
    }

    @DisableCachingByDefault(because = "Deletes generated files")
    abstract class CleanTask : DefaultTask() {
        @TaskAction
        fun run() {
            var cleaned = false

            // Clean opsx skill directory
            val skillsDir = java.io.File(System.getProperty("user.home"), SkillGenerator.SKILLS_DIR)
            if (skillsDir.exists()) {
                val count = skillsDir.walkTopDown().filter { it.isFile }.count()
                skillsDir.deleteRecursively()
                logger.lifecycle("opsx-clean: deleted ${SkillGenerator.SKILLS_DIR} ($count files)")
                cleaned = true
            }

            // Remove only opsx-generated symlinks (pointing into our skills dir)
            val home = java.io.File(System.getProperty("user.home"))
            val skillsPath = java.io.File(home, SkillGenerator.SKILLS_DIR).toPath()
            SkillGenerator.AGENT_TARGETS.forEach { target ->
                val dir = java.io.File(home, target)
                if (dir.exists()) {
                    dir
                        .listFiles()
                        ?.filter { file ->
                            val path = file.toPath()
                            java.nio.file.Files
                                .isSymbolicLink(path) &&
                                runCatching {
                                    java.nio.file.Files
                                        .readSymbolicLink(path)
                                        .startsWith(skillsPath)
                                }.getOrDefault(false)
                        }?.forEach { link ->
                            link.delete()
                            logger.lifecycle("opsx-clean: removed symlink ${link.name} from $target")
                            cleaned = true
                        }
                }
            }
            SkillGenerator.instructionFiles(project.rootDir).forEach { file ->
                if (file.exists()) {
                    removeMarkers(file)
                    logger.lifecycle("opsx-clean: removed OPSX section from ${file.name}")
                    cleaned = true
                }
            }

            // Clean srcx at root
            cleanDir(project.rootDir, ".srcx")?.let { cleaned = true }

            // Clean srcx and wrkx in every included build
            project.gradle.includedBuilds.forEach { build ->
                cleanDir(build.projectDir, ".srcx")?.let { cleaned = true }
                cleanDir(build.projectDir, ".wrkx")?.let { cleaned = true }
            }

            if (!cleaned) logger.lifecycle("opsx-clean: nothing to clean")
        }

        private fun cleanDir(
            baseDir: java.io.File,
            dirName: String,
        ): Boolean? {
            val dir = java.io.File(baseDir, dirName)
            if (!dir.exists()) return null
            val count = dir.walkTopDown().filter { it.isFile }.count()
            dir.deleteRecursively()
            val rel = if (baseDir == project.rootDir) dirName else "${baseDir.name}/$dirName"
            logger.lifecycle("opsx-clean: deleted $rel ($count files)")
            return true
        }

        private fun removeMarkers(file: java.io.File) {
            val marker = "<!-- OPSX:AUTO -->"
            val endMarker = "<!-- /OPSX:AUTO -->"
            val content = file.readText()
            if (!content.contains(marker)) return
            val before = content.substringBefore(marker).trimEnd()
            val after = content.substringAfter(endMarker, "").trimStart()
            val result = "$before\n$after".trim()
            if (result.isEmpty()) {
                file.delete()
            } else {
                file.writeText("$result\n")
            }
        }
    }

    @DisableCachingByDefault(because = "Displays change status to console")
    abstract class StatusTask : DefaultTask() {
        @get:Internal
        lateinit var extension: SettingsExtension

        @TaskAction
        fun run() {
            val reader = ChangeReader(project.rootDir, extension)
            val changes = reader.readAll()
            if (changes.isEmpty()) {
                logger.lifecycle("No changes found in ${extension.outputDir}/${extension.changesDir}")
                return
            }
            changes.forEach { change ->
                val deps = if (change.depends.isEmpty()) "" else " (depends: ${change.depends.joinToString()})"
                val proposal = if (change.proposalFile.exists()) "proposal" else ""
                val design = if (change.designFile.exists()) "design" else ""
                val tasks = if (change.tasksFile.exists()) "tasks" else ""
                val files = listOf(proposal, design, tasks).filter { it.isNotEmpty() }.joinToString(", ")
                logger.lifecycle("  ${change.name} [${change.status}]$deps — files: $files")
            }
        }
    }

    @DisableCachingByDefault(because = "Displays change list to console")
    abstract class ListTask : DefaultTask() {
        @get:Internal
        lateinit var extension: SettingsExtension

        @TaskAction
        fun run() {
            val reader = ChangeReader(project.rootDir, extension)
            val changes = reader.readAll()
            if (changes.isEmpty()) {
                logger.lifecycle("No changes found in ${extension.outputDir}/${extension.changesDir}")
                return
            }
            logger.lifecycle("Changes (${changes.size}):")
            changes.forEach { change ->
                logger.lifecycle("  ${change.name} [${change.status}]")
            }
        }
    }
}
