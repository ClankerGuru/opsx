/**
 * Activity log reader/writer for opsx changes.
 *
 * Each change directory contains an `activity.log` file -- one JSON-lines entry per agent event.
 * This file defines the [State] enum, the [ActivityEvent] data class, and the [ActivityLog]
 * object that appends and reads those entries.
 */
package zone.clanker.opsx.cli.status

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tri-state outcome of an agent activity event. */
@Serializable
enum class State {
    @SerialName("start")
    START,

    @SerialName("done")
    DONE,

    @SerialName("failed")
    FAILED,
}

/**
 * A single agent activity event serialized as one JSON line in `activity.log`.
 *
 * @property ts ISO-8601 timestamp of when the event occurred
 * @property agent name of the agent that produced this event (e.g. `developer`, `qa`)
 * @property state whether the agent started, completed, or failed the task
 * @property task optional task ID from `tasks.md` (e.g. `a1b2c3`)
 * @property desc optional human-readable description of what happened
 */
@Serializable
data class ActivityEvent(
    @SerialName("ts") val ts: String,
    @SerialName("agent") val agent: String,
    @SerialName("state") val state: State,
    @SerialName("task") val task: String? = null,
    @SerialName("desc") val desc: String? = null,
)

/**
 * Reads and appends [ActivityEvent] entries to a change's `activity.log` file.
 * The log format is JSON-lines -- one JSON object per line, no outer array.
 */
object ActivityLog {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    /**
     * Append a single [event] as a JSON line to `opsx/changes/<change>/activity.log`.
     *
     * @param rootDir workspace root
     * @param change change directory name
     * @param event the event to serialize and append
     */
    fun append(
        rootDir: Path,
        change: String,
        event: ActivityEvent,
    ) {
        val logFile = Path(rootDir, "opsx/changes/$change/activity.log")
        logFile.parent?.let { SystemFileSystem.createDirectories(it) }
        val line = json.encodeToString(ActivityEvent.serializer(), event)
        // kotlinx-io has no appendingSink — bridge to java.io.File for append
        java.io.File(logFile.toString()).appendText("$line\n")
    }

    /**
     * Read all activity events for a change.
     *
     * @param rootDir workspace root
     * @param change change directory name
     * @return list of events in file order; empty if the log file does not exist
     */
    fun read(
        rootDir: Path,
        change: String,
    ): List<ActivityEvent> {
        val logFile = Path(rootDir, "opsx/changes/$change/activity.log")
        if (!SystemFileSystem.exists(logFile)) return emptyList()
        return SystemFileSystem
            .source(logFile)
            .buffered()
            .use { it.readString() }
            .lines()
            .filter { it.isNotBlank() }
            .map { json.decodeFromString(ActivityEvent.serializer(), it) }
    }
}
