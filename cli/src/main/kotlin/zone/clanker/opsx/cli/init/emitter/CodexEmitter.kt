/**
 * Emitter for OpenAI Codex CLI.
 *
 * Writes a `.codex-plugin/` directory containing `plugin.json`, skill files under
 * `skills/<name>/SKILL.md`, and agent definitions. Also merges an `opsx` entry into
 * the user's `~/.agents/plugins/marketplace.json` so Codex discovers the plugin.
 * Upserts a marker block into `AGENTS.md`.
 */
package zone.clanker.opsx.cli.init.emitter

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import zone.clanker.opsx.cli.init.config.Manifest
import zone.clanker.opsx.cli.init.config.MarkerBlock
import zone.clanker.opsx.cli.init.config.ResourceTree
import zone.clanker.opsx.cli.init.host.Host

/** [HostEmitter] for Codex CLI -- writes `.codex-plugin/` and registers in marketplace.json. */
object CodexEmitter : HostEmitter {
    override val host = Host.CODEX
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    override fun emit(
        rootDir: Path,
        manifest: Manifest,
        resources: ResourceTree,
    ): EmitPlan {
        val written = mutableListOf<Path>()

        val pluginJson = Path(rootDir, ".codex-plugin/plugin.json")
        pluginJson.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(pluginJson).buffered().use {
            it.writeString("""{"name":"opsx","version":"1.0.0"}""" + "\n")
        }
        written += pluginJson

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
                val dest = Path(rootDir, "$dir/$name")
                dest.parent?.let { SystemFileSystem.createDirectories(it) }
                SystemFileSystem.sink(dest).buffered().use { it.writeString(content) }
                written += dest
            }
        }

        host.instructionFile?.let {
            MarkerBlock.upsert(Path(rootDir, it), buildMarker(manifest))
        }

        mergeMarketplace(rootDir)

        return EmitPlan(written, emptyList(), emptyList(), emptyList())
    }

    override fun unmake(
        rootDir: Path,
        manifest: Manifest,
    ): UnmakePlan {
        val deleted = mutableListOf<Path>()
        Path(rootDir, ".codex-plugin").let {
            if (SystemFileSystem.exists(it)) {
                // kotlinx-io has no deleteRecursively — bridge to java.io.File
                java.io.File(it.toString()).deleteRecursively()
                deleted += it
            }
        }
        host.instructionFile?.let { MarkerBlock.remove(Path(rootDir, it)) }
        removeMarketplaceEntry()
        return UnmakePlan(deleted, emptyList(), emptyList())
    }

    private fun mergeMarketplace(rootDir: Path) {
        val home = System.getProperty("user.home")
        val file = Path(home, ".agents/plugins/marketplace.json")
        file.parent?.let { SystemFileSystem.createDirectories(it) }
        val existing =
            if (SystemFileSystem.exists(file)) {
                runCatching {
                    json
                        .parseToJsonElement(
                            SystemFileSystem.source(file).buffered().use { it.readString() },
                        ).jsonObject
                }.getOrDefault(JsonObject(emptyMap()))
            } else {
                JsonObject(emptyMap())
            }
        val merged =
            buildJsonObject {
                for ((key, value) in existing) if (key != "opsx") put(key, value)
                put("opsx", buildJsonObject { put("path", Path(rootDir, ".codex-plugin").toString()) })
            }
        SystemFileSystem.sink(file).buffered().use {
            it.writeString(json.encodeToString(JsonElement.serializer(), merged) + "\n")
        }
    }

    private fun removeMarketplaceEntry() {
        val home = System.getProperty("user.home")
        val file = Path(home, ".agents/plugins/marketplace.json")
        if (!SystemFileSystem.exists(file)) return
        val existing =
            runCatching {
                json
                    .parseToJsonElement(
                        SystemFileSystem.source(file).buffered().use { it.readString() },
                    ).jsonObject
            }.getOrNull() ?: return
        if ("opsx" !in existing) return
        val pruned = buildJsonObject { for ((k, v) in existing) if (k != "opsx") put(k, v) }
        SystemFileSystem.sink(file).buffered().use {
            it.writeString(json.encodeToString(JsonElement.serializer(), pruned) + "\n")
        }
    }

    private fun buildMarker(manifest: Manifest) =
        buildString {
            appendLine("# Agents")
            for ((name, entry) in manifest.agents) appendLine("- **@$name** — ${entry.description}")
        }
}
