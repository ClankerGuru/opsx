/**
 * Deserialization model for the embedded `manifest.json` that catalogues all agents,
 * their skill assignments, and per-CLI directory conventions.
 */
package zone.clanker.opsx.cli.init.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Top-level manifest read from `resources/content/manifest.json` at init time.
 *
 * @property agents map of agent name to [AgentEntry] (skills, description, color)
 * @property clis map of CLI host id to [CliEntry] (directory conventions)
 */
@Serializable
data class Manifest(
    @SerialName("agents") val agents: Map<String, AgentEntry>,
    @SerialName("clis") val clis: Map<String, CliEntry>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Deserialize a [Manifest] from the given JSON [text]. */
        fun parse(text: String): Manifest = json.decodeFromString(serializer(), text)
    }
}

/**
 * One agent's metadata from the manifest.
 *
 * @property description one-line role description shown in generated instruction files
 * @property color Mordant color name used for terminal output
 * @property skills list of skill IDs this agent has access to
 */
@Serializable
data class AgentEntry(
    @SerialName("description") val description: String,
    @SerialName("color") val color: String,
    @SerialName("skills") val skills: List<String> = emptyList(),
)

/**
 * Per-CLI directory and formatting conventions from the manifest.
 *
 * @property skillsDir relative path where skills are written for this CLI
 * @property agentDir relative path where agents are written for this CLI
 * @property instructionFile relative path to the CLI's root instruction markdown
 * @property frontmatter YAML frontmatter format required by the CLI for agent files
 */
@Serializable
data class CliEntry(
    @SerialName("skillsDir") val skillsDir: String,
    @SerialName("agentDir") val agentDir: String,
    @SerialName("instructionFile") val instructionFile: String,
    @SerialName("frontmatter") val frontmatter: String,
)
