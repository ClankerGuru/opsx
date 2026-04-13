package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.skill.SkillGenerator
import java.io.File
import java.nio.file.Files

/** Removes all generated skill files, symlinks, instruction markers, and srcx/wrkx output. */
@DisableCachingByDefault(because = "Deletes generated files")
abstract class CleanTask : DefaultTask() {
    @TaskAction
    fun run() {
        var cleaned = false
        val home = File(System.getProperty("user.home"))
        val sourceDir = File(home, SkillGenerator.SKILLS_DIR)

        // Remove source skills
        if (sourceDir.exists()) {
            val count = sourceDir.listFiles().orEmpty().count { it.name.endsWith(".md") }
            sourceDir.deleteRecursively()
            logger.quiet("opsx-clean: deleted ~/.clkx/skills/ ($count skills)")
            cleaned = true
        }

        // Remove symlinks from agent dirs (only ours)
        SkillGenerator.AGENT_TARGETS.forEach { target ->
            val dir = File(home, target)
            if (dir.exists()) {
                val links = dir.listFiles().orEmpty()
                val toRemove = links.filter { isOpsxSymlink(it, sourceDir) || isBrokenSymlink(it) }
                toRemove.forEach { it.delete() }
                if (toRemove.isNotEmpty()) {
                    logger.quiet("opsx-clean: removed ${toRemove.size} symlinks from ~/$target")
                    cleaned = true
                }
            }
        }

        // Remove OPSX markers from instruction files
        SkillGenerator.instructionFiles(project.rootDir).forEach { file ->
            if (file.exists()) {
                removeMarkers(file)
                logger.quiet("opsx-clean: removed OPSX section from ${file.name}")
                cleaned = true
            }
        }

        cleanDir(project.rootDir, ".srcx")?.let { cleaned = true }

        project.gradle.includedBuilds.forEach { build ->
            cleanDir(build.projectDir, ".srcx")?.let { cleaned = true }
            cleanDir(build.projectDir, ".wrkx")?.let { cleaned = true }
        }

        if (!cleaned) logger.quiet("opsx-clean: nothing to clean")
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
        baseDir: File,
        dirName: String,
    ): Boolean? {
        val dir = File(baseDir, dirName)
        if (!dir.exists()) return null
        val count = dir.walkTopDown().filter { it.isFile }.count()
        dir.deleteRecursively()
        val rel = if (baseDir == project.rootDir) dirName else "${baseDir.name}/$dirName"
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
