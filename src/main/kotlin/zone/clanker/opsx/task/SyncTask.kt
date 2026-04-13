package zone.clanker.opsx.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import zone.clanker.opsx.Opsx
import zone.clanker.opsx.skill.SkillGenerator
import java.io.File
import java.nio.file.Files

/** Generates slash commands and instruction files for all agents. */
@DisableCachingByDefault(because = "Generates files in multiple agent-specific directories")
abstract class SyncTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Opsx.SettingsExtension

    @TaskAction
    fun run() {
        val generator = SkillGenerator(project)
        val home = File(System.getProperty("user.home"))
        val sourceDir = File(home, SkillGenerator.SKILLS_DIR)

        // Clean source dir (we own everything here)
        if (sourceDir.exists()) {
            val mdFiles = sourceDir.listFiles().orEmpty()
            mdFiles.filter { it.name.endsWith(".md") }.forEach { it.delete() }
        }

        // Clean only symlinks pointing to our source dir from agent dirs
        SkillGenerator.AGENT_TARGETS.forEach { target ->
            val dir = File(home, target)
            if (dir.exists()) {
                val links = dir.listFiles().orEmpty()
                links.filter { isOpsxSymlink(it, sourceDir) }.forEach { it.delete() }
            }
        }

        generator.generate()

        val fileCount = sourceDir.listFiles()?.count { it.name.endsWith(".md") } ?: 0
        val agentCount = SkillGenerator.AGENT_TARGETS.size
        logger.quiet("opsx-sync: $fileCount skills in ~/.clkx/skills/, symlinked to $agentCount agents")
    }

    private fun isOpsxSymlink(
        file: File,
        sourceDir: File,
    ): Boolean {
        val path = file.toPath()
        if (!Files.isSymbolicLink(path)) return false
        return runCatching {
            val target = Files.readSymbolicLink(path)
            target.startsWith(sourceDir.toPath())
        }.getOrDefault(false)
    }
}
