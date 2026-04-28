/**
 * Emitter for the OpenCode CLI.
 *
 * Writes skill files to `.opencode/command/<name>.md` (one flat file per skill,
 * not a subdirectory) and agent definitions to `.opencode/agent/<name>.md`.
 * Upserts a marker block into `AGENTS.md` listing all agents.
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

/** [HostEmitter] for OpenCode CLI -- writes `.opencode/command/` and `.opencode/agent/`. */
object OpenCodeEmitter : HostEmitter {
    override val host = Host.OPENCODE

    override fun emit(
        rootDir: Path,
        manifest: Manifest,
        resources: ResourceTree,
    ): EmitPlan {
        val written = mutableListOf<Path>()

        for (name in resources.listNames("skills").filter { !it.startsWith("_") }) {
            val content = runCatching { resources.readText("skills/$name/SKILL.md") }.getOrNull() ?: continue
            val dest = Path(rootDir, "${host.skillsDir}/$name.md")
            dest.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(dest).buffered().use { it.writeString(content) }
            written += dest
        }

        host.agentDir?.let { dir ->
            for (name in resources.listNames("agents")) {
                val content = resources.readText("agents/$name")
                val dest = Path(rootDir, "$dir/$name")
                dest.parent?.let { SystemFileSystem.createDirectories(it) }
                SystemFileSystem.sink(dest).buffered().use { it.writeString(content) }
                written += dest
            }
        }

        host.instructionFile?.let {
            MarkerBlock.upsert(Path(rootDir, it), buildMarker(manifest))
        }

        return EmitPlan(written, emptyList(), emptyList(), emptyList())
    }

    override fun unmake(
        rootDir: Path,
        manifest: Manifest,
    ): UnmakePlan {
        val deleted = mutableListOf<Path>()
        Path(rootDir, host.skillsDir).let {
            if (SystemFileSystem.exists(it)) {
                // kotlinx-io has no deleteRecursively — bridge to java.io.File
                java.io.File(it.toString()).deleteRecursively()
                deleted += it
            }
        }
        host.agentDir?.let {
            Path(rootDir, it).let { d ->
                if (SystemFileSystem.exists(d)) {
                    java.io.File(d.toString()).deleteRecursively()
                    deleted += d
                }
            }
        }
        host.instructionFile?.let { MarkerBlock.remove(Path(rootDir, it)) }
        return UnmakePlan(deleted, emptyList(), emptyList())
    }

    private fun buildMarker(manifest: Manifest) =
        buildString {
            appendLine("# Agents")
            for ((name, entry) in manifest.agents) appendLine("- **@$name** — ${entry.description}")
        }
}
