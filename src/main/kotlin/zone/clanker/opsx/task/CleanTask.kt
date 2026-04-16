package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.model.Agent
import zone.clanker.opsx.skill.SkillGenerator
import java.io.File
import java.nio.file.Files

/** Removes all generated skill files, symlinks, instruction markers, and srcx/wrkx output. */
@DisableCachingByDefault(because = "Deletes generated files")
abstract class CleanTask : DefaultTask() {
    @get:Internal
    abstract val rootDir: Property<File>

    @get:Internal
    abstract val includedBuildDirs: ListProperty<File>

    @TaskAction
    fun run() {
        val home = File(System.getProperty("user.home"))
        val sourceDir = File(home, SkillGenerator.SKILLS_DIR)
        val root = rootDir.get()

        val cleaned =
            cleanSourceSkills(sourceDir) +
                cleanAgentSymlinks(root, sourceDir) +
                cleanAgentDefinitions(root) +
                cleanInstructionFiles(root) +
                cleanContextDirs(root)

        if (cleaned == 0) logger.quiet("opsx-clean: nothing to clean")
    }

    private fun cleanSourceSkills(sourceDir: File): Int {
        if (!sourceDir.exists()) return 0
        val count = sourceDir.listFiles().orEmpty().count { it.isDirectory }
        sourceDir.deleteRecursively()
        logger.quiet("opsx-clean: deleted ~/.clkx/skills/ ($count skills)")
        return 1
    }

    private fun cleanAgentSymlinks(
        root: File,
        sourceDir: File,
    ): Int {
        var count = 0
        (Agent.allSkillsDirs + SkillGenerator.SHARED_SKILLS_DIR).forEach { target ->
            count += cleanSymlinksInDir(File(root, target), root, target, sourceDir)
        }
        return count
    }

    private fun cleanSymlinksInDir(
        dir: File,
        root: File,
        target: String,
        sourceDir: File,
    ): Int {
        if (!dir.exists()) return 0
        val toRemove =
            dir
                .listFiles()
                .orEmpty()
                .filter { isOpsxSymlink(it, sourceDir) || isBrokenSymlink(it) }
        toRemove.forEach { Files.deleteIfExists(it.toPath()) }
        if (toRemove.isNotEmpty()) {
            val rel = runCatching { dir.relativeTo(root).path }.getOrDefault("~/$target")
            logger.quiet("opsx-clean: removed ${toRemove.size} symlinks from $rel")
        }
        return if (toRemove.isNotEmpty()) 1 else 0
    }

    private fun cleanAgentDefinitions(root: File): Int {
        var count = 0
        val home = File(System.getProperty("user.home"))

        // Delete ~/.clkx/agents/ directory
        val agentsDir = File(home, SkillGenerator.AGENTS_DIR)
        if (agentsDir.exists()) {
            agentsDir.deleteRecursively()
            logger.quiet("opsx-clean: deleted ~/.clkx/agents/")
            count++
        }

        // Remove symlinks and files from project-level agent dirs
        Agent.entries.forEach { agent ->
            val agentDir = agent.agentDir ?: return@forEach
            val agentFile = File(root, "$agentDir/opsx.md")
            val path = agentFile.toPath()
            if (Files.isSymbolicLink(path) || agentFile.exists()) {
                Files.deleteIfExists(path)
                logger.quiet("opsx-clean: removed ${agentFile.relativeTo(root).path}")
                count++
            }
        }
        return count
    }

    private fun cleanInstructionFiles(root: File): Int {
        var count = 0
        SkillGenerator.instructionFiles(root).forEach { file ->
            if (file.exists()) {
                removeMarkers(file)
                logger.quiet("opsx-clean: removed OPSX section from ${file.name}")
                count++
            }
        }
        return count
    }

    private fun cleanContextDirs(root: File): Int {
        var count = 0
        cleanDir(root, root, ".srcx")?.let { count++ }
        includedBuildDirs.get().forEach { buildDir ->
            cleanDir(root, buildDir, ".srcx")?.let { count++ }
            cleanDir(root, buildDir, ".wrkx")?.let { count++ }
        }
        return count
    }

    private fun isOpsxSymlink(
        file: File,
        sourceDir: File,
    ): Boolean {
        val path = file.toPath()
        if (!Files.isSymbolicLink(path)) return false
        return runCatching {
            Files.readSymbolicLink(path).startsWith(sourceDir.toPath())
        }.getOrDefault(false)
    }

    private fun isBrokenSymlink(file: File): Boolean {
        val path = file.toPath()
        return Files.isSymbolicLink(path) && !Files.exists(path)
    }

    private fun cleanDir(
        root: File,
        baseDir: File,
        dirName: String,
    ): Boolean? {
        val dir = File(baseDir, dirName)
        if (!dir.exists()) return null
        val count = dir.walkTopDown().filter { it.isFile }.count()
        dir.deleteRecursively()
        val rel = if (baseDir == root) dirName else "${baseDir.name}/$dirName"
        logger.quiet("opsx-clean: deleted $rel ($count files)")
        return true
    }

    private fun removeMarkers(file: File) {
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
