/**
 * Emitter for GitHub Copilot CLI.
 *
 * Writes skills directly to `.github/skills/<name>/SKILL.md` and agents to
 * `.github/agents/<name>.agent.md`. Also upserts a marker block into
 * `.github/copilot-instructions.md` listing agents and their skill assignments.
 */
package zone.clanker.opsx.cli.init.emitter

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import zone.clanker.opsx.cli.init.config.Manifest
import zone.clanker.opsx.cli.init.config.MarkerBlock
import zone.clanker.opsx.cli.init.config.ResourceTree
import zone.clanker.opsx.cli.init.host.Host

/** [HostEmitter] for Copilot CLI -- writes directly to `.github/skills/` and `.github/agents/`. */
object CopilotEmitter : HostEmitter {
    override val host = Host.COPILOT

    override fun emit(
        rootDir: Path,
        manifest: Manifest,
        resources: ResourceTree,
    ): EmitPlan {
        val written = mutableListOf<Path>()

        for (name in resources.listNames("skills").filter { !it.startsWith("_") }) {
            val content = runCatching { resources.readText("skills/$name/SKILL.md") }.getOrNull() ?: continue
            val dest = Path(rootDir, "${host.skillsDir}/$name/SKILL.md")
            dest.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(dest).buffered().use { it.writeString(content) }
            written += dest
        }

        host.agentDir?.let { dir ->
            for (name in resources.listNames("agents")) {
                val content = resources.readText("agents/$name")
                val base = name.removeSuffix(".md")
                val dest = Path(rootDir, "$dir/$base.agent.md")
                dest.parent?.let { SystemFileSystem.createDirectories(it) }
                SystemFileSystem.sink(dest).buffered().use { it.writeString(content) }
                written += dest
            }
        }

        host.instructionFile?.let {
            MarkerBlock.upsert(Path(rootDir, it), buildMarker(manifest))
        }

        return EmitPlan(
            writtenFiles = written,
            deletedFiles = emptyList(),
            externalCommands = emptyList(),
            warnings = emptyList(),
        )
    }

    override fun unmake(
        rootDir: Path,
        manifest: Manifest,
    ): UnmakePlan {
        host.instructionFile?.let { MarkerBlock.remove(Path(rootDir, it)) }
        return UnmakePlan(
            deletedFiles = emptyList(),
            externalCommands = emptyList(),
            warnings = emptyList(),
        )
    }

    private fun buildMarker(manifest: Manifest) =
        buildString {
            appendLine("# Agents")
            for ((name, entry) in manifest.agents) appendLine("- **@$name** — ${entry.description}")
        }
}
