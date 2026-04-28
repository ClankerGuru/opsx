/**
 * Serialization model and loader/saver for `.opsx/config.json`, the workspace-level
 * configuration file that records which hosts are active and where changes/archives live.
 */
package zone.clanker.opsx.cli.init.config

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Workspace configuration persisted at `.opsx/config.json`.
 *
 * @property version schema version (currently `1`)
 * @property hosts list of active host IDs (e.g. `["claude", "copilot"]`)
 * @property changesDir path relative to workspace root where active changes live
 * @property archiveDir path relative to workspace root where archived changes live
 * @property specsDir path relative to workspace root where spec documents live
 */
@Serializable
data class OpsxConfig(
    val version: Int = 1,
    val hosts: List<String> = emptyList(),
    val changesDir: String = "opsx/changes",
    val archiveDir: String = "opsx/archive",
    val specsDir: String = "opsx/specs",
)

/** Reads and writes [OpsxConfig] from/to `.opsx/config.json` using kotlinx.serialization. */
object ConfigLoader {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /**
     * Load the config from `.opsx/config.json` under [rootDir], or return null if absent.
     */
    fun load(rootDir: Path): OpsxConfig? {
        val file = Path(rootDir, ".opsx/config.json")
        if (!SystemFileSystem.exists(file)) return null
        val text = SystemFileSystem.source(file).buffered().use { it.readString() }
        return json.decodeFromString(OpsxConfig.serializer(), text)
    }

    /**
     * Persist [config] to `.opsx/config.json` under [rootDir], creating parent dirs if needed.
     */
    fun save(
        rootDir: Path,
        config: OpsxConfig,
    ) {
        val file = Path(rootDir, ".opsx/config.json")
        file.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(file).buffered().use {
            it.writeString(json.encodeToString(OpsxConfig.serializer(), config) + "\n")
        }
    }
}
