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

    // Plugin IDs to auto-apply if not present
    private val MANAGED_PLUGINS =
        listOf(
            "zone.clanker.gradle.wrkx",
            "zone.clanker.gradle.srcx",
        )

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
            applyManagedPlugins(settings)
            settings.gradle.rootProject(
                Action { rootProject ->
                    registerTasks(rootProject, extension)
                },
            )
        }

        private fun applyManagedPlugins(settings: Settings) {
            MANAGED_PLUGINS.forEach { pluginId ->
                if (!settings.pluginManager.hasPlugin(pluginId)) {
                    runCatching { settings.pluginManager.apply(pluginId) }
                }
            }
        }

        internal fun registerTasks(
            rootProject: Project,
            extension: SettingsExtension,
        ) {
            registerWorkflowTasks(rootProject)
            registerInfrastructureTasks(rootProject, extension)
        }

        private fun registerWorkflowTasks(rootProject: Project) {
            rootProject.tasks.register(TASK_PROPOSE, StubTask::class.java) {
                it.group = GROUP
                it.description = "Propose a new change from a spec"
                it.taskMessage = "$TASK_PROPOSE: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_APPLY, StubTask::class.java) {
                it.group = GROUP
                it.description = "Apply a change proposal to the codebase"
                it.taskMessage = "$TASK_APPLY: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_VERIFY, StubTask::class.java) {
                it.group = GROUP
                it.description = "Verify a change was applied correctly"
                it.taskMessage = "$TASK_VERIFY: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_ARCHIVE, StubTask::class.java) {
                it.group = GROUP
                it.description = "Archive a completed change"
                it.taskMessage = "$TASK_ARCHIVE: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_CONTINUE, StubTask::class.java) {
                it.group = GROUP
                it.description = "Continue work on an in-progress change"
                it.taskMessage = "$TASK_CONTINUE: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_EXPLORE, StubTask::class.java) {
                it.group = GROUP
                it.description = "Explore the codebase for a change"
                it.taskMessage = "$TASK_EXPLORE: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_FEEDBACK, StubTask::class.java) {
                it.group = GROUP
                it.description = "Provide feedback on a change"
                it.taskMessage = "$TASK_FEEDBACK: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_ONBOARD, StubTask::class.java) {
                it.group = GROUP
                it.description = "Onboard a new contributor to the project"
                it.taskMessage = "$TASK_ONBOARD: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_FF, StubTask::class.java) {
                it.group = GROUP
                it.description = "Fast-forward a change to the latest state"
                it.taskMessage = "$TASK_FF: not yet implemented — run opsx-sync first to generate skills"
            }

            rootProject.tasks.register(TASK_BULK_ARCHIVE, StubTask::class.java) {
                it.group = GROUP
                it.description = "Archive all completed changes in bulk"
                it.taskMessage =
                    "$TASK_BULK_ARCHIVE: not yet implemented — run opsx-sync first to generate skills"
            }
        }

        private fun registerInfrastructureTasks(
            rootProject: Project,
            extension: SettingsExtension,
        ) {
            rootProject.tasks.register(TASK_SYNC, SyncTask::class.java) {
                it.group = GROUP
                it.description = "Generate slash commands for all agents"
                it.extension = extension
            }

            rootProject.tasks.register(TASK_CLEAN, CleanTask::class.java) {
                it.group = GROUP
                it.description = "Remove all generated skill files and .gitignore entries"
            }

            rootProject.tasks.register(TASK_STATUS, StatusTask::class.java) {
                it.group = GROUP
                it.description = "Show status of all changes"
                it.extension = extension
            }

            rootProject.tasks.register(TASK_LIST, ListTask::class.java) {
                it.group = GROUP
                it.description = "List all changes and their status"
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

            // Clean root opsx skill files
            SkillGenerator.generatedDirs(project.rootDir).forEach { dir ->
                if (dir.exists()) {
                    val count = dir.walkTopDown().filter { it.isFile }.count()
                    dir.deleteRecursively()
                    logger.lifecycle("opsx-clean: deleted ${dir.relativeTo(project.rootDir)} ($count files)")
                    cleaned = true
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
